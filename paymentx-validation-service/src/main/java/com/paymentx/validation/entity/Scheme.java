package com.paymentx.validation.entity;

/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * Scheme is a enum in the validation module of PaymentX. It lives in package com.paymentx.validation.entity and participates in validation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through validation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * Scheme PaymentX ke validation module ka ek enum hai. Ye com.paymentx.validation.entity package me hai aur validation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise validation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public enum Scheme {
    INSTANT_PAYMENT,
    CARD_PAYMENT,
    REAL_TIME_PAYMENT,
    ALL // used only on business_rule rows that apply to every scheme
}
