package com.paymentx.reconciliation.service.impl;

import com.paymentx.reconciliation.cache.InternalTransactionCacheService;
import com.paymentx.reconciliation.config.ReconciliationProperties;
import com.paymentx.reconciliation.dto.ExternalSettlementRecord;
import com.paymentx.reconciliation.dto.InternalTransaction;
import com.paymentx.reconciliation.entity.BatchStatus;
import com.paymentx.reconciliation.entity.MismatchRecord;
import com.paymentx.reconciliation.entity.ReconciliationBatch;
import com.paymentx.reconciliation.entity.ReconciliationRecord;
import com.paymentx.reconciliation.entity.ReconciliationStatus;
import com.paymentx.reconciliation.entity.ReconciliationSummary;
import com.paymentx.reconciliation.entity.SettlementFile;
import com.paymentx.reconciliation.entity.SettlementFileStatus;
import com.paymentx.reconciliation.event.BatchCompletionApplicationEvent;
import com.paymentx.reconciliation.event.MismatchDetectedEvent;
import com.paymentx.reconciliation.event.MismatchDetectionApplicationEvent;
import com.paymentx.reconciliation.event.ReconciliationCompletedEvent;
import com.paymentx.reconciliation.event.SettlementCompletedEvent;
import com.paymentx.reconciliation.event.SettlementCompletionApplicationEvent;
import com.paymentx.reconciliation.metrics.ReconciliationMetrics;
import com.paymentx.reconciliation.repository.MismatchRecordRepository;
import com.paymentx.reconciliation.repository.ReconciliationBatchRepository;
import com.paymentx.reconciliation.repository.ReconciliationRecordRepository;
import com.paymentx.reconciliation.repository.ReconciliationSummaryRepository;
import com.paymentx.reconciliation.repository.SettlementFileRepository;
import com.paymentx.reconciliation.service.importer.SettlementFileImporter;
import com.paymentx.reconciliation.service.importer.SettlementFileImporterFactory;
import com.paymentx.reconciliation.service.matching.MatchingEngine;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * WHY @Async lives on a separate bean from ReconciliationServiceImpl:
 * identical Spring self-invocation rationale as NotificationDispatcher.
 *
 * WHY the whole batch runs inside ONE @Transactional(REQUIRES_NEW), not
 * per-record transactions: a reconciliation batch is one atomic unit of
 * work by nature - "half a batch committed, half rolled back" would
 * leave ReconciliationSummary counts inconsistent with the actual
 * ReconciliationRecord rows. Batch INSERTS within this transaction still
 * use saveAll() in chunks purely to avoid N+1 round-trips, not to create
 * separate transaction boundaries.
 */
