package com.paymentx.audit.entity;

/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * EventType is a enum in the audit module of PaymentX. It lives in package com.paymentx.audit.entity and participates in audit's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through audit's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * EventType PaymentX ke audit module ka ek enum hai. Ye com.paymentx.audit.entity package me hai aur audit ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise audit ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public enum EventType {
    PAYMENT_CREATED,
    PAYMENT_UPDATED,
    PAYMENT_ROUTED,
    PAYMENT_COMPLETED,
    PAYMENT_FAILED,
    PAYMENT_CANCELLED,
    PAYMENT_REFUNDED,
    VALIDATION_COMPLETED,
    PARTICIPANT_UPDATED,
    ROUTING_RULE_CHANGED,
    SECURITY_EVENT,
    API_REQUEST,
    API_RESPONSE,
    SYSTEM_EVENT,
    KAFKA_EVENT
}
