package com.paymentx.agent.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 4.2.2 - deterministic unit tests for RagQueryFilters' pure conversion logic (no HTTP
 * needed - RagServiceClientTest covers wire-level propagation).
 */
class RagQueryFiltersTest {

    @Test
    void toMap_allFieldsNull_producesEmptyMap() {
        RagQueryFilters filters = new RagQueryFilters(null, null, null, null, null, null);

        assertThat(filters.toMap()).isEmpty();
        assertThat(filters.isEmpty()).isTrue();
    }

    @Test
    void toMap_onlyPaymentScheme_producesExactlyOneEntry() {
        RagQueryFilters filters = new RagQueryFilters(null, null, PaymentScheme.INSTANT_PAYMENT, null, null, null);

        assertThat(filters.toMap()).containsExactly(java.util.Map.entry("paymentScheme", "INSTANT_PAYMENT"));
        assertThat(filters.isEmpty()).isFalse();
    }

    @Test
    void toMap_allFieldsSet_producesAllSixEntries() {
        RagQueryFilters filters = new RagQueryFilters(
                "ERROR_CODE_REFERENCE", "paymentx-validation-service", PaymentScheme.CARD_PAYMENT,
                "DUPLICATE_PAYMENT_REFERENCE", "MEDIUM", false);

        assertThat(filters.toMap()).containsExactlyInAnyOrderEntriesOf(java.util.Map.of(
                "documentType", "ERROR_CODE_REFERENCE",
                "service", "paymentx-validation-service",
                "paymentScheme", "CARD_PAYMENT",
                "errorCode", "DUPLICATE_PAYMENT_REFERENCE",
                "severity", "MEDIUM",
                "retryable", false));
    }

    @Test
    void paymentScheme_isExactlyTheThreeRealPaymentXSchemes() {
        // Item 6 of Phase 4.2.2 Part D: an "unsupported scheme" cannot be constructed at all -
        // PaymentScheme is a closed Java enum, the compiler rejects anything else. This test is a
        // regression guard against silently widening that set to include a scheme PaymentX does
        // not actually have (ACH, FedNow, TCH, SEPA, SWIFT, RTP, or any other).
        assertThat(PaymentScheme.values()).extracting(Enum::name)
                .containsExactlyInAnyOrder("INSTANT_PAYMENT", "REAL_TIME_PAYMENT", "CARD_PAYMENT");
    }

    @Test
    void retryable_falseIsDistinctFromNull_notDroppedFromTheMap() {
        // A real, meaningful false value (e.g. "only non-retryable error docs") must not be
        // confused with "field not set" - only a genuinely null Boolean is omitted.
        RagQueryFilters filters = new RagQueryFilters(null, null, null, null, null, false);

        assertThat(filters.toMap()).containsEntry("retryable", false);
    }
}
