package com.paymentx.payment.scheduler;

import com.paymentx.payment.config.RetryProperties;
import com.paymentx.payment.constant.KafkaTopics;
import com.paymentx.payment.entity.Payment;
import com.paymentx.payment.entity.PaymentAudit;
import com.paymentx.payment.entity.PaymentRetry;
import com.paymentx.payment.entity.PaymentStatus;
import com.paymentx.payment.entity.PaymentStatusHistory;
import com.paymentx.payment.entity.PaymentType;
import com.paymentx.payment.entity.RetryStatus;
import com.paymentx.payment.event.PaymentFailedEvent;
import com.paymentx.payment.outbox.OutboxEventWriter;
import com.paymentx.payment.repository.PaymentAuditRepository;
import com.paymentx.payment.repository.PaymentRepository;
import com.paymentx.payment.repository.PaymentRetryRepository;
import com.paymentx.payment.repository.PaymentStatusHistoryRepository;
import com.paymentx.payment.service.PaymentProcessor;
import com.paymentx.payment.service.impl.CreditProcessor;
import com.paymentx.payment.service.impl.DebitProcessor;
import com.paymentx.payment.service.impl.RetryProcessor;
import com.paymentx.payment.service.impl.SettlementProcessor;
import com.paymentx.payment.util.LoggingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * WHY this depends on DebitProcessor/CreditProcessor/SettlementProcessor
 * DIRECTLY, alongside RetryProcessor, rather than exclusively going
 * through RetryProcessor: a standard payment's PaymentType is always
 * DEBIT regardless of which pipeline stage actually failed, so
 * PaymentType-based dispatch alone cannot correctly identify which
 * processor to retry. This class determines the failed stage from
 * PaymentStatusHistory (the fromStatus recorded on the transition INTO
 * RETRYING) and dispatches directly for standard payments;
 * RetryProcessor is used only for RETURN/REVERSAL type payments, where
 * PaymentType genuinely does identify the single correct processor.
 *
 * WHY retry success does NOT continue the rest of the pipeline (e.g. a
 * successful debit-retry does not automatically proceed to CREDITING):
 * that orchestration lives inside PaymentEngineImpl, off-limits to
 * modify this round. This is a known, explicitly flagged limitation, not
 * an oversight.
 */
