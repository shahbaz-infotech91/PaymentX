package com.paymentx.payment.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Published to payment.completed when both debit and credit legs (and
 * settlement, if synchronous for the scheme) have succeeded. Carries
 * processingDurationMillis - a field with no equivalent on the other
 * events - because "how long did this take end-to-end" is a question
 * that is ONLY answerable, and ONLY meaningful, at the moment of
 * completion. This is exactly the kind of event-specific field the
 * independent-classes approach (vs. a shared base) makes natural to add
 * without polluting every other event's shape.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * PaymentCompletedEvent is a class in the payment module of PaymentX. It lives in package com.paymentx.payment.event and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * PaymentCompletedEvent PaymentX ke payment module ka ek class hai. Ye com.paymentx.payment.event package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class PaymentCompletedEvent {
    private UUID paymentId;
    private String paymentReference;
    // Flat (not Money) on purpose: reconciliation-service's
    // ReconciliationEventConsumer#parseAmount reads a top-level scalar
    // "amount" field off the raw JSON payload - nesting a Money object
    // here (as PaymentDebitedEvent/PaymentCreditedEvent do) would make
    // it unparseable by that consumer.
    private java.math.BigDecimal amount;
    private String currency;
    private String debtorParticipantId;
    private String creditorParticipantId;
    private OffsetDateTime completedAt;
    private long processingDurationMillis;
}
