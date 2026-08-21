package com.paymentx.reconciliation.controller;

import com.paymentx.common.dto.ApiResponse;
import com.paymentx.common.dto.PageResponse;
import com.paymentx.reconciliation.dto.BatchResponse;
import com.paymentx.reconciliation.dto.MismatchRecordResponse;
import com.paymentx.reconciliation.dto.MismatchSearchCriteria;
import com.paymentx.reconciliation.dto.ReconciliationSummaryResponse;
import com.paymentx.reconciliation.dto.SettlementFileResponse;
import com.paymentx.reconciliation.dto.StartReconciliationRequest;
import com.paymentx.reconciliation.entity.ReconciliationStatus;
import com.paymentx.reconciliation.service.ReconciliationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * WHY upload/start/reprocess require RECONCILIATION_ADMIN
 * (@PreAuthorize) but search/status/summary/download stay open: matches
 * the platform's established mutating-vs-read-only pattern - uploading
 * a settlement file or triggering a reconciliation run against real
 * settlement data is an operator action with real consequences, reading
 * results is not.
 */
@RestController
@RequestMapping("/api/v1/reconciliation")
@RequiredArgsConstructor
@Tag(name = "Reconciliation", description = "Settlement file upload, batch reconciliation, and mismatch management")
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * ReconciliationController is a REST controller in the reconciliation module of PaymentX. It lives in package com.paymentx.reconciliation.controller and participates in reconciliation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through reconciliation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * ReconciliationController PaymentX ke reconciliation module ka ek REST controller hai. Ye com.paymentx.reconciliation.controller package me hai aur reconciliation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise reconciliation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    @PostMapping(value = "/settlement-files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('RECONCILIATION_ADMIN')")
    @Operation(summary = "Upload a settlement file (CSV/JSON) - admin only")
    public ResponseEntity<ApiResponse<SettlementFileResponse>> uploadSettlementFile(@RequestPart MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(reconciliationService.uploadSettlementFile(file)));
    }

    @PostMapping("/batches")
    @PreAuthorize("hasRole('RECONCILIATION_ADMIN')")
    @Operation(summary = "Start a reconciliation batch - admin only")
    public ResponseEntity<ApiResponse<BatchResponse>> startReconciliation(
            @Valid @RequestBody StartReconciliationRequest request,
            @RequestHeader(value = "X-Participant-Id", required = false) String triggeredBy) {
        String actor = triggeredBy != null ? triggeredBy : "unknown";
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(reconciliationService.startReconciliation(request, actor)));
    }

    @GetMapping("/batches/{batchId}")
    @Operation(summary = "Get a reconciliation batch's status")
    public ResponseEntity<ApiResponse<BatchResponse>> getBatchStatus(@PathVariable UUID batchId) {
        return ResponseEntity.ok(ApiResponse.success(reconciliationService.getBatchStatus(batchId)));
    }

    @GetMapping("/batches/{batchId}/summary")
    @Operation(summary = "Get a completed batch's aggregate summary")
    public ResponseEntity<ApiResponse<ReconciliationSummaryResponse>> getSummary(@PathVariable UUID batchId) {
        return ResponseEntity.ok(ApiResponse.success(reconciliationService.getSummary(batchId)));
    }

    @PostMapping("/batches/{batchId}/reprocess")
    @PreAuthorize("hasRole('RECONCILIATION_ADMIN')")
    @Operation(summary = "Reprocess a batch - admin only")
    public ResponseEntity<ApiResponse<BatchResponse>> reprocessBatch(
            @PathVariable UUID batchId,
            @RequestHeader(value = "X-Participant-Id", required = false) String triggeredBy) {
        String actor = triggeredBy != null ? triggeredBy : "unknown";
        return ResponseEntity.ok(ApiResponse.success(reconciliationService.reprocessBatch(batchId, actor)));
    }

    @GetMapping("/batches/{batchId}/report")
    @Operation(summary = "Download a batch's full reconciliation report as CSV")
    public ResponseEntity<byte[]> downloadReport(@PathVariable UUID batchId) {
        byte[] csv = reconciliationService.downloadReport(batchId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"reconciliation-report-" + batchId + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }

    @GetMapping("/mismatches")
    @Operation(summary = "Search mismatch records", description = "All parameters are optional and combined with AND.")
    public ResponseEntity<ApiResponse<PageResponse<MismatchRecordResponse>>> searchMismatches(
            @RequestParam(required = false) UUID batchId,
            @RequestParam(required = false) ReconciliationStatus mismatchType,
            @RequestParam(required = false) Boolean resolved,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        MismatchSearchCriteria criteria = new MismatchSearchCriteria(batchId, mismatchType, resolved);
        return ResponseEntity.ok(ApiResponse.success(reconciliationService.searchMismatches(criteria, page, size)));
    }

    @PostMapping("/mismatches/{mismatchId}/resolve")
    @PreAuthorize("hasRole('RECONCILIATION_ADMIN')")
    @Operation(summary = "Mark a mismatch as resolved - admin only")
    public ResponseEntity<ApiResponse<MismatchRecordResponse>> resolveMismatch(
            @PathVariable UUID mismatchId,
            @RequestParam String notes,
            @RequestHeader(value = "X-Participant-Id", required = false) String resolvedBy) {
        String actor = resolvedBy != null ? resolvedBy : "unknown";
        return ResponseEntity.ok(ApiResponse.success(reconciliationService.resolveMismatch(mismatchId, actor, notes)));
    }
}
