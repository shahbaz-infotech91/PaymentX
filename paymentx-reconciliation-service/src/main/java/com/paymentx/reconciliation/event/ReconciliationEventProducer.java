package com.paymentx.reconciliation.event;

import com.paymentx.common.event.PaymentEvent;
import com.paymentx.reconciliation.constant.ReconciliationKafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * ReconciliationEventProducer is a component in the reconciliation module of PaymentX. It lives in package com.paymentx.reconciliation.event and participates in reconciliation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through reconciliation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * ReconciliationEventProducer PaymentX ke reconciliation module ka ek component hai. Ye com.paymentx.reconciliation.event package me hai aur reconciliation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise reconciliation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class ReconciliationEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishReconciliationCompleted(ReconciliationCompletedEvent event, String traceId) {
        PaymentEvent<ReconciliationCompletedEvent> envelope = PaymentEvent.of("RECONCILIATION_COMPLETED", traceId, event);
        kafkaTemplate.send(ReconciliationKafkaTopics.RECONCILIATION_COMPLETED, event.getBatchId().toString(), envelope)
                .whenComplete((r, ex) -> logIfFailed(ex, "reconciliation-completed", event.getBatchId().toString()));
    }

    public void publishMismatchDetected(MismatchDetectedEvent event, String traceId) {
        PaymentEvent<MismatchDetectedEvent> envelope = PaymentEvent.of("MISMATCH_DETECTED", traceId, event);
        kafkaTemplate.send(ReconciliationKafkaTopics.MISMATCH_DETECTED, event.getMismatchRecordId().toString(), envelope)
                .whenComplete((r, ex) -> logIfFailed(ex, "mismatch-detected", event.getMismatchRecordId().toString()));
    }

    public void publishSettlementCompleted(SettlementCompletedEvent event, String traceId) {
        PaymentEvent<SettlementCompletedEvent> envelope = PaymentEvent.of("SETTLEMENT_COMPLETED", traceId, event);
        kafkaTemplate.send(ReconciliationKafkaTopics.SETTLEMENT_COMPLETED, event.getReconciliationRecordId().toString(), envelope)
                .whenComplete((r, ex) -> logIfFailed(ex, "settlement-completed", event.getReconciliationRecordId().toString()));
    }

    private void logIfFailed(Throwable ex, String eventName, String key) {
        if (ex != null) {
            log.warn("Failed to publish {} event key={}", eventName, key, ex);
        }
    }
}
