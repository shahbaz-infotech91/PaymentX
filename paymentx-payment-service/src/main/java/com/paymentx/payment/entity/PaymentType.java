package com.paymentx.payment.entity;

/**
 * WHY this is distinct from PaymentStatus: type answers "what KIND of
 * money movement is this" (set once, at creation, never changes) while
 * status answers "where is it in its lifecycle right now" (changes many
 * times). A RETURN payment and a DEBIT payment both move through similar
 * PROCESSING/SUCCESS/FAILED states, but they are fundamentally different
 * OPERATIONS - conflating the two into one enum would force awkward
 * values like "PROCESSING_RETURN" instead of two orthogonal fields.
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * PaymentType is a enum in the payment module of PaymentX. It lives in package com.paymentx.payment.entity and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * PaymentType PaymentX ke payment module ka ek enum hai. Ye com.paymentx.payment.entity package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public enum PaymentType {
    DEBIT,
    CREDIT,
    RETURN,
    REVERSAL
}
