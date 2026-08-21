package com.paymentx.reconciliation.dto;

import com.paymentx.reconciliation.entity.ReconciliationStatus;

import java.util.UUID;

/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * MismatchSearchCriteria is a record (DTO) in the reconciliation module of PaymentX. It lives in package com.paymentx.reconciliation.dto and participates in reconciliation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through reconciliation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * MismatchSearchCriteria PaymentX ke reconciliation module ka ek record (DTO) hai. Ye com.paymentx.reconciliation.dto package me hai aur reconciliation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise reconciliation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public record MismatchSearchCriteria(
        UUID batchId,
        ReconciliationStatus mismatchType,
        Boolean resolved
) {
}
