package com.paymentx.controlcenter.dto.e2e;

import java.time.OffsetDateTime;

/**
 * ENGLISH: One real stage of one real E2E run - Gateway/Authentication
 * are set from this backend's own real HTTP call outcome against the
 * real API Gateway; Validation/Routing/Payment/Audit/Notification/
 * Reconciliation/Reporting are set by delegating to the existing,
 * already-tested PaymentFlowService (see E2EFlowService) once a real
 * payment row exists - this DTO never carries a status this backend
 * did not actually observe.
 *
 * HINGLISH: Ek real E2E run ka ek real stage - Gateway/Authentication
 * is backend ke apne real HTTP call outcome se set hote hain, real API
 * Gateway ke against; Validation/Routing/Payment/Audit/Notification/
 * Reconciliation/Reporting existing, already-tested PaymentFlowService
 * ko delegate karke set hote hain (E2EFlowService dekho) jab ek real
 * payment row exist kare - ye DTO kabhi wo status carry nahi karta jo
 * is backend ne actually observe nahi kiya.
 */
public record E2EStage(
        String name,
        E2EStageStatus status,
        String detail,
        OffsetDateTime occurredAt
) {
}
