package com.paymentx.reporting.controller;

import com.paymentx.common.dto.ApiResponse;
import com.paymentx.common.dto.PageResponse;
import com.paymentx.reporting.dto.GenerateReportRequest;
import com.paymentx.reporting.dto.ReportExecutionResponse;
import com.paymentx.reporting.dto.ReportResponse;
import com.paymentx.reporting.dto.ReportResultResponse;
import com.paymentx.reporting.dto.ReportSearchCriteria;
import com.paymentx.reporting.dto.ScheduleReportRequest;
import com.paymentx.reporting.entity.ReportFormat;
import com.paymentx.reporting.entity.ReportType;
import com.paymentx.reporting.service.ReportingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * ====================================================================
 * ENGLISH:
 * Every REST endpoint explicitly required - Generate/Download/List/
 * Search/Schedule/Cancel/Delete. Matches the platform's established
 * "mutating endpoints require ADMIN role, read-only stays open" pattern.
 *
 * HINGLISH:
 * Har explicitly required REST endpoint - Generate/Download/List/
 * Search/Schedule/Cancel/Delete. Platform ke established
 * "mutating endpoints ko ADMIN role chahiye, read-only open rehta hai"
 * pattern jaisa.
 * ====================================================================
 */
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Reporting", description = "Report generation, search, download, and scheduling")
public class ReportingController {

    private final ReportingService reportingService;

    @GetMapping
    @Operation(summary = "List all supported report types")
    public ResponseEntity<ApiResponse<List<ReportResponse>>> listReports() {
        return ResponseEntity.ok(ApiResponse.success(reportingService.listReports()));
    }

    @PostMapping("/generate")
    @PreAuthorize("hasRole('REPORTING_ADMIN')")
    @Operation(summary = "Generate a report (admin only)")
    public ResponseEntity<ApiResponse<ReportExecutionResponse>> generateReport(
            @Valid @RequestBody GenerateReportRequest request,
            @RequestHeader(value = "X-Participant-Id", required = false) String requestedBy,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        String actor = requestedBy != null ? requestedBy : "unknown";
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(reportingService.generateReport(request, actor, correlationId)));
    }

    @GetMapping("/executions/{executionId}")
    @Operation(summary = "Get a report execution's status")
    public ResponseEntity<ApiResponse<ReportExecutionResponse>> getExecutionStatus(@PathVariable UUID executionId) {
        return ResponseEntity.ok(ApiResponse.success(reportingService.getExecutionStatus(executionId)));
    }

    @GetMapping("/executions/{executionId}/result")
    @Operation(summary = "View a completed report's result data")
    public ResponseEntity<ApiResponse<ReportResultResponse>> getResult(@PathVariable UUID executionId) {
        return ResponseEntity.ok(ApiResponse.success(reportingService.getResult(executionId)));
    }

    @GetMapping("/executions")
    @Operation(summary = "Search report requests/executions", description = "All parameters are optional and combined with AND.")
    public ResponseEntity<ApiResponse<PageResponse<ReportExecutionResponse>>> searchExecutions(
            @RequestParam(required = false) ReportType reportType,
            @RequestParam(required = false) String participantId,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) String correlationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        ReportSearchCriteria criteria = new ReportSearchCriteria(reportType, participantId, currency, correlationId, fromDate, toDate);
        return ResponseEntity.ok(ApiResponse.success(reportingService.searchExecutions(criteria, page, size)));
    }

    @GetMapping("/executions/{executionId}/download")
    @Operation(summary = "Download an exported report file (CSV/XLSX/PDF/JSON)")
    public ResponseEntity<byte[]> downloadReport(@PathVariable UUID executionId, @RequestParam ReportFormat format) {
        byte[] content = reportingService.downloadReport(executionId, format);
        String extension = format.name().toLowerCase();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"report-" + executionId + "." + extension + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(content);
    }

    @PostMapping("/schedules")
    @PreAuthorize("hasRole('REPORTING_ADMIN')")
    @Operation(summary = "Schedule a recurring report (admin only)")
    public ResponseEntity<ApiResponse<UUID>> scheduleReport(
            @Valid @RequestBody ScheduleReportRequest request,
            @RequestHeader(value = "X-Participant-Id", required = false) String createdBy) {
        String actor = createdBy != null ? createdBy : "unknown";
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(reportingService.scheduleReport(request, actor)));
    }

    @PostMapping("/executions/{executionId}/cancel")
    @PreAuthorize("hasRole('REPORTING_ADMIN')")
    @Operation(summary = "Cancel a pending/running report execution (admin only)")
    public ResponseEntity<ApiResponse<Void>> cancelReport(@PathVariable UUID executionId) {
        reportingService.cancelReport(executionId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/executions/{executionId}")
    @PreAuthorize("hasRole('REPORTING_ADMIN')")
    @Operation(summary = "Delete a report execution (admin only)")
    public ResponseEntity<ApiResponse<Void>> deleteReport(@PathVariable UUID executionId) {
        reportingService.deleteReport(executionId);
        return ResponseEntity.noContent().build();
    }
}
