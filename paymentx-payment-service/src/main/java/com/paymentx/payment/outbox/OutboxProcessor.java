package com.paymentx.payment.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentx.payment.entity.OutboxStatus;
import com.paymentx.payment.entity.Payment;
import com.paymentx.payment.entity.PaymentOutbox;
import com.paymentx.payment.event.PaymentCancelledEvent;
import com.paymentx.payment.event.PaymentCompletedEvent;
import com.paymentx.payment.event.PaymentCreditedEvent;
import com.paymentx.payment.event.PaymentDebitedEvent;
import com.paymentx.payment.event.PaymentFailedEvent;
import com.paymentx.payment.event.PaymentProcessingStartedEvent;
import com.paymentx.payment.event.PaymentReturnedEvent;
import com.paymentx.payment.event.PaymentReversedEvent;
import com.paymentx.payment.event.PaymentTimeoutEvent;
import com.paymentx.payment.producer.PaymentCancelledProducer;
import com.paymentx.payment.producer.PaymentCompletedProducer;
import com.paymentx.payment.producer.PaymentCreditedProducer;
import com.paymentx.payment.producer.PaymentDebitedProducer;
import com.paymentx.payment.producer.PaymentFailedProducer;
import com.paymentx.payment.producer.PaymentProcessingProducer;
import com.paymentx.payment.producer.PaymentReturnedProducer;
import com.paymentx.payment.producer.PaymentReversedProducer;
import com.paymentx.payment.producer.PaymentTimeoutProducer;
import com.paymentx.payment.repository.PaymentOutboxRepository;
import com.paymentx.payment.repository.PaymentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * WHY three separate transactional phases instead of one big
 * @Transactional method wrapping the whole batch (the single most
 * important design decision in this class):
 *
 * Phase 1 (claimBatch, its own short transaction): SELECT ... FOR UPDATE
 * SKIP LOCKED, then immediately mark each claimed row PUBLISHING within
 * THAT SAME transaction, then commit. This releases row locks quickly
 * and makes the claim durable and visible - any other OutboxProcessor
 * instance's concurrent poll skips these rows (status is no longer
 * PENDING) without blocking on the lock.
 *
 * Phase 2 (outside ANY transaction): the actual Kafka publish calls -
 * the literal implementation of "never publish Kafka inside the database
 * transaction." By the time this runs, phase 1's transaction has already
 * committed and closed.
 *
 * Phase 3 (markResult, one short transaction per record): update status
 * to PUBLISHED, or back to PENDING for a bounded retry, or FAILED once
 * outboxMaxRetries is exceeded (terminal - requires manual investigation,
 * no infinite retry loop).
 */
