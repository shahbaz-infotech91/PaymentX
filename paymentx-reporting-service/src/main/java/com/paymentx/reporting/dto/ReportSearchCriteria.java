package com.paymentx.reporting.dto;

import com.paymentx.reporting.entity.ReportType;

import java.time.OffsetDateTime;

/**
 * ====================================================================
 * ENGLISH: Query-parameter-bound search criteria for GET /requests -
 * bound to ReportRequestSpecifications' predicates one-to-one.
 *
 * HINGLISH: GET /requests ke liye query-parameter-bound search criteria
 * - ReportRequestSpecifications ke predicates se ek-ek karke bandhe
 * hain.
 * ====================================================================
 */
public record ReportSearchCriteria(
        ReportType reportType,
        String participantId,
        String currency,
        String correlationId,
        OffsetDateTime fromDate,
        OffsetDateTime toDate
) {
}
