package com.paymentx.payment.scheduler;

import com.paymentx.payment.config.SchedulerProperties;
import com.paymentx.payment.constant.KafkaTopics;
import com.paymentx.payment.entity.Payment;
import com.paymentx.payment.entity.PaymentAudit;
import com.paymentx.payment.entity.PaymentStatus;
import com.paymentx.payment.entity.PaymentStatusHistory;
import com.paymentx.payment.event.PaymentTimeoutEvent;
import com.paymentx.payment.outbox.OutboxEventWriter;
import com.paymentx.payment.repository.PaymentAuditRepository;
import com.paymentx.payment.repository.PaymentRepository;
import com.paymentx.payment.repository.PaymentStatusHistoryRepository;
import com.paymentx.payment.util.LoggingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * WHY this scans only PaymentStatus.PROCESSING (matching the literal
 * spec), not also DEBITING/CREDITING/SETTLING: under the CURRENT
 * synchronous pipeline (PaymentEngineImpl runs debit->credit->settlement
 * inside one transaction, completing in milliseconds), a payment
 * realistically only sits in an in-progress status for longer than a
 * moment if the process crashed mid-transaction - and a crash rolls back
 * the whole transaction, so the payment row wouldn't even exist in that
 * intermediate state. PROCESSING is the one status set BEFORE the
 * pipeline transaction begins its risky work and is genuinely
 * observable as "stuck" if something external prevented the pipeline
 * from ever running. Broadening this to the other in-progress statuses
 * becomes meaningful once Routing Service introduces real asynchronous
 * rail responses - noted here rather than pre-emptively implemented, to
 * avoid unrequested scope expansion this round.
 */
@Component
@RequiredArgsConstructor
@Slf4j
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * TimeoutScheduler is a component in the payment module of PaymentX. It lives in package com.paymentx.payment.scheduler and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * TimeoutScheduler PaymentX ke payment module ka ek component hai. Ye com.paymentx.payment.scheduler package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class TimeoutScheduler {

    private final PaymentRepository paymentRepository;
    private final PaymentStatusHistoryRepository statusHistoryRepository;
    private final PaymentAuditRepository auditRepository;
    private final OutboxEventWriter outboxEventWriter;
    private final SchedulerProperties schedulerProperties;

    @Scheduled(fixedDelayString = "${payment.scheduler.timeout-interval-millis}")
    public void detectStuckPayments() {
        List<Payment> processing = paymentRepository.findByStatusIn(List.of(PaymentStatus.PROCESSING));
        if (processing.isEmpty()) {
            return;
        }

        OffsetDateTime threshold = OffsetDateTime.now()
                .minusMinutes(schedulerProperties.getTimeoutStuckThresholdMinutes());

        List<Payment> stuck = processing.stream()
                .filter(p -> p.getUpdatedAt().isBefore(threshold))
                .toList();

        if (stuck.isEmpty()) {
            return;
        }
        log.warn("Found {} payment(s) stuck in PROCESSING past {} minute threshold",
                stuck.size(), schedulerProperties.getTimeoutStuckThresholdMinutes());

        for (Payment payment : stuck) {
            try {
                markTimedOut(payment);
            } catch (Exception e) {
                log.error("Unexpected error marking payment id={} as TIMEOUT", payment.getId(), e);
            }
        }
    }

    @Transactional
    protected void markTimedOut(Payment payment) {
        LoggingContext.setTraceId(payment.getTraceId());
        LoggingContext.setPaymentId(payment.getId().toString());

        PaymentStatus previousStatus = payment.getStatus();

        PaymentStatusHistory history = PaymentStatusHistory.builder()
                .paymentId(payment.getId())
                .fromStatus(previousStatus)
                .toStatus(PaymentStatus.TIMEOUT)
                .reason("Stuck in " + previousStatus + " past "
                        + schedulerProperties.getTimeoutStuckThresholdMinutes() + " minute threshold")
                .transitionedAt(OffsetDateTime.now())
                .build();
        statusHistoryRepository.save(history);

        payment.setStatus(PaymentStatus.TIMEOUT);
        paymentRepository.save(payment);

        PaymentAudit audit = PaymentAudit.builder()
                .paymentId(payment.getId())
                .action("PAYMENT_TIMEOUT_DETECTED")
                .actor("SYSTEM")
                .details("Payment was stuck in " + previousStatus + " and marked TIMEOUT by TimeoutScheduler")
                .occurredAt(OffsetDateTime.now())
                .build();
        auditRepository.save(audit);

        outboxEventWriter.write(payment.getId(), KafkaTopics.PAYMENT_TIMEOUT, "PAYMENT_TIMEOUT",
                PaymentTimeoutEvent.builder()
                        .paymentId(payment.getId())
                        .paymentReference(payment.getPaymentReference())
                        .stuckAtStatus(previousStatus)
                        .detectedAt(OffsetDateTime.now())
                        .build());

        log.warn("Payment marked TIMEOUT paymentReference={} previousStatus={}",
                payment.getPaymentReference(), previousStatus);
    }
}
