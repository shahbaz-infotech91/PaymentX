package com.paymentx.payment.dto;

import com.paymentx.payment.entity.PaymentScheme;
import com.paymentx.payment.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Bound from GET query parameters (never a request body - this is a
 * read/search operation, not a command). Every field is optional; the
 * controller passes whichever ones the caller supplied to
 * PaymentSpecifications, which composes only the provided filters.
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * PaymentSearchCriteria is a record (DTO) in the payment module of PaymentX. It lives in package com.paymentx.payment.dto and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * PaymentSearchCriteria PaymentX ke payment module ka ek record (DTO) hai. Ye com.paymentx.payment.dto package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public record PaymentSearchCriteria(
        PaymentStatus status,
        PaymentScheme scheme,
        String participantId,
        OffsetDateTime createdFrom,
        OffsetDateTime createdTo,
        BigDecimal minAmount,
        BigDecimal maxAmount
) {
}
