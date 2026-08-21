package com.paymentx.payment.dto;

import java.util.List;

/**
 * Backs the "Payment History" feature (#13 in the spec) - combines the
 * current payment snapshot with its full ordered list of state
 * transitions, so a single API call gives a caller (support tooling,
 * a future ops dashboard) the complete story of a payment without a
 * second round-trip.
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * PaymentHistoryResponse is a record (DTO) in the payment module of PaymentX. It lives in package com.paymentx.payment.dto and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * PaymentHistoryResponse PaymentX ke payment module ka ek record (DTO) hai. Ye com.paymentx.payment.dto package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public record PaymentHistoryResponse(
        PaymentResponse payment,
        List<PaymentStatusHistoryItem> history
) {
}
