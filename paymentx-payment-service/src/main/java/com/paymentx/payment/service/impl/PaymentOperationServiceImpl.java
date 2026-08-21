package com.paymentx.payment.service.impl;

import com.paymentx.common.constant.ErrorCodes;
import com.paymentx.common.exception.ConflictException;
import com.paymentx.common.exception.ResourceNotFoundException;
import com.paymentx.payment.constant.KafkaTopics;
import com.paymentx.payment.dto.CancellationRequest;
import com.paymentx.payment.dto.PaymentResponse;
import com.paymentx.payment.dto.PaymentRetryRequest;
import com.paymentx.payment.entity.Payment;
import com.paymentx.payment.entity.PaymentAudit;
import com.paymentx.payment.entity.PaymentRetry;
import com.paymentx.payment.entity.PaymentStatus;
import com.paymentx.payment.entity.PaymentStatusHistory;
import com.paymentx.payment.entity.RetryStatus;
import com.paymentx.payment.event.PaymentCancelledEvent;
import com.paymentx.payment.mapper.PaymentMapper;
import com.paymentx.payment.outbox.OutboxEventWriter;
import com.paymentx.payment.repository.PaymentAuditRepository;
import com.paymentx.payment.repository.PaymentRepository;
import com.paymentx.payment.repository.PaymentRetryRepository;
import com.paymentx.payment.repository.PaymentStatusHistoryRepository;
import com.paymentx.payment.config.RetryProperties;
import com.paymentx.payment.service.PaymentOperationService;
import com.paymentx.payment.util.LoggingContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

/**
 * WHY neither method here ever calls PaymentEngine, a PaymentProcessor,
 * or a Kafka producer directly (the central constraint of this class):
 * retry() seeds a new PaymentRetry row with nextRetryTime=now - the
 * ALREADY-RUNNING RetryScheduler (a completely separate, pre-existing
 * background component) picks it up on its next poll tick and performs
 * the actual retry through the normal RetryScheduler-processor path.
 * cancel() writes a terminal status directly and an outbox row - the
 * ALREADY-RUNNING OutboxProcessor publishes the resulting event. Both
 * operations are therefore "trigger existing machinery", not "add a new
 * processing path" - the Kafka-based payment.validated consumption flow
 * (PaymentValidatedConsumer -> PaymentEngineImpl) is completely
 * untouched by this class.
 */
