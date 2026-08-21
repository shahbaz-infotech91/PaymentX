package com.paymentx.common.exception;

/**
 * "I know who you are, but you're not allowed to do this" - maps to
 * HTTP 403. See {@link UnauthorizedException}'s javadoc for the
 * distinction.
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * ForbiddenException is a exception in the common module of PaymentX. It lives in package com.paymentx.common.exception and participates in common's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through common's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * ForbiddenException PaymentX ke common module ka ek exception hai. Ye com.paymentx.common.exception package me hai aur common ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise common ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class ForbiddenException extends PaymentXException {

    public ForbiddenException(String message) {
        super("FORBIDDEN", message, false);
    }
}
