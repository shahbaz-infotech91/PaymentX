package com.paymentx.payment.entity;

/**
 * Captures HOW the payment entered PaymentX - relevant for both analytics
 * (e.g. "what % of volume is BATCH vs API") and for future business rules
 * that may differ by channel (e.g. MANUAL entries might require stricter
 * approval workflows than API-initiated ones - not implemented yet, but
 * this column is what such a rule would key off).
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * PaymentChannel is a enum in the payment module of PaymentX. It lives in package com.paymentx.payment.entity and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * PaymentChannel PaymentX ke payment module ka ek enum hai. Ye com.paymentx.payment.entity package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public enum PaymentChannel {
    API,
    BATCH,
    MANUAL,
    SCHEDULED
}
