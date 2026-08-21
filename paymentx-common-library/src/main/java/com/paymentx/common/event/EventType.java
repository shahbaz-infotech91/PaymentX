package com.paymentx.common.event;

/**
 * Centralized event-type identifiers. Previously, every service that
 * called {@code PaymentEvent.of("PAYMENT_VALIDATED", ...)} used a raw
 * string literal (Validation Service's {@code ValidationEventPublisher},
 * Payment Service's {@code PaymentEngineImpl} and schedulers all did
 * this independently). A typo in one of those literals is a silent,
 * runtime-only bug (the event publishes fine, but a consumer's dispatch
 * map - see Payment Service's {@code OutboxProcessor} - simply never
 * matches it).
 *
 * <p>THIS ENUM IS ADDITIVE, NOT YET WIRED IN: existing services keep
 * using their own string literals for now (changing those call sites is
 * a separate, deliberate follow-up task, not bundled into this
 * library-introduction migration - consistent with the "zero breaking
 * changes, incremental migration" goal). New services built from this
 * point forward should use {@code EventType.PAYMENT_VALIDATED.name()}
 * (or similar) instead of a raw string literal.
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * EventType is a enum in the common module of PaymentX. It lives in package com.paymentx.common.event and participates in common's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through common's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * EventType PaymentX ke common module ka ek enum hai. Ye com.paymentx.common.event package me hai aur common ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise common ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public enum EventType {
    PAYMENT_VALIDATED,
    PAYMENT_PROCESSING_STARTED,
    PAYMENT_DEBITED,
    PAYMENT_CREDITED,
    PAYMENT_COMPLETED,
    PAYMENT_FAILED,
    PAYMENT_RETURNED,
    PAYMENT_REVERSED,
    PAYMENT_CANCELLED,
    PAYMENT_TIMEOUT,
    PAYMENT_REJECTED
}
