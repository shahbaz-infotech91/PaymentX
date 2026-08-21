package com.paymentx.audit.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.paymentx.audit.constant.AuditKafkaTopics;
import com.paymentx.audit.entity.EventType;
import com.paymentx.audit.metrics.AuditMetrics;
import com.paymentx.audit.service.AuditService;
import com.paymentx.common.event.PaymentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * WHY one @KafkaListener with a topics array, not 14 separate listener
 * methods: every topic shares the exact same handling logic (extract
 * best-effort search fields, idempotency-check, insert, record metrics)
 * - the only thing that varies per-topic is which EventType it maps to
 * (see TopicEventTypeResolver) and which fields the specific JSON shape
 * happens to carry. 14 near-identical methods would be pure duplication
 * with no behavioral difference worth the extra surface area.
 *
 * WHY field extraction uses JsonNode.path() (never-throws navigation)
 * rather than a typed DTO per event: see KafkaConsumerConfig's javadoc -
 * this consumer deliberately does not need to fully understand every
 * producer's payload shape, only opportunistically pull out the 3-4
 * fields useful for search. path() returns a MissingNode (never null,
 * never throws) for an absent field, keeping this extraction safe
 * regardless of which of the 14 payload shapes arrived.
 *
 * WHY acknowledge() is called ONLY on the success path, never in a
 * finally block: a finally block runs even after a rethrown exception,
 * which would commit the Kafka offset for a message that just failed
 * processing - silently defeating DefaultErrorHandler's entire
 * retry+DLQ mechanism (KafkaConsumerConfig) by advancing past the
 * failed message before it ever gets retried.
 */
@Component
@RequiredArgsConstructor
@Slf4j
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * AuditEventConsumer is a Kafka consumer in the audit module of PaymentX. It lives in package com.paymentx.audit.event and participates in audit's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through audit's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * AuditEventConsumer PaymentX ke audit module ka ek Kafka consumer hai. Ye com.paymentx.audit.event package me hai aur audit ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise audit ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class AuditEventConsumer {

    private final AuditService auditService;
    private final AuditMetrics auditMetrics;

    @KafkaListener(
            topics = {
                    AuditKafkaTopics.INSTANT_PAYMENT_VALIDATED, AuditKafkaTopics.CARD_PAYMENT_VALIDATED, AuditKafkaTopics.REAL_TIME_PAYMENT_VALIDATED,
                    AuditKafkaTopics.PAYMENT_PROCESSING, AuditKafkaTopics.PAYMENT_DEBITED, AuditKafkaTopics.PAYMENT_CREDITED,
                    AuditKafkaTopics.PAYMENT_COMPLETED, AuditKafkaTopics.PAYMENT_FAILED, AuditKafkaTopics.PAYMENT_RETURNED,
                    AuditKafkaTopics.PAYMENT_REVERSED, AuditKafkaTopics.PAYMENT_CANCELLED, AuditKafkaTopics.PAYMENT_TIMEOUT,
                    AuditKafkaTopics.ROUTING_ROUTE_RESOLVED, AuditKafkaTopics.ROUTING_RULE_CHANGED
            },
            groupId = "audit-service",
            containerFactory = "auditKafkaListenerContainerFactory")
    public void onEvent(ConsumerRecord<String, PaymentEvent<JsonNode>> record, Acknowledgment acknowledgment) {
        String topic = record.topic();
        PaymentEvent<JsonNode> envelope = record.value();

        EventType eventType = TopicEventTypeResolver.resolve(topic);
        String sourceService = TopicEventTypeResolver.resolveSourceService(topic);
        JsonNode payload = envelope.getPayload();

        String paymentId = firstNonBlank(payload, "paymentId", "id");
        String participantId = firstNonBlank(payload, "participantId", "debtorParticipantId", "creditorParticipantId");
        String reference = firstNonBlank(payload, "paymentReference", "reference");

        auditService.recordFromKafka(eventType, sourceService, envelope.getEventId().toString(),
                envelope.getTraceId(), paymentId, participantId, reference, payload.toString());

        if (envelope.getOccurredAt() != null) {
            auditMetrics.recordConsumerLag(Duration.between(envelope.getOccurredAt(), Instant.now()).toMillis(), topic);
        }

        // Only reached if recordFromKafka() completed without throwing -
        // any exception above propagates to DefaultErrorHandler, which
        // retries (does NOT advance the offset) and eventually publishes
        // to the dead-letter topic without this line ever running.
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
