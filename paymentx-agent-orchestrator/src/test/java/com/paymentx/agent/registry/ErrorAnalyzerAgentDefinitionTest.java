package com.paymentx.agent.registry;

import com.paymentx.agent.AgentOrchestratorApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 4.2.3 - proves the REAL, deployed {@code agents.definitions} entry for "error-analyzer"
 * (application.yml) loads exactly as designed - a real Spring context boot, not a hand-built
 * AgentRegistryProperties fixture (registry.AgentRegistryTest already covers the registry's own
 * generic mechanics against fixtures; this test is specifically about the actual production
 * configuration, closing the loop between "the YAML we wrote" and "what AgentRegistry actually
 * resolves at runtime").
 */
@SpringBootTest(classes = AgentOrchestratorApplication.class)
class ErrorAnalyzerAgentDefinitionTest {

    @Autowired
    private AgentRegistry agentRegistry;

    @Test
    void errorAnalyzer_resolvesFromTheRealConfiguredYaml() {
        AgentDefinition definition = agentRegistry.resolve("error-analyzer");

        assertThat(definition.agentId()).isEqualTo("error-analyzer");
        assertThat(definition.enabled()).isTrue();
        assertThat(definition.promptKey()).isEqualTo("PAYMENT_ERROR_ANALYSIS");
    }

    @Test
    void errorAnalyzer_allowedTools_isExactlyTheFiveRealReadOnlyMcpTools_noWriteToolNoExtraTool() {
        AgentDefinition definition = agentRegistry.resolve("error-analyzer");

        assertThat(definition.allowedTools()).containsExactlyInAnyOrder(
                "payment.lookup", "payment.status", "routing.lookup", "reconciliation.status", "audit.search");
    }

    @Test
    void errorAnalyzer_capabilities_reflectItsRealInvestigativeScope() {
        AgentDefinition definition = agentRegistry.resolve("error-analyzer");

        assertThat(definition.capabilities()).containsExactlyInAnyOrder(
                AgentCapability.PAYMENT_ANALYSIS, AgentCapability.ROUTING_ANALYSIS,
                AgentCapability.RECONCILIATION_ANALYSIS, AgentCapability.KNOWLEDGE_RETRIEVAL);
    }

    @Test
    void errorAnalyzer_usesPlatformDefaultLimits_noOverrideConfigured() {
        // maxIterations/timeoutMs are left unset in application.yml - falls back to the `agent:`
        // block's platform defaults, the same pattern "default" already uses.
        AgentDefinition definition = agentRegistry.resolve("error-analyzer");

        assertThat(definition.maxIterations()).isNull();
        assertThat(definition.timeoutMs()).isNull();
    }

    @Test
    void defaultAgent_isStillTheConfiguredDefault_errorAnalyzerDidNotDisplaceIt() {
        // Backward compatibility (item 25): adding error-analyzer must not change what an
        // omitted/blank agentId resolves to.
        assertThat(agentRegistry.getDefaultAgentId()).isEqualTo("default");
        assertThat(agentRegistry.resolve(null).agentId()).isEqualTo("default");
        assertThat(agentRegistry.resolve(null).promptKey()).isEqualTo("PAYMENTX_AGENT_ORCHESTRATOR");
    }

    @Test
    void registry_containsAtLeastDefaultAndErrorAnalyzer() {
        // Phase 4.3 added a third agent ("knowledge-assistant") - the exact total agent count is
        // now asserted in registry.KnowledgeAssistantAgentDefinitionTest.registry_containsExactlyThreeAgents;
        // this test only confirms error-analyzer's own presence was not displaced by that addition.
        assertThat(agentRegistry.list()).extracting(AgentDefinition::agentId)
                .contains("default", "error-analyzer");
    }
}
