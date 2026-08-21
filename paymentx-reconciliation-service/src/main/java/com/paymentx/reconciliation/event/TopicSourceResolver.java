package com.paymentx.reconciliation.event;

import com.paymentx.reconciliation.constant.ReconciliationKafkaTopics;

import java.util.Map;

/** Maps a consumed Payment Service topic to the internal status string
 *  that will be stored/compared by the matching engine. */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * TopicSourceResolver is a class in the reconciliation module of PaymentX. It lives in package com.paymentx.reconciliation.event and participates in reconciliation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through reconciliation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * TopicSourceResolver PaymentX ke reconciliation module ka ek class hai. Ye com.paymentx.reconciliation.event package me hai aur reconciliation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise reconciliation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public final class TopicSourceResolver {

    private static final Map<String, String> TOPIC_TO_STATUS = Map.of(
            ReconciliationKafkaTopics.PAYMENT_COMPLETED, "COMPLETED",
            ReconciliationKafkaTopics.PAYMENT_FAILED, "FAILED",
            ReconciliationKafkaTopics.PAYMENT_CANCELLED, "CANCELLED",
            ReconciliationKafkaTopics.PAYMENT_RETURNED, "RETURNED",
            ReconciliationKafkaTopics.PAYMENT_REVERSED, "REVERSED"
    );

    private TopicSourceResolver() {}

    public static String resolveStatus(String topic) {
        return TOPIC_TO_STATUS.getOrDefault(topic, "UNKNOWN");
    }
}
