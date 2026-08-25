package com.paymentx.agent.registry;

import java.util.Set;

/**
 * The trusted, immutable description of one agent - everything AgentToolPolicy, AgentPlanner,
 * and AgentOrchestratorService need to run a bounded execution safely on that agent's behalf.
 * Named to match MCP Gateway's own {@code McpToolDefinition} (the same "Definition" naming
 * already used one layer down for a single tool's metadata), not the brief's example name
 * verbatim, for consistency across the two closest-related classes in this codebase.
 *
 * <p>This is the single source of truth an {@link AgentRegistry} resolves and hands to the
 * orchestrator loop per request - the planning LLM never sees, and can never construct or
 * mutate, an instance of this type. {@code allowedTools} is the actual security boundary
 * AgentToolPolicy enforces (MCP Gateway's own ToolAuthorizationService remains the second,
 * independent gate - this record does not, and must not, replace it).
 *
 * @param agentId       stable logical identity, e.g. "default" - never a human user id,
 *                      participant id, or payment id (those are separate concepts entirely)
 * @param name           short display name
 * @param description    what this agent is for
 * @param version        free-form version label for this definition (e.g. "1.0")
 * @param capabilities   descriptive-only tags (see {@link AgentCapability}), never itself an
 *                      enforcement boundary
 * @param allowedTools   the real, code-enforced tool allow-list for this agent - checked by
 *                      AgentToolPolicy before MCP Gateway is ever called
 * @param promptKey      the Prompt Service key this agent's planning prompt is rendered from -
 *                      never a hardcoded prompt string
 * @param riskLevel      operational risk classification for this agent as a whole
 * @param enabled        whether this agent may currently be resolved/executed at all
 * @param maxIterations  per-agent override of the bounded-loop iteration limit; {@code null}
 *                      means "use AgentOrchestratorProperties' platform default"
 * @param timeoutMs      per-agent override of the overall execution timeout; {@code null}
 *                      means "use AgentOrchestratorProperties' platform default"
 */
public record AgentDefinition(
        String agentId,
        String name,
        String description,
        String version,
        Set<AgentCapability> capabilities,
        Set<String> allowedTools,
        String promptKey,
        AgentRiskLevel riskLevel,
        boolean enabled,
        Integer maxIterations,
        Long timeoutMs
) {
    public AgentDefinition {
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("AgentDefinition.agentId must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("AgentDefinition.agentId=" + agentId + " has a blank name");
        }
        if (promptKey == null || promptKey.isBlank()) {
            throw new IllegalArgumentException("AgentDefinition.agentId=" + agentId + " has a blank promptKey");
        }
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
        allowedTools = allowedTools == null ? Set.of() : Set.copyOf(allowedTools);
        riskLevel = riskLevel == null ? AgentRiskLevel.LOW : riskLevel;
        if (maxIterations != null && maxIterations <= 0) {
            throw new IllegalArgumentException("AgentDefinition.agentId=" + agentId + " has a non-positive maxIterations");
        }
        if (timeoutMs != null && timeoutMs <= 0) {
            throw new IllegalArgumentException("AgentDefinition.agentId=" + agentId + " has a non-positive timeoutMs");
        }
    }
}
