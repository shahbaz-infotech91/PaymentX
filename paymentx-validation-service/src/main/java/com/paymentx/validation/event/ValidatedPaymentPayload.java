package com.paymentx.validation.event;

import com.paymentx.validation.entity.Scheme;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * This is the payload that goes INSIDE the common PaymentEvent<T> envelope
 * (see paymentx-common's PaymentEvent). Payment Service, which consumes
 * instant-payment-validated/card-payment-validated/real-time-payment-validated, deserializes into this
 * exact shape - so this record is effectively the CONTRACT between
 * Validation Service and Payment Service. Changing a field here without
 * coordinating is a breaking change across service boundaries.
 *
 * WHY debtorParticipantId/creditorParticipantId, not debtorBankId/
 * creditorBankId: this field previously drifted out of sync with Payment
 * Service's consumer-side PaymentValidatedEvent, which has always expected
 * debtorParticipantId/creditorParticipantId. Jackson silently leaves
 * unmatched fields null rather than failing deserialization, so every
 * validated payment produced a Payment row with a NULL participant ID,
 * violating the NOT NULL constraint, rolling back the insert, and causing
 * the Kafka message to be redelivered forever (manual-ack, never
 * acknowledged on failure). Renamed to match the receiving side and the
 * "participantId" naming convention used platform-wide (Payment entity
 * columns, PaymentResponse DTO, X-Participant-Id header).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * ValidatedPaymentPayload is a class in the validation module of PaymentX. It lives in package com.paymentx.validation.event and participates in validation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through validation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * ValidatedPaymentPayload PaymentX ke validation module ka ek class hai. Ye com.paymentx.validation.event package me hai aur validation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise validation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class ValidatedPaymentPayload {
    private String paymentReference;
    private Scheme scheme;
    private BigDecimal amount;
    private String currency;
    private String debtorAccount;
    private String debtorParticipantId;
    private String creditorAccount;
    private String creditorParticipantId;
}
