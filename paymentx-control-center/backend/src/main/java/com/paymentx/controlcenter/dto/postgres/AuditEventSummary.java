package com.paymentx.controlcenter.dto.postgres;

import java.time.OffsetDateTime;

/**
 * ENGLISH: One real row from paymentx_audit.audit_event (event_type,
 * event_status, source_service, actor_id, actor_type, correlation_id,
 * trace_id, payment_id, participant_id, reference, occurred_at).
 * actor_id/actor_type were added in Phase 4 for the Audit timeline's
 * real "Actor" column - re-verified live via `\d audit_event`. payload
 * (jsonb) is deliberately excluded from this summary - it can carry
 * arbitrary event data not meant for a generic list view. Read-only.
 *
 * HINGLISH: paymentx_audit.audit_event ki ek real row (event_type,
 * event_status, source_service, actor_id, actor_type, correlation_id,
 * trace_id, payment_id, participant_id, reference, occurred_at).
 * actor_id/actor_type Phase 4 me Audit timeline ke real "Actor" column
 * ke liye add kiye gaye - live `\d audit_event` se dobara verify kiye
 * gaye. payload (jsonb) is summary se jaan-boojh kar excluded hai -
 * usme arbitrary event data ho sakta hai jo generic list view ke liye
 * nahi hai. Read-only hai.
 */
public record AuditEventSummary(
        String id,
        String eventType,
        String eventStatus,
        String sourceService,
        String actorId,
        String actorType,
        String correlationId,
        String traceId,
        String paymentId,
        String participantId,
        String reference,
        OffsetDateTime occurredAt
) {
}
