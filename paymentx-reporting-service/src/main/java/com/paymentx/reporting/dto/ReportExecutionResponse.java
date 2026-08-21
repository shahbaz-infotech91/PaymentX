package com.paymentx.reporting.dto;

import com.paymentx.reporting.entity.ReportStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * ====================================================================
 * ENGLISH: What the REST API returns for "start generating" and "get
 * status" calls - the current state of a report execution, without
 * exposing the actual generated data.
 *
 * HINGLISH: "generation shuru karo" aur "status do" calls ke liye REST
 * API ka response - ek report-execution ki current state, bina actual
 * generated data dikhaye.
 * ====================================================================
 */
public record ReportExecutionResponse(
        UUID id,
        UUID reportRequestId,
        ReportStatus status,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        Integer rowCount,
        Long generationTimeMillis,
        Integer retryCount,
        String failureReason
) {
}
