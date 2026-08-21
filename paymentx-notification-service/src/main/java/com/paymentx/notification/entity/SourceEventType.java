package com.paymentx.notification.entity;

/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * SourceEventType is a enum in the notification module of PaymentX. It lives in package com.paymentx.notification.entity and participates in notification's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through notification's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * SourceEventType PaymentX ke notification module ka ek enum hai. Ye com.paymentx.notification.entity package me hai aur notification ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise notification ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public enum SourceEventType {
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
    AUDIT_COMPLETED,
    SECURITY_EVENT
}
