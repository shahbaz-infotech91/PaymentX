package com.paymentx.payment.controller;

import com.paymentx.common.dto.ApiResponse;
import com.paymentx.common.dto.PageResponse;
import com.paymentx.payment.dto.CancellationRequest;
import com.paymentx.payment.dto.PaymentHistoryResponse;
import com.paymentx.payment.dto.PaymentResponse;
import com.paymentx.payment.dto.PaymentRetryRequest;
import com.paymentx.payment.dto.PaymentSearchCriteria;
import com.paymentx.payment.dto.PaymentStatusResponse;
import com.paymentx.payment.entity.PaymentScheme;
import com.paymentx.payment.entity.PaymentStatus;
import com.paymentx.payment.service.PaymentOperationService;
import com.paymentx.payment.service.PaymentQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * WHY this controller exposes ONLY query and operational-control
 * endpoints, never a debit/credit/return/reversal entry point: those
 * payment COMMANDS enter this system exclusively through
 * API Gateway -> Validation Service -> Kafka -> PaymentValidatedConsumer
 * -> PaymentEngineImpl - that pipeline is untouched by this class. retry()
 * and cancel() below are operational CONTROLS over an already-in-flight
 * payment (seed a retry record / mark cancelled), not new ways to
 * initiate money movement.
 *
 * Every response is wrapped in {@link ApiResponse} from
 * paymentx-common-library - no locally-defined response envelope.
 */
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Query and operational-control APIs for payments already in the system")
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * PaymentController is a REST controller in the payment module of PaymentX. It lives in package com.paymentx.payment.controller and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * PaymentController PaymentX ke payment module ka ek REST controller hai. Ye com.paymentx.payment.controller package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class PaymentController {

    private final PaymentQueryService paymentQueryService;
    private final PaymentOperationService paymentOperationService;

    @GetMapping("/{paymentReference}")
    @Operation(summary = "Get a payment by its reference", description = "Returns the full current snapshot of a payment.")
    public ResponseEntity<ApiResponse<PaymentResponse>> getByReference(
            @Parameter(description = "The payment reference assigned by the originating bank/participant")
            @PathVariable String paymentReference) {
        PaymentResponse response = paymentQueryService.getByReference(paymentReference);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{paymentReference}/status")
    @Operation(summary = "Get a payment's current status", description = "Lightweight status-only response, suitable for polling.")
    public ResponseEntity<ApiResponse<PaymentStatusResponse>> getStatus(@PathVariable String paymentReference) {
        PaymentStatusResponse response = paymentQueryService.getStatus(paymentReference);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{paymentReference}/history")
    @Operation(summary = "Get a payment's full ordered status-transition history",
            description = "Phase 4.8.0 - wires up spec item #13 (PaymentHistoryResponse/PaymentStatusHistoryItem, "
                    + "written on every real status transition by PaymentEngineImpl/RetryScheduler/TimeoutScheduler) "
                    + "to a real endpoint for the first time. Returns the current snapshot plus every fromStatus->toStatus "
                    + "transition, oldest first, each with its own reason and timestamp - a real, precise timeline.")
    public ResponseEntity<ApiResponse<PaymentHistoryResponse>> getHistory(@PathVariable String paymentReference) {
        PaymentHistoryResponse response = paymentQueryService.getHistory(paymentReference);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    @Operation(summary = "List payments", description = "Paginated listing, optionally filtered by status.")
    public ResponseEntity<ApiResponse<PageResponse<PaymentResponse>>> list(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResponse<PaymentResponse> response = paymentQueryService.list(status, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/search")
    @Operation(summary = "Search payments by multiple optional criteria",
            description = "All parameters are optional and combined with AND. participantId matches either the debtor or the creditor.")
    public ResponseEntity<ApiResponse<PageResponse<PaymentResponse>>> search(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) PaymentScheme scheme,
            @RequestParam(required = false) String participantId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime createdTo,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PaymentSearchCriteria criteria = new PaymentSearchCriteria(
                status, scheme, participantId, createdFrom, createdTo, minAmount, maxAmount);

        PageResponse<PaymentResponse> response = paymentQueryService.search(criteria, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{paymentReference}/retry")
    @Operation(summary = "Manually trigger a retry for a failed payment",
            description = "Seeds a new retry record picked up by the existing retry scheduler on its next cycle - does not invoke payment processing directly.")
    public ResponseEntity<ApiResponse<PaymentResponse>> retry(
            @PathVariable String paymentReference,
            @Valid @RequestBody PaymentRetryRequest request) {
        PaymentResponse response = paymentOperationService.retry(paymentReference, request);
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(response));
    }

    @PostMapping("/{paymentReference}/cancel")
    @Operation(summary = "Cancel a payment before funds have moved",
            description = "Only valid while the payment has not yet debited funds. A payment that has already debited requires a reversal, not a cancellation.")
    public ResponseEntity<ApiResponse<PaymentResponse>> cancel(
            @PathVariable String paymentReference,
            @Valid @RequestBody CancellationRequest request) {
        PaymentResponse response = paymentOperationService.cancel(paymentReference, request);
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(response));
    }
}
