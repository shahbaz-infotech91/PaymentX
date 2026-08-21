package com.paymentx.reporting.dto;

import com.paymentx.reporting.entity.ReportFormat;
import com.paymentx.reporting.entity.ReportFrequency;
import com.paymentx.reporting.entity.ReportType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * ====================================================================
 * ENGLISH: The REST request body for "schedule a recurring report" -
 * what type, how often, and the explicit cron expression that drives
 * exactly when it fires.
 *
 * HINGLISH: "recurring report schedule karo" ka REST request body -
 * kaunsa type, kitni baar, aur explicit cron expression jo exactly
 * decide karta hai kab chalega.
 * ====================================================================
 */
public record ScheduleReportRequest(
        @NotNull(message = "reportType is required")
        ReportType reportType,

        @NotNull(message = "reportFormat is required")
        ReportFormat reportFormat,

        @NotNull(message = "frequency is required")
        ReportFrequency frequency,

        @NotBlank(message = "cronExpression is required")
        String cronExpression
) {
}
