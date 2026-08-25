package com.paymentx.agent.dto;

import com.paymentx.agent.registry.AgentCapability;
import com.paymentx.agent.registry.AgentRiskLevel;

import java.util.Set;

/**
 * Phase 4.7 - the read-only "what agents exist" view for GET /api/v1/agent/agents, built
 * directly from the real AgentRegistry (registry.AgentDefinition), never a hardcoded list. Exists
 * so Control Center's AI Agent Control Center can render a live agent dashboard/dropdown driven
 * by actual configuration rather than duplicating five agent descriptions in the frontend.
 * Deliberately excludes promptKey's own rendered content, maxIterations/timeoutMs internals
 * beyond what's already safe platform config, and anything MCP-Gateway-side - this is a registry
 * summary, not an execution contract.
 */
public record AgentSummaryResponse(
        String agentId,
        String name,
        String description,
        String version,
        Set<AgentCapability> capabilities,
        Set<String> allowedTools,
        AgentRiskLevel riskLevel,
        boolean enabled
) {
}
