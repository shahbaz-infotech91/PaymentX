package com.paymentx.controlcenter.dto.postgres;

import java.util.List;

/**
 * ENGLISH: The full, real Payment Flow view for one real payment - its
 * real identifiers (id, reference, correlationId, traceId), its real
 * current payment.status, and the real 9-stage breakdown assembled by
 * PaymentFlowService from actual payment/audit_event/notification
 * rows.
 *
 * HINGLISH: Ek real payment ke liye poora, real Payment Flow view -
 * uske real identifiers (id, reference, correlationId, traceId), uska
 * real current payment.status, aur PaymentFlowService dwara actual
 * payment/audit_event/notification rows se assemble kiya gaya real
 * 9-stage breakdown.
 */
public record PaymentFlowDetail(
        String paymentId,
        String paymentReference,
        String correlationId,
        String traceId,
        String currentStatus,
        List<PaymentFlowStage> stages
) {
}
