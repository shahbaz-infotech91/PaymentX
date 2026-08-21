package com.paymentx.reporting.dto;

import com.paymentx.reporting.entity.ReportFormat;
import com.paymentx.reporting.entity.ReportType;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

/**
 * ====================================================================
 * ENGLISH: The REST request body for "generate a report" - what type,
 * for which window, in which format, plus the universally-useful
 * filters. additionalFilters (a free-form JSON string) carries any
 * report-type-specific filter not covered by these fixed fields.
 *
 * HINGLISH: "report generate karo" ka REST request body - kaunsa type,
 * kaunsi window ke liye, kaunse format me, plus universally-useful
 * filters. additionalFilters (ek free-form JSON string) koi bhi
 * report-type-specific filter carry karta hai jo in fixed fields me
 * cover nahi hota.
 * ====================================================================
 */
public record GenerateReportRequest(
        @NotNull(message = "reportType is required")
        ReportType reportType,

        @NotNull(message = "reportFormat is required")
        ReportFormat reportFormat,

        OffsetDateTime dateFrom,
        OffsetDateTime dateTo,
        String participantId,
        String currency,
        String additionalFilters
) {
}
