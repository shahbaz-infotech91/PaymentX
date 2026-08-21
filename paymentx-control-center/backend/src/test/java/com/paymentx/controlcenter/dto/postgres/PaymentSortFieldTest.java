package com.paymentx.controlcenter.dto.postgres;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ENGLISH: Proves PaymentSortField is the actual "no raw ORDER BY
 * identifier from the browser" boundary for the Transaction Monitor -
 * a known sort name resolves to its real column, and an unrecognized
 * or hostile value (e.g. an attempted SQL injection payload) falls
 * back to createdAt instead of ever reaching PaymentRepository's SQL
 * text.
 *
 * HINGLISH: Prove karta hai ki PaymentSortField Transaction Monitor ke
 * liye actual "browser se raw ORDER BY identifier nahi" boundary hai -
 * ek known sort name apne real column par resolve hota hai, aur ek
 * unrecognized ya hostile value (jaise ek attempted SQL injection
 * payload) createdAt par fallback ho jaata hai, PaymentRepository ke
 * SQL text tak kabhi nahi pahunchta.
 */
class PaymentSortFieldTest {

    @Test
    void knownSortNameResolvesToItsRealColumn() {
        assertThat(PaymentSortField.fromParam("AMOUNT")).isEqualTo(PaymentSortField.AMOUNT);
        assertThat(PaymentSortField.fromParam("amount").column()).isEqualTo("amount");
    }

    @Test
    void unknownOrHostileValueFallsBackToCreatedAt() {
        assertThat(PaymentSortField.fromParam(null)).isEqualTo(PaymentSortField.CREATED_AT);
        assertThat(PaymentSortField.fromParam("amount; DROP TABLE payment;--")).isEqualTo(PaymentSortField.CREATED_AT);
    }
}
