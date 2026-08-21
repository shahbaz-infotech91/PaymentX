package com.paymentx.controlcenter.dto.postgres;

import java.time.OffsetDateTime;

/**
 * ENGLISH: One real row from paymentx_reporting.report_execution
 * (report_request_id, status, started_at, completed_at, row_count,
 * generation_time_millis, retry_count, failure_reason). Read-only.
 *
 * HINGLISH: paymentx_reporting.report_execution ki ek real row
 * (report_request_id, status, started_at, completed_at, row_count,
 * generation_time_millis, retry_count, failure_reason). Read-only hai.
 */
public record ReportExecutionSummary(
        String id,
        String reportRequestId,
        String status,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        Integer rowCount,
        Long generationTimeMillis,
        Integer retryCount,
        String failureReason
) {
}
