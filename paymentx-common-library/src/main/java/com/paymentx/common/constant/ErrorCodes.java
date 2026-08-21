package com.paymentx.common.constant;

/**
 * WHY this holds only GENERIC, cross-cutting error codes: service-owned
 * business error codes (e.g. Validation Service's
 * {@code "ACCOUNT_BLACKLISTED_DEBTOR"}, Payment Service's
 * {@code "AMOUNT_EXCEEDS_LIMIT"}) stay defined where they're thrown -
 * centralizing every service's error vocabulary here would be the exact
 * coupling ADR 0004 exists to prevent, and would require a shared
 * library release for any service to add a new business error code.
 * These constants are for infrastructure-level failures that are
 * genuinely identical in meaning regardless of which service raises
 * them.
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * ErrorCodes is a class in the common module of PaymentX. It lives in package com.paymentx.common.constant and participates in common's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through common's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * ErrorCodes PaymentX ke common module ka ek class hai. Ye com.paymentx.common.constant package me hai aur common ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise common ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public final class ErrorCodes {
    private ErrorCodes() {}

    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND";
    public static final String CONFLICT = "CONFLICT";
    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String RATE_LIMITED = "RATE_LIMITED";
    public static final String SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE";
    public static final String TIMEOUT = "TIMEOUT";
    public static final String IDEMPOTENCY_CONFLICT = "IDEMPOTENCY_CONFLICT";
    public static final String BAD_GATEWAY = "BAD_GATEWAY";
}
