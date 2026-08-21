package com.paymentx.controlcenter.service;

import com.paymentx.controlcenter.dto.postgres.AuditEventSummary;
import com.paymentx.controlcenter.dto.postgres.PaymentFlowDetail;
import com.paymentx.controlcenter.dto.postgres.PaymentFlowStage;
import com.paymentx.controlcenter.dto.postgres.PaymentFlowStageStatus;
import com.paymentx.controlcenter.dto.postgres.PaymentSummary;
import com.paymentx.controlcenter.repository.AuditEventRepository;
import com.paymentx.controlcenter.repository.NotificationRepository;
import com.paymentx.controlcenter.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * ENGLISH: Phase 12 Defect #2 remediation - proves the Payment Flow
 * "Validation/Routing: Not Started" defect is fixed for Validation (real
 * reference-based correlation, since validation-service's real audit
 * events populate reference but leave payment_id blank) and honestly
 * addressed for Routing (UNAVAILABLE with a real reason, since
 * routing-service's real audit events carry no correlation field at all
 * - proven with a genuinely-unmatchable event so this stays a defect
 * that is reported, not papered over). Repositories are mocked - no real
 * database involved, matching this backend's existing service-test
 * convention (see PostgresDataServiceTest).
 *
 * HINGLISH: Phase 12 Defect #2 remediation - proves karta hai ki Payment
 * Flow ka "Validation/Routing: Not Started" defect Validation ke liye
 * fix hai (real reference-based correlation, kyunki validation-service
 * ke real audit events reference populate karte hain, payment_id nahi)
 * aur Routing ke liye honestly address kiya gaya hai (UNAVAILABLE ek
 * real reason ke saath, kyunki routing-service ke real audit events me
 * koi correlation field hoti hi nahi - ek genuinely-unmatchable event se
 * proven, taaki ye ek defect hi rahe jo report ho, chhupaya na jaaye).
 * Repositories mocked hain - koi real database involved nahi, is
 * backend ke existing service-test convention ko match karte hue.
 */