@Component
@RequiredArgsConstructor
@Slf4j
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * RetryScheduler is a component in the payment module of PaymentX. It lives in package com.paymentx.payment.scheduler and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * RetryScheduler PaymentX ke payment module ka ek component hai. Ye com.paymentx.payment.scheduler package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class RetryScheduler {

    private final PaymentRetryRepository retryRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentStatusHistoryRepository statusHistoryRepository;
    private final PaymentAuditRepository auditRepository;
    private final OutboxEventWriter outboxEventWriter;
    private final RetryProperties retryProperties;
    private final DebitProcessor debitProcessor;
    private final CreditProcessor creditProcessor;
    private final SettlementProcessor settlementProcessor;
    private final RetryProcessor retryProcessor;

    @Scheduled(fixedDelayString = "${payment.scheduler.retry-interval-millis}")
    public void scanAndRetry() {
        List<PaymentRetry> due = retryRepository.findDueRetries(RetryStatus.SCHEDULED, OffsetDateTime.now());
        if (due.isEmpty()) {
            return;
        }
        log.info("Found {} due retry record(s)", due.size());

        for (PaymentRetry retry : due) {
            try {
                processRetry(retry);
            } catch (Exception e) {
                log.error("Unexpected error processing retry id={} paymentId={}", retry.getId(), retry.getPaymentId(), e);
            }
        }
    }

    @Transactional
    protected void processRetry(PaymentRetry retry) {
        Payment payment = paymentRepository.findById(retry.getPaymentId()).orElse(null);
        if (payment == null) {
            log.warn("PaymentRetry id={} references missing payment id={} - marking EXHAUSTED", retry.getId(), retry.getPaymentId());
            retry.setStatus(RetryStatus.EXHAUSTED);
            retryRepository.save(retry);
            return;
        }

        LoggingContext.setTraceId(payment.getTraceId());
        LoggingContext.setPaymentId(payment.getId().toString());

        retry.setStatus(RetryStatus.IN_PROGRESS);
        retry.setLastAttemptedAt(OffsetDateTime.now());
        retryRepository.save(retry);

        PaymentProcessor processor = resolveProcessor(payment);
        PaymentProcessor.ProcessingResult result = processor.process(payment);

        if (result.success()) {
            handleRetrySuccess(payment, retry, processor);
        } else {
            handleRetryFailure(payment, retry, result);
        }
    }

    private PaymentProcessor resolveProcessor(Payment payment) {
        if (payment.getPaymentType() == PaymentType.RETURN || payment.getPaymentType() == PaymentType.REVERSAL) {
            return retryProcessor;
        }

        List<PaymentStatusHistory> history = statusHistoryRepository.findByPaymentIdOrderByTransitionedAtAsc(payment.getId());
        if (history.isEmpty()) {
            log.warn("No status history for paymentId={} - defaulting retry to DebitProcessor", payment.getId());
            return debitProcessor;
        }

        PaymentStatusHistory lastTransition = history.get(history.size() - 1);
        PaymentStatus failedStage = lastTransition.getFromStatus();

        if (failedStage == PaymentStatus.CREDITING) {
            return creditProcessor;
        }
        if (failedStage == PaymentStatus.SETTLING) {
            return settlementProcessor;
        }
        return debitProcessor;
    }

    private void handleRetrySuccess(Payment payment, PaymentRetry retry, PaymentProcessor processor) {
        retry.setStatus(RetryStatus.SUCCEEDED);
        retryRepository.save(retry);

        PaymentStatus resultStatus = successStatusFor(processor);
        recordTransition(payment, payment.getStatus(), resultStatus,
                "Retry succeeded via " + processor.getClass().getSimpleName());
        payment.setStatus(resultStatus);
        payment.setFailureReason(null);
        paymentRepository.save(payment);

        recordAudit(payment, "PAYMENT_RETRY_SUCCEEDED", "SYSTEM",
                "Retry attempt " + retry.getCurrentRetry() + " succeeded via " + processor.getClass().getSimpleName());

        log.info("Retry succeeded paymentReference={} newStatus={}", payment.getPaymentReference(), resultStatus);
    }

    private void handleRetryFailure(Payment payment, PaymentRetry retry, PaymentProcessor.ProcessingResult result) {
        int attempts = retry.getCurrentRetry() + 1;
        retry.setCurrentRetry(attempts);
        retry.setRetryReason(result.reason());

        if (attempts >= retry.getMaxRetry() || !result.retryable()) {
            retry.setStatus(RetryStatus.EXHAUSTED);
            retryRepository.save(retry);

            PaymentStatus lastStatus = payment.getStatus();
            recordTransition(payment, lastStatus, PaymentStatus.FAILED,
                    "Retries exhausted after " + attempts + " attempt(s): " + result.reason());
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(result.reason());
            paymentRepository.save(payment);

            recordAudit(payment, "PAYMENT_RETRY_EXHAUSTED", "SYSTEM",
                    "Exhausted after " + attempts + " attempts: " + result.reason());

            outboxEventWriter.write(payment.getId(), KafkaTopics.PAYMENT_FAILED, "PAYMENT_FAILED",
                    PaymentFailedEvent.builder()
                            .paymentId(payment.getId())
                            .paymentReference(payment.getPaymentReference())
                            .amount(payment.getAmount().getAmount())
                            .currency(payment.getAmount().getCurrency())
                            .debtorParticipantId(payment.getDebtorParticipantId())
                            .creditorParticipantId(payment.getCreditorParticipantId())
                            .failedAtStage(lastStatus)
                            .failureReason(result.reason())
                            .failedAt(OffsetDateTime.now())
                            .build());

            log.warn("Retries exhausted paymentReference={} attempts={}", payment.getPaymentReference(), attempts);
            return;
        }

        long backoffSeconds = (long) (retryProperties.getInitialBackoffSeconds()
                * Math.pow(retryProperties.getBackoffMultiplier(), attempts));
        retry.setNextRetryTime(OffsetDateTime.now().plusSeconds(backoffSeconds));
        retry.setStatus(RetryStatus.SCHEDULED);
        retryRepository.save(retry);

        recordAudit(payment, "PAYMENT_RETRY_RESCHEDULED", "SYSTEM",
                "Attempt " + attempts + "/" + retry.getMaxRetry() + " failed, next attempt in " + backoffSeconds + "s: " + result.reason());

        log.info("Retry rescheduled paymentReference={} attempt={}/{} backoffSeconds={}",
                payment.getPaymentReference(), attempts, retry.getMaxRetry(), backoffSeconds);
    }

    private PaymentStatus successStatusFor(PaymentProcessor processor) {
        if (processor instanceof CreditProcessor) {
            return PaymentStatus.CREDIT_SUCCESS;
        }
        if (processor instanceof SettlementProcessor) {
            return PaymentStatus.SETTLED;
        }
        return PaymentStatus.DEBIT_SUCCESS;
    }

    private void recordTransition(Payment payment, PaymentStatus from, PaymentStatus to, String reason) {
        PaymentStatusHistory history = PaymentStatusHistory.builder()
                .paymentId(payment.getId())
                .fromStatus(from)
                .toStatus(to)
                .reason(reason)
                .transitionedAt(OffsetDateTime.now())
                .build();
        statusHistoryRepository.save(history);
    }

    private void recordAudit(Payment payment, String action, String actor, String details) {
        PaymentAudit audit = PaymentAudit.builder()
                .paymentId(payment.getId())
                .action(action)
                .actor(actor)
                .details(details)
                .occurredAt(OffsetDateTime.now())
                .build();
        auditRepository.save(audit);
    }
}
