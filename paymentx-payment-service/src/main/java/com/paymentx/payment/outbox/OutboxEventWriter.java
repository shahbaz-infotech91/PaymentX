package com.paymentx.payment.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentx.payment.entity.OutboxStatus;
import com.paymentx.payment.entity.PaymentOutbox;
import com.paymentx.payment.repository.PaymentOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * WHY this duplicates logic conceptually similar to PaymentEngineImpl's
 * private createOutboxEvent method rather than reusing it: that method is
 * private to PaymentEngineImpl, which is explicitly off-limits to modify
 * in this round of changes. Rather than either (a) making it public and
 * touching PaymentEngine to expose it, or (b) inlining this serialization
 * logic separately inside both RetryScheduler and TimeoutScheduler
 * (duplicating it TWICE instead of once), this shared component is the
 * least-bad option: one small, honestly-duplicated helper, used by the
 * two NEW callers that need it. Flagging this duplication explicitly
 * rather than hiding it - a future batch that's allowed to touch
 * PaymentEngine should consolidate both call sites onto one shared writer.
 */
@Component
@RequiredArgsConstructor
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * OutboxEventWriter is a component in the payment module of PaymentX. It lives in package com.paymentx.payment.outbox and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * OutboxEventWriter PaymentX ke payment module ka ek component hai. Ye com.paymentx.payment.outbox package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class OutboxEventWriter {

    private final PaymentOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public <T> void write(UUID paymentId, String topic, String eventType, T payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            PaymentOutbox record = PaymentOutbox.builder()
                    .paymentId(paymentId)
                    .eventType(eventType)
                    .topic(topic)
                    .payload(json)
                    .status(OutboxStatus.PENDING)
                    .retryCount(0)
                    .build();
            outboxRepository.save(record);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize outbox payload for eventType=" + eventType, e);
        }
    }
}
