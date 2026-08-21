package com.paymentx.validation.dto;

import com.paymentx.validation.entity.ValidationStatus;

/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * ValidationResponse is a record (DTO) in the validation module of PaymentX. It lives in package com.paymentx.validation.dto and participates in validation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through validation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * ValidationResponse PaymentX ke validation module ka ek record (DTO) hai. Ye com.paymentx.validation.dto package me hai aur validation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise validation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public record ValidationResponse(
        String paymentReference,
        String traceId,
        ValidationStatus status,
        String rejectionReason
) {
    public static ValidationResponse validated(String paymentReference, String traceId) {
        return new ValidationResponse(paymentReference, traceId, ValidationStatus.VALIDATED, null);
    }

    public static ValidationResponse rejected(String paymentReference, String traceId, String reason) {
        return new ValidationResponse(paymentReference, traceId, ValidationStatus.REJECTED, reason);
    }

    public static ValidationResponse duplicate(String paymentReference, String traceId) {
        return new ValidationResponse(paymentReference, traceId, ValidationStatus.DUPLICATE,
                "Payment with this reference has already been processed");
    }
}
