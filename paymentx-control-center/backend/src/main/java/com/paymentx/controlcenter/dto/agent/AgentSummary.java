package com.paymentx.controlcenter.dto.agent;

import java.util.List;

/**
 * Phase 4.7 - mirrors Agent Orchestrator's own real GET /api/v1/agent/agents response shape
 * (paymentx-agent-orchestrator's AgentSummaryResponse). This module deliberately has no
 * compile-time dependency on paymentx-agent-orchestrator's own DTO classes - the same
 * JsonNode-parsing-then-remapping convention every other client/*Client.java in this package
 * already follows (see AiPlatformClient's own javadoc) - so a live agent dashboard here never
 * needs a rebuild just because Agent Orchestrator's internal registry DTO shape changes.
 */
public record AgentSummary(
        String agentId,
        String name,
        String description,
        String version,
        List<String> capabilities,
        List<String> allowedTools,
        String riskLevel,
        boolean enabled
) {
}
