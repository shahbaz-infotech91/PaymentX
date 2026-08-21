package com.paymentx.notification.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.paymentx.common.event.PaymentEvent;
import com.paymentx.notification.constant.NotificationKafkaTopics;
import com.paymentx.notification.entity.NotificationChannel;
import com.paymentx.notification.entity.SourceEventType;
import com.paymentx.notification.metrics.NotificationMetrics;
import com.paymentx.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * WHY channel/recipient are best-effort extracted from the raw event
 * payload, defaulting to INTERNAL when no external contact info is
 * present: none of the 15 source topics carry a dedicated "notify this
 * email/phone" field - they carry business identifiers (paymentId,
 * participantId), not contact details, because no participant-directory
 * service with contact info is currently accessible to this service
 * (Validation Service owns the Participant table; no REST integration
 * to look up contact-by-participantId exists today - building one is a
 * cross-service integration beyond this task's "complete ONLY
 * notification-service" scope). Falling back to INTERNAL - which
 * requires no external contact info at all, only the Notification row
 * itself being queryable - guarantees every consumed event still
 * produces a genuinely useful, real notification record rather than
 * being silently dropped or requiring a fabricated fake email address.
 *
 * WHY concurrency is NOT explicitly raised above the container
 * factory's default of 1: the explicit "Ordering" requirement - Kafka
 * guarantees in-order delivery per partition only when a single consumer
 * thread processes that partition. Each source service's own producer
 * already keys messages by a stable business id, so same-key messages
 * route to the same partition and process strictly in order at
 * concurrency=1.
 */
@Component
@RequiredArgsConstructor
@Slf4j
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * NotificationEventConsumer is a Kafka consumer in the notification module of PaymentX. It lives in package com.paymentx.notification.event and participates in notification's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through notification's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * NotificationEventConsumer PaymentX ke notification module ka ek Kafka consumer hai. Ye com.paymentx.notification.event package me hai aur notification ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise notification ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class NotificationEventConsumer {

    private final NotificationService notificationService;
    private final NotificationMetrics notificationMetrics;

    @KafkaListener(
            topics = {
                    NotificationKafkaTopics.INSTANT_PAYMENT_VALIDATED, NotificationKafkaTopics.CARD_PAYMENT_VALIDATED, NotificationKafkaTopics.REAL_TIME_PAYMENT_VALIDATED,
                    NotificationKafkaTopics.PAYMENT_PROCESSING, NotificationKafkaTopics.PAYMENT_DEBITED, NotificationKafkaTopics.PAYMENT_CREDITED,
                    NotificationKafkaTopics.PAYMENT_COMPLETED, NotificationKafkaTopics.PAYMENT_FAILED, NotificationKafkaTopics.PAYMENT_RETURNED,
                    NotificationKafkaTopics.PAYMENT_REVERSED, NotificationKafkaTopics.PAYMENT_CANCELLED, NotificationKafkaTopics.PAYMENT_TIMEOUT,
                    NotificationKafkaTopics.ROUTING_ROUTE_RESOLVED, NotificationKafkaTopics.ROUTING_RULE_CHANGED, NotificationKafkaTopics.AUDIT_COMPLETED
            },
            groupId = "notification-service",
            containerFactory = "notificationKafkaListenerContainerFactory")
    public void onEvent(ConsumerRecord<String, PaymentEvent<JsonNode>> record, Acknowledgment acknowledgment) {
        String topic = record.topic();
        PaymentEvent<JsonNode> envelope = record.value();
        JsonNode payload = envelope.getPayload();

        SourceEventType sourceEventType = TopicSourceEventTypeResolver.resolve(topic);

        // "paymentReference" fallback: the three *_VALIDATED topics (published
        // by Validation Service, before Payment Service has created the
        // Payment row/UUID) never carry a "paymentId" - by design, no such id
        // exists yet at validation time. They do carry "paymentReference"
        // (the client-supplied business reference), which is the only
        // identifier available at that point in the payment's lifecycle.
        String paymentId = firstNonBlank(payload, "paymentId", "id", "paymentReference");
        String participantId = firstNonBlank(payload, "participantId", "debtorParticipantId", "creditorParticipantId");
        String email = firstNonBlank(payload, "email", "notificationEmail", "debtorEmail", "creditorEmail");
        String phone = firstNonBlank(payload, "phone", "phoneNumber", "notificationPhone");
        String webhookUrl = firstNonBlank(payload, "webhookUrl", "callbackUrl");

        NotificationChannel channel;
        String recipient;
        if (email != null) {
            channel = NotificationChannel.EMAIL;
            recipient = email;
        } else if (phone != null) {
            channel = NotificationChannel.SMS;
            recipient = phone;
        } else if (webhookUrl != null) {
            channel = NotificationChannel.WEBHOOK;
            recipient = webhookUrl;
        } else {
            channel = NotificationChannel.INTERNAL;
            recipient = participantId != null ? participantId : "platform";
        }

        String subject = sourceEventType.name().replace('_', ' ') + " Notification";
        String body = channel == NotificationChannel.WEBHOOK ? payload.toString()
                : "Event: " + sourceEventType + (paymentId != null ? " | Payment: " + paymentId : "") + (participantId != null ? " | Participant: " + participantId : "");

        notificationService.createFromEvent(sourceEventType, channel, recipient, subject, body, null,
                envelope.getEventId().toString(), envelope.getTraceId(), envelope.getTraceId(), participantId, paymentId);

        if (envelope.getOccurredAt() != null) {
            notificationMetrics.recordConsumerLag(Duration.between(envelope.getOccurredAt(), Instant.now()).toMillis(), topic);
        }

        // Only reached if createFromEvent() completed without throwing -
        // see Audit Service's AuditEventConsumer javadoc for why
        // acknowledge() must never run in a finally block (would defeat
        // DefaultErrorHandler's retry+DLQ mechanism).
        acknowledgment.acknowledge();
    }

    private String firstNonBlank(JsonNode payload, String... fieldNames) {
        for (String field : fieldNames) {
            JsonNode value = payload.path(field);
            if (!value.isMissingNode() && !value.isNull() && !value.asText().isBlank()) {
                return value.asText();
            }
        }
        return null;
    }
}
