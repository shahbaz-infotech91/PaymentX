package com.paymentx.agent.registry;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * YAML-bound source for {@link AgentRegistry} - deliberately a plain mutable
 * {@code @Getter/@Setter} bean (matching AgentOrchestratorProperties' own established
 * {@code @ConfigurationProperties} style in this module), not the immutable {@link
 * AgentDefinition} record itself. AgentDefinition's compact constructor validates and its
 * fields are typed ({@code Set<AgentCapability>}, {@code AgentRiskLevel}); this class exists
 * only so Spring's relaxed YAML-list binding has a simple, mutable, string/list-typed shape to
 * bind into before {@link AgentRegistry} converts each entry into a real AgentDefinition at
 * startup.
 *
 * <p>Bound from the top-level {@code agents:} block - a sibling of the existing singular
 * {@code agent:} block ({@link com.paymentx.agent.config.AgentOrchestratorProperties}), not a
 * replacement for it. Phase 4.0's own recommendation (configuration-based, not database-backed,
 * registry - no existing runtime-admin-edit requirement justifies a database yet) is followed
 * here: this is a plain deploy-time YAML list, reviewed the same way the tool allow-list itself
 * already is.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "agents")
public class AgentRegistryProperties {

    /** The agentId to resolve when a caller's request omits one - preserves Phase 3.8's
     * existing single-agent behavior for every pre-Phase-4.1 client. */
    private String defaultAgentId = "default";

    private List<Entry> definitions = new ArrayList<>();

    @Getter
    @Setter
    public static class Entry {
        private String agentId;
        private String name;
        private String description;
        private String version = "1.0";
        private List<String> capabilities = new ArrayList<>();
        private List<String> allowedTools = new ArrayList<>();
        private String promptKey;
        private String riskLevel = "LOW";
        private boolean enabled = true;
        private Integer maxIterations;
        private Long timeoutMs;
    }
}
