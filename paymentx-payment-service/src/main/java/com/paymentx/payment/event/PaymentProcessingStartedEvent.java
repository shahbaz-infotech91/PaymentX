package com.paymentx.payment.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Published to payment.processing when PaymentEngine picks up a
 * PaymentValidatedEvent and begins the debit/credit flow. Downstream
 * consumers (Notification Service, eventually) use this to tell the
 * originating bank "we've received and started working your payment" -
 * distinct from RECEIVED, which only means "we accepted the validated
 * event," not "we started acting on it."
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * PaymentProcessingStartedEvent is a class in the payment module of PaymentX. It lives in package com.paymentx.payment.event and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * PaymentProcessingStartedEvent PaymentX ke payment module ka ek class hai. Ye com.paymentx.payment.event package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class PaymentProcessingStartedEvent {
    private UUID paymentId;
    private String paymentReference;
    private OffsetDateTime startedAt;
}
