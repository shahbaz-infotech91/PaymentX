package com.paymentx.agent.registry;

import com.paymentx.agent.exception.AgentErrorCodes;
import com.paymentx.agent.exception.AgentException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 4.1 - deterministic unit tests for the agent registry foundation: registration,
 * lookup, duplicate-id rejection, unknown-agent rejection, disabled-agent rejection, listing,
 * and capability/allowed-tool lookup. Mirrors MCP Gateway's own ToolRegistry test discipline -
 * a REAL registry (no mocking possible or needed; it has no external dependencies), built from
 * plain AgentRegistryProperties the way Spring would bind it from application.yml.
 */
class AgentRegistryTest {

    private static AgentRegistryProperties.Entry entry(String agentId, boolean enabled, String... allowedTools) {
        AgentRegistryProperties.Entry entry = new AgentRegistryProperties.Entry();
        entry.setAgentId(agentId);
        entry.setName("Agent " + agentId);
        entry.setDescription("desc");
        entry.setPromptKey("PAYMENTX_AGENT_ORCHESTRATOR");
        entry.setAllowedTools(List.of(allowedTools));
        entry.setCapabilities(List.of("PAYMENT_ANALYSIS"));
        entry.setEnabled(enabled);
        return entry;
    }

    private static AgentRegistryProperties propertiesWith(AgentRegistryProperties.Entry... entries) {
        AgentRegistryProperties properties = new AgentRegistryProperties();
        properties.setDefaultAgentId(entries[0].getAgentId());
        properties.getDefinitions().addAll(List.of(entries));
        return properties;
    }

    // ---------------------------------------------------------------
    // Registration / construction
    // ---------------------------------------------------------------

    @Test
    void construction_noDefinitionsConfigured_failsFast() {
        assertThatThrownBy(() -> new AgentRegistry(new AgentRegistryProperties()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("agents.definitions");
    }

    @Test
    void construction_defaultAgentIdNotAmongDefinitions_failsFast() {
        AgentRegistryProperties properties = propertiesWith(entry("some-agent", true, "audit.search"));
        properties.setDefaultAgentId("does-not-exist");

        assertThatThrownBy(() -> new AgentRegistry(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("default-agent-id");
    }

    @Test
    void construction_duplicateAgentId_failsFast() {
        AgentRegistryProperties properties = propertiesWith(
                entry("dup", true, "audit.search"),
                entry("dup", true, "payment.lookup"));

        assertThatThrownBy(() -> new AgentRegistry(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate agentId");
    }

    @Test
    void construction_validSingleEntry_registersSuccessfully() {
        AgentRegistry registry = new AgentRegistry(propertiesWith(entry("default", true, "audit.search")));
        assertThat(registry.list()).hasSize(1);
    }

    // ---------------------------------------------------------------
    // Lookup / resolve
    // ---------------------------------------------------------------

    @Test
    void resolve_blankOrNullAgentId_resolvesToConfiguredDefault() {
        AgentRegistry registry = new AgentRegistry(propertiesWith(entry("default", true, "audit.search")));

        assertThat(registry.resolve(null).agentId()).isEqualTo("default");
        assertThat(registry.resolve("").agentId()).isEqualTo("default");
        assertThat(registry.resolve("   ").agentId()).isEqualTo("default");
    }

    @Test
    void resolve_realAgentId_returnsMatchingDefinition() {
        AgentRegistry registry = new AgentRegistry(propertiesWith(
                entry("default", true, "audit.search"),
                entry("second", true, "payment.lookup")));

        AgentDefinition resolved = registry.resolve("second");

        assertThat(resolved.agentId()).isEqualTo("second");
        assertThat(resolved.allowedTools()).containsExactly("payment.lookup");
    }

    @Test
    void resolve_unknownAgentId_throwsAgentNotFound() {
        AgentRegistry registry = new AgentRegistry(propertiesWith(entry("default", true, "audit.search")));

        assertThatThrownBy(() -> registry.resolve("does-not-exist"))
                .isInstanceOf(AgentException.class)
                .extracting(ex -> ((AgentException) ex).getErrorCode())
                .isEqualTo(AgentErrorCodes.AGENT_NOT_FOUND);
    }

    @Test
    void resolve_disabledAgentId_throwsAgentDisabled() {
        AgentRegistry registry = new AgentRegistry(propertiesWith(
                entry("default", true, "audit.search"),
                entry("off", false, "payment.lookup")));

        assertThatThrownBy(() -> registry.resolve("off"))
                .isInstanceOf(AgentException.class)
                .extracting(ex -> ((AgentException) ex).getErrorCode())
                .isEqualTo(AgentErrorCodes.AGENT_DISABLED);
    }

    @Test
    void find_unknownAgentId_returnsEmpty() {
        AgentRegistry registry = new AgentRegistry(propertiesWith(entry("default", true, "audit.search")));
        assertThat(registry.find("nope")).isEmpty();
    }

    @Test
    void find_knownAgentId_returnsDefinitionEvenIfDisabled() {
        // find() is a non-throwing lookup (e.g. for a future admin listing) - unlike resolve(),
        // it does not enforce the enabled flag itself.
        AgentRegistry registry = new AgentRegistry(propertiesWith(
                entry("default", true, "audit.search"),
                entry("off", false, "payment.lookup")));

        assertThat(registry.find("off")).isPresent();
    }

    // ---------------------------------------------------------------
    // enabled / listing / capability & tool lookup
    // ---------------------------------------------------------------

    @Test
    void isEnabled_reflectsEachDefinitionsOwnFlag() {
        AgentRegistry registry = new AgentRegistry(propertiesWith(
                entry("on", true, "audit.search"),
                entry("off", false, "payment.lookup")));

        assertThat(registry.isEnabled("on")).isTrue();
        assertThat(registry.isEnabled("off")).isFalse();
        assertThat(registry.isEnabled("unknown")).isFalse();
    }

    @Test
    void list_returnsEveryConfiguredAgent_enabledAndDisabledAlike() {
        AgentRegistry registry = new AgentRegistry(propertiesWith(
                entry("on", true, "audit.search"),
                entry("off", false, "payment.lookup")));

        assertThat(registry.list()).extracting(AgentDefinition::agentId).containsExactlyInAnyOrder("on", "off");
    }

    @Test
    void capabilitiesOf_returnsTheResolvedDefinitionsCapabilities() {
        AgentRegistryProperties.Entry withCapability = entry("default", true, "audit.search");
        withCapability.setCapabilities(List.of("KNOWLEDGE_RETRIEVAL", "LOG_ANALYSIS"));
        AgentRegistry registry = new AgentRegistry(propertiesWith(withCapability));

        assertThat(registry.capabilitiesOf("default")).containsExactlyInAnyOrder(
                AgentCapability.KNOWLEDGE_RETRIEVAL, AgentCapability.LOG_ANALYSIS);
    }

    @Test
    void allowedToolsOf_returnsTheResolvedDefinitionsToolAllowList() {
        AgentRegistry registry = new AgentRegistry(propertiesWith(entry("default", true, "audit.search", "payment.lookup")));

        assertThat(registry.allowedToolsOf("default")).containsExactlyInAnyOrder("audit.search", "payment.lookup");
    }
}
