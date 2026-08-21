package com.paymentx.payment.service;

import com.paymentx.payment.event.PaymentValidatedEvent;

/**
 * The entry point into the payment processing pipeline. PaymentEngine's
 * responsibility (per the spec): receive a validated payment, load or
 * create the Payment aggregate, select and execute the appropriate
 * PaymentProcessor (Strategy Pattern - Batch 4), persist the result, and
 * write an outbox event - all within one transaction boundary.
 *
 * WHY PaymentValidatedConsumer depends on this INTERFACE, not directly on
 * PaymentEngineImpl (which doesn't exist until Batch 4): Consumer's job
 * is "deserialize a Kafka message and hand it off," full stop - it should
 * not need to know or care HOW a validated payment gets processed. This
 * also means Consumer can be fully unit-tested right now with a mocked
 * PaymentEngine, without waiting for the real implementation to exist.
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * PaymentEngine is a interface in the payment module of PaymentX. It lives in package com.paymentx.payment.service and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * PaymentEngine PaymentX ke payment module ka ek interface hai. Ye com.paymentx.payment.service package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public interface PaymentEngine {

    /**
     * @param validatedEvent the deserialized payload from Validation Service
     * @param eventId the Kafka message's own eventId (from the common PaymentEvent
     *                envelope) - becomes Payment.idempotencyKey, guarding against
     *                THIS specific Kafka message being redelivered and reprocessed.
     *                Distinct from paymentReference-level duplicate detection,
     *                which Validation Service already performs - see Payment.java's
     *                javadoc for the full explanation of why these are separate.
     * @param traceId the end-to-end business transaction identifier (propagated, never regenerated)
     * @param correlationId identifies this specific processing attempt
     */
    void processValidatedPayment(PaymentValidatedEvent validatedEvent, String eventId, String traceId, String correlationId);
}
