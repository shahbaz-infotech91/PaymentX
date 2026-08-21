package com.paymentx.common.exception;

/**
 * Thrown when a routing decision cannot be made or fails to execute -
 * an infrastructure/cross-cutting FAILURE MODE (e.g. "no route available,"
 * "routing target unreachable"), not a business domain MODEL. This is
 * why it belongs here despite the "no business domain objects" rule
 * (ADR 0004): {@code RoutingException} describes a failure shape, the
 * same way {@code ConflictException}/{@code ForbiddenException} do -
 * it carries no reference to Routing Service's actual domain types
 * (e.g. no {@code RoutingDecision} field, which correctly stays inside
 * Routing Service per ADR 0004).
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * RoutingException is a exception in the common module of PaymentX. It lives in package com.paymentx.common.exception and participates in common's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through common's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * RoutingException PaymentX ke common module ka ek exception hai. Ye com.paymentx.common.exception package me hai aur common ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise common ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class RoutingException extends PaymentXException {

    public RoutingException(String errorCode, String message) {
        super(errorCode, message, false);
    }

    public RoutingException(String errorCode, String message, boolean retryable) {
        super(errorCode, message, retryable);
    }
}
