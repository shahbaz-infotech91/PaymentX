package com.paymentx.reconciliation.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * WHY this is a plain record, not an entity: transient, built from a
 * PaymentEvent's payload JSON at consumption time and cached in Redis
 * (see InternalTransactionCacheService) so the matching engine can look
 * it up later against an arriving settlement file - it is never itself
 * the persisted row (ReconciliationRecord is, once matched/compared).
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * InternalTransaction is a record (DTO) in the reconciliation module of PaymentX. It lives in package com.paymentx.reconciliation.dto and participates in reconciliation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through reconciliation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * InternalTransaction PaymentX ke reconciliation module ka ek record (DTO) hai. Ye com.paymentx.reconciliation.dto package me hai aur reconciliation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise reconciliation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public record InternalTransaction(
        String paymentId,
        String referenceId,
        String participantId,
        BigDecimal amount,
        String currency,
        String status,
        OffsetDateTime settlementDate
) {
}
