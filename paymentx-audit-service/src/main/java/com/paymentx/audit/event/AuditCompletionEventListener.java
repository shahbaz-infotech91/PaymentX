package com.paymentx.audit.event;

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
 * AuditCompletionEventListener is a component in the audit module of PaymentX. It lives in package com.paymentx.audit.event and participates in audit's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through audit's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * AuditCompletionEventListener PaymentX ke audit module ka ek component hai. Ye com.paymentx.audit.event package me hai aur audit ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise audit ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class AuditCompletionEventListener {

    private final AuditCompletionProducer auditCompletionProducer;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAuditRecorded(AuditCompletionApplicationEvent event) {
        auditCompletionProducer.publishCompletion(event.getAuditEventId(), event.getEventType(), event.getSourceService(), event.getTraceId());
    }
}