@Component
@RequiredArgsConstructor
@Slf4j
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * ReconciliationBatchProcessor is a component in the reconciliation module of PaymentX. It lives in package com.paymentx.reconciliation.service.impl and participates in reconciliation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through reconciliation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * ReconciliationBatchProcessor PaymentX ke reconciliation module ka ek component hai. Ye com.paymentx.reconciliation.service.impl package me hai aur reconciliation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise reconciliation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class ReconciliationBatchProcessor {

    private final SettlementFileImporterFactory settlementFileImporterFactory;
    private final SettlementFileRepository settlementFileRepository;
    private final ReconciliationBatchRepository reconciliationBatchRepository;
    private final ReconciliationRecordRepository reconciliationRecordRepository;
    private final MismatchRecordRepository mismatchRecordRepository;
    private final ReconciliationSummaryRepository reconciliationSummaryRepository;
    private final MatchingEngine matchingEngine;
    private final InternalTransactionCacheService internalTransactionCacheService;
    private final ReconciliationProperties reconciliationProperties;
    private final ReconciliationMetrics reconciliationMetrics;
    private final ApplicationEventPublisher applicationEventPublisher;

    // WHY @Transactional lives on runAsync(), not on the run() method it
    // calls: runAsync() -> run(batchId) was a plain `this.run(...)` call -
    // self-invocation, which bypasses Spring's AOP proxy entirely, so
    // @Transactional(REQUIRES_NEW) on run() silently never took effect
    // (Spring can only apply an annotation-driven aspect when the call
    // arrives through the proxy, i.e. from a DIFFERENT bean - exactly the
    // reason this class's own javadoc gives for why @Async lives on a
    // SEPARATE bean from ReconciliationServiceImpl, but that same
    // self-invocation trap was still present one level down). Each
    // .save(batch) call inside the "transactional" method was actually
    // running in its own separate auto-committing transaction, so the
    // in-memory `batch` entity went stale between the RUNNING save and the
    // COMPLETED save, and Hibernate's optimistic-lock check on the second
    // save correctly detected the mismatch and threw
    // ObjectOptimisticLockingFailureException - silently swallowed by
    // Spring's default @Async exception handler, leaving the batch stuck
    // at RUNNING forever with no visible error anywhere. Found via a real
    // end-to-end reconciliation run during Phase 1 validation (DB showed a
    // correctly-matched ReconciliationRecord but a batch that never left
    // RUNNING). Moving @Transactional onto runAsync() - which callers
    // outside this class DO invoke through the proxy - makes it actually
    // apply to the whole unit of work.
    @Async("reconciliationBatchExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void runAsync(UUID batchId) {
        run(batchId);
    }

    private void run(UUID batchId) {
        Timer.Sample timerSample = reconciliationMetrics.startTimer();
        ReconciliationBatch batch = reconciliationBatchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalStateException("Batch not found at execution time: " + batchId));

        batch.setStatus(BatchStatus.RUNNING);
        batch.setStartedAt(OffsetDateTime.now());
        reconciliationBatchRepository.save(batch);

        List<ReconciliationRecord> allRecords = new ArrayList<>();

        try {
            if (batch.getSettlementFileId() != null) {
                allRecords.addAll(processSettlementFile(batch));
            }
            allRecords.addAll(processUnclaimedPendingSettlements(batch));

            insertRecordsBatched(allRecords);
            List<MismatchRecord> mismatches = createMismatchRecords(batch, allRecords);
            buildAndSaveSummary(batch, allRecords);

            batch.setStatus(mismatches.isEmpty() ? BatchStatus.COMPLETED : BatchStatus.PARTIALLY_COMPLETED);
            batch.setTotalRecords(allRecords.size());
            batch.setMatchedCount((int) allRecords.stream().filter(r -> r.getReconciliationStatus() == ReconciliationStatus.MATCHED).count());
            batch.setMismatchCount(mismatches.size());
            batch.setCompletedAt(OffsetDateTime.now());
            reconciliationBatchRepository.save(batch);

            publishCompletionEvents(batch, mismatches, allRecords);

        } catch (Exception e) {
            log.error("Reconciliation batch failed batchId={}", batchId, e);
            batch.setStatus(BatchStatus.FAILED);
            batch.setFailureReason(e.getMessage());
            batch.setCompletedAt(OffsetDateTime.now());
            reconciliationBatchRepository.save(batch);
            reconciliationMetrics.recordBatchFailure(batch.getBatchType().name());
        } finally {
            reconciliationMetrics.recordBatchDuration(timerSample, batch.getBatchType().name());
        }
    }

    private List<ReconciliationRecord> processSettlementFile(ReconciliationBatch batch) throws Exception {
        SettlementFile settlementFile = settlementFileRepository.findById(batch.getSettlementFileId())
                .orElseThrow(() -> new IllegalStateException("SettlementFile not found: " + batch.getSettlementFileId()));

        if (settlementFile.getStoragePath() == null) {
            throw new IllegalStateException("SettlementFile has no stored file path: " + settlementFile.getId());
        }

        SettlementFileImporter importer = settlementFileImporterFactory.getImporter(settlementFile.getFileType())
                .orElseThrow(() -> new IllegalStateException("No importer registered for file type: " + settlementFile.getFileType()));

        Timer.Sample importTimer = reconciliationMetrics.startTimer();
        List<ReconciliationRecord> records = new ArrayList<>();
        int recordCount = 0;

        try (var fileInputStream = java.nio.file.Files.newInputStream(java.nio.file.Path.of(settlementFile.getStoragePath()));
             var stream = importer.parse(fileInputStream)) {
            var iterator = stream.iterator();
            while (iterator.hasNext()) {
                ExternalSettlementRecord external = iterator.next();
                Optional<InternalTransaction> internal = internalTransactionCacheService.get(external.paymentId());
                ReconciliationRecord record = matchingEngine.match(batch.getId(), external, internal);
                records.add(record);
                reconciliationMetrics.recordClassification(record.getReconciliationStatus());
                recordCount++;
            }
        }

        settlementFile.setRecordCount(recordCount);
        settlementFile.setStatus(SettlementFileStatus.PROCESSED);
        settlementFileRepository.save(settlementFile);
        reconciliationMetrics.recordFileImportDuration(importTimer, settlementFile.getFileType().name());

        return records;
    }

    /** For INCREMENTAL/FULL batches with no file, and as the final pass
     *  for file-based batches too: any internal transaction still marked
     *  "pending settlement" that this batch never claimed via a matching
     *  external row is MISSING. */
    private List<ReconciliationRecord> processUnclaimedPendingSettlements(ReconciliationBatch batch) {
        List<ReconciliationRecord> missingRecords = new ArrayList<>();
        for (String paymentId : internalTransactionCacheService.getPendingSettlements()) {
            internalTransactionCacheService.get(paymentId).ifPresent(internal -> {
                ReconciliationRecord missing = matchingEngine.buildMissingRecord(batch.getId(), internal);
                missingRecords.add(missing);
                reconciliationMetrics.recordClassification(ReconciliationStatus.MISSING);
            });
        }
        return missingRecords;
    }

    private void insertRecordsBatched(List<ReconciliationRecord> records) {
        int batchSize = reconciliationProperties.getBatch().getInsertBatchSize();
        for (int i = 0; i < records.size(); i += batchSize) {
            int end = Math.min(i + batchSize, records.size());
            reconciliationRecordRepository.saveAll(records.subList(i, end));
        }
    }

    private List<MismatchRecord> createMismatchRecords(ReconciliationBatch batch, List<ReconciliationRecord> records) {
        List<MismatchRecord> mismatches = new ArrayList<>();
        for (ReconciliationRecord record : records) {
            if (record.getReconciliationStatus() == ReconciliationStatus.MATCHED) {
                continue;
            }
            MismatchRecord mismatch = MismatchRecord.builder()
                    .reconciliationRecordId(record.getId())
                    .batchId(batch.getId())
                    .mismatchType(record.getReconciliationStatus())
                    .description(buildMismatchDescription(record))
                    .resolved(false)
                    .build();
            mismatches.add(mismatch);
        }
        if (!mismatches.isEmpty()) {
            int batchSize = reconciliationProperties.getBatch().getInsertBatchSize();
            for (int i = 0; i < mismatches.size(); i += batchSize) {
                int end = Math.min(i + batchSize, mismatches.size());
                mismatchRecordRepository.saveAll(mismatches.subList(i, end));
            }
        }
        return mismatches;
    }

    private String buildMismatchDescription(ReconciliationRecord record) {
        return record.getReconciliationStatus() + " for paymentId=" + record.getPaymentId()
                + " internalAmount=" + record.getInternalAmount() + " externalAmount=" + record.getExternalAmount();
    }

    private void buildAndSaveSummary(ReconciliationBatch batch, List<ReconciliationRecord> records) {
        ReconciliationSummary summary = ReconciliationSummary.builder()
                .batchId(batch.getId())
                .totalRecords(records.size())
                .matchedCount(countByStatus(records, ReconciliationStatus.MATCHED))
                .missingCount(countByStatus(records, ReconciliationStatus.MISSING))
                .duplicateCount(countByStatus(records, ReconciliationStatus.DUPLICATE))
                .amountMismatchCount(countByStatus(records, ReconciliationStatus.AMOUNT_MISMATCH))
                .currencyMismatchCount(countByStatus(records, ReconciliationStatus.CURRENCY_MISMATCH))
                .statusMismatchCount(countByStatus(records, ReconciliationStatus.STATUS_MISMATCH))
                .settlementDelayCount(countByStatus(records, ReconciliationStatus.SETTLEMENT_DELAY))
                .lateSettlementCount(countByStatus(records, ReconciliationStatus.LATE_SETTLEMENT))
                .orphanCount(countByStatus(records, ReconciliationStatus.ORPHAN))
                .unexpectedSettlementCount(countByStatus(records, ReconciliationStatus.UNEXPECTED_SETTLEMENT))
                .generatedAt(OffsetDateTime.now())
                .build();
        reconciliationSummaryRepository.save(summary);
    }

    private int countByStatus(List<ReconciliationRecord> records, ReconciliationStatus status) {
        return (int) records.stream().filter(r -> r.getReconciliationStatus() == status).count();
    }

    private void publishCompletionEvents(ReconciliationBatch batch, List<MismatchRecord> mismatches, List<ReconciliationRecord> allRecords) {
        applicationEventPublisher.publishEvent(new BatchCompletionApplicationEvent(this,
                ReconciliationCompletedEvent.builder()
                        .batchId(batch.getId())
                        .totalRecords(batch.getTotalRecords())
                        .matchedCount(batch.getMatchedCount())
                        .mismatchCount(batch.getMismatchCount())
                        .completedAt(batch.getCompletedAt())
                        .build(),
                null));

        for (MismatchRecord mismatch : mismatches) {
            applicationEventPublisher.publishEvent(new MismatchDetectionApplicationEvent(this,
                    MismatchDetectedEvent.builder()
                            .mismatchRecordId(mismatch.getId())
                            .batchId(batch.getId())
                            .paymentId(findPaymentId(allRecords, mismatch.getReconciliationRecordId()))
                            .mismatchType(mismatch.getMismatchType())
                            .build(),
                    null));
        }

        allRecords.stream()
                .filter(r -> r.getReconciliationStatus() == ReconciliationStatus.MATCHED)
                .forEach(r -> applicationEventPublisher.publishEvent(new SettlementCompletionApplicationEvent(this,
                        SettlementCompletedEvent.builder()
                                .reconciliationRecordId(r.getId())
                                .paymentId(r.getPaymentId())
                                .participantId(r.getParticipantId())
                                .build(),
                        null)));
    }

    private String findPaymentId(List<ReconciliationRecord> records, UUID recordId) {
        return records.stream().filter(r -> r.getId().equals(recordId)).findFirst().map(ReconciliationRecord::getPaymentId).orElse(null);
    }
}
