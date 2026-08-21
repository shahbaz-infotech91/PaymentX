package com.paymentx.common.exception;

/**
 * "Who are you?" - missing or invalid credentials. Maps to HTTP 401.
 * Distinct from {@link ForbiddenException} ("I know who you are, but
 * you can't do this") - conflating the two loses information a client
 * needs (401 means "try authenticating again"; 403 means "authenticating
 * again won't help").
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * UnauthorizedException is a exception in the common module of PaymentX. It lives in package com.paymentx.common.exception and participates in common's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through common's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * UnauthorizedException PaymentX ke common module ka ek exception hai. Ye com.paymentx.common.exception package me hai aur common ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise common ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class UnauthorizedException extends PaymentXException {

    public UnauthorizedException(String message) {
        super("UNAUTHORIZED", message, false);
    }
}
