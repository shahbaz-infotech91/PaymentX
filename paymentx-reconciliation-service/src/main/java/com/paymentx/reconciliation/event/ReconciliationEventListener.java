package com.paymentx.reconciliation.event;

import com.paymentx.reconciliation.service.impl.ReconciliationBatchProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * ReconciliationEventListener is a component in the reconciliation module of PaymentX. It lives in package com.paymentx.reconciliation.event and participates in reconciliation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through reconciliation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * ReconciliationEventListener PaymentX ke reconciliation module ka ek component hai. Ye com.paymentx.reconciliation.event package me hai aur reconciliation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise reconciliation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class ReconciliationEventListener {

    private final ReconciliationEventProducer reconciliationEventProducer;
    private final ReconciliationBatchProcessor reconciliationBatchProcessor;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBatchStartRequested(BatchStartRequestedApplicationEvent event) {
        reconciliationBatchProcessor.runAsync(event.getBatchId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBatchCompleted(BatchCompletionApplicationEvent event) {
        reconciliationEventProducer.publishReconciliationCompleted(event.getPayload(), event.getTraceId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMismatchDetected(MismatchDetectionApplicationEvent event) {
        reconciliationEventProducer.publishMismatchDetected(event.getPayload(), event.getTraceId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSettlementCompleted(SettlementCompletionApplicationEvent event) {
        reconciliationEventProducer.publishSettlementCompleted(event.getPayload(), event.getTraceId());
    }
}
