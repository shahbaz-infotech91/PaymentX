package com.paymentx.validation.event;

/**
 * Centralizing topic names here (rather than string literals scattered
 * through the codebase) means a topic rename is a one-line change, and
 * IDE "find usages" actually works.
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * KafkaTopics is a class in the validation module of PaymentX. It lives in package com.paymentx.validation.event and participates in validation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through validation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * KafkaTopics PaymentX ke validation module ka ek class hai. Ye com.paymentx.validation.event package me hai aur validation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise validation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public final class KafkaTopics {
    private KafkaTopics() {}

    public static final String INSTANT_PAYMENT_VALIDATED = "instant-payment-validated";
    public static final String CARD_PAYMENT_VALIDATED = "card-payment-validated";
    public static final String REAL_TIME_PAYMENT_VALIDATED = "real-time-payment-validated";
    public static final String PAYMENT_REJECTED = "payment-rejected";
}
