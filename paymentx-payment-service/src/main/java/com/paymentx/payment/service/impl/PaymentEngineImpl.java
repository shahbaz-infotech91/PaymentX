package com.paymentx.payment.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentx.payment.client.RoutingResolutionException;
import com.paymentx.payment.config.RoutingClientProperties;
import com.paymentx.payment.entity.*;
import com.paymentx.payment.event.PaymentCompletedEvent;
import com.paymentx.payment.event.PaymentCreditedEvent;
import com.paymentx.payment.event.PaymentDebitedEvent;
import com.paymentx.payment.event.PaymentFailedEvent;
import com.paymentx.payment.event.PaymentProcessingStartedEvent;
import com.paymentx.payment.event.PaymentValidatedEvent;
import com.paymentx.payment.config.RetryProperties;
import com.paymentx.payment.constant.KafkaTopics;
import com.paymentx.payment.repository.PaymentAuditRepository;
import com.paymentx.payment.repository.PaymentOutboxRepository;
import com.paymentx.payment.repository.PaymentRepository;
import com.paymentx.payment.repository.PaymentRetryRepository;
import com.paymentx.payment.repository.PaymentStatusHistoryRepository;
import com.paymentx.payment.service.PaymentEngine;
import com.paymentx.payment.service.PaymentProcessor;
import com.paymentx.payment.service.RoutingResolutionService;
import com.paymentx.payment.util.LoggingContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * WHY the whole standard pipeline (debit then credit then settlement)
 * runs inside ONE @Transactional method rather than each stage being a
 * separately-triggered step: this is a deliberate simplification for
 * THIS stage of the project, appropriate because SchemeGateway and
 * SettlementProcessor currently resolve synchronously (no real network
 * call to a not-yet-built Routing Service exists yet). Once Routing
 * Service introduces genuine asynchronous rail responses, this method's
 * shape will need to split - flagging that now so it isn't mistaken for
 * an oversight later.
 *
 * WHY PaymentType alone doesn't drive the full sequence: for a standard
 * payment (paymentType=DEBIT, the current default - see the known gap
 * noted where Payment is built), the debit leg IS selected via the
 * strategy map, but credit and settlement are not payment "types" - they
 * are mandatory next steps of completing any standard payment. This
 * method selects DebitProcessor via the map, then explicitly invokes
 * creditProcessor and settlementProcessor as fixed pipeline stages.
 * RETURN/REVERSAL-type payments (a different, not-yet-built entry point)
 * would use only the map-selected processor with no forced chain - that
 * path is not exercised by processValidatedPayment.
 */
