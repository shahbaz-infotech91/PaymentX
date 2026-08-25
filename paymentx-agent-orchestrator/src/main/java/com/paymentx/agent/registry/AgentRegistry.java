package com.paymentx.agent.registry;

import com.paymentx.agent.exception.AgentException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The Phase 4.1 agent registry - configuration-based and in-memory (Phase 4.0's own finding:
 * nothing today needs a human to enable/disable/reconfigure an agent at runtime without a
 * deploy, and this platform's own stated philosophy keeps security-relevant surfaces, like a
 * tool allow-list, in reviewed configuration/code rather than a runtime-mutable database table -
 * matching {@code AgentToolPolicy}'s own pre-Phase-4.1 hardcoded-allow-list precedent, just made
 * configurable instead of hardcoded).
 *
 * <p>Built once at startup from {@link AgentRegistryProperties} and immutable afterward - the
 * same fail-fast-on-duplicate-identity discipline MCP Gateway's own {@code ToolRegistry} already
 * established for tools ("Collects every PaymentXTool bean at startup, indexes by name, fails
 * fast on duplicate names") is reused here for agents.
 */
@Component
@Slf4j
public class AgentRegistry {

    private final Map<String, AgentDefinition> agentsById;
    private final String defaultAgentId;

    public AgentRegistry(AgentRegistryProperties properties) {
        if (properties.getDefinitions().isEmpty()) {
            throw new IllegalStateException(
                    "No agent definitions configured under 'agents.definitions' - at least the default agent must be defined.");
        }

        Map<String, AgentDefinition> built = new LinkedHashMap<>();
        for (AgentRegistryProperties.Entry entry : properties.getDefinitions()) {
            AgentDefinition definition = toDefinition(entry);
            if (built.containsKey(definition.agentId())) {
                throw new IllegalStateException("Duplicate agentId in 'agents.definitions': " + definition.agentId());
            }
            built.put(definition.agentId(), definition);
        }

        this.defaultAgentId = properties.getDefaultAgentId();
        if (!built.containsKey(defaultAgentId)) {
            throw new IllegalStateException(
                    "'agents.default-agent-id=" + defaultAgentId + "' does not match any configured agent definition.");
        }

        this.agentsById = Map.copyOf(built);
        log.info("AgentRegistry initialized with {} agent definition(s), defaultAgentId={}", agentsById.size(), defaultAgentId);
    }

    private AgentDefinition toDefinition(AgentRegistryProperties.Entry entry) {
        Set<AgentCapability> capabilities = entry.getCapabilities().stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(value -> AgentCapability.valueOf(value.toUpperCase()))
                .collect(Collectors.toUnmodifiableSet());

        Set<String> allowedTools = entry.getAllowedTools().stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());

        AgentRiskLevel riskLevel = AgentRiskLevel.valueOf(entry.getRiskLevel().trim().toUpperCase());

        return new AgentDefinition(
                entry.getAgentId(),
                entry.getName(),
                entry.getDescription(),
                entry.getVersion(),
                capabilities,
                allowedTools,
                entry.getPromptKey(),
                riskLevel,
                entry.isEnabled(),
                entry.getMaxIterations(),
                entry.getTimeoutMs());
    }

    /**
     * Resolves the agent to run for a request. A blank/null requested id resolves to the
     * configured default agent - the exact backward-compatible behavior every pre-Phase-4.1
     * caller (Control Center's AiChatService, every existing test) relies on.
     *
     * @throws AgentException agentNotFound if the id does not match any configured agent,
     *                        agentDisabled if it does but is currently disabled
     */
    public AgentDefinition resolve(String requestedAgentId) {
        String effectiveId = (requestedAgentId == null || requestedAgentId.isBlank()) ? defaultAgentId : requestedAgentId.trim();
        AgentDefinition definition = agentsById.get(effectiveId);
        if (definition == null) {
            log.warn("Rejected agent execution request for unknown agentId={}", effectiveId);
            throw AgentException.agentNotFound("Unknown agent: " + effectiveId);
        }
        if (!definition.enabled()) {
            log.warn("Rejected agent execution request for disabled agentId={}", effectiveId);
            throw AgentException.agentDisabled("Agent is disabled: " + effectiveId);
        }
        return definition;
    }

    public Optional<AgentDefinition> find(String agentId) {
        return Optional.ofNullable(agentId).map(agentsById::get);
    }

    public boolean isEnabled(String agentId) {
        return find(agentId).map(AgentDefinition::enabled).orElse(false);
    }

    public Set<AgentCapability> capabilitiesOf(String agentId) {
        return resolve(agentId).capabilities();
    }

    public Set<String> allowedToolsOf(String agentId) {
        return resolve(agentId).allowedTools();
    }

    /** All registered agents, enabled and disabled alike - callers filter on {@link AgentDefinition#enabled()}. */
    public List<AgentDefinition> list() {
        return List.copyOf(agentsById.values());
    }

    public String getDefaultAgentId() {
        return defaultAgentId;
    }
}
