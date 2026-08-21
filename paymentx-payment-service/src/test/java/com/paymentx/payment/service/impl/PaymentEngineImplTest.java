package com.paymentx.payment.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.paymentx.payment.client.RoutingResolutionException;
import com.paymentx.payment.config.RetryProperties;
import com.paymentx.payment.config.RoutingClientProperties;
import com.paymentx.payment.entity.Payment;
import com.paymentx.payment.entity.PaymentScheme;
import com.paymentx.payment.entity.PaymentStatus;
import com.paymentx.payment.entity.PaymentType;
import com.paymentx.payment.event.PaymentValidatedEvent;
import com.paymentx.payment.repository.PaymentAuditRepository;
import com.paymentx.payment.repository.PaymentOutboxRepository;
import com.paymentx.payment.repository.PaymentRepository;
import com.paymentx.payment.repository.PaymentRetryRepository;
import com.paymentx.payment.repository.PaymentStatusHistoryRepository;
import com.paymentx.payment.service.PaymentProcessor;
import com.paymentx.payment.service.RoutingResolutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers the Phase 1 Routing Service integration inserted into
 * PaymentEngineImpl.runPipeline() (via the new resolveRoute() helper).
 * The behavior under test is the one explicitly required by this task:
 * routing success -> processing continues exactly as before; routing
 * failure -> payment fails/retries, and the existing Payment-Type gateway
 * (represented here by a mocked PaymentProcessor for DEBIT) is NEVER
 * invoked - no silent fallback to the pre-integration internal selection.
 */
class PaymentEngineImplTest {

    private PaymentRepository paymentRepository;
    private PaymentStatusHistoryRepository statusHistoryRepository;
    private PaymentAuditRepository auditRepository;
    private PaymentOutboxRepository outboxRepository;
    private PaymentRetryRepository retryRepository;
    private RetryProperties retryProperties;
    private PaymentProcessor debitProcessor;
    private CreditProcessor creditProcessor;
    private SettlementProcessor settlementProcessor;
    private RoutingResolutionService routingResolutionService;
    private RoutingClientProperties routingClientProperties;

    private PaymentEngineImpl engine;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        statusHistoryRepository = mock(PaymentStatusHistoryRepository.class);
        auditRepository = mock(PaymentAuditRepository.class);
        outboxRepository = mock(PaymentOutboxRepository.class);
        retryRepository = mock(PaymentRetryRepository.class);
        retryProperties = new RetryProperties();
        debitProcessor = mock(PaymentProcessor.class);
        creditProcessor = mock(CreditProcessor.class);
        settlementProcessor = mock(SettlementProcessor.class);
        routingResolutionService = mock(RoutingResolutionService.class);
        routingClientProperties = new RoutingClientProperties();
        routingClientProperties.setEnabled(true);

