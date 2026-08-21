package com.paymentx.payment.entity;

/**
 * PUBLISHING is an intermediate, in-flight marker (added when the Outbox
 * draining pattern was implemented) between claiming a batch and actually
 * publishing to Kafka. This lets OutboxProcessor commit the "I've claimed
 * this row" fact BEFORE making the Kafka call, then update to PUBLISHED/
 * FAILED afterward - without ever holding a DB row lock across the actual
 * network I/O. See OutboxProcessor's javadoc for the full three-phase
 * rationale.
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * OutboxStatus is a enum in the payment module of PaymentX. It lives in package com.paymentx.payment.entity and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * OutboxStatus PaymentX ke payment module ka ek enum hai. Ye com.paymentx.payment.entity package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public enum OutboxStatus {
    PENDING,
    PUBLISHING,
    PUBLISHED,
    FAILED
}