@ExtendWith(MockitoExtension.class)
class PaymentFlowServiceTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private AuditEventRepository auditEventRepository;
    @Mock
    private NotificationRepository notificationRepository;

    private PaymentFlowService service() {
        return new PaymentFlowService(paymentRepository, auditEventRepository, notificationRepository);
    }

    private PaymentSummary payment(String id, String reference, String status) {
        return new PaymentSummary(id, reference, "corr-1", "trace-1", "INSTANT_PAYMENT", "CREDIT_TRANSFER",
                "API", new BigDecimal("1.00"), "USD", "DEBTOR-ACC", "BANK001", "CREDITOR-ACC", "BANK002",
                status, null, OffsetDateTime.now(), OffsetDateTime.now());
    }

    private AuditEventSummary auditEvent(String sourceService, String eventType, String paymentId, String reference) {
        return new AuditEventSummary("evt-1", eventType, "RECORDED", sourceService, "actor-1", "SYSTEM",
                "corr-1", "trace-1", paymentId, "BANK001", reference, OffsetDateTime.now());
    }

    private PaymentFlowStage stage(PaymentFlowDetail flow, String name) {
        return flow.stages().stream().filter(s -> s.stage().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("No stage named " + name));
    }

    @Test
    void validationCompletedEvent_withBlankPaymentIdButRealReference_isNoLongerMissed() {
        // Reproduces the real, verified production shape: validation-service's audit
        // event has an empty payment_id (the payment row does not exist yet at that
        // point in the pipeline) but a real, populated reference.
        PaymentSummary payment = payment("pay-1", "CC-E2E-TEST-REF", "SETTLED");
        when(paymentRepository.findByReference("CC-E2E-TEST-REF")).thenReturn(Optional.of(payment));
        when(auditEventRepository.findByPaymentIdOrReference("pay-1", "CC-E2E-TEST-REF")).thenReturn(List.of(
                auditEvent("validation-service", "VALIDATION_COMPLETED", "", "CC-E2E-TEST-REF")));
        when(notificationRepository.findByPaymentId("pay-1")).thenReturn(List.of());

        PaymentFlowDetail flow = service().flowForReference("CC-E2E-TEST-REF");

        PaymentFlowStage validation = stage(flow, "Validation");
        assertThat(validation.status()).isEqualTo(PaymentFlowStageStatus.COMPLETED);
        assertThat(validation.detail()).isEqualTo("VALIDATION_COMPLETED");
    }

    @Test
    void genuinelyNoValidationEvent_stillReportsNotStarted() {
        // A real payment with no validation-service audit event anywhere (by
        // payment_id or reference) must still say NOT_STARTED - the fix must not
        // turn every payment's Validation stage into a false COMPLETED.
        PaymentSummary payment = payment("pay-2", "CC-E2E-NO-VALIDATION", "PROCESSING");
        when(paymentRepository.findByReference("CC-E2E-NO-VALIDATION")).thenReturn(Optional.of(payment));
        when(auditEventRepository.findByPaymentIdOrReference("pay-2", "CC-E2E-NO-VALIDATION")).thenReturn(List.of());
        when(notificationRepository.findByPaymentId("pay-2")).thenReturn(List.of());

        PaymentFlowDetail flow = service().flowForReference("CC-E2E-NO-VALIDATION");

        assertThat(stage(flow, "Validation").status()).isEqualTo(PaymentFlowStageStatus.NOT_STARTED);
    }

    @Test
    void routingEvent_withNoCorrelationFieldAtAll_isReportedUnavailableNotFalselyNotStarted() {
        // Reproduces the real, verified production shape: routing-service's audit
        // event has payment_id AND reference both blank, so
        // findByPaymentIdOrReference genuinely cannot return it here (there is
        // nothing to match on) - proving this stays an honest UNAVAILABLE, not a
        // silently invented COMPLETED, and not the old misleading NOT_STARTED.
        PaymentSummary payment = payment("pay-3", "CC-E2E-ROUTED", "SETTLED");
        when(paymentRepository.findByReference("CC-E2E-ROUTED")).thenReturn(Optional.of(payment));
        when(auditEventRepository.findByPaymentIdOrReference("pay-3", "CC-E2E-ROUTED")).thenReturn(List.of());
        when(notificationRepository.findByPaymentId("pay-3")).thenReturn(List.of());

        PaymentFlowDetail flow = service().flowForReference("CC-E2E-ROUTED");

        PaymentFlowStage routing = stage(flow, "Routing");
        assertThat(routing.status()).isEqualTo(PaymentFlowStageStatus.UNAVAILABLE);
        assertThat(routing.detail()).contains("routing-service").contains("cannot be reliably attributed");
    }

    @Test
    void routingEvent_ifItEverDoesCarryAMatchingIdentifier_isReportedRealCompleted() {
        // If routing-service is ever fixed to populate a correlation field, this
        // stage must immediately start reporting the real COMPLETED state - the
        // remediation must not hardcode UNAVAILABLE regardless of data.
        PaymentSummary payment = payment("pay-4", "CC-E2E-ROUTED-FIXED", "SETTLED");
        when(paymentRepository.findByReference("CC-E2E-ROUTED-FIXED")).thenReturn(Optional.of(payment));
        when(auditEventRepository.findByPaymentIdOrReference("pay-4", "CC-E2E-ROUTED-FIXED")).thenReturn(List.of(
                auditEvent("routing-service", "PAYMENT_ROUTED", "pay-4", "CC-E2E-ROUTED-FIXED")));
        when(notificationRepository.findByPaymentId("pay-4")).thenReturn(List.of());

        PaymentFlowDetail flow = service().flowForReference("CC-E2E-ROUTED-FIXED");

        PaymentFlowStage routing = stage(flow, "Routing");
        assertThat(routing.status()).isEqualTo(PaymentFlowStageStatus.COMPLETED);
        assertThat(routing.detail()).isEqualTo("PAYMENT_ROUTED");
    }
}
