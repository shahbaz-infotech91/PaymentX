package com.paymentx.agent.registry;

import com.paymentx.agent.AgentOrchestratorApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 4.4 - proves the REAL, deployed {@code agents.definitions} entry for
 * "database-analysis-agent" (application.yml) loads exactly as designed, mirroring
 * KnowledgeAssistantAgentDefinitionTest's own real-Spring-context pattern.
 */
@SpringBootTest(classes = AgentOrchestratorApplication.class)
class DatabaseAnalysisAgentDefinitionTest {

    @Autowired
    private AgentRegistry agentRegistry;

    @Test
    void databaseAnalysisAgent_resolvesFromTheRealConfiguredYaml() {
        AgentDefinition definition = agentRegistry.resolve("database-analysis-agent");

        assertThat(definition.agentId()).isEqualTo("database-analysis-agent");
        assertThat(definition.enabled()).isTrue();
        assertThat(definition.promptKey()).isEqualTo("PAYMENT_DATABASE_ANALYSIS");
    }

    @Test
    void databaseAnalysisAgent_allowedTools_includesTheNewReadOnlyDatabaseToolAndNoWriteTool() {
        AgentDefinition definition = agentRegistry.resolve("database-analysis-agent");

        assertThat(definition.allowedTools()).containsExactlyInAnyOrder(
                "payment.lookup", "payment.status", "audit.search", "reconciliation.status", "database.statistics");
        assertThat(definition.allowedTools()).doesNotContain("routing.lookup");
    }

    @Test
    void databaseAnalysisAgent_capabilities_reflectDatabaseAnalysisScope() {
        AgentDefinition definition = agentRegistry.resolve("database-analysis-agent");

        assertThat(definition.capabilities()).containsExactlyInAnyOrder(
                AgentCapability.DATABASE_ANALYSIS, AgentCapability.PAYMENT_ANALYSIS,
                AgentCapability.RECONCILIATION_ANALYSIS, AgentCapability.KNOWLEDGE_RETRIEVAL);
    }

    @Test
    void databaseAnalysisAgent_usesPlatformDefaultLimits_noOverrideConfigured() {
        AgentDefinition definition = agentRegistry.resolve("database-analysis-agent");

        assertThat(definition.maxIterations()).isNull();
        assertThat(definition.timeoutMs()).isNull();
    }

    @Test
    void otherAgents_remainUnaffected() {
        assertThat(agentRegistry.getDefaultAgentId()).isEqualTo("default");
        assertThat(agentRegistry.resolve(null).agentId()).isEqualTo("default");

        AgentDefinition knowledgeAssistant = agentRegistry.resolve("knowledge-assistant");
        assertThat(knowledgeAssistant.allowedTools()).containsExactlyInAnyOrder(
                "payment.lookup", "payment.status", "audit.search");

        AgentDefinition errorAnalyzer = agentRegistry.resolve("error-analyzer");
        assertThat(errorAnalyzer.allowedTools()).containsExactlyInAnyOrder(
                "payment.lookup", "payment.status", "routing.lookup", "reconciliation.status", "audit.search");
    }

    @Test
    void registry_containsAtLeastThePriorFourAgents() {
        // Phase 4.5.3 added a fifth agent ("fraud-detection-agent") - the exact total agent count
        // is now asserted in registry.FraudDetectionAgentDefinitionTest.registry_containsExactlyFiveAgents;
        // this test only confirms database-analysis-agent's own presence was not displaced by that addition.
        assertThat(agentRegistry.list()).extracting(AgentDefinition::agentId)
                .contains("default", "error-analyzer", "knowledge-assistant", "database-analysis-agent");
    }
}
