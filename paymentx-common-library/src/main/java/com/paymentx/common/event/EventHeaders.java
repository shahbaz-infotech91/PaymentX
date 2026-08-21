package com.paymentx.common.event;

/**
 * Kafka RECORD HEADER names (not the JSON envelope's fields) used for
 * cross-cutting propagation - e.g. an API Gateway or Kafka-bridging
 * component that needs to read the correlation ID without deserializing
 * the full message body. Distinct from {@link PaymentEvent}'s
 * {@code traceId} field, which lives IN the payload, not as a Kafka
 * header.
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * EventHeaders is a class in the common module of PaymentX. It lives in package com.paymentx.common.event and participates in common's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through common's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * EventHeaders PaymentX ke common module ka ek class hai. Ye com.paymentx.common.event package me hai aur common ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise common ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public final class EventHeaders {
    private EventHeaders() {}

    public static final String CORRELATION_ID = "X-Correlation-Id";
    public static final String TRACE_ID = "X-Trace-Id";
    public static final String SOURCE_SERVICE = "X-Source-Service";
    public static final String EVENT_TYPE = "X-Event-Type";
}
