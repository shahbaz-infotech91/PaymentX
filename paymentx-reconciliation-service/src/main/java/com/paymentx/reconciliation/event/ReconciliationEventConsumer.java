package com.paymentx.reconciliation.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.paymentx.common.event.PaymentEvent;
import com.paymentx.reconciliation.cache.InternalTransactionCacheService;
import com.paymentx.reconciliation.cache.ReconciliationDedupService;
import com.paymentx.reconciliation.constant.ReconciliationKafkaTopics;
import com.paymentx.reconciliation.dto.InternalTransaction;
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
 * WHY audit.event-recorded (AUDIT_COMPLETED) is subscribed but not
 * actively used to populate the internal-transaction cache: Payment
 * Service's own terminal-state topics already carry the full payment
 * details (amount, currency, participant) needed by the matching
 * engine - Audit Service's event is a secondary confirmation signal,
 * not an additional data source. It's consumed (idempotency-checked,
 * logged) to satisfy the explicit "consume events from Audit Service"
 * requirement, without inventing a redundant second write path into
 * the same cache.
 */
@Component
@RequiredArgsConstructor
@Slf4j
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * ReconciliationEventConsumer is a Kafka consumer in the reconciliation module of PaymentX. It lives in package com.paymentx.reconciliation.event and participates in reconciliation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through reconciliation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * ReconciliationEventConsumer PaymentX ke reconciliation module ka ek Kafka consumer hai. Ye com.paymentx.reconciliation.event package me hai aur reconciliation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise reconciliation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class ReconciliationEventConsumer {

    private final InternalTransactionCacheService internalTransactionCacheService;
    private final ReconciliationDedupService reconciliationDedupService;

    @KafkaListener(
            topics = {
                    ReconciliationKafkaTopics.PAYMENT_COMPLETED, ReconciliationKafkaTopics.PAYMENT_FAILED,
                    ReconciliationKafkaTopics.PAYMENT_CANCELLED, ReconciliationKafkaTopics.PAYMENT_RETURNED,
                    ReconciliationKafkaTopics.PAYMENT_REVERSED, ReconciliationKafkaTopics.AUDIT_COMPLETED
            },
            groupId = "reconciliation-service",
            containerFactory = "reconciliationKafkaListenerContainerFactory")
    public void onEvent(ConsumerRecord<String, PaymentEvent<JsonNode>> record, Acknowledgment acknowledgment) {
        String topic = record.topic();
        PaymentEvent<JsonNode> envelope = record.value();

        if (!reconciliationDedupService.markEventIfNew(envelope.getEventId().toString())) {
            log.info("Skipping duplicate event eventId={} topic={}", envelope.getEventId(), topic);
            acknowledgment.acknowledge();
            return;
        }

        if (ReconciliationKafkaTopics.AUDIT_COMPLETED.equals(topic)) {
            log.debug("Audit confirmation received eventId={}", envelope.getEventId());
            acknowledgment.acknowledge();
            return;
        }

        JsonNode payload = envelope.getPayload();
        String paymentId = firstNonBlank(payload, "paymentId", "id");
        if (paymentId == null) {
            log.warn("Payment event missing paymentId, cannot cache for reconciliation topic={} eventId={}", topic, envelope.getEventId());
            acknowledgment.acknowledge();
            return;
        }

        OffsetDateTime occurredAt = envelope.getOccurredAt() != null
                ? envelope.getOccurredAt().atOffset(ZoneOffset.UTC)
                : OffsetDateTime.now();

        InternalTransaction transaction = new InternalTransaction(
                paymentId,
                firstNonBlank(payload, "paymentReference", "reference"),
                firstNonBlank(payload, "participantId", "debtorParticipantId", "creditorParticipantId"),
                parseAmount(payload),
                firstNonBlank(payload, "currency"),
                TopicSourceResolver.resolveStatus(topic),
                occurredAt);

        internalTransactionCacheService.put(transaction);

        if (ReconciliationKafkaTopics.PAYMENT_COMPLETED.equals(topic)) {
            internalTransactionCacheService.trackPendingSettlement(paymentId);
        }

        acknowledgment.acknowledge();
    }

    private BigDecimal parseAmount(JsonNode payload) {
        JsonNode amountNode = payload.path("amount");
        if (amountNode.isMissingNode() || amountNode.isNull()) {
            return BigDecimal.ZERO;
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