@Component
@Slf4j
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * OutboxProcessor is a component in the payment module of PaymentX. It lives in package com.paymentx.payment.outbox and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * OutboxProcessor PaymentX ke payment module ka ek component hai. Ye com.paymentx.payment.outbox package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class OutboxProcessor {

    private final PaymentOutboxRepository outboxRepository;
    private final PaymentRepository paymentRepository;
    private final ObjectMapper objectMapper;
    private final Map<String, OutboxEventPublisher> publishersByEventType;

    public OutboxProcessor(PaymentOutboxRepository outboxRepository,
                            PaymentRepository paymentRepository,
                            ObjectMapper objectMapper,
                            PaymentProcessingProducer paymentProcessingProducer,
                            PaymentDebitedProducer paymentDebitedProducer,
                            PaymentCreditedProducer paymentCreditedProducer,
                            PaymentCompletedProducer paymentCompletedProducer,
                            PaymentFailedProducer paymentFailedProducer,
                            PaymentReturnedProducer paymentReturnedProducer,
                            PaymentReversedProducer paymentReversedProducer,
                            PaymentCancelledProducer paymentCancelledProducer,
                            PaymentTimeoutProducer paymentTimeoutProducer) {
        this.outboxRepository = outboxRepository;
        this.paymentRepository = paymentRepository;
        this.objectMapper = objectMapper;

        // Registry-based dispatch, not a switch/if-else chain: adding a
        // 10th event type later means adding one more map entry, zero
        // changes to the dispatch logic below - same Open/Closed
        // philosophy as the PaymentProcessor strategy map.
        this.publishersByEventType = Map.of(
                "PAYMENT_PROCESSING_STARTED", (traceId, node) ->
                        paymentProcessingProducer.publish(traceId, objectMapper.treeToValue(node, PaymentProcessingStartedEvent.class)),
                "PAYMENT_DEBITED", (traceId, node) ->
                        paymentDebitedProducer.publish(traceId, objectMapper.treeToValue(node, PaymentDebitedEvent.class)),
                "PAYMENT_CREDITED", (traceId, node) ->
                        paymentCreditedProducer.publish(traceId, objectMapper.treeToValue(node, PaymentCreditedEvent.class)),
                "PAYMENT_COMPLETED", (traceId, node) ->
                        paymentCompletedProducer.publish(traceId, objectMapper.treeToValue(node, PaymentCompletedEvent.class)),
                "PAYMENT_FAILED", (traceId, node) ->
                        paymentFailedProducer.publish(traceId, objectMapper.treeToValue(node, PaymentFailedEvent.class)),
                "PAYMENT_RETURNED", (traceId, node) ->
                        paymentReturnedProducer.publish(traceId, objectMapper.treeToValue(node, PaymentReturnedEvent.class)),
                "PAYMENT_REVERSED", (traceId, node) ->
                        paymentReversedProducer.publish(traceId, objectMapper.treeToValue(node, PaymentReversedEvent.class)),
                "PAYMENT_CANCELLED", (traceId, node) ->
                        paymentCancelledProducer.publish(traceId, objectMapper.treeToValue(node, PaymentCancelledEvent.class)),
                "PAYMENT_TIMEOUT", (traceId, node) ->
                        paymentTimeoutProducer.publish(traceId, objectMapper.treeToValue(node, PaymentTimeoutEvent.class))
        );
    }

    public void processPendingBatch(int batchSize, int maxRetries) {
        List<PaymentOutbox> claimed = claimBatch(batchSize);
        if (claimed.isEmpty()) {
            return;
        }
        log.info("Claimed {} outbox record(s) for publishing", claimed.size());

        for (PaymentOutbox record : claimed) {
            try {
                publish(record);
                markPublished(record.getId());
            } catch (Exception e) {
                log.error("Failed to publish outbox record id={} eventType={} attempt={}",
                        record.getId(), record.getEventType(), record.getRetryCount() + 1, e);
                markFailedOrRetry(record.getId(), maxRetries);
            }
        }
    }

    @Transactional
    protected List<PaymentOutbox> claimBatch(int batchSize) {
        List<PaymentOutbox> batch = outboxRepository.findBatchForPublishing(batchSize);
        for (PaymentOutbox record : batch) {
            record.setStatus(OutboxStatus.PUBLISHING);
        }
        return outboxRepository.saveAll(batch);
    }

    private void publish(PaymentOutbox record) throws Exception {
        Payment payment = paymentRepository.findById(record.getPaymentId())
                .orElseThrow(() -> new IllegalStateException(
                        "Outbox record references missing payment id=" + record.getPaymentId()));

        OutboxEventPublisher publisher = publishersByEventType.get(record.getEventType());
        if (publisher == null) {
            throw new IllegalStateException("No publisher registered for eventType=" + record.getEventType());
        }

        JsonNode payloadNode = objectMapper.readTree(record.getPayload());
        publisher.publish(payment.getTraceId(), payloadNode);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void markPublished(UUID outboxId) {
        outboxRepository.findById(outboxId).ifPresent(record -> {
            record.setStatus(OutboxStatus.PUBLISHED);
            record.setPublishedAt(OffsetDateTime.now());
            outboxRepository.save(record);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void markFailedOrRetry(UUID outboxId, int maxRetries) {
        outboxRepository.findById(outboxId).ifPresent(record -> {
            int attempts = record.getRetryCount() + 1;
            record.setRetryCount(attempts);
            if (attempts >= maxRetries) {
                record.setStatus(OutboxStatus.FAILED);
                log.error("Outbox record id={} eventType={} exceeded max retries ({}) - marked FAILED, " +
                        "requires manual investigation", outboxId, record.getEventType(), maxRetries);
            } else {
                record.setStatus(OutboxStatus.PENDING);
            }
            outboxRepository.save(record);
        });
    }

    @FunctionalInterface
    private interface OutboxEventPublisher {
        void publish(String traceId, JsonNode payloadNode) throws Exception;
    }
}
