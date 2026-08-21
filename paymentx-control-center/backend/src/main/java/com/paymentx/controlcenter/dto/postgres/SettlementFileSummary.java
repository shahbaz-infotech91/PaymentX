package com.paymentx.controlcenter.dto.postgres;

import java.time.OffsetDateTime;

/**
 * ENGLISH: One real row from paymentx_reconciliation.settlement_file
 * (file_name, file_type, status, file_size_bytes, record_count,
 * checksum_hash, storage_path). Read-only.
 *
 * HINGLISH: paymentx_reconciliation.settlement_file ki ek real row
 * (file_name, file_type, status, file_size_bytes, record_count,
 * checksum_hash, storage_path). Read-only hai.
 */
public record SettlementFileSummary(
        String id,
        String fileName,
        String fileType,
        String status,
        Long fileSizeBytes,
        Integer recordCount,
        String checksumHash,
        String storagePath,
        String failureReason,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
