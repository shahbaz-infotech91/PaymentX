package com.paymentx.payment.producer;

import com.paymentx.common.event.PaymentEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * WHY this base class exists (and why it's a DIFFERENT design decision
 * than keeping the 9 event payload classes independent): every producer
 * performs the mechanically identical sequence - wrap the payload in the
 * shared PaymentEvent envelope, key the Kafka message by paymentReference,
 * send. That mechanic has nothing to do with any individual event's
 * DATA SHAPE (which is what independence was about) - it's pure
 * behavioral plumbing. Sharing it here means a change to HOW we publish
 * (e.g. adding a header, changing the keying strategy) happens in ONE
 * place instead of nine.
 *
 * IMPORTANT ARCHITECTURAL CONSTRAINT enforced by WHERE this class is
 * used: per the Outbox Pattern requirement, these producers must be
 * called ONLY by the background OutboxProcessor (Batch 5) - never
 * directly from service.impl inside a database transaction. Publishing
 * to Kafka inside a DB transaction risks the exact dual-write problem
 * the outbox table exists to prevent (see payment_outbox's Liquibase
 * changeset comment from Part 2). Nothing in this class enforces that
 * constraint at compile time - it is a discipline the codebase must
 * maintain, and worth flagging loudly here since it's easy to violate
 * accidentally by injecting a producer into a service.impl class instead
 * of into OutboxProcessor.
 */
@Slf4j
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * AbstractEventProducer is a class in the payment module of PaymentX. It lives in package com.paymentx.payment.producer and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * AbstractEventProducer PaymentX ke payment module ka ek class hai. Ye com.paymentx.payment.producer package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public abstract class AbstractEventProducer<T> {

    protected final KafkaTemplate<String, Object> kafkaTemplate;

    protected AbstractEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    protected void publish(String topic, String key, String eventType, String traceId, T payload) {
        PaymentEvent<T> event = PaymentEvent.of(eventType, traceId, payload);
        kafkaTemplate.send(topic, key, event);
        log.info("Published eventType={} topic={} key={} traceId={}", eventType, topic, key, traceId);
    }
}
