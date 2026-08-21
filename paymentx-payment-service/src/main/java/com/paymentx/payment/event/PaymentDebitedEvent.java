package com.paymentx.payment.event;

import com.paymentx.payment.entity.Money;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Published to payment.debited when DebitProcessor confirms the debtor's
 * funds have been secured. Carries debtorParticipantId specifically
 * (not both debtor+creditor) because this event's entire purpose is
 * narrower than PaymentResponse's full snapshot - it answers exactly one
 * question for exactly one interested party (reconciliation, the debtor
 * bank's own systems): "was money actually pulled, how much, from whom."
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * PaymentDebitedEvent is a class in the payment module of PaymentX. It lives in package com.paymentx.payment.event and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * PaymentDebitedEvent PaymentX ke payment module ka ek class hai. Ye com.paymentx.payment.event package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class PaymentDebitedEvent {
    private UUID paymentId;
    private String paymentReference;
    private String debtorParticipantId;
    private Money amount;
    private OffsetDateTime debitedAt;
}
