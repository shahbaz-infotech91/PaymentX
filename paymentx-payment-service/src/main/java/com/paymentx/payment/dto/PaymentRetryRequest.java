package com.paymentx.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Manual/operator-triggered retry - distinct from the automatic
 * RetryScheduler's retries (which happen on a timer after a pipeline
 * failure). This endpoint seeds a new PaymentRetry row that the
 * ALREADY-RUNNING RetryScheduler picks up on its next poll - it does not
 * invoke PaymentEngine or the Kafka flow directly, keeping that
 * processing pipeline completely untouched.
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * PaymentRetryRequest is a record (DTO) in the payment module of PaymentX. It lives in package com.paymentx.payment.dto and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * PaymentRetryRequest PaymentX ke payment module ka ek record (DTO) hai. Ye com.paymentx.payment.dto package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public record PaymentRetryRequest(
        @Size(max = 512)
        String reason,

        @NotBlank(message = "requestedBy is required")
        String requestedBy
) {
}
