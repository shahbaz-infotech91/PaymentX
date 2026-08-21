package com.paymentx.reconciliation.dto;

import com.paymentx.reconciliation.entity.BatchStatus;
import com.paymentx.reconciliation.entity.BatchType;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * BatchResponse is a record (DTO) in the reconciliation module of PaymentX. It lives in package com.paymentx.reconciliation.dto and participates in reconciliation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through reconciliation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * BatchResponse PaymentX ke reconciliation module ka ek record (DTO) hai. Ye com.paymentx.reconciliation.dto package me hai aur reconciliation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise reconciliation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public record BatchResponse(
        UUID id,
        BatchType batchType,
        BatchStatus status,
        UUID settlementFileId,
        OffsetDateTime windowFrom,
        OffsetDateTime windowTo,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        Integer totalRecords,
        Integer matchedCount,
        Integer mismatchCount,
        String triggeredBy,
        String failureReason
) {
}
