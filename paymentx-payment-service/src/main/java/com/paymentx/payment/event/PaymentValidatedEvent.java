package com.paymentx.payment.event;

import com.paymentx.payment.entity.PaymentScheme;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Deserialization target for messages consumed by PaymentValidatedConsumer.
 * Renamed from ValidatedPaymentPayload -> PaymentValidatedEvent purely for
 * naming consistency with the other 9 named event classes in this package
 * - this is the CONSUMED counterpart to the 9 PRODUCED events below.
 *
 * See ADR 0002 for the current reality: this is deserialized from THREE
 * physical topics (instant-payment-validated/card-payment-validated/real-time-payment-validated), not a
 * single "payment.validated" topic, despite the class name matching the
 * spec's conceptual single-topic description.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * PaymentValidatedEvent is a class in the payment module of PaymentX. It lives in package com.paymentx.payment.event and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * PaymentValidatedEvent PaymentX ke payment module ka ek class hai. Ye com.paymentx.payment.event package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class PaymentValidatedEvent {
    private String paymentReference;
    private PaymentScheme scheme;
    private BigDecimal amount;
    private String currency;
    private String debtorAccount;
    private String debtorParticipantId;
    private String creditorAccount;
    private String creditorParticipantId;
}
