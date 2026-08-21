package com.paymentx.common.exception;

/**
 * "The request conflicts with current state" - e.g. a duplicate
 * resource, an optimistic-lock version mismatch surfaced to an API
 * caller. Maps to HTTP 409 in a global exception handler.
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * ConflictException is a exception in the common module of PaymentX. It lives in package com.paymentx.common.exception and participates in common's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through common's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * ConflictException PaymentX ke common module ka ek exception hai. Ye com.paymentx.common.exception package me hai aur common ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise common ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class ConflictException extends PaymentXException {

    public ConflictException(String message) {
        super("CONFLICT", message, false);
    }

    public ConflictException(String errorCode, String message) {
        super(errorCode, message, false);
    }
}
