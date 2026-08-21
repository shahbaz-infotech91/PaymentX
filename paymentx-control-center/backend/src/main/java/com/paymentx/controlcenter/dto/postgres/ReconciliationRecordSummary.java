package com.paymentx.controlcenter.dto.postgres;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * ENGLISH: One real row from paymentx_reconciliation.
 * reconciliation_record - the actual record-level match/mismatch
 * result the reconciliation engine produced for one payment,
 * discovered live via `\d reconciliation_record` during Phase 4 (a
 * table this dashboard had not queried before). paymentReference is
 * the record's real reference_id (verified to match
 * payment.payment_reference's format live). settlementFileName is
 * joined from the parent batch's real settlement_file (the closest
 * real analogue to "Settlement Reference" in this schema - there is
 * no separate per-record settlement reference column). mismatchReason
 * is the real mismatch_record.description for this record when one
 * exists (null for MATCHED records, which genuinely have no mismatch
 * row - never a fabricated reason).
 *
 * HINGLISH: paymentx_reconciliation.reconciliation_record ki ek real
 * row - wo actual record-level match/mismatch result jo reconciliation
 * engine ne ek payment ke liye produce kiya, Phase 4 ke dauraan live
 * `\d reconciliation_record` se discover kiya gaya (ek table jise ye
 * dashboard pehle query nahi karta tha). paymentReference record ka
 * real reference_id hai (live verify kiya gaya ki ye
 * payment.payment_reference ke format se match karta hai).
 * settlementFileName parent batch ke real settlement_file se joined
 * hai (is schema me "Settlement Reference" ka sabse close real
 * analogue - koi alag per-record settlement reference column nahi
 * hai). mismatchReason us record ke real mismatch_record.description
 * hai jab ek exist kare (MATCHED records ke liye null, jinke paas
 * genuinely koi mismatch row nahi hoti - kabhi fabricated reason
 * nahi).
 */
public record ReconciliationRecordSummary(
        String id,
        String batchId,
        String paymentReference,
        String participantId,
        BigDecimal internalAmount,
        BigDecimal externalAmount,
        String internalCurrency,
        String externalCurrency,
        String internalStatus,
        String externalStatus,
        String reconciliationStatus,
        String settlementFileName,
        String mismatchReason,
        OffsetDateTime createdAt
) {
}
