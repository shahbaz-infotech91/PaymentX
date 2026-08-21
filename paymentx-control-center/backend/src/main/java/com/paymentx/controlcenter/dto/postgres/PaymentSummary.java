package com.paymentx.controlcenter.dto.postgres;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * ENGLISH: One real row from paymentx_payment.payment, mirroring the
 * live-verified columns (payment_reference, correlation_id, trace_id,
 * scheme, payment_type, channel, amount, currency, debtor/creditor
 * accounts, status, timestamps) - re-verified via a live `\d payment`
 * in Phase 3 after discovering correlation_id/payment_type/channel
 * were missing from the Phase 2 version of this record. Read-only -
 * this record is never used to write back to the table.
 *
 * HINGLISH: paymentx_payment.payment ki ek real row, live-verified
 * columns (payment_reference, correlation_id, trace_id, scheme,
 * payment_type, channel, amount, currency, debtor/creditor accounts,
 * status, timestamps) ko mirror karti hai - Phase 3 me ek live `\d
 * payment` se dobara verify kiya gaya jab pata chala ki
 * correlation_id/payment_type/channel is record ke Phase 2 version me
 * missing the. Read-only hai - ye record kabhi table me wapas likhne
 * ke liye use nahi hota.
 */
public record PaymentSummary(
        String id,
        String paymentReference,
        String correlationId,
        String traceId,
        String scheme,
        String paymentType,
        String channel,
        BigDecimal amount,
        String currency,
        String debtorAccount,
        String debtorParticipantId,
        String creditorAccount,
        String creditorParticipantId,
        String status,
        String failureReason,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
