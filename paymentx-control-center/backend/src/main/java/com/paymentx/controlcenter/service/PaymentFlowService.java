package com.paymentx.controlcenter.service;

import com.paymentx.controlcenter.dto.postgres.*;
import com.paymentx.controlcenter.exception.ControlCenterException;
import com.paymentx.controlcenter.repository.AuditEventRepository;
import com.paymentx.controlcenter.repository.NotificationRepository;
import com.paymentx.controlcenter.repository.PaymentRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * ENGLISH: Assembles the real, 9-stage Payment Flow view for one real
 * payment by reading three real tables (payment, audit_event,
 * notification) - it never infers a stage as COMPLETED without a real
 * row backing that claim. What it does per stage: GATEWAY is inferred
 * COMPLETED purely from the payment row existing (a payment cannot
 * exist without having passed through the gateway); AUTHENTICATION is
 * always UNAVAILABLE because auth-service has zero business logic and
 * leaves no audit trail (verified during earlier PaymentX
 * observability work); VALIDATION is COMPLETED only if a real
 * audit_event row from validation-service matches this payment_id OR
 * reference (validation-service populates reference but not payment_id,
 * verified live - Phase 12 defect remediation), else NOT_STARTED;
 * ROUTING is COMPLETED only if a real audit_event row from
 * routing-service matches, else UNAVAILABLE (not NOT_STARTED) because
 * routing-service's audit events carry no correlation field at all
 * (verified live) - see routingStage()'s own javadoc; PAYMENT reflects the real,
 * current payment.status; AUDIT is COMPLETED if any audit_event row
 * references this payment_id at all; NOTIFICATION reflects the latest
 * real notification row's real status; RECONCILIATION and REPORTING
 * are always UNAVAILABLE because those domains operate on batches/
 * settlement files and report executions, which this schema does not
 * link back to an individual payment_id. Why it exists: the Phase 3
 * "use actual payment/correlation/trace information" + "never fake
 * unavailable metrics" requirements together, for the Payment Flow
 * feature specifically.
 *
 * HINGLISH: Ek real payment ke liye real, 9-stage Payment Flow view
 * teen real tables (payment, audit_event, notification) padh kar
 * assemble karta hai - ye kabhi kisi stage ko COMPLETED infer nahi
 * karta jab tak us claim ko backup karne wali ek real row na ho. Har
 * stage ke liye: GATEWAY purely payment row ke exist karne se
 * COMPLETED infer hota hai (ek payment gateway se guzre bina exist
 * nahi kar sakta); AUTHENTICATION hamesha UNAVAILABLE hai kyunki
 * auth-service ke paas zero business logic hai aur wo koi audit trail
 * nahi chhodta (pehle ke PaymentX observability work me verify kiya
 * gaya); VALIDATION sirf tabhi COMPLETED hai jab validation-service se
 * is payment_id YA reference waali ek real audit_event row match kare
 * (validation-service reference populate karta hai, payment_id nahi -
 * Phase 12 defect remediation), warna NOT_STARTED; ROUTING sirf tabhi
 * COMPLETED hai jab routing-service se ek real audit_event row match
 * kare, warna UNAVAILABLE (NOT_STARTED nahi) kyunki routing-service ke
 * audit events me koi bhi correlation field nahi hoti (live verify
 * kiya gaya); PAYMENT real, current
 * payment.status reflect karta hai; AUDIT COMPLETED hai agar koi bhi
 * audit_event row is payment_id ko reference kare; NOTIFICATION
 * latest real notification row ke real status ko reflect karta hai;
 * RECONCILIATION aur REPORTING hamesha UNAVAILABLE hain kyunki wo
 * domains batches/settlement files aur report executions par operate
 * karte hain, jinhe ye schema ek individual payment_id se link nahi
 * karta. Ye dashboard me kyu hai: Phase 3 ke "use actual
 * payment/correlation/trace information" + "never fake unavailable
 * metrics" requirements ek saath, specifically Payment Flow feature
 * ke liye.
 */
@Service
public class PaymentFlowService {

    private static final Set<String> SUCCESSFUL_STATUSES = Set.of("SETTLED");
    private static final Set<String> FAILED_STATUSES = Set.of(
            "DEBIT_FAILED", "CREDIT_FAILED", "FAILED", "TIMEOUT", "CANCELLED", "RETURNED", "REVERSED");

    private final PaymentRepository paymentRepository;
    private final AuditEventRepository auditEventRepository;
    private final NotificationRepository notificationRepository;

    public PaymentFlowService(PaymentRepository paymentRepository,
                               AuditEventRepository auditEventRepository,
                               NotificationRepository notificationRepository) {
        this.paymentRepository = paymentRepository;
        this.auditEventRepository = auditEventRepository;
        this.notificationRepository = notificationRepository;
    }

    public PaymentFlowDetail flowForReference(String reference) {
        PaymentSummary payment = paymentRepository.findByReference(reference)
                .orElseThrow(() -> new ControlCenterException("PAYMENT_NOT_FOUND", "No payment found with reference: " + reference));
        return buildFlow(payment);
    }