@Service
@Slf4j
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * PaymentOperationServiceImpl is a service in the payment module of PaymentX. It lives in package com.paymentx.payment.service.impl and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * PaymentOperationServiceImpl PaymentX ke payment module ka ek service hai. Ye com.paymentx.payment.service.impl package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class PaymentOperationServiceImpl implements PaymentOperationService {

    private static final Set<PaymentStatus> CANCELLABLE_STATUSES =
            Set.of(PaymentStatus.RECEIVED, PaymentStatus.VALIDATED, PaymentStatus.PROCESSING, PaymentStatus.RETRYING);

    private static final Set<PaymentStatus> RETRYABLE_STATUSES =
            Set.of(PaymentStatus.FAILED, PaymentStatus.DEBIT_FAILED, PaymentStatus.CREDIT_FAILED, PaymentStatus.TIMEOUT);

    private final PaymentRepository paymentRepository;
    private final PaymentStatusHistoryRepository statusHistoryRepository;
    private final PaymentAuditRepository auditRepository;
    private final PaymentRetryRepository retryRepository;
    private final OutboxEventWriter outboxEventWriter;
    private final RetryProperties retryProperties;
    private final PaymentMapper paymentMapper;

    public PaymentOperationServiceImpl(PaymentRepository paymentRepository,
                                        PaymentStatusHistoryRepository statusHistoryRepository,
                                        PaymentAuditRepository auditRepository,
                                        PaymentRetryRepository retryRepository,
                                        OutboxEventWriter outboxEventWriter,
                                        RetryProperties retryProperties,
                                        PaymentMapper paymentMapper) {
        this.paymentRepository = paymentRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.auditRepository = auditRepository;
        this.retryRepository = retryRepository;
        this.outboxEventWriter = outboxEventWriter;
        this.retryProperties = retryProperties;
        this.paymentMapper = paymentMapper;
    }

    @Override
    @Transactional
    public PaymentResponse retry(String paymentReference, PaymentRetryRequest request) {
        Payment payment = findOrThrow(paymentReference);
        LoggingContext.setPaymentId(payment.getId().toString());

        if (!RETRYABLE_STATUSES.contains(payment.getStatus())) {
            throw new ConflictException(ErrorCodes.CONFLICT,
                    "Payment '" + paymentReference + "' is in status " + payment.getStatus()
                            + " and is not eligible for retry. Eligible statuses: " + RETRYABLE_STATUSES);
        }

        String reason = (request.reason() != null && !request.reason().isBlank())
                ? request.reason()
                : "Manually triggered by " + request.requestedBy();

        PaymentRetry retry = PaymentRetry.builder()
                .paymentId(payment.getId())
                .currentRetry(0)
                .maxRetry(retryProperties.getMaxAttempts())
                .retryReason(reason)
                .status(RetryStatus.SCHEDULED)
                .nextRetryTime(OffsetDateTime.now())
                .build();
        retryRepository.save(retry);

        PaymentStatus previousStatus = payment.getStatus();
        transition(payment, previousStatus, PaymentStatus.RETRYING, reason);
        payment.setStatus(PaymentStatus.RETRYING);
        paymentRepository.save(payment);

        recordAudit(payment, "PAYMENT_RETRY_MANUALLY_TRIGGERED", request.requestedBy(), reason);

        log.info("Manual retry triggered paymentReference={} requestedBy={} previousStatus={}",
                paymentReference, request.requestedBy(), previousStatus);

        return paymentMapper.toResponse(payment);
    }

    @Override
    @Transactional
    public PaymentResponse cancel(String paymentReference, CancellationRequest request) {
        Payment payment = findOrThrow(paymentReference);
        LoggingContext.setPaymentId(payment.getId().toString());

        if (!CANCELLABLE_STATUSES.contains(payment.getStatus())) {
            throw new ConflictException(ErrorCodes.CONFLICT,
                    "Payment '" + paymentReference + "' is in status " + payment.getStatus()
                            + " and can no longer be cancelled - funds may already have moved. "
                            + "Eligible statuses: " + CANCELLABLE_STATUSES);
        }

        PaymentStatus previousStatus = payment.getStatus();
        transition(payment, previousStatus, PaymentStatus.CANCELLED, request.reason());
        payment.setStatus(PaymentStatus.CANCELLED);
        paymentRepository.save(payment);

        List<PaymentRetry> retries = retryRepository.findByPaymentIdOrderByCurrentRetryAsc(payment.getId());
        retries.stream()
                .filter(r -> r.getStatus() == RetryStatus.SCHEDULED)
                .forEach(r -> {
                    r.setStatus(RetryStatus.EXHAUSTED);
                    retryRepository.save(r);
                });

        recordAudit(payment, "PAYMENT_CANCELLED", request.requestedBy(), request.reason());

        outboxEventWriter.write(payment.getId(), KafkaTopics.PAYMENT_CANCELLED, "PAYMENT_CANCELLED",
                PaymentCancelledEvent.builder()
                        .paymentId(payment.getId())
                        .paymentReference(payment.getPaymentReference())
                        .amount(payment.getAmount().getAmount())
                        .currency(payment.getAmount().getCurrency())
                        .debtorParticipantId(payment.getDebtorParticipantId())
                        .creditorParticipantId(payment.getCreditorParticipantId())
                        .cancellationReason(request.reason())
                        .cancelledBy(request.requestedBy())
                        .cancelledAt(OffsetDateTime.now())
                        .build());

        log.info("Payment cancelled paymentReference={} requestedBy={} previousStatus={}",
                paymentReference, request.requestedBy(), previousStatus);

        return paymentMapper.toResponse(payment);
    }

    private Payment findOrThrow(String paymentReference) {
        return paymentRepository.findByPaymentReference(paymentReference)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentReference));
    }

    private void transition(Payment payment, PaymentStatus from, PaymentStatus to, String reason) {
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
