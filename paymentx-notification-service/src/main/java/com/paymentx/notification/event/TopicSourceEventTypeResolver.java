package com.paymentx.notification.event;

import com.paymentx.notification.constant.NotificationKafkaTopics;
import com.paymentx.notification.entity.SourceEventType;

import java.util.Map;

/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * TopicSourceEventTypeResolver is a class in the notification module of PaymentX. It lives in package com.paymentx.notification.event and participates in notification's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through notification's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * TopicSourceEventTypeResolver PaymentX ke notification module ka ek class hai. Ye com.paymentx.notification.event package me hai aur notification ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise notification ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public final class TopicSourceEventTypeResolver {

    private static final Map<String, SourceEventType> TOPIC_TO_EVENT_TYPE = Map.ofEntries(
            Map.entry(NotificationKafkaTopics.INSTANT_PAYMENT_VALIDATED, SourceEventType.VALIDATION_COMPLETED),
            Map.entry(NotificationKafkaTopics.CARD_PAYMENT_VALIDATED, SourceEventType.VALIDATION_COMPLETED),
            Map.entry(NotificationKafkaTopics.REAL_TIME_PAYMENT_VALIDATED, SourceEventType.VALIDATION_COMPLETED),
            Map.entry(NotificationKafkaTopics.PAYMENT_PROCESSING, SourceEventType.PAYMENT_UPDATED),
            Map.entry(NotificationKafkaTopics.PAYMENT_DEBITED, SourceEventType.PAYMENT_UPDATED),
            Map.entry(NotificationKafkaTopics.PAYMENT_CREDITED, SourceEventType.PAYMENT_UPDATED),
            Map.entry(NotificationKafkaTopics.PAYMENT_COMPLETED, SourceEventType.PAYMENT_COMPLETED),
            Map.entry(NotificationKafkaTopics.PAYMENT_FAILED, SourceEventType.PAYMENT_FAILED),
            Map.entry(NotificationKafkaTopics.PAYMENT_TIMEOUT, SourceEventType.PAYMENT_FAILED),
            Map.entry(NotificationKafkaTopics.PAYMENT_RETURNED, SourceEventType.PAYMENT_REFUNDED),
            Map.entry(NotificationKafkaTopics.PAYMENT_REVERSED, SourceEventType.PAYMENT_REFUNDED),
            Map.entry(NotificationKafkaTopics.PAYMENT_CANCELLED, SourceEventType.PAYMENT_CANCELLED),
            Map.entry(NotificationKafkaTopics.ROUTING_ROUTE_RESOLVED, SourceEventType.PAYMENT_ROUTED),
            Map.entry(NotificationKafkaTopics.ROUTING_RULE_CHANGED, SourceEventType.ROUTING_RULE_CHANGED),
            Map.entry(NotificationKafkaTopics.AUDIT_COMPLETED, SourceEventType.AUDIT_COMPLETED)
    );

    private TopicSourceEventTypeResolver() {}

    public static SourceEventType resolve(String topic) {
        SourceEventType resolved = TOPIC_TO_EVENT_TYPE.get(topic);
        if (resolved == null) {
            throw new IllegalArgumentException("No SourceEventType mapping for topic: " + topic);
        }
        return resolved;
    }
}
