package com.paymentx.controlcenter.dto.agent;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Phase 4.7 - the full Execution Detail view. Sourced from the SAME real, already-persisted
 * audit_event row as AgentExecutionSummary, this time including its full jsonb payload (userQuery,
 * answer, sources, toolCalls - all added to AgentAuditClient's own audit write this phase
 * specifically so a historical execution, opened later from Execution History rather than shown
 * live right after Execute Agent, can still be inspected completely). `toolsCalled` here carries
 * only tool name + status (matches what Agent Orchestrator's audit payload has always recorded,
 * Phase 4.1 onward) - NOT each tool's full result payload, which is only ever available in the
 * live AgentExecuteResponse at execution time, never persisted to audit (a real, disclosed
 * limitation, not an oversight - see the Phase 4.7 documentation's Known Limitations section).
 */
public record AgentExecutionDetail(
        String executionId,
        String correlationId,
        String agentId,
        String userQuery,
        String paymentReference,
        String outcome,
        String answer,
        List<String> ragSources,
        List<ToolCallNameStatus> toolsCalled,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        Long durationMs,
        Integer iterations,
        Boolean ragUsed,
        String error,
        // Phase 5 - LLM provider/fallback visibility (AgentAuditClient's payload gained
        // provider/fallbackUsed/fallbackReason this phase; see AgentExecution's own javadoc for
        // exact semantics).
        String provider,
        Boolean fallbackUsed,
        String fallbackReason
) {
    public record ToolCallNameStatus(String tool, String status) {
    }
}
