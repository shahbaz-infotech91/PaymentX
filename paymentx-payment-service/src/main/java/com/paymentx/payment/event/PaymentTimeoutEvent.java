package com.paymentx.payment.event;

import com.paymentx.payment.entity.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Published by the Timeout Scheduler (Batch 5) when a payment has sat in
 * an in-progress state (PROCESSING, DEBITING, CREDITING, SETTLING, etc.)
 * past a configured threshold with no update. stuckAtStatus tells a
 * downstream ops consumer exactly where in the pipeline it hung, which
 * matters operationally: stuck in DEBITING points at the debit rail;
 * stuck in SETTLING points at the settlement process - different teams
 * would investigate different systems.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * PaymentTimeoutEvent is a class in the payment module of PaymentX. It lives in package com.paymentx.payment.event and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * PaymentTimeoutEvent PaymentX ke payment module ka ek class hai. Ye com.paymentx.payment.event package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class PaymentTimeoutEvent {
    private UUID paymentId;
    private String paymentReference;
    private PaymentStatus stuckAtStatus;
    private OffsetDateTime detectedAt;
}
