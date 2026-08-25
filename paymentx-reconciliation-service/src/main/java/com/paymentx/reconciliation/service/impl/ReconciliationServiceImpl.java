package com.paymentx.reconciliation.service.impl;

import com.paymentx.common.dto.PageResponse;
import com.paymentx.common.exception.ConflictException;
import com.paymentx.common.exception.ResourceNotFoundException;
import com.paymentx.reconciliation.config.ReconciliationProperties;
import com.paymentx.reconciliation.dto.BatchResponse;
import com.paymentx.reconciliation.dto.MismatchRecordResponse;
import com.paymentx.reconciliation.dto.MismatchSearchCriteria;
import com.paymentx.reconciliation.dto.ReconciliationRecordResponse;
import com.paymentx.reconciliation.dto.ReconciliationSummaryResponse;
import com.paymentx.reconciliation.dto.SettlementFileResponse;
import com.paymentx.reconciliation.dto.StartReconciliationRequest;
import com.paymentx.reconciliation.entity.BatchStatus;
import com.paymentx.reconciliation.entity.MismatchRecord;
import com.paymentx.reconciliation.entity.ReconciliationBatch;
import com.paymentx.reconciliation.entity.ReconciliationRecord;
import com.paymentx.reconciliation.entity.SettlementFile;
import com.paymentx.reconciliation.entity.SettlementFileStatus;
import com.paymentx.reconciliation.entity.SettlementFileType;
import com.paymentx.reconciliation.event.BatchStartRequestedApplicationEvent;
import com.paymentx.reconciliation.mapper.ReconciliationMapper;
import com.paymentx.reconciliation.repository.MismatchRecordRepository;
import com.paymentx.reconciliation.repository.MismatchRecordSpecifications;
import com.paymentx.reconciliation.repository.ReconciliationBatchRepository;
import com.paymentx.reconciliation.repository.ReconciliationRecordRepository;
import com.paymentx.reconciliation.repository.ReconciliationSummaryRepository;
import com.paymentx.reconciliation.repository.SettlementFileRepository;
import com.paymentx.reconciliation.service.ReconciliationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * ReconciliationServiceImpl is a service in the reconciliation module of PaymentX. It lives in package com.paymentx.reconciliation.service.impl and participates in reconciliation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through reconciliation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * ReconciliationServiceImpl PaymentX ke reconciliation module ka ek service hai. Ye com.paymentx.reconciliation.service.impl package me hai aur reconciliation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise reconciliation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class ReconciliationServiceImpl implements ReconciliationService {

    private final SettlementFileRepository settlementFileRepository;
    private final ReconciliationBatchRepository reconciliationBatchRepository;
    private final ReconciliationRecordRepository reconciliationRecordRepository;
    private final MismatchRecordRepository mismatchRecordRepository;
    private final ReconciliationSummaryRepository reconciliationSummaryRepository;
    private final ReconciliationMapper reconciliationMapper;
    // WHY ApplicationEventPublisher, not a direct ReconciliationBatchProcessor
    // reference: see BatchStartRequestedApplicationEvent's javadoc - calling
    // runAsync() directly from within this @Transactional class fires the
    // async batch processor before this method's own transaction commits,
    // a real race the async thread loses (it can't see the just-inserted,
    // not-yet-committed batch row). Publishing an event and letting an
    // AFTER_COMMIT listener trigger the async call instead guarantees
    // correct ordering.
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ReconciliationProperties reconciliationProperties;

    @Override
    public SettlementFileResponse uploadSettlementFile(MultipartFile file) {
        SettlementFileType fileType = resolveFileType(file.getOriginalFilename());
        UUID storageKey = UUID.randomUUID();
        java.nio.file.Path storagePath = resolveStoragePath(storageKey, file.getOriginalFilename());

        String checksum;
        try {
            java.nio.file.Files.createDirectories(storagePath.getParent());
            // Streamed copy (not file.getBytes()) - the file is never
            // fully materialized as a single in-memory byte[], keeping
            // upload memory usage bounded regardless of file size (see
            // SettlementFile.storagePath's javadoc for the full
            // rationale). The checksum is computed via a digest-wrapped
            // stream in the same pass, avoiding a second full read.
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var in = new java.security.DigestInputStream(file.getInputStream(), digest);
                 var out = java.nio.file.Files.newOutputStream(storagePath)) {
                in.transferTo(out);
            }
            checksum = HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to persist uploaded settlement file", e);
        }

        if (settlementFileRepository.existsByChecksumHash(checksum)) {
            deleteQuietly(storagePath);
            throw new ConflictException("SETTLEMENT_FILE_DUPLICATE",
                    "A file with identical content has already been uploaded (checksum=" + checksum + ")");
        }

        SettlementFile settlementFile = SettlementFile.builder()
                .fileName(file.getOriginalFilename())
                .fileType(fileType)
                .status(SettlementFileStatus.UPLOADED)
                .fileSizeBytes(file.getSize())
                .checksumHash(checksum)
                .storagePath(storagePath.toString())
                .build();

        SettlementFile saved = settlementFileRepository.save(settlementFile);
        log.info("Settlement file uploaded id={} fileName={} fileType={} sizeBytes={}",
                saved.getId(), saved.getFileName(), saved.getFileType(), saved.getFileSizeBytes());

        return reconciliationMapper.toResponse(saved);
    }

    private java.nio.file.Path resolveStoragePath(UUID fileId, String originalFileName) {
        String extension = originalFileName != null && originalFileName.contains(".")
                ? originalFileName.substring(originalFileName.lastIndexOf('.')) : "";
        return java.nio.file.Path.of(reconciliationProperties.getStorage().getSettlementFileDirectory(), fileId + extension);
    }

    private void deleteQuietly(java.nio.file.Path path) {
        try {
            java.nio.file.Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Failed to delete rejected duplicate upload at path={}", path, e);
        }
    }

    @Override
    public BatchResponse startReconciliation(StartReconciliationRequest request, String triggeredBy) {
        if (request.settlementFileId() != null) {
            SettlementFile settlementFile = settlementFileRepository.findById(request.settlementFileId())
                    .orElseThrow(() -> new ResourceNotFoundException("SettlementFile", request.settlementFileId().toString()));
            if (settlementFile.getStatus() == SettlementFileStatus.PROCESSED) {
                throw new ConflictException("SETTLEMENT_FILE_ALREADY_PROCESSED",
                        "SettlementFile '" + request.settlementFileId() + "' has already been reconciled");
            }
        }

        ReconciliationBatch batch = ReconciliationBatch.builder()
                .batchType(request.batchType())
                .status(BatchStatus.PENDING)
                .settlementFileId(request.settlementFileId())
                .windowFrom(request.windowFrom())
                .windowTo(request.windowTo())
                .triggeredBy(triggeredBy)
                .build();

        ReconciliationBatch saved = reconciliationBatchRepository.save(batch);
        log.info("Reconciliation batch created id={} batchType={} triggeredBy={}", saved.getId(), saved.getBatchType(), triggeredBy);

        applicationEventPublisher.publishEvent(new BatchStartRequestedApplicationEvent(this, saved.getId()));

        return reconciliationMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public BatchResponse getBatchStatus(UUID batchId) {
        return reconciliationMapper.toResponse(findBatchOrThrow(batchId));
    }

    @Override
    @Transactional(readOnly = true)
    public ReconciliationSummaryResponse getSummary(UUID batchId) {
        findBatchOrThrow(batchId);
        return reconciliationSummaryRepository.findByBatchId(batchId)
                .map(reconciliationMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("ReconciliationSummary", "batchId=" + batchId));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MismatchRecordResponse> searchMismatches(MismatchSearchCriteria criteria, int page, int size) {
        Specification<MismatchRecord> spec = Specification
                .allOf(MismatchRecordSpecifications.hasBatchId(criteria.batchId()))
                .and(MismatchRecordSpecifications.hasMismatchType(criteria.mismatchType()))
                .and(MismatchRecordSpecifications.isResolved(criteria.resolved()));

        var pageResult = mismatchRecordRepository.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

        return PageResponse.of(
                pageResult.getContent().stream().map(reconciliationMapper::toResponse).toList(),
                pageResult.getNumber(), pageResult.getSize(), pageResult.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReconciliationRecordResponse> findRecordsByReference(String paymentReference) {
        return reconciliationRecordRepository.findByReferenceIdOrderByCreatedAtDesc(paymentReference).stream()
                .map(reconciliationMapper::toResponse)
                .toList();
    }

    @Override
    public MismatchRecordResponse resolveMismatch(UUID mismatchId, String resolvedBy, String notes) {
        MismatchRecord mismatch = mismatchRecordRepository.findById(mismatchId)
                .orElseThrow(() -> new ResourceNotFoundException("MismatchRecord", mismatchId.toString()));

        if (Boolean.TRUE.equals(mismatch.getResolved())) {
            throw new ConflictException("MISMATCH_ALREADY_RESOLVED", "Mismatch '" + mismatchId + "' is already resolved");
        }

        mismatch.setResolved(true);
        mismatch.setResolvedBy(resolvedBy);
        mismatch.setResolvedAt(OffsetDateTime.now());
        mismatch.setResolutionNotes(notes);

        MismatchRecord saved = mismatchRecordRepository.save(mismatch);
        log.info("Mismatch resolved id={} resolvedBy={}", mismatchId, resolvedBy);
        return reconciliationMapper.toResponse(saved);
    }

    @Override
    public BatchResponse reprocessBatch(UUID batchId, String triggeredBy) {
        ReconciliationBatch batch = findBatchOrThrow(batchId);

        if (batch.getStatus() == BatchStatus.RUNNING || batch.getStatus() == BatchStatus.PENDING) {
            throw new ConflictException("BATCH_ALREADY_IN_PROGRESS",
                    "Batch '" + batchId + "' is currently " + batch.getStatus() + " and cannot be reprocessed");
        }

        batch.setStatus(BatchStatus.PENDING);
        batch.setStartedAt(null);
        batch.setCompletedAt(null);
        batch.setFailureReason(null);
        batch.setTriggeredBy(triggeredBy);
        ReconciliationBatch saved = reconciliationBatchRepository.save(batch);

        log.info("Reconciliation batch reprocessing requested id={} triggeredBy={}", batchId, triggeredBy);
        applicationEventPublisher.publishEvent(new BatchStartRequestedApplicationEvent(this, saved.getId()));

        return reconciliationMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] downloadReport(UUID batchId) {
        findBatchOrThrow(batchId);
        List<ReconciliationRecord> records = reconciliationRecordRepository.findByBatchId(batchId);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PrintWriter writer = new PrintWriter(out)) {
            writer.println("paymentId,referenceId,participantId,internalAmount,externalAmount,internalCurrency,externalCurrency,reconciliationStatus");
            for (ReconciliationRecord record : records) {
                writer.println(String.join(",",
                        safe(record.getPaymentId()), safe(record.getReferenceId()), safe(record.getParticipantId()),
                        safe(record.getInternalAmount()), safe(record.getExternalAmount()),
                        safe(record.getInternalCurrency()), safe(record.getExternalCurrency()),
                        safe(record.getReconciliationStatus())));
            }
        }
        return out.toByteArray();
    }

    private String safe(Object value) {
        return value == null ? "" : value.toString();
    }

    private ReconciliationBatch findBatchOrThrow(UUID batchId) {
        return reconciliationBatchRepository.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException("ReconciliationBatch", batchId.toString()));
    }

    private SettlementFileType resolveFileType(String fileName) {
        if (fileName == null) {
            throw new IllegalArgumentException("File name is required to determine settlement file type");
        }
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".csv")) {
            return SettlementFileType.CSV;
        }
        if (lower.endsWith(".json")) {
            return SettlementFileType.JSON;
        }
        throw new IllegalArgumentException("Unsupported settlement file extension: " + fileName);
    }
}
