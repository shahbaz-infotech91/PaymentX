package com.paymentx.reporting.service.impl;

import com.paymentx.common.dto.PageResponse;
import com.paymentx.common.exception.ConflictException;
import com.paymentx.common.exception.ResourceNotFoundException;
import com.paymentx.reporting.config.ReportingProperties;
import com.paymentx.reporting.cache.ReportResultCacheService;
import com.paymentx.reporting.dto.GenerateReportRequest;
import com.paymentx.reporting.dto.ReportExecutionResponse;
import com.paymentx.reporting.dto.ReportResponse;
import com.paymentx.reporting.dto.ReportResultResponse;
import com.paymentx.reporting.dto.ReportSearchCriteria;
import com.paymentx.reporting.dto.ScheduleReportRequest;
import com.paymentx.reporting.entity.ReportExecution;
import com.paymentx.reporting.entity.ReportExport;
import com.paymentx.reporting.entity.ReportFormat;
import com.paymentx.reporting.entity.ReportFrequency;
import com.paymentx.reporting.entity.ReportMetadata;
import com.paymentx.reporting.entity.ReportRequest;
import com.paymentx.reporting.entity.ReportResult;
import com.paymentx.reporting.entity.ReportSchedule;
import com.paymentx.reporting.entity.ReportStatus;
import com.paymentx.reporting.event.ReportCompletedApplicationEvent;
import com.paymentx.reporting.event.ReportCompletedEvent;
import com.paymentx.reporting.mapper.ReportMapper;
import com.paymentx.reporting.metrics.ReportingMetrics;
import com.paymentx.reporting.repository.ReportExecutionRepository;
import com.paymentx.reporting.repository.ReportExportRepository;
import com.paymentx.reporting.repository.ReportMetadataRepository;
import com.paymentx.reporting.repository.ReportRepository;
import com.paymentx.reporting.repository.ReportRequestRepository;
import com.paymentx.reporting.repository.ReportRequestSpecifications;
import com.paymentx.reporting.repository.ReportResultRepository;
import com.paymentx.reporting.repository.ReportScheduleRepository;
import com.paymentx.reporting.service.ReportingService;
import com.paymentx.reporting.service.exporter.ReportExporterFactory;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * ====================================================================
 * ENGLISH:
 * The application-facing implementation every REST endpoint and the
 * scheduler call - creates ReportRequest/ReportExecution rows, kicks
 * off async generation via ReportGenerationProcessor, and handles the
 * on-demand export-and-download path (writing exported files to disk,
 * matching Reconciliation Service's SettlementFile storage pattern
 * exactly, rather than storing large binary blobs in Postgres).
 *
 * HINGLISH:
 * Application-facing implementation jise har REST endpoint aur
 * scheduler call karta hai - ReportRequest/ReportExecution rows
 * banata hai, ReportGenerationProcessor ke through async generation
 * shuru karta hai, aur on-demand export-and-download path handle karta
 * hai (exported files ko disk pe likhta hai, Reconciliation Service ke
 * SettlementFile storage pattern jaisa hi).
 * ====================================================================
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ReportingServiceImpl implements ReportingService {

    private final ReportRepository reportRepository;
    private final ReportRequestRepository reportRequestRepository;
    private final ReportExecutionRepository reportExecutionRepository;
    private final ReportResultRepository reportResultRepository;
    private final ReportMetadataRepository reportMetadataRepository;
    private final ReportScheduleRepository reportScheduleRepository;
    private final ReportExportRepository reportExportRepository;
    private final ReportMapper reportMapper;
    private final ReportGenerationProcessor reportGenerationProcessor;
    private final ReportExporterFactory reportExporterFactory;
    private final ReportResultCacheService reportResultCacheService;
    private final ReportingProperties reportingProperties;
    private final ReportingMetrics reportingMetrics;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    @Transactional(readOnly = true)
    public List<ReportResponse> listReports() {
        return reportRepository.findAll().stream().map(reportMapper::toResponse).toList();
    }

    @Override
    public ReportExecutionResponse generateReport(GenerateReportRequest generateRequest, String requestedBy, String correlationId) {
        ReportRequest request = ReportRequest.builder()
                .reportType(generateRequest.reportType())
                .reportFormat(generateRequest.reportFormat())
                .frequency(ReportFrequency.ON_DEMAND)
                .dateFrom(generateRequest.dateFrom())
                .dateTo(generateRequest.dateTo())
                .participantId(generateRequest.participantId())
                .currency(generateRequest.currency())
                .additionalFilters(generateRequest.additionalFilters())
                .requestedBy(requestedBy)
                .correlationId(correlationId)
                .build();
        ReportRequest savedRequest = reportRequestRepository.save(request);

        ReportExecution execution = ReportExecution.builder()
                .reportRequestId(savedRequest.getId())
                .status(ReportStatus.PENDING)
                .retryCount(0)
                .build();
        ReportExecution savedExecution = reportExecutionRepository.save(execution);

        log.info("Report generation requested executionId={} reportType={} requestedBy={}",
                savedExecution.getId(), generateRequest.reportType(), requestedBy);

        // WHY defer to afterCommit() instead of calling generateAsync() directly
        // here: the async task runs on a separate thread in its own
        // REQUIRES_NEW transaction (see ReportGenerationProcessor), which
        // re-reads this ReportExecution/ReportRequest by id on a different
        // connection. Firing it while this transaction is still open is a
        // read-committed-visibility race - the background thread can (and,
        // in practice, reliably does) query before this transaction's INSERT
        // is committed, throwing "ReportExecution not found at generation
        // time". Registering the trigger as an afterCommit() synchronization
        // guarantees the rows are visible before generation ever starts.
        UUID executionId = savedExecution.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                reportGenerationProcessor.generateAsync(executionId);
            }
        });

        return reportMapper.toResponse(savedExecution);
    }

    @Override
    @Transactional(readOnly = true)
    public ReportExecutionResponse getExecutionStatus(UUID executionId) {
        return reportMapper.toResponse(findExecutionOrThrow(executionId));
    }

    @Override
    @Transactional(readOnly = true)
    public ReportResultResponse getResult(UUID executionId) {
        var cached = reportResultCacheService.get(executionId);
        if (cached.isPresent()) {
            return cached.get();
        }

        ReportExecution execution = findExecutionOrThrow(executionId);
        if (execution.getStatus() != ReportStatus.COMPLETED) {
            throw new ConflictException("REPORT_NOT_COMPLETED",
                    "Report execution '" + executionId + "' is " + execution.getStatus() + ", result not yet available");
        }

        ReportResult result = reportResultRepository.findByReportExecutionId(executionId)
                .orElseThrow(() -> new ResourceNotFoundException("ReportResult", "executionId=" + executionId));
        ReportMetadata metadata = reportMetadataRepository.findByReportExecutionId(executionId).orElse(null);

        ReportResultResponse response = new ReportResultResponse(
                executionId, result.getResultData(),
                metadata != null ? metadata.getSourceServices() : null,
                metadata != null ? metadata.getDataAsOf() : null);

        reportResultCacheService.put(executionId, response);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReportExecutionResponse> searchExecutions(ReportSearchCriteria criteria, int page, int size) {
        Specification<ReportRequest> spec = Specification
                .allOf(ReportRequestSpecifications.hasReportType(criteria.reportType()))
                .and(ReportRequestSpecifications.hasParticipantId(criteria.participantId()))
                .and(ReportRequestSpecifications.hasCurrency(criteria.currency()))
                .and(ReportRequestSpecifications.hasCorrelationId(criteria.correlationId()))
                .and(ReportRequestSpecifications.createdAfter(criteria.fromDate()))
                .and(ReportRequestSpecifications.createdBefore(criteria.toDate()));

        var pageResult = reportRequestRepository.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

        // Batch-fetch every execution for this page's request ids in
        // ONE query - avoids the N+1 (or a full-table-scan-per-page,
        // which is worse) that calling findAll() per row would cause.
        List<UUID> requestIds = pageResult.getContent().stream().map(ReportRequest::getId).toList();
        List<ReportExecutionResponse> executions = reportExecutionRepository.findByReportRequestIdIn(requestIds).stream()
                .map(reportMapper::toResponse)
                .toList();

        return PageResponse.of(executions, pageResult.getNumber(), pageResult.getSize(), pageResult.getTotalElements());
    }

    @Override
    public byte[] downloadReport(UUID executionId, ReportFormat format) {
        ReportExecution execution = findExecutionOrThrow(executionId);
        if (execution.getStatus() != ReportStatus.COMPLETED) {
            throw new ConflictException("REPORT_NOT_COMPLETED",
                    "Report execution '" + executionId + "' is " + execution.getStatus() + ", cannot export yet");
        }

        var existingExport = reportExportRepository.findByReportExecutionIdAndFormat(executionId, format);
        if (existingExport.isPresent()) {
            reportingMetrics.recordDownload(format.name());
            incrementDownloadCount(existingExport.get());
            return readExportedFile(existingExport.get().getStoragePath());
        }

        return exportAndPersist(executionId, format);
    }

    private byte[] exportAndPersist(UUID executionId, ReportFormat format) {
        Timer.Sample exportTimer = reportingMetrics.startTimer();

        ReportResult result = reportResultRepository.findByReportExecutionId(executionId)
                .orElseThrow(() -> new ResourceNotFoundException("ReportResult", "executionId=" + executionId));

        var exporter = reportExporterFactory.getExporter(format)
                .orElseThrow(() -> new IllegalStateException("No exporter registered for format: " + format));

        Path storagePath = Path.of(reportingProperties.getStorage().getReportExportDirectory(),
                executionId + "." + format.name().toLowerCase());

        try {
            Files.createDirectories(storagePath.getParent());
            try (var out = Files.newOutputStream(storagePath)) {
                exporter.export(result.getResultData(), out);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to export report executionId=" + executionId + " format=" + format, e);
        }

        ReportExport export = ReportExport.builder()
                .reportExecutionId(executionId)
                .format(format)
                .storagePath(storagePath.toString())
                .fileSizeBytes(fileSizeOf(storagePath))
                .downloadCount(1)
                .build();
        reportExportRepository.save(export);

        reportingMetrics.recordExportDuration(exportTimer, format.name());
        reportingMetrics.recordDownload(format.name());
        applicationEventPublisher.publishEvent(new ReportCompletedApplicationEvent(this,
                ReportCompletedEvent.builder().reportExecutionId(executionId).format(format.name()).storagePath(storagePath.toString()).build(),
                null));

        return readExportedFile(storagePath.toString());
    }

    @Override
    public UUID scheduleReport(ScheduleReportRequest scheduleRequest, String createdBy) {
        ReportSchedule schedule = ReportSchedule.builder()
                .reportType(scheduleRequest.reportType())
                .reportFormat(scheduleRequest.reportFormat())
                .frequency(scheduleRequest.frequency())
                .cronExpression(scheduleRequest.cronExpression())
                .active(true)
                .createdByUser(createdBy)
                .build();
        ReportSchedule saved = reportScheduleRepository.save(schedule);
        log.info("Report schedule created id={} reportType={} cron={}", saved.getId(), scheduleRequest.reportType(), scheduleRequest.cronExpression());
        return saved.getId();
    }

    @Override
    public void cancelReport(UUID executionId) {
        ReportExecution execution = findExecutionOrThrow(executionId);
        if (execution.getStatus() == ReportStatus.COMPLETED || execution.getStatus() == ReportStatus.FAILED) {
            throw new ConflictException("REPORT_ALREADY_TERMINAL",
                    "Report execution '" + executionId + "' is already " + execution.getStatus() + " and cannot be cancelled");
        }
        execution.setStatus(ReportStatus.CANCELLED);
        execution.setCompletedAt(OffsetDateTime.now());
        reportExecutionRepository.save(execution);
        log.info("Report execution cancelled id={}", executionId);
    }

    @Override
    public void deleteReport(UUID executionId) {
        findExecutionOrThrow(executionId);
        reportExecutionRepository.deleteById(executionId);
        reportResultCacheService.evict(executionId);
        log.info("Report execution deleted id={}", executionId);
    }

    private ReportExecution findExecutionOrThrow(UUID executionId) {
        return reportExecutionRepository.findById(executionId)
                .orElseThrow(() -> new ResourceNotFoundException("ReportExecution", executionId.toString()));
    }

    private void incrementDownloadCount(ReportExport export) {
        export.setDownloadCount(export.getDownloadCount() + 1);
        reportExportRepository.save(export);
    }

    private Long fileSizeOf(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return null;
        }
    }

    private byte[] readExportedFile(String storagePath) {
        try {
            return Files.readAllBytes(Path.of(storagePath));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read exported report file at " + storagePath, e);
        }
    }
}
