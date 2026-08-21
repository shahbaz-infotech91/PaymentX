package com.paymentx.common.constant;

/**
 * HTTP header name constants shared across every service's controllers
 * and filters (API Gateway's filters and every downstream service's
 * {@code CorrelationIdFilter}-equivalent both reference these same
 * literal strings today, independently - this class is the eventual
 * single source of truth new code should reference).
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * HeaderConstants is a class in the common module of PaymentX. It lives in package com.paymentx.common.constant and participates in common's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through common's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * HeaderConstants PaymentX ke common module ka ek class hai. Ye com.paymentx.common.constant package me hai aur common ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise common ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public final class HeaderConstants {
    private HeaderConstants() {}

    public static final String CORRELATION_ID = "X-Correlation-Id";
    public static final String TRACE_ID = "X-Trace-Id";
    public static final String API_KEY = "X-Api-Key";
    public static final String IDEMPOTENCY_KEY = "X-Idempotency-Key";
    public static final String AUTHORIZATION = "Authorization";
    public static final String CONTENT_TYPE = "Content-Type";
    public static final String REQUEST_ID = "X-Request-Id";
    public static final String CLIENT_ID = "X-Client-Id";
}
