package com.paymentx.payment.consumer;

import com.paymentx.common.event.PaymentEvent;
import com.paymentx.payment.constant.KafkaTopics;
import com.paymentx.payment.event.PaymentValidatedEvent;
import com.paymentx.payment.service.PaymentEngine;
import com.paymentx.payment.util.LoggingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * WHY one listener across all three physical topics
 * (instant-payment-validated/card-payment-validated/real-time-payment-validated) instead of three separate
 * @KafkaListener methods: from Payment Service's perspective, "a payment
 * was validated" is ONE conceptual event regardless of which scheme it
 * came from - the scheme is data (PaymentValidatedEvent.scheme), not a
 * reason for different processing entry points. PaymentEngine (Batch 4)
 * branches on scheme internally where it actually matters (routing),
 * not here at the consumption boundary. @KafkaListener's topics attribute
 * natively accepts multiple topic names for exactly this "same handling,
 * multiple sources" case.
 *
 * WHY manual acknowledgment happens ONLY after paymentEngine returns
 * successfully: this is the actual mechanism that makes "we only advance
 * the Kafka offset after durably persisting the outcome" (explained in
 * application.yml's kafka.consumer.enable-auto-commit comment) concrete.
 * If processValidatedPayment throws, ack.acknowledge() is never called,
 * the container's error handler takes over (Batch 6), and - critically -
 * this exact message will be REDELIVERED on the next poll. That
 * redelivery is precisely why Payment Service needs its own idempotency
 * check (via idempotencyKey, derived from eventId) independent of
 * Validation Service's - see Payment.java's javadoc.
 */
@Component
@RequiredArgsConstructor
@Slf4j
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * PaymentValidatedConsumer is a Kafka consumer in the payment module of PaymentX. It lives in package com.paymentx.payment.consumer and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * PaymentValidatedConsumer PaymentX ke payment module ka ek Kafka consumer hai. Ye com.paymentx.payment.consumer package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class PaymentValidatedConsumer {

    private final PaymentEngine paymentEngine;

    @KafkaListener(
            topics = {KafkaTopics.INSTANT_PAYMENT_VALIDATED, KafkaTopics.CARD_PAYMENT_VALIDATED, KafkaTopics.REAL_TIME_PAYMENT_VALIDATED},
            containerFactory = "paymentValidatedKafkaListenerContainerFactory"
    )
    public void onPaymentValidated(PaymentEvent<PaymentValidatedEvent> event, Acknowledgment acknowledgment) {
        String traceId = event.getTraceId();
        // No correlationId arrives on the envelope today - see ADR-worthy
        // gap noted in this module's design discussion: end-to-end
        // correlationId propagation from Gateway through Validation
        // Service's Kafka events isn't wired yet. Generating a fresh one
        // here treats "a Kafka message was consumed and is being acted
        // upon" as its own correlatable unit of work, consistent with how
        // CorrelationIdFilter generates one for an HTTP request that
        // arrives without one.
        String correlationId = UUID.randomUUID().toString();
        PaymentValidatedEvent payload = event.getPayload();

        LoggingContext.setTraceId(traceId);
        LoggingContext.setCorrelationId(correlationId);
        LoggingContext.setParticipantId(payload.getDebtorParticipantId());

        try {
            log.info("Consumed PaymentValidatedEvent paymentReference={} scheme={}",
                    payload.getPaymentReference(), payload.getScheme());

            paymentEngine.processValidatedPayment(payload, event.getEventId().toString(), traceId, correlationId);

            acknowledgment.acknowledge();
            log.info("Acknowledged PaymentValidatedEvent paymentReference={}", payload.getPaymentReference());
        } catch (Exception ex) {
            log.error("Failed to process PaymentValidatedEvent paymentReference={} - offset NOT acknowledged, " +
                    "message will be redelivered", payload.getPaymentReference(), ex);
            // Deliberately not caught-and-swallowed: rethrowing lets the
            // container's configured error handler (Batch 6) decide
            // retry-vs-dead-letter policy in one central place, rather
            // than every consumer method reimplementing that decision.
            throw ex;
        } finally {
            LoggingContext.clear();
        }
    }
}
