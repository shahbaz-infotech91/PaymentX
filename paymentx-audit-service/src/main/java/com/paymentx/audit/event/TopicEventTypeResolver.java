package com.paymentx.audit.event;

import com.paymentx.audit.constant.AuditKafkaTopics;
import com.paymentx.audit.entity.EventType;

import java.util.Map;

/**
 * WHY PAYMENT_CREATED and PARTICIPANT_UPDATED have no topic mapping
 * here: no currently-existing topic represents either event.
 * PaymentEngineImpl creates a Payment row synchronously inside
 * processValidatedPayment() without a distinct "created" Kafka event
 * (processing starts immediately) - the closest existing signal is
 * payment.processing, mapped to PAYMENT_UPDATED. PARTICIPANT_UPDATED
 * would need Validation Service to publish participant CRUD changes to
 * Kafka, which it does not do today. Both EventType values remain valid,
 * structurally supported enum members (satisfying "must store" for when
 * a producer eventually exists) - see AuditKafkaTopics' javadoc for why
 * adding new producers elsewhere is out of this task's scope.
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * TopicEventTypeResolver is a class in the audit module of PaymentX. It lives in package com.paymentx.audit.event and participates in audit's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through audit's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * TopicEventTypeResolver PaymentX ke audit module ka ek class hai. Ye com.paymentx.audit.event package me hai aur audit ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise audit ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public final class TopicEventTypeResolver {

    private static final Map<String, EventType> TOPIC_TO_EVENT_TYPE = Map.ofEntries(
            Map.entry(AuditKafkaTopics.INSTANT_PAYMENT_VALIDATED, EventType.VALIDATION_COMPLETED),
            Map.entry(AuditKafkaTopics.CARD_PAYMENT_VALIDATED, EventType.VALIDATION_COMPLETED),
            Map.entry(AuditKafkaTopics.REAL_TIME_PAYMENT_VALIDATED, EventType.VALIDATION_COMPLETED),
            Map.entry(AuditKafkaTopics.PAYMENT_PROCESSING, EventType.PAYMENT_UPDATED),
            Map.entry(AuditKafkaTopics.PAYMENT_DEBITED, EventType.PAYMENT_UPDATED),
            Map.entry(AuditKafkaTopics.PAYMENT_CREDITED, EventType.PAYMENT_UPDATED),
            Map.entry(AuditKafkaTopics.PAYMENT_COMPLETED, EventType.PAYMENT_COMPLETED),
            Map.entry(AuditKafkaTopics.PAYMENT_FAILED, EventType.PAYMENT_FAILED),
            Map.entry(AuditKafkaTopics.PAYMENT_TIMEOUT, EventType.PAYMENT_FAILED),
            Map.entry(AuditKafkaTopics.PAYMENT_RETURNED, EventType.PAYMENT_REFUNDED),
            Map.entry(AuditKafkaTopics.PAYMENT_REVERSED, EventType.PAYMENT_REFUNDED),
            Map.entry(AuditKafkaTopics.PAYMENT_CANCELLED, EventType.PAYMENT_CANCELLED),
            Map.entry(AuditKafkaTopics.ROUTING_ROUTE_RESOLVED, EventType.PAYMENT_ROUTED),
            Map.entry(AuditKafkaTopics.ROUTING_RULE_CHANGED, EventType.ROUTING_RULE_CHANGED)
    );

    private TopicEventTypeResolver() {}

    public static EventType resolve(String topic) {
        EventType resolved = TOPIC_TO_EVENT_TYPE.get(topic);
        return resolved != null ? resolved : EventType.KAFKA_EVENT;
    }

    public static String resolveSourceService(String topic) {
        if (topic.contains("validated")) {
            return "validation-service";
        }
        if (topic.startsWith("payment.")) {
            return "payment-service";
        }
        if (topic.startsWith("routing.")) {
            return "routing-service";
        }
        return "unknown";
    }
}
