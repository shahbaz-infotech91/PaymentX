package com.paymentx.agent.registry;

import com.paymentx.agent.AgentOrchestratorApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 4.3 - proves the REAL, deployed {@code agents.definitions} entry for
 * "knowledge-assistant" (application.yml) loads exactly as designed, mirroring
 * ErrorAnalyzerAgentDefinitionTest's own real-Spring-context pattern.
 */
@SpringBootTest(classes = AgentOrchestratorApplication.class)
class KnowledgeAssistantAgentDefinitionTest {

    @Autowired
    private AgentRegistry agentRegistry;

    @Test
    void knowledgeAssistant_resolvesFromTheRealConfiguredYaml() {
        AgentDefinition definition = agentRegistry.resolve("knowledge-assistant");

        assertThat(definition.agentId()).isEqualTo("knowledge-assistant");
        assertThat(definition.enabled()).isTrue();
        assertThat(definition.promptKey()).isEqualTo("PAYMENT_KNOWLEDGE_ASSISTANT");
    }

    @Test
    void knowledgeAssistant_allowedTools_isTheSmallestPossibleSet_noRoutingNoReconciliationNoWrite() {
        AgentDefinition definition = agentRegistry.resolve("knowledge-assistant");

        assertThat(definition.allowedTools()).containsExactlyInAnyOrder(
                "payment.lookup", "payment.status", "audit.search");
    }

    @Test
    void knowledgeAssistant_capabilities_reflectKnowledgeFirstScope() {
        AgentDefinition definition = agentRegistry.resolve("knowledge-assistant");

        assertThat(definition.capabilities()).containsExactlyInAnyOrder(
                AgentCapability.KNOWLEDGE_RETRIEVAL, AgentCapability.PAYMENT_ANALYSIS);
    }

    @Test
    void knowledgeAssistant_usesPlatformDefaultLimits_noOverrideConfigured() {
        AgentDefinition definition = agentRegistry.resolve("knowledge-assistant");

        assertThat(definition.maxIterations()).isNull();
        assertThat(definition.timeoutMs()).isNull();
    }

    @Test
    void defaultAgent_andErrorAnalyzer_areStillUnaffected() {
        // Backward compatibility: adding knowledge-assistant must not change what an
        // omitted/blank agentId resolves to, nor error-analyzer's own definition.
        assertThat(agentRegistry.getDefaultAgentId()).isEqualTo("default");
        assertThat(agentRegistry.resolve(null).agentId()).isEqualTo("default");

        AgentDefinition errorAnalyzer = agentRegistry.resolve("error-analyzer");
        assertThat(errorAnalyzer.allowedTools()).containsExactlyInAnyOrder(
                "payment.lookup", "payment.status", "routing.lookup", "reconciliation.status", "audit.search");
        assertThat(errorAnalyzer.promptKey()).isEqualTo("PAYMENT_ERROR_ANALYSIS");
    }

    @Test
    void registry_containsAtLeastTheThreePriorAgents() {
        // Phase 4.4 added a fourth agent ("database-analysis-agent") - the exact total agent count is
        // now asserted in registry.DatabaseAnalysisAgentDefinitionTest.registry_containsExactlyFourAgents;
        // this test only confirms knowledge-assistant's own presence was not displaced by that addition.
        assertThat(agentRegistry.list()).extracting(AgentDefinition::agentId)
                .contains("default", "error-analyzer", "knowledge-assistant");
    }
}
