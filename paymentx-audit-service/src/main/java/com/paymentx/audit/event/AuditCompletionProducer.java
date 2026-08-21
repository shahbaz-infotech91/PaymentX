package com.paymentx.audit.event;

import com.paymentx.audit.constant.AuditKafkaTopics;
import com.paymentx.common.event.PaymentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * AuditCompletionProducer is a component in the audit module of PaymentX. It lives in package com.paymentx.audit.event and participates in audit's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through audit's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * AuditCompletionProducer PaymentX ke audit module ka ek component hai. Ye com.paymentx.audit.event package me hai aur audit ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise audit ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class AuditCompletionProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishCompletion(UUID auditEventId, String eventType, String sourceService, String traceId) {
        PaymentEvent<AuditCompletionEvent> envelope = PaymentEvent.of("AUDIT_EVENT_RECORDED", traceId,
                AuditCompletionEvent.builder()
                        .auditEventId(auditEventId)
                        .eventType(eventType)
                        .sourceService(sourceService)
                        .build());

        kafkaTemplate.send(AuditKafkaTopics.AUDIT_COMPLETED, auditEventId.toString(), envelope)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.warn("Failed to publish audit-completion event auditEventId={}", auditEventId, ex);
                    }
                });
    }
}
