package com.paymentx.controlcenter.dto.postgres;

import java.time.OffsetDateTime;

/**
 * ENGLISH: One real row from paymentx_notification.notification
 * (channel, status, recipient, subject, correlation_id, trace_id,
 * payment_id, participant_id, retry_count, max_retries,
 * last_attempt_at, next_retry_at, failure_reason). failure_reason
 * added in Phase 4 - re-verified live via `\d notification`. channel
 * is a real, unmodified value from a 5-value real enum (EMAIL/SMS/
 * WEBHOOK/INTERNAL/PUSH) - live data shows only INTERNAL is ever
 * actually written (verified: 430/430 rows), which this DTO reflects
 * honestly rather than inventing EMAIL/SMS/PUSH rows that don't exist.
 * body/template content excluded from this summary. Read-only.
 *
 * HINGLISH: paymentx_notification.notification ki ek real row
 * (channel, status, recipient, subject, correlation_id, trace_id,
 * payment_id, participant_id, retry_count, max_retries,
 * last_attempt_at, next_retry_at, failure_reason). failure_reason
 * Phase 4 me add kiya gaya - live `\d notification` se dobara verify
 * kiya gaya. channel ek real, unmodified value hai ek 5-value real
 * enum se (EMAIL/SMS/WEBHOOK/INTERNAL/PUSH) - live data dikhata hai ki
 * sirf INTERNAL hi actually kabhi likha jaata hai (verified: 430/430
 * rows), jise ye DTO honestly reflect karta hai, EMAIL/SMS/PUSH rows
 * invent karne ke bajaye jo exist hi nahi karte. body/template content
 * is summary se excluded hai. Read-only hai.
 */
public record NotificationSummary(
        String id,
        String sourceEventType,
        String channel,
        String status,
        String recipient,
        String subject,
        String correlationId,
        String traceId,
        String paymentId,
        String participantId,
        Integer retryCount,
        Integer maxRetries,
        OffsetDateTime lastAttemptAt,
        OffsetDateTime nextRetryAt,
        String failureReason,
        OffsetDateTime createdAt
) {
}