        when(debitProcessor.supports(PaymentType.DEBIT)).thenReturn(true);
        when(paymentRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            if (payment.getId() == null) {
                payment.setId(UUID.randomUUID());
            }
            return payment;
        });

        engine = new PaymentEngineImpl(paymentRepository, statusHistoryRepository, auditRepository,
                outboxRepository, retryRepository, retryProperties, new ObjectMapper().registerModule(new JavaTimeModule()),
                List.of(debitProcessor), creditProcessor, settlementProcessor,
                routingResolutionService, routingClientProperties);
    }

    private PaymentValidatedEvent validEvent() {
        return PaymentValidatedEvent.builder()
                .paymentReference("TEST-E2E-ROUTING-" + UUID.randomUUID())
                .scheme(PaymentScheme.INSTANT_PAYMENT)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .debtorAccount("ACC-DEBTOR")
                .debtorParticipantId("BANK001")
                .creditorAccount("ACC-CREDITOR")
                .creditorParticipantId("BANK002")
                .build();
    }

    @Test
    void routingSucceedsForBothLegs_pipelineProceedsUnchanged() {
        when(routingResolutionService.resolveTargetRoute(eq("INSTANT_PAYMENT"), eq("BANK001"), any()))
                .thenReturn("instant-payment-processor-bank001");
        when(routingResolutionService.resolveTargetRoute(eq("INSTANT_PAYMENT"), eq("BANK002"), any()))
                .thenReturn("instant-payment-processor");
        when(debitProcessor.process(any())).thenReturn(PaymentProcessor.ProcessingResult.ok());
        when(creditProcessor.process(any())).thenReturn(PaymentProcessor.ProcessingResult.ok());
        when(settlementProcessor.process(any())).thenReturn(PaymentProcessor.ProcessingResult.ok());

        engine.processValidatedPayment(validEvent(), "event-1", "trace-1", "corr-1");

        verify(debitProcessor).process(any());
        verify(creditProcessor).process(any());
        verify(settlementProcessor).process(any());

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(PaymentStatus.SETTLED);
    }

    @Test
    void noRouteFound_failsPayment_gatewayNeverInvoked() {
        when(routingResolutionService.resolveTargetRoute(eq("INSTANT_PAYMENT"), eq("BANK001"), any()))
                .thenThrow(new RoutingResolutionException(
                        RoutingResolutionException.Reason.ROUTE_NOT_FOUND, false, "no route configured"));

        engine.processValidatedPayment(validEvent(), "event-2", "trace-2", "corr-2");

        verify(debitProcessor, never()).process(any());
        verify(retryRepository, never()).save(any());

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(saved.getValue().getFailureReason()).contains("ROUTE_NOT_FOUND");
    }

    @Test
    void routingServiceUnavailable_schedulesRetry_gatewayNeverInvoked_noSilentFallback() {
        when(routingResolutionService.resolveTargetRoute(eq("INSTANT_PAYMENT"), eq("BANK001"), any()))
                .thenThrow(new RoutingResolutionException(
                        RoutingResolutionException.Reason.ROUTING_SERVICE_UNAVAILABLE, true, "simulated outage"));

        engine.processValidatedPayment(validEvent(), "event-3", "trace-3", "corr-3");

        // The critical assertion for this task: a transient routing failure
        // must NEVER result in the old internal gateway selection running as
        // if routing had succeeded.
        verify(debitProcessor, never()).process(any());
        verify(retryRepository).save(any());

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(PaymentStatus.RETRYING);
    }

    @Test
    void routingTimeout_isTreatedAsRetryable_gatewayNeverInvoked() {
        when(routingResolutionService.resolveTargetRoute(eq("INSTANT_PAYMENT"), eq("BANK001"), any()))
                .thenThrow(new RoutingResolutionException(
                        RoutingResolutionException.Reason.ROUTING_TIMEOUT, true, "simulated timeout"));

        engine.processValidatedPayment(validEvent(), "event-4", "trace-4", "corr-4");

        verify(debitProcessor, never()).process(any());
        verify(retryRepository).save(any());
    }

    @Test
    void routingDisabledByFlag_bypassesRoutingEntirely_usesOldGatewaySelectionDirectly() {
        routingClientProperties.setEnabled(false);
        when(debitProcessor.process(any())).thenReturn(PaymentProcessor.ProcessingResult.ok());
        when(creditProcessor.process(any())).thenReturn(PaymentProcessor.ProcessingResult.ok());
        when(settlementProcessor.process(any())).thenReturn(PaymentProcessor.ProcessingResult.ok());

        engine.processValidatedPayment(validEvent(), "event-5", "trace-5", "corr-5");

        // Explicit, operator-controlled rollback path: routing is skipped
        // entirely (not attempted-and-ignored), and the existing gateway
        // selection runs exactly as it did before this integration.
        verifyNoInteractions(routingResolutionService);
        verify(debitProcessor).process(any());
        verify(creditProcessor).process(any());
    }

    @Test
    void duplicateEventId_isSkipped_routingNeverInvoked() {
        Payment existing = Payment.builder().paymentReference("EXISTING").build();
        when(paymentRepository.findByIdempotencyKey("event-6")).thenReturn(Optional.of(existing));

        engine.processValidatedPayment(validEvent(), "event-6", "trace-6", "corr-6");

        verifyNoInteractions(routingResolutionService);
        verify(debitProcessor, never()).process(any());
    }
}
