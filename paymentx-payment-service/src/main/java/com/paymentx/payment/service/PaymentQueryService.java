package com.paymentx.payment.service;

import com.paymentx.common.dto.PageResponse;
import com.paymentx.payment.dto.PaymentHistoryResponse;
import com.paymentx.payment.dto.PaymentResponse;
import com.paymentx.payment.dto.PaymentSearchCriteria;
import com.paymentx.payment.dto.PaymentStatusResponse;
import com.paymentx.payment.entity.PaymentStatus;

/**
 * Pure read operations - no state transitions, no outbox writes, no
 * interaction with PaymentEngine. Kept separate from PaymentOperationService
 * (retry/cancel) so the query path can never accidentally trigger a
 * side-effecting write.
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * PaymentQueryService is a interface in the payment module of PaymentX. It lives in package com.paymentx.payment.service and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * PaymentQueryService PaymentX ke payment module ka ek interface hai. Ye com.paymentx.payment.service package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public interface PaymentQueryService {

    PaymentResponse getByReference(String paymentReference);

    PaymentStatusResponse getStatus(String paymentReference);

    /** Phase 4.8.0 - wires up the pre-existing PaymentHistoryResponse/PaymentStatusHistoryItem
     *  DTOs and PaymentStatusHistoryRepository (originally spec item #13, written by
     *  PaymentEngineImpl/RetryScheduler/TimeoutScheduler on every real status transition, but
     *  never previously exposed by any service method or REST endpoint) - a real, precise,
     *  ordered state-transition timeline for Incident RCA's own timeline-analysis requirement. */
    PaymentHistoryResponse getHistory(String paymentReference);

    PageResponse<PaymentResponse> list(PaymentStatus status, int page, int size);

    PageResponse<PaymentResponse> search(PaymentSearchCriteria criteria, int page, int size);
}
