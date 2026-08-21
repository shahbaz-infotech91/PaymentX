package com.paymentx.reconciliation.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * ReconciliationSummaryResponse is a record (DTO) in the reconciliation module of PaymentX. It lives in package com.paymentx.reconciliation.dto and participates in reconciliation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through reconciliation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * ReconciliationSummaryResponse PaymentX ke reconciliation module ka ek record (DTO) hai. Ye com.paymentx.reconciliation.dto package me hai aur reconciliation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise reconciliation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public record ReconciliationSummaryResponse(
        UUID batchId,
        Integer totalRecords,
        Integer matchedCount,
        Integer missingCount,
        Integer duplicateCount,
        Integer amountMismatchCount,
        Integer currencyMismatchCount,
        Integer statusMismatchCount,
        Integer settlementDelayCount,
        Integer lateSettlementCount,
        Integer orphanCount,
        Integer unexpectedSettlementCount,
        OffsetDateTime generatedAt
) {
}
