package com.paymentx.common.exception;

/**
 * Generic business-rule violation - the cross-cutting base for any
 * "this operation is not allowed given current business state" failure.
 * Service-specific exceptions (e.g. Payment Service's
 * {@code BusinessRuleViolationException}) may continue to extend
 * {@link PaymentXException} directly, or extend this class instead once
 * migrated - both remain valid; this is additive, not a forced rename.
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * BusinessException is a exception in the common module of PaymentX. It lives in package com.paymentx.common.exception and participates in common's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through common's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * BusinessException PaymentX ke common module ka ek exception hai. Ye com.paymentx.common.exception package me hai aur common ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise common ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class BusinessException extends PaymentXException {

    public BusinessException(String errorCode, String message) {
        super(errorCode, message, false);
    }

    public BusinessException(String errorCode, String message, boolean retryable) {
        super(errorCode, message, retryable);
    }
}
