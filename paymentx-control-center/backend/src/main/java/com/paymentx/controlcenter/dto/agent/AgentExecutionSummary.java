package com.paymentx.controlcenter.dto.agent;

import java.time.OffsetDateTime;

/**
 * Phase 4.7 - one row in the Execution History list. Sourced entirely from the REAL,
 * already-persisted audit_event row Agent Orchestrator's own AgentAuditClient wrote for that
 * run (never a Control Center-fabricated record - see AgentExecutionRepository). Deliberately
 * excludes the full answer/toolCalls/sources payload (kept for AgentExecutionDetail only), per
 * this phase's own "do not store huge raw responses in the list view" instruction.
 */
public record AgentExecutionSummary(
        String executionId,
        String correlationId,
        String agentId,
        String userQuery,
        String paymentReference,
        String outcome,
        OffsetDateTime timestamp,
        Long durationMs,
        Integer toolCallCount,
        Boolean ragUsed,
        // Phase 5 - LLM provider/fallback visibility (AgentAuditClient's payload gained
        // provider/fallbackUsed/fallbackReason this phase; see AgentExecution's own javadoc for
        // exact semantics). fallbackReason omitted from the list view per this record's own
        // "exclude the full payload, keep it for Detail only" convention above.
        String provider,
        Boolean fallbackUsed
) {
}
