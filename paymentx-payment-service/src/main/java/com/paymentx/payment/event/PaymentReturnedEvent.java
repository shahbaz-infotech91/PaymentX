package com.paymentx.payment.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A RETURN is creditor-bank-initiated ("give the money back," e.g.
 * account closed, invalid account) - distinct from a REVERSAL, which is
 * PaymentX-operator-initiated (see PaymentReversedEvent). returnReason
 * here typically carries a scheme-specific return code (e.g. an REAL_TIME_PAYMENT
 * return reason code like R01), not a free-text PaymentX-internal message.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * PaymentReturnedEvent is a class in the payment module of PaymentX. It lives in package com.paymentx.payment.event and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * PaymentReturnedEvent PaymentX ke payment module ka ek class hai. Ye com.paymentx.payment.event package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class PaymentReturnedEvent {
    private UUID paymentId;
    private String paymentReference;
    private String returnReason;
    private OffsetDateTime returnedAt;
}