    public PaymentFlowDetail flowForId(String paymentId) {
        PaymentSummary payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ControlCenterException("PAYMENT_NOT_FOUND", "No payment found with id: " + paymentId));
        return buildFlow(payment);
    }

    private PaymentFlowDetail buildFlow(PaymentSummary payment) {
        // Widened to also match by reference (Defect #2 remediation) - validation-service's
        // real audit events populate reference but leave payment_id blank (the payment row
        // does not exist yet at that point in the pipeline), so a payment_id-only lookup
        // silently missed them. See AuditEventRepository#findByPaymentIdOrReference javadoc.
        List<AuditEventSummary> auditEvents = auditEventRepository.findByPaymentIdOrReference(payment.id(), payment.paymentReference());
        List<NotificationSummary> notifications = notificationRepository.findByPaymentId(payment.id());

        List<PaymentFlowStage> stages = List.of(
                gatewayStage(payment),
                authenticationStage(),
                serviceAuditStage("Validation", "validation-service", auditEvents),
                routingStage(auditEvents),
                paymentStage(payment),
                auditStage(auditEvents),
                notificationStage(notifications),
                unavailableStage("Reconciliation", "Reconciliation batches operate on settlement files, not individual payments - this schema has no per-payment reconciliation linkage."),
                unavailableStage("Reporting", "Report executions are not linked to individual payments in this schema."));

        return new PaymentFlowDetail(payment.id(), payment.paymentReference(), payment.correlationId(),
                payment.traceId(), payment.status(), stages);
    }

    private PaymentFlowStage gatewayStage(PaymentSummary payment) {
        return new PaymentFlowStage("Gateway", PaymentFlowStageStatus.COMPLETED,
                "Inferred from the payment record existing - a payment cannot be created without passing through the gateway.",
                payment.createdAt());
    }

    private PaymentFlowStage authenticationStage() {
        return new PaymentFlowStage("Authentication", PaymentFlowStageStatus.UNAVAILABLE,
                "auth-service has no business logic or database of its own (verified) - authentication cannot be tracked per-payment.",
                null);
    }

    /**
     * Routing gets its own stage method, not the generic serviceAuditStage,
     * because of a real, verified asymmetry (Defect #2 remediation): unlike
     * validation-service, routing-service's real audit_event rows never
     * populate payment_id, correlation_id, trace_id, OR reference on any row
     * (confirmed live against paymentx_audit.audit_event) - so an absent match
     * here does not mean routing has not started, it means this schema cannot
     * currently attribute a routing-service event to one specific payment at
     * all. Reporting that honestly as UNAVAILABLE (this class's existing,
     * established status for "cannot determine from available data") rather
     * than the misleading NOT_STARTED, which would incorrectly imply routing
     * genuinely has not run yet. If routing-service is ever changed to include
     * a correlation field on its audit events, the match branch below will
     * immediately start reporting the real COMPLETED state with no further
     * change needed here.
     */
    private PaymentFlowStage routingStage(List<AuditEventSummary> auditEvents) {
        return auditEvents.stream()
                .filter(e -> "routing-service".equals(e.sourceService()))
                .findFirst()
                .map(e -> new PaymentFlowStage("Routing", PaymentFlowStageStatus.COMPLETED, e.eventType(), e.occurredAt()))
                .orElseGet(() -> new PaymentFlowStage("Routing", PaymentFlowStageStatus.UNAVAILABLE,
                        "routing-service's audit events do not carry payment_id, correlation_id, trace_id, or reference " +
                                "(verified live), so a routing event cannot be reliably attributed to this specific payment " +
                                "with the data currently emitted. This does not mean routing has not run.",
                        null));
    }

    private PaymentFlowStage serviceAuditStage(String stageName, String sourceService, List<AuditEventSummary> auditEvents) {
        return auditEvents.stream()
                .filter(e -> sourceService.equals(e.sourceService()))
                .findFirst()
                .map(e -> new PaymentFlowStage(stageName, PaymentFlowStageStatus.COMPLETED, e.eventType(), e.occurredAt()))
                .orElseGet(() -> new PaymentFlowStage(stageName, PaymentFlowStageStatus.NOT_STARTED,
                        "No audit_event row from " + sourceService + " references this payment yet.", null));
    }

    private PaymentFlowStage paymentStage(PaymentSummary payment) {
        PaymentFlowStageStatus status;
        if (SUCCESSFUL_STATUSES.contains(payment.status())) status = PaymentFlowStageStatus.COMPLETED;
        else if (FAILED_STATUSES.contains(payment.status())) status = PaymentFlowStageStatus.FAILED;
        else status = PaymentFlowStageStatus.PROCESSING;
        return new PaymentFlowStage("Payment", status, payment.status(), payment.updatedAt());
    }

    private PaymentFlowStage auditStage(List<AuditEventSummary> auditEvents) {
        if (auditEvents.isEmpty()) {
            return new PaymentFlowStage("Audit", PaymentFlowStageStatus.NOT_STARTED,
                    "No audit_event row references this payment yet.", null);
        }
        AuditEventSummary latest = auditEvents.get(auditEvents.size() - 1);
        return new PaymentFlowStage("Audit", PaymentFlowStageStatus.COMPLETED,
                auditEvents.size() + " audit event(s) recorded, most recent: " + latest.eventType(), latest.occurredAt());
    }

    private PaymentFlowStage notificationStage(List<NotificationSummary> notifications) {
        if (notifications.isEmpty()) {
            return new PaymentFlowStage("Notification", PaymentFlowStageStatus.NOT_STARTED,
                    "No notification row references this payment yet.", null);
        }
        NotificationSummary latest = notifications.get(notifications.size() - 1);
        PaymentFlowStageStatus status = switch (latest.status()) {
            case "SENT" -> PaymentFlowStageStatus.COMPLETED;
            case "SENDING", "PENDING" -> PaymentFlowStageStatus.PROCESSING;
            case "FAILED" -> PaymentFlowStageStatus.FAILED;
            default -> PaymentFlowStageStatus.PROCESSING;
        };
        return new PaymentFlowStage("Notification", status,
                latest.channel() + " notification " + latest.status().toLowerCase(), latest.createdAt());
    }

    private PaymentFlowStage unavailableStage(String stageName, String reason) {
        return new PaymentFlowStage(stageName, PaymentFlowStageStatus.UNAVAILABLE, reason, null);
    }
}
