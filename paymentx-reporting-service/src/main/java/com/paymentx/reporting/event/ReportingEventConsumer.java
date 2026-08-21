package com.paymentx.reporting.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.paymentx.common.event.PaymentEvent;
import com.paymentx.reporting.cache.ReportingDedupService;
import com.paymentx.reporting.constant.ReportingKafkaTopics;
import com.paymentx.reporting.entity.SourceEvent;
import com.paymentx.reporting.repository.SourceEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * ====================================================================
 * ENGLISH:
 * Consumes every relevant cross-service Kafka event and flattens it
 * into a SourceEvent row - the shared aggregation substrate every
 * ReportGenerator queries. WHY idempotency is checked via Redis SETNX
 * BEFORE touching the database, matching Reconciliation/Notification
 * Service's established 2-layer pattern: the overwhelming majority of
 * Kafka redeliveries are rejected in one fast in-memory operation,
 * avoiding a DB round-trip for the common case.
 *
 * HINGLISH:
 * Har relevant cross-service Kafka event ko consume karke ek
 * SourceEvent row me flatten karta hai - shared aggregation substrate
 * jise har ReportGenerator query karta hai. WHY idempotency Redis
 * SETNX se check hoti hai database chhoone se PEHLE: zyadatar Kafka
 * redeliveries ek fast in-memory operation me reject ho jaate hain.
 * ====================================================================
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReportingEventConsumer {

    private final SourceEventRepository sourceEventRepository;
    private final ReportingDedupService reportingDedupService;

    @KafkaListener(
            topics = {
                    ReportingKafkaTopics.PAYMENT_COMPLETED, ReportingKafkaTopics.PAYMENT_FAILED,
                    ReportingKafkaTopics.PAYMENT_CANCELLED, ReportingKafkaTopics.PAYMENT_RETURNED,
                    ReportingKafkaTopics.PAYMENT_REVERSED, ReportingKafkaTopics.INSTANT_PAYMENT_VALIDATED,
                    ReportingKafkaTopics.CARD_PAYMENT_VALIDATED, ReportingKafkaTopics.REAL_TIME_PAYMENT_VALIDATED,
                    ReportingKafkaTopics.AUDIT_COMPLETED, ReportingKafkaTopics.ROUTING_RULE_CHANGED,
                    ReportingKafkaTopics.RECONCILIATION_BATCH_COMPLETED
            },
            groupId = "reporting-service",
            containerFactory = "reportingKafkaListenerContainerFactory")
    public void onEvent(ConsumerRecord<String, PaymentEvent<JsonNode>> record, Acknowledgment acknowledgment) {
        String topic = record.topic();
        PaymentEvent<JsonNode> envelope = record.value();

        if (!reportingDedupService.markIfNew(envelope.getEventId().toString())) {
            log.info("Skipping duplicate event eventId={} topic={}", envelope.getEventId(), topic);
            acknowledgment.acknowledge();
            return;
        }

        JsonNode payload = envelope.getPayload();
        OffsetDateTime occurredAt = envelope.getOccurredAt() != null
                ? envelope.getOccurredAt().atOffset(ZoneOffset.UTC) : OffsetDateTime.now();

        SourceEvent sourceEvent = SourceEvent.builder()
                .sourceService(TopicSourceServiceResolver.resolve(topic))
                .eventType(envelope.getEventType())
                .paymentId(firstNonBlank(payload, "paymentId", "id"))
                .referenceId(firstNonBlank(payload, "paymentReference", "reference"))
                .participantId(firstNonBlank(payload, "participantId", "debtorParticipantId", "creditorParticipantId"))
                .amount(parseAmount(payload))
                .currency(firstNonBlank(payload, "currency"))
                .status(firstNonBlank(payload, "status", "eventStatus"))
                .correlationId(envelope.getTraceId())
                .sourceEventId(envelope.getEventId().toString())
                .occurredAt(occurredAt)
                .build();

        sourceEventRepository.save(sourceEvent);
        acknowledgment.acknowledge();
    }

    private BigDecimal parseAmount(JsonNode payload) {
        JsonNode amountNode = payload.path("amount");
        if (amountNode.isMissingNode() || amountNode.isNull()) {
            return null;
        }
        return new BigDecimal(amountNode.asText());
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
