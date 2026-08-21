package com.paymentx.reconciliation.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * WHY this is a plain record, not an entity: it's a transient parsing
 * result - SettlementFileImporter produces a stream of these, and
 * MatchingEngine consumes them to build ReconciliationRecord entities.
 * It never itself gets persisted.
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * ExternalSettlementRecord is a record (DTO) in the reconciliation module of PaymentX. It lives in package com.paymentx.reconciliation.dto and participates in reconciliation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through reconciliation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * ExternalSettlementRecord PaymentX ke reconciliation module ka ek record (DTO) hai. Ye com.paymentx.reconciliation.dto package me hai aur reconciliation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise reconciliation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public record ExternalSettlementRecord(
        String paymentId,
        String referenceId,
        String participantId,
        BigDecimal amount,
        String currency,
        String status,
        OffsetDateTime settlementDate
) {
}
