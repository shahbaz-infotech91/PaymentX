package com.paymentx.payment.scheduler;

import com.paymentx.payment.config.SchedulerProperties;
import com.paymentx.payment.outbox.OutboxProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Thin scheduling wrapper - all actual logic lives in OutboxProcessor
 * (kept separate and independently unit-testable without a scheduler in
 * the loop, same separation-of-concerns principle applied to
 * RetryScheduler/RetryProcessor in Batch 4).
 *
 * WHY fixedDelay (not fixedRate) via SchedulerProperties: fixedDelay
 * waits for the PREVIOUS run to finish before counting down to the next
 * one. If a batch publish takes longer than the configured interval
 * (Kafka slow, large batch), fixedRate would pile up overlapping
 * executions; fixedDelay cannot overlap by construction - the right
 * choice for a job whose duration can vary with load.
 */
@Component
@RequiredArgsConstructor
@Slf4j
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * OutboxScheduler is a component in the payment module of PaymentX. It lives in package com.paymentx.payment.scheduler and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * OutboxScheduler PaymentX ke payment module ka ek component hai. Ye com.paymentx.payment.scheduler package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class OutboxScheduler {

    private final OutboxProcessor outboxProcessor;
    private final SchedulerProperties schedulerProperties;

    @Scheduled(fixedDelayString = "${payment.scheduler.outbox-interval-millis}")
    public void drainOutbox() {
        try {
            outboxProcessor.processPendingBatch(
                    schedulerProperties.getOutboxBatchSize(),
                    schedulerProperties.getOutboxMaxRetries());
        } catch (Exception e) {
            // A scheduled method that throws stops future executions in
            // some Spring configurations - catching here guarantees the
            // NEXT tick still runs even if this one hit an unexpected
            // error, which matters far more for a recurring background
            // job than for a one-shot request handler.
            log.error("Unexpected error during outbox draining", e);
        }
    }
}
