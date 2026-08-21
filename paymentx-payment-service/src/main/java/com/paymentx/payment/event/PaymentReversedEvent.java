package com.paymentx.payment.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A REVERSAL is PaymentX-operator-initiated (see ReversalRequest DTO -
 * requires an authenticated requestedBy and a mandatory reason), as
 * opposed to a RETURN which is initiated by the receiving bank. Carries
 * initiatedBy so downstream audit/compliance consumers of this Kafka
 * event (not just the internal payment_audit table) have the actor
 * without needing to query back into Payment Service.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * PaymentReversedEvent is a class in the payment module of PaymentX. It lives in package com.paymentx.payment.event and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * PaymentReversedEvent PaymentX ke payment module ka ek class hai. Ye com.paymentx.payment.event package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class PaymentReversedEvent {
    private UUID paymentId;
    private String paymentReference;
    private String reversalReason;
    private String initiatedBy;
    private OffsetDateTime reversedAt;
}
