package com.paymentx.payment.event;

import com.paymentx.payment.entity.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * failedAtStage carries the PaymentStatus the payment was in when it
 * failed (e.g. DEBIT_FAILED vs CREDIT_FAILED) - this single field is what
 * lets a downstream alerting rule distinguish "money was never moved"
 * (safe to just notify) from "debit succeeded but credit failed" (which
 * requires a compensating reversal, a materially different and more
 * urgent operational response).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * PaymentFailedEvent is a class in the payment module of PaymentX. It lives in package com.paymentx.payment.event and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * PaymentFailedEvent PaymentX ke payment module ka ek class hai. Ye com.paymentx.payment.event package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class PaymentFailedEvent {
    private UUID paymentId;
    private String paymentReference;
    // Flat scalars, not Money - see PaymentCompletedEvent for why
    // (reconciliation-service parses top-level "amount"/"currency").
    private java.math.BigDecimal amount;
    private String currency;
    private String debtorParticipantId;
    private String creditorParticipantId;
    private PaymentStatus failedAtStage;
    private String failureReason;
    private OffsetDateTime failedAt;
}