@Service
@Slf4j
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * PaymentEngineImpl is a service in the payment module of PaymentX. It lives in package com.paymentx.payment.service.impl and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * PaymentEngineImpl PaymentX ke payment module ka ek service hai. Ye com.paymentx.payment.service.impl package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class PaymentEngineImpl implements PaymentEngine {

    private final PaymentRepository paymentRepository;
    private final PaymentStatusHistoryRepository statusHistoryRepository;
    private final PaymentAuditRepository auditRepository;
    private final PaymentOutboxRepository outboxRepository;
    private final PaymentRetryRepository retryRepository;
    private final RetryProperties retryProperties;
    private final ObjectMapper objectMapper;
    private final Map<PaymentType, PaymentProcessor> processorsByType;
    private final CreditProcessor creditProcessor;
    private final SettlementProcessor settlementProcessor;
    private final RoutingResolutionService routingResolutionService;
    private final RoutingClientProperties routingClientProperties;

    public PaymentEngineImpl(PaymentRepository paymentRepository,
                              PaymentStatusHistoryRepository statusHistoryRepository,
                              PaymentAuditRepository auditRepository,
                              PaymentOutboxRepository outboxRepository,
                              PaymentRetryRepository retryRepository,
                              RetryProperties retryProperties,
                              ObjectMapper objectMapper,
                              List<PaymentProcessor> allProcessors,
                              CreditProcessor creditProcessor,
                              SettlementProcessor settlementProcessor,
                              RoutingResolutionService routingResolutionService,
                              RoutingClientProperties routingClientProperties) {
        this.paymentRepository = paymentRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.auditRepository = auditRepository;
        this.outboxRepository = outboxRepository;
        this.retryRepository = retryRepository;
        this.retryProperties = retryProperties;
        this.objectMapper = objectMapper;
        this.creditProcessor = creditProcessor;
        this.settlementProcessor = settlementProcessor;
        this.routingResolutionService = routingResolutionService;
        this.routingClientProperties = routingClientProperties;

        this.processorsByType = allProcessors.stream()
                .filter(p -> p.supports(PaymentType.DEBIT) || p.supports(PaymentType.RETURN)
                        || p.supports(PaymentType.REVERSAL))
                .filter(p -> !(p instanceof RetryProcessor))
                .collect(Collectors.toMap(this::resolveType, Function.identity()));
    }

    @Override
    @Transactional
    public void processValidatedPayment(PaymentValidatedEvent validatedEvent, String eventId,
                                         String traceId, String correlationId) {
        LoggingContext.setTraceId(traceId);
        LoggingContext.setCorrelationId(correlationId);

        Optional<Payment> existing = paymentRepository.findByIdempotencyKey(eventId);
        if (existing.isPresent()) {
            log.warn("Duplicate Kafka delivery detected for eventId={} paymentReference={} - skipping reprocessing",
                    eventId, validatedEvent.getPaymentReference());
            return;
        }

        Payment payment = createPayment(validatedEvent, eventId, traceId, correlationId);
        payment = paymentRepository.save(payment);
        LoggingContext.setPaymentId(payment.getId().toString());

        recordAudit(payment, "PAYMENT_RECEIVED", "SYSTEM",
                "Validated payment consumed from scheme-validated topic");

        transition(payment, null, PaymentStatus.PROCESSING, "Beginning processing pipeline");
        createOutboxEvent(payment, KafkaTopics.PAYMENT_PROCESSING, "PAYMENT_PROCESSING_STARTED",
                PaymentProcessingStartedEvent.builder()
                        .paymentId(payment.getId())
                        .paymentReference(payment.getPaymentReference())
                        .startedAt(OffsetDateTime.now())
                        .build());

        runPipeline(payment);

        paymentRepository.save(payment);
    }

    private void runPipeline(Payment payment) {
        OffsetDateTime pipelineStart = OffsetDateTime.now();

        if (!resolveRoute(payment, payment.getDebtorParticipantId())) {
            return; // routing failure already recorded inside resolveRoute() - do not proceed
        }

        PaymentProcessor debitProcessor = processorsByType.get(PaymentType.DEBIT);
        transition(payment, payment.getStatus(), PaymentStatus.DEBITING, "Attempting debit");
        PaymentProcessor.ProcessingResult debitResult = debitProcessor.process(payment);

        if (!debitResult.success()) {
            handleFailure(payment, PaymentStatus.DEBIT_FAILED, debitResult);
            return;
        }
        transition(payment, PaymentStatus.DEBITING, PaymentStatus.DEBIT_SUCCESS, "Debit succeeded");
        createOutboxEvent(payment, KafkaTopics.PAYMENT_DEBITED, "PAYMENT_DEBITED",
                PaymentDebitedEvent.builder()
                        .paymentId(payment.getId())
                        .paymentReference(payment.getPaymentReference())
                        .debtorParticipantId(payment.getDebtorParticipantId())
                        .amount(payment.getAmount())
                        .debitedAt(OffsetDateTime.now())
                        .build());

        if (!resolveRoute(payment, payment.getCreditorParticipantId())) {
            return; // routing failure already recorded inside resolveRoute() - do not proceed
        }

        transition(payment, PaymentStatus.DEBIT_SUCCESS, PaymentStatus.CREDITING, "Attempting credit");
        PaymentProcessor.ProcessingResult creditResult = creditProcessor.process(payment);

        if (!creditResult.success()) {
            handleFailure(payment, PaymentStatus.CREDIT_FAILED, creditResult);
            return;
        }
        transition(payment, PaymentStatus.CREDITING, PaymentStatus.CREDIT_SUCCESS, "Credit succeeded");
        createOutboxEvent(payment, KafkaTopics.PAYMENT_CREDITED, "PAYMENT_CREDITED",
                PaymentCreditedEvent.builder()
                        .paymentId(payment.getId())
                        .paymentReference(payment.getPaymentReference())
                        .creditorParticipantId(payment.getCreditorParticipantId())
                        .amount(payment.getAmount())
                        .creditedAt(OffsetDateTime.now())
                        .build());

        transition(payment, PaymentStatus.CREDIT_SUCCESS, PaymentStatus.SETTLING, "Attempting settlement");
        PaymentProcessor.ProcessingResult settleResult = settlementProcessor.process(payment);

        if (!settleResult.success()) {
            handleFailure(payment, PaymentStatus.FAILED, settleResult);
            return;
        }
        transition(payment, PaymentStatus.SETTLING, PaymentStatus.SETTLED, "Settlement succeeded");

        long durationMillis = Duration.between(pipelineStart, OffsetDateTime.now()).toMillis();
        createOutboxEvent(payment, KafkaTopics.PAYMENT_COMPLETED, "PAYMENT_COMPLETED",
                PaymentCompletedEvent.builder()
                        .paymentId(payment.getId())
                        .paymentReference(payment.getPaymentReference())
                        .amount(payment.getAmount().getAmount())
                        .currency(payment.getAmount().getCurrency())
                        .debtorParticipantId(payment.getDebtorParticipantId())
                        .creditorParticipantId(payment.getCreditorParticipantId())
                        .completedAt(OffsetDateTime.now())
                        .processingDurationMillis(durationMillis)
                        .build());

        recordAudit(payment, "PAYMENT_COMPLETED", "SYSTEM", "Payment reached SETTLED");
    }

    /**
     * Resolves the target route for one payment leg's participant via the
     * now-real Routing Service integration (RoutingResolutionService ->
     * RoutingClient -> Routing Service's existing, already-tested
     * resolveRoute() contract: participant-specific rule -> type-default
     * rule -> ROUTE_NOT_FOUND). This does NOT change which Payment-Type
     * gateway is used (SchemeGatewayFactory/InstantPaymentGateway/etc. are
     * untouched) - it only determines whether processing may proceed at
     * all.
     *
     * routingClientProperties.isEnabled()==false is the ONLY sanctioned
     * bypass of Routing Service - an explicit, operator-controlled
     * configuration state (emergency rollback without a redeploy, per
     * this platform's existing boolean-flag convention), not a runtime
     * fallback triggered by a routing failure. When enabled (the intended
     * steady state), any RoutingResolutionException - including
     * ROUTING_SERVICE_UNAVAILABLE/ROUTING_TIMEOUT - fails the payment via
     * the existing handleFailure() machinery; it never causes processing
     * to continue with the old pre-integration internal gateway selection
     * as if routing had succeeded.
     *
     * Returns true if processing may proceed; false if a failure was
     * already recorded and the caller must return immediately.
     */
    private boolean resolveRoute(Payment payment, String participantId) {
        if (!routingClientProperties.isEnabled()) {
            return true;
        }

        transition(payment, payment.getStatus(), PaymentStatus.ROUTING,
                "Resolving route for participantId=" + participantId);

        try {
            String targetRoute = routingResolutionService.resolveTargetRoute(
                    payment.getScheme().name(), participantId, payment.getCorrelationId());
            log.info("Routing succeeded paymentReference={} participantId={} targetRoute={}",
                    payment.getPaymentReference(), participantId, targetRoute);
            recordAudit(payment, "PAYMENT_ROUTED", "SYSTEM",
                    "participantId=" + participantId + " targetRoute=" + targetRoute);
            return true;
        } catch (RoutingResolutionException routingFailure) {
            log.warn("Routing failed paymentReference={} participantId={} reason={} retryable={}",
                    payment.getPaymentReference(), participantId,
                    routingFailure.getReason(), routingFailure.isRetryable());
            handleFailure(payment, PaymentStatus.ROUTING,
                    PaymentProcessor.ProcessingResult.failure(
                            routingFailure.getReason().name() + ": " + routingFailure.getMessage(),
                            routingFailure.isRetryable()));
            return false;
        }
    }

    /**
     * A retryable failure does NOT immediately publish PaymentFailedEvent:
     * "failed but will be retried" and "permanently failed" are different
     * facts. PaymentFailedEvent is reserved for the terminal FAILED
     * status, published either here (non-retryable failures) or by
     * RetryScheduler (Batch 5) once retries are exhausted.
     */
    private void handleFailure(Payment payment, PaymentStatus failedStageStatus,
                                PaymentProcessor.ProcessingResult result) {
        payment.setFailureReason(result.reason());

        if (result.retryable()) {
            transition(payment, payment.getStatus(), PaymentStatus.RETRYING,
                    "Retryable failure at " + failedStageStatus + ": " + result.reason());
            scheduleRetry(payment, result.reason());
            recordAudit(payment, "PAYMENT_RETRY_SCHEDULED", "SYSTEM", result.reason());
            return;
        }

        transition(payment, payment.getStatus(), failedStageStatus, result.reason());
        transition(payment, failedStageStatus, PaymentStatus.FAILED, "Non-retryable failure - terminal");
        recordAudit(payment, "PAYMENT_FAILED", "SYSTEM", result.reason());

        createOutboxEvent(payment, KafkaTopics.PAYMENT_FAILED, "PAYMENT_FAILED",
                PaymentFailedEvent.builder()
                        .paymentId(payment.getId())
                        .paymentReference(payment.getPaymentReference())
                        .amount(payment.getAmount().getAmount())
                        .currency(payment.getAmount().getCurrency())
                        .debtorParticipantId(payment.getDebtorParticipantId())
                        .creditorParticipantId(payment.getCreditorParticipantId())
                        .failedAtStage(failedStageStatus)
                        .failureReason(result.reason())
                        .failedAt(OffsetDateTime.now())
                        .build());
    }

    private void scheduleRetry(Payment payment, String reason) {
        PaymentRetry retry = PaymentRetry.builder()
                .paymentId(payment.getId())
                .currentRetry(0)
                .maxRetry(retryProperties.getMaxAttempts())
                .retryReason(reason)
                .status(RetryStatus.SCHEDULED)
                .nextRetryTime(OffsetDateTime.now().plusSeconds(retryProperties.getInitialBackoffSeconds()))
                .build();
        retryRepository.save(retry);
    }

    private Payment createPayment(PaymentValidatedEvent validatedEvent, String eventId,
                                   String traceId, String correlationId) {
        return Payment.builder()
                .paymentReference(validatedEvent.getPaymentReference())
                .idempotencyKey(eventId)
                .traceId(traceId)
                .correlationId(correlationId)
                .scheme(validatedEvent.getScheme())
                .paymentType(PaymentType.DEBIT)
                .channel(PaymentChannel.API)
                .amount(Money.of(validatedEvent.getAmount(), validatedEvent.getCurrency()))
                .debtorAccount(validatedEvent.getDebtorAccount())
                .debtorParticipantId(validatedEvent.getDebtorParticipantId())
                .creditorAccount(validatedEvent.getCreditorAccount())
                .creditorParticipantId(validatedEvent.getCreditorParticipantId())
                .status(PaymentStatus.RECEIVED)
                .build();
    }

    private void transition(Payment payment, PaymentStatus from, PaymentStatus to, String reason) {
        payment.setStatus(to);

        PaymentStatusHistory history = PaymentStatusHistory.builder()
                .paymentId(payment.getId())
                .fromStatus(from)
                .toStatus(to)
                .reason(reason)
                .transitionedAt(OffsetDateTime.now())
                .build();
        statusHistoryRepository.save(history);

        log.info("Payment status transition paymentReference={} from={} to={} reason={}",
                payment.getPaymentReference(), from, to, reason);
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

    /**
     * Never touches KafkaTemplate/producers - only writes a row to
     * payment_outbox, in the same transaction as every other change in
     * this method. OutboxProcessor (Batch 5) is the only code path
     * permitted to actually call a Kafka producer.
     */
    private <T> void createOutboxEvent(Payment payment, String topic, String eventType, T payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            PaymentOutbox outboxRecord = PaymentOutbox.builder()
                    .paymentId(payment.getId())
                    .eventType(eventType)
                    .topic(topic)
                    .payload(json)
                    .status(OutboxStatus.PENDING)
                    .retryCount(0)
                    .build();
            outboxRepository.save(outboxRecord);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize outbox payload for eventType=" + eventType, e);
        }
    }

    private PaymentType resolveType(PaymentProcessor processor) {
        for (PaymentType type : PaymentType.values()) {
            if (processor.supports(type)) {
                return type;
            }
        }
        throw new IllegalStateException(
                "PaymentProcessor " + processor.getClass().getSimpleName() + " supports no PaymentType");
    }
}
