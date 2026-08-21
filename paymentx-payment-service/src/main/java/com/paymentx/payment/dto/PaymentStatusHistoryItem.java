package com.paymentx.payment.dto;

import com.paymentx.payment.entity.PaymentStatus;

import java.time.OffsetDateTime;

/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * PaymentStatusHistoryItem is a record (DTO) in the payment module of PaymentX. It lives in package com.paymentx.payment.dto and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * PaymentStatusHistoryItem PaymentX ke payment module ka ek record (DTO) hai. Ye com.paymentx.payment.dto package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public record PaymentStatusHistoryItem(
        PaymentStatus fromStatus,
        PaymentStatus toStatus,
        String reason,
        OffsetDateTime transitionedAt
) {
}
