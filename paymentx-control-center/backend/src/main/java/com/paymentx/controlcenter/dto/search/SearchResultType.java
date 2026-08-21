package com.paymentx.controlcenter.dto.search;

/**
 * ENGLISH: The fixed set of real record types Global Search can find -
 * Payments (paymentx_payment.payment), Participants
 * (paymentx_validation.participant), and Settlement Files
 * (paymentx_reconciliation.settlement_file). Used by the frontend to
 * pick the right route/icon per result, never a raw string.
 *
 * HINGLISH: Real record types ka fixed set jo Global Search dhoond
 * sakta hai - Payments (paymentx_payment.payment), Participants
 * (paymentx_validation.participant), aur Settlement Files
 * (paymentx_reconciliation.settlement_file). Frontend isse har result
 * ke liye sahi route/icon choose karne ke liye use karta hai, kabhi ek
 * raw string nahi.
 */
public enum SearchResultType {
    PAYMENT,
    PARTICIPANT,
    SETTLEMENT_FILE
}
