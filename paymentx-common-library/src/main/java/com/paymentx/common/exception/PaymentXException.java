package com.paymentx.common.exception;

import lombok.Getter;

/**
 * Migrated from {@code paymentx-common} (Module 1) - see ADR 0003/0004.
 * Field-for-field identical: {@code errorCode}/{@code message}/
 * {@code retryable}, same constructor signature, same package
 * ({@code com.paymentx.common.exception}). Every service already
 * catching/throwing this type (Payment Service's
 * {@code BusinessRuleViolationException}, Validation Service's
 * equivalent) continues to compile unchanged after the {@code pom.xml}
 * swap.
 */
@Getter
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * PaymentXException is a exception in the common module of PaymentX. It lives in package com.paymentx.common.exception and participates in common's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through common's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * PaymentXException PaymentX ke common module ka ek exception hai. Ye com.paymentx.common.exception package me hai aur common ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise common ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class PaymentXException extends RuntimeException {

    private final String errorCode;
    private final boolean retryable;

    public PaymentXException(String errorCode, String message, boolean retryable) {
        super(message);
        this.errorCode = errorCode;
        this.retryable = retryable;
    }
}
