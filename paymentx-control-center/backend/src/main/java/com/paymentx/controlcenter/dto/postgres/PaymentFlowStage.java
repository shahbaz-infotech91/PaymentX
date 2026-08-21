package com.paymentx.controlcenter.dto.postgres;

import java.time.OffsetDateTime;

/**
 * ENGLISH: One real stage in the 9-stage payment flow (Gateway,
 * Authentication, Validation, Routing, Payment, Audit, Notification,
 * Reconciliation, Reporting). detail carries the real signal this
 * status was derived from (a real event_type, a real notification
 * status, or a one-line explanation of why the stage is UNAVAILABLE) -
 * never a generic placeholder string.
 *
 * HINGLISH: 9-stage payment flow (Gateway, Authentication, Validation,
 * Routing, Payment, Audit, Notification, Reconciliation, Reporting) ka
 * ek real stage. detail wahi real signal carry karta hai jisse ye
 * status derive hua (ek real event_type, ek real notification status,
 * ya ye stage UNAVAILABLE kyun hai uska ek one-line explanation) -
 * kabhi ek generic placeholder string nahi.
 */
public record PaymentFlowStage(
        String stage,
        PaymentFlowStageStatus status,
        String detail,
        OffsetDateTime occurredAt
) {
}
