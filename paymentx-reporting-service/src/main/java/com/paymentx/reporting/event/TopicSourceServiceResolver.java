package com.paymentx.reporting.event;

import com.paymentx.reporting.constant.ReportingKafkaTopics;

import java.util.Map;

/**
 * ====================================================================
 * ENGLISH: Maps a consumed topic to the sourceService label stored on
 * SourceEvent - the exact string every ReportGenerator queries by, so
 * this mapping and the generators' service names must stay in sync.
 *
 * HINGLISH: Ek consumed topic ko sourceService label se map karta hai
 * jo SourceEvent pe store hota hai - wahi exact string jise har
 * ReportGenerator query karta hai, isliye ye mapping aur generators ke
 * service names sync me rehne chahiye.
 * ====================================================================
 */
public final class TopicSourceServiceResolver {

    private static final Map<String, String> TOPIC_TO_SERVICE = Map.ofEntries(
            Map.entry(ReportingKafkaTopics.PAYMENT_COMPLETED, "payment-service"),
            Map.entry(ReportingKafkaTopics.PAYMENT_FAILED, "payment-service"),
            Map.entry(ReportingKafkaTopics.PAYMENT_CANCELLED, "payment-service"),
            Map.entry(ReportingKafkaTopics.PAYMENT_RETURNED, "payment-service"),
            Map.entry(ReportingKafkaTopics.PAYMENT_REVERSED, "payment-service"),
            Map.entry(ReportingKafkaTopics.INSTANT_PAYMENT_VALIDATED, "validation-service"),
            Map.entry(ReportingKafkaTopics.CARD_PAYMENT_VALIDATED, "validation-service"),
            Map.entry(ReportingKafkaTopics.REAL_TIME_PAYMENT_VALIDATED, "validation-service"),
            Map.entry(ReportingKafkaTopics.AUDIT_COMPLETED, "audit-service"),
            Map.entry(ReportingKafkaTopics.ROUTING_RULE_CHANGED, "routing-service"),
            Map.entry(ReportingKafkaTopics.RECONCILIATION_BATCH_COMPLETED, "reconciliation-service")
    );

    private TopicSourceServiceResolver() {}

    public static String resolve(String topic) {
        return TOPIC_TO_SERVICE.getOrDefault(topic, "unknown");
    }
}
