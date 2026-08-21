package com.paymentx.common.security;

/**
 * How {@link DataMaskingUtils} (and, via it,
 * {@code com.paymentx.common.annotation.Mask}) should obscure a sensitive
 * value before it reaches a log line or a serialized JSON response.
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * MaskStrategy is a enum in the common module of PaymentX. It lives in package com.paymentx.common.security and participates in common's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through common's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * MaskStrategy PaymentX ke common module ka ek enum hai. Ye com.paymentx.common.security package me hai aur common ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise common ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public enum MaskStrategy {

    /** Replace every character, e.g. account numbers where even the length shouldn't leak: {@code "****"}. */
    FULL,

    /** Keep the trailing N characters visible, mask the rest - the common "card ending in 1234" pattern. */
    PARTIAL,

    /** Mask the local part of an email address, keep the domain: {@code "j***@example.com"}. */
    EMAIL
}
