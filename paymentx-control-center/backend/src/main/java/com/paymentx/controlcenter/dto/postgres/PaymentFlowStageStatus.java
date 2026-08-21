package com.paymentx.controlcenter.dto.postgres;

/**
 * ENGLISH: The real, honest status of one Payment Flow stage.
 * COMPLETED/PROCESSING/FAILED are derived from a real row this
 * backend actually found (a real audit_event, a real notification
 * row, or the payment's own real status); NOT_STARTED means the
 * backend looked for a real signal and genuinely found none yet;
 * UNAVAILABLE means this stage has no per-payment tracking mechanism
 * at all in the current schema (auth-service leaves no audit trail;
 * reconciliation/reporting operate on batches, not individual
 * payments) - a documented, honest limitation, never silently shown
 * as COMPLETED.
 *
 * HINGLISH: Ek Payment Flow stage ka real, honest status.
 * COMPLETED/PROCESSING/FAILED ek real row se derive hote hain jo ye
 * backend ne actually dhoondi (ek real audit_event, ek real
 * notification row, ya payment ka apna real status); NOT_STARTED ka
 * matlab hai backend ne ek real signal dhoonda aur genuinely abhi tak
 * koi nahi mila; UNAVAILABLE ka matlab hai is stage ka current schema
 * me koi per-payment tracking mechanism hi nahi hai (auth-service koi
 * audit trail nahi chhodta; reconciliation/reporting batches par
 * operate karte hain, individual payments par nahi) - ek documented,
 * honest limitation, kabhi silently COMPLETED dikhaya nahi jaata.
 */
public enum PaymentFlowStageStatus {
    COMPLETED,
    PROCESSING,
    FAILED,
    NOT_STARTED,
    UNAVAILABLE
}
