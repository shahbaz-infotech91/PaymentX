package com.paymentx.payment.entity;

/**
 * The full payment lifecycle state machine. Valid transitions between
 * these states are enforced in service.impl (Part 4), NOT here - an enum
 * should describe possible values, not transition rules; embedding
 * transition logic in the enum itself makes it impossible to unit-test
 * the state machine independently of the enum's compilation unit.
 *
 * WHY both a terminal "success" state AND an "in-progress" state for each
 * lifecycle stage (e.g. DEBITING before DEBIT_SUCCESS/DEBIT_FAILED,
 * SETTLING before SETTLED): a payment sitting in DEBITING for an unusually
 * long time is itself an operationally meaningful signal (something is
 * stuck) that the timeout-detection scheduler (Part 5) queries for
 * directly. Collapsing "in progress" and "not started" into one implicit
 * state would make that monitoring query impossible to express.
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * PaymentStatus is a enum in the payment module of PaymentX. It lives in package com.paymentx.payment.entity and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * PaymentStatus PaymentX ke payment module ka ek enum hai. Ye com.paymentx.payment.entity package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public enum PaymentStatus {
    RECEIVED,
    VALIDATED,
    PROCESSING,
    ROUTING,
    DEBITING,
    DEBIT_SUCCESS,
    DEBIT_FAILED,
    CREDITING,
    CREDIT_SUCCESS,
    CREDIT_FAILED,
    SETTLING,
    SETTLED,
    RETURNED,
    REVERSED,
    FAILED,
    CANCELLED,
    TIMEOUT,
    RETRYING
}
