package com.paymentx.validation.event;

import com.paymentx.common.event.PaymentEvent;
import com.paymentx.validation.entity.Scheme;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * WHY this class exists as a separate component instead of calling
 * kafkaTemplate.send() directly from ValidationService:
 *   1. Scheme -> topic routing logic lives in ONE place. Payment Service
 *      later needs to know this same mapping for its OWN publishes -
 *      keeping it isolated here means it's easy to find and reason about.
 *   2. ValidationService's job is "decide whether this payment is valid,"
 *      not "know Kafka topic names." Separating these is Single
 *      Responsibility Principle applied at the class level - if we ever
 *      swap Kafka for another broker, only this class changes.
 */
@Component
@RequiredArgsConstructor
@Slf4j
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * ValidationEventPublisher is a component in the validation module of PaymentX. It lives in package com.paymentx.validation.event and participates in validation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through validation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * ValidationEventPublisher PaymentX ke validation module ka ek component hai. Ye com.paymentx.validation.event package me hai aur validation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise validation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class ValidationEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishValidated(String traceId, ValidatedPaymentPayload payload) {
        String topic = topicForScheme(payload.getScheme());
        PaymentEvent<ValidatedPaymentPayload> event = PaymentEvent.of(
                "PAYMENT_VALIDATED", traceId, payload);

        // Key by paymentReference, not traceId: Kafka guarantees ORDERING
        // only within a partition, and messages with the same key always
        // land on the same partition. If Payment Service later needs to
        // process multiple events for the SAME payment reference in order
        // (e.g. validated -> then a later correction event), keying by
        // paymentReference guarantees they arrive in order. Keying by a
        // random/round-robin value would NOT give us that guarantee.
        kafkaTemplate.send(topic, payload.getPaymentReference(), event);
        log.info("Published PAYMENT_VALIDATED to topic={} paymentReference={} traceId={}",
                topic, payload.getPaymentReference(), traceId);
    }

    public void publishRejected(String traceId, RejectedPaymentPayload payload) {
        PaymentEvent<RejectedPaymentPayload> event = PaymentEvent.of(
                "PAYMENT_REJECTED", traceId, payload);

        kafkaTemplate.send(KafkaTopics.PAYMENT_REJECTED, payload.getPaymentReference(), event);
        log.info("Published PAYMENT_REJECTED topic={} paymentReference={} reason={}",
                KafkaTopics.PAYMENT_REJECTED, payload.getPaymentReference(), payload.getRejectionReason());
    }

    private String topicForScheme(Scheme scheme) {
        return switch (scheme) {
            case INSTANT_PAYMENT -> KafkaTopics.INSTANT_PAYMENT_VALIDATED;
            case CARD_PAYMENT -> KafkaTopics.CARD_PAYMENT_VALIDATED;
            case REAL_TIME_PAYMENT -> KafkaTopics.REAL_TIME_PAYMENT_VALIDATED;
            case ALL -> throw new IllegalArgumentException("ALL is not a publishable scheme");
        };
    }
}
