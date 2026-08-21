package com.paymentx.audit.controller;

import com.paymentx.audit.dto.AuditEventRequest;
import com.paymentx.audit.dto.AuditEventResponse;
import com.paymentx.audit.entity.EventStatus;
import com.paymentx.audit.entity.EventType;
import com.paymentx.audit.dto.AuditSearchCriteria;
import com.paymentx.audit.service.AuditService;
import com.paymentx.common.dto.ApiResponse;
import com.paymentx.common.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * WHY the audit-record-write endpoint (recordApiEvent) requires
 * AUDIT_WRITER role while search/get are open to any authenticated
 * caller: matching Routing Service's established
 * @PreAuthorize-on-mutating-endpoints pattern - writing an audit record
 * directly via REST (bypassing Kafka) is a privileged operation only
 * Gateway/internal services should perform, while reading the audit
 * trail is the whole point of this service's read-only public surface
 * (per the "Read-only audit APIs" requirement).
 */
@RestController
@RequestMapping("/api/v1/audit-events")
@RequiredArgsConstructor
@Tag(name = "Audit", description = "Immutable audit trail - search and direct-write APIs")
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * AuditController is a REST controller in the audit module of PaymentX. It lives in package com.paymentx.audit.controller and participates in audit's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through audit's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * AuditController PaymentX ke audit module ka ek REST controller hai. Ye com.paymentx.audit.controller package me hai aur audit ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise audit ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class AuditController {

    private final AuditService auditService;

    @PostMapping
    @PreAuthorize("hasRole('AUDIT_WRITER')")
    @Operation(summary = "Record an audit event directly (API request/response, security events)")
    public ResponseEntity<ApiResponse<AuditEventResponse>> record(@Valid @RequestBody AuditEventRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(auditService.record(request)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an audit event by id")
    public ResponseEntity<ApiResponse<AuditEventResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(auditService.getById(id)));
    }

    @GetMapping
    @Operation(summary = "Search audit events",
            description = "All parameters are optional and combined with AND.")
    public ResponseEntity<ApiResponse<PageResponse<AuditEventResponse>>> search(
            @RequestParam(required = false) String correlationId,
            @RequestParam(required = false) String paymentId,
            @RequestParam(required = false) String participantId,
            @RequestParam(required = false) String reference,
            @RequestParam(required = false) EventStatus status,
            @RequestParam(required = false) EventType eventType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        AuditSearchCriteria criteria = new AuditSearchCriteria(
                correlationId, paymentId, participantId, reference, status, eventType, fromDate, toDate);

        return ResponseEntity.ok(ApiResponse.success(auditService.search(criteria, page, size)));
    }
}
