package com.paymentx.payment.entity;

/**
 * Renamed from Scheme -> PaymentScheme (same three values: INSTANT_PAYMENT, REAL_TIME_PAYMENT,
 * CARD_PAYMENT) purely for naming consistency with the other Payment* enums in
 * this package (PaymentType, PaymentChannel, PaymentStatus). Same
 * cross-service-duplication rationale as before applies unchanged - see
 * the git history of the original Scheme.java for the full explanation of
 * why this is Payment Service's own copy, not shared with Validation
 * Service.
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * PaymentScheme is a enum in the payment module of PaymentX. It lives in package com.paymentx.payment.entity and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * PaymentScheme PaymentX ke payment module ka ek enum hai. Ye com.paymentx.payment.entity package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public enum PaymentScheme {
    INSTANT_PAYMENT,
    REAL_TIME_PAYMENT,
    CARD_PAYMENT
}
