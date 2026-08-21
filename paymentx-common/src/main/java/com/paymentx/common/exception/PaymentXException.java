package com.paymentx.common.exception;

import lombok.Getter;

/**
 * Root exception for all PaymentX business errors.
 *
 * WHY a shared exception hierarchy:
 * A payment failure needs to be classified consistently across every service
 * so that (a) the API Gateway can map it to the right HTTP status, and
 * (b) downstream systems (Reconciliation, Notification) can branch on
 * `errorCode` without parsing free-text messages - free-text parsing in a
 * payment system is a production incident waiting to happen the moment
 * someone reword a log message.
 *
 * errorCode is a STABLE, versioned string (e.g. "VALIDATION_001",
 * "ROUTING_TIMEOUT_002"). It is a contract. Never repurpose an existing
 * code for a new meaning - deprecate and add a new one instead. Downstream
 * consumers and dashboards key off these.
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
