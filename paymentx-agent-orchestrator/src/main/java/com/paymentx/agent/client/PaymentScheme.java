package com.paymentx.agent.client;

/**
 * Phase 4.2.2 - the three real PaymentX payment scheme identifiers, for use as a RAG retrieval
 * filter value ({@link RagQueryFilters#paymentScheme()}). Deliberately a local, service-specific
 * copy - not shared via the common library - matching the platform's own established convention
 * (see routing-service's {@code RoutingScheme}, ADR 0004 in paymentx-common-library's docs: a
 * shared enum would couple this service's compile unit to every other service's release cadence
 * for a value set that is, in practice, platform configuration data). Confirmed identical to
 * {@code PaymentScheme} (payment-service), {@code RoutingScheme} (routing-service), and the
 * payment-relevant subset of {@code Scheme} (validation-service, which additionally has a
 * business-rule-only {@code ALL} value that is never a real payment's scheme and is therefore
 * not included here).
 *
 * <p>Exactly these three values exist in PaymentX. No other scheme (ACH, FedNow, TCH, SEPA,
 * SWIFT, RTP, or any other) exists anywhere in this codebase.
 */
public enum PaymentScheme {
    INSTANT_PAYMENT,
    REAL_TIME_PAYMENT,
    CARD_PAYMENT
}
