package com.paymentx.controlcenter.dto.postgres;

import java.time.OffsetDateTime;

/**
 * ENGLISH: One real row from
 * paymentx_reconciliation.reconciliation_batch (batch_type, status,
 * settlement_file_id, window_from/to, started_at, completed_at,
 * total_records, matched_count, mismatch_count, triggered_by). Read-only.
 *
 * HINGLISH: paymentx_reconciliation.reconciliation_batch ki ek real
 * row (batch_type, status, settlement_file_id, window_from/to,
 * started_at, completed_at, total_records, matched_count,
 * mismatch_count, triggered_by). Read-only hai.
 */
public record ReconciliationBatchSummary(
        String id,
        String batchType,
        String status,
        String settlementFileId,
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
