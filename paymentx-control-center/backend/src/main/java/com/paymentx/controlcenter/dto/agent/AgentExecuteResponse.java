package com.paymentx.controlcenter.dto.agent;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Phase 4.7 - the live, synchronous execution result returned to the frontend the moment
 * Execute Agent completes. Every field here is real: executionId/correlationId/agentId/answer/
 * status/sources/toolsCalled/executionMetadata come verbatim from Agent Orchestrator's own real
 * POST /api/v1/agent/execute response (Phase 4.7 also added executionId/correlationId/agentId
 * to that response for exactly this purpose - see paymentx-agent-orchestrator's
 * AgentExecuteResponse). startedAt/completedAt are genuinely observed by this backend around its
 * own call to Agent Orchestrator (not fabricated - this IS when Control Center actually sent
 * and received the request). `error` is populated only when the call could not be completed at
 * all (Agent Orchestrator unreachable, etc.) - a real agent outcome like INSUFFICIENT_CONTEXT/
 * DENIED/REFUSED is never treated as an error; it is a real, honest `status` value, exactly the
 * same non-error-outcome precedent AiPlatformClient's own AgentExecuteResult already established.
 */
public record AgentExecuteResponse(
        String executionId,
        String correlationId,
        String agentId,
        String userQuery,
        String paymentReference,
        String status,
        String answer,
        List<RagSourceSummary> sources,
        List<ToolCallSummary> toolsCalled,
        AgentExecutionMetadata executionMetadata,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        long durationMs,
        String error,
        // Phase 5 - LLM provider/fallback visibility, verbatim from Agent Orchestrator's own real
        // response (see this record's own javadoc precedent: sources/toolsCalled/etc. are already
        // never fabricated here).
        String provider,
        boolean fallbackUsed,
        String fallbackReason
) {
}
