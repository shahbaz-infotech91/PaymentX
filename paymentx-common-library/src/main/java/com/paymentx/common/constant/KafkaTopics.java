package com.paymentx.common.constant;

/**
 * WHY this does NOT list every actual topic name (e.g. {@code instant-payment-validated},
 * {@code payment.completed}) - those are owned and versioned by the
 * PRODUCING service (see Validation Service's and Payment Service's own
 * {@code KafkaTopics} constant classes, which remain the source of truth
 * for their own topics). Centralizing every service's topic name here
 * would recreate exactly the tight coupling this library is designed to
 * avoid (ADR 0004) - any service adding a topic would require a shared
 * library release. This class holds only conventions and prefixes every
 * service should follow, and the one genuinely cross-cutting topic
 * (dead-letter routing) that infrastructure code needs to know about
 * regardless of which service originated the failed message.
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * KafkaTopics is a class in the common module of PaymentX. It lives in package com.paymentx.common.constant and participates in common's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through common's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * KafkaTopics PaymentX ke common module ka ek class hai. Ye com.paymentx.common.constant package me hai aur common ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise common ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public final class KafkaTopics {
    private KafkaTopics() {}

    /** Suffix convention every dead-letter topic in the platform should use. */
    public static final String DEAD_LETTER_SUFFIX = ".DLT";

    /** Suffix convention for a service's own retry-topic, if using topic-based retry. */
    public static final String RETRY_SUFFIX = ".retry";

    /** Consumer group ID prefix convention - {service-name}-group. */
    public static final String CONSUMER_GROUP_SUFFIX = "-group";
}
