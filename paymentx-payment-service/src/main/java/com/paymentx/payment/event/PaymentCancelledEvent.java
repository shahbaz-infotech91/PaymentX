package com.paymentx.payment.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A cancellation is only valid BEFORE any funds have moved (see
 * CancellationRequest DTO) - service.impl (Batch 4) is responsible for
 * enforcing that a payment already past DEBIT_SUCCESS cannot be
 * cancelled, only reversed. This event simply records that a valid
 * pre-movement cancellation occurred.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * PaymentCancelledEvent is a class in the payment module of PaymentX. It lives in package com.paymentx.payment.event and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * PaymentCancelledEvent PaymentX ke payment module ka ek class hai. Ye com.paymentx.payment.event package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class PaymentCancelledEvent {
    private UUID paymentId;
    private String paymentReference;
    // Flat scalars, not Money - see PaymentCompletedEvent for why
    // (reconciliation-service parses top-level "amount"/"currency").
    private java.math.BigDecimal amount;
    private String currency;
    private String debtorParticipantId;
    private String creditorParticipantId;
    private String cancellationReason;
    private String cancelledBy;
    private OffsetDateTime cancelledAt;
}
