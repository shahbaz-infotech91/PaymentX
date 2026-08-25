package com.paymentx.agent.registry;

import com.paymentx.agent.AgentOrchestratorApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 4.5.3 - proves the REAL, deployed {@code agents.definitions} entry for
 * "fraud-detection-agent" (application.yml) loads exactly as designed, mirroring
 * DatabaseAnalysisAgentDefinitionTest's own real-Spring-context pattern.
 */
@SpringBootTest(classes = AgentOrchestratorApplication.class)
class FraudDetectionAgentDefinitionTest {

    @Autowired
    private AgentRegistry agentRegistry;

    @Test
    void fraudDetectionAgent_resolvesFromTheRealConfiguredYaml() {
        AgentDefinition definition = agentRegistry.resolve("fraud-detection-agent");

        assertThat(definition.agentId()).isEqualTo("fraud-detection-agent");
        assertThat(definition.enabled()).isTrue();
        assertThat(definition.promptKey()).isEqualTo("PAYMENT_FRAUD_RISK_ANALYSIS");
    }

    @Test
    void fraudDetectionAgent_allowedTools_excludesRoutingAndDatabaseStatistics_noWriteTool() {
        AgentDefinition definition = agentRegistry.resolve("fraud-detection-agent");

        assertThat(definition.allowedTools()).containsExactlyInAnyOrder(
                "payment.lookup", "payment.status", "audit.search", "reconciliation.status");
        // routing.lookup deliberately excluded - Phase 4.5.0 discovery found zero fraud relevance.
        assertThat(definition.allowedTools()).doesNotContain("routing.lookup");
        // database.statistics deliberately excluded - platform-wide only, no participant scoping.
        assertThat(definition.allowedTools()).doesNotContain("database.statistics");
    }

    @Test
    void fraudDetectionAgent_capabilities_includeTheNewRiskAnalysisValue() {
        AgentDefinition definition = agentRegistry.resolve("fraud-detection-agent");

        assertThat(definition.capabilities()).containsExactlyInAnyOrder(
                AgentCapability.RISK_ANALYSIS, AgentCapability.PAYMENT_ANALYSIS,
                AgentCapability.RECONCILIATION_ANALYSIS, AgentCapability.KNOWLEDGE_RETRIEVAL);
    }

    @Test
    void fraudDetectionAgent_usesPlatformDefaultLimits_noOverrideConfigured() {
        AgentDefinition definition = agentRegistry.resolve("fraud-detection-agent");

        assertThat(definition.maxIterations()).isNull();
        assertThat(definition.timeoutMs()).isNull();
    }

    @Test
    void otherAgents_remainUnaffected() {
        assertThat(agentRegistry.getDefaultAgentId()).isEqualTo("default");
        assertThat(agentRegistry.resolve(null).agentId()).isEqualTo("default");

        AgentDefinition databaseAnalysisAgent = agentRegistry.resolve("database-analysis-agent");
        assertThat(databaseAnalysisAgent.allowedTools()).containsExactlyInAnyOrder(
                "payment.lookup", "payment.status", "audit.search", "reconciliation.status", "database.statistics");

        AgentDefinition knowledgeAssistant = agentRegistry.resolve("knowledge-assistant");
        assertThat(knowledgeAssistant.allowedTools()).containsExactlyInAnyOrder(
                "payment.lookup", "payment.status", "audit.search");

        AgentDefinition errorAnalyzer = agentRegistry.resolve("error-analyzer");
        assertThat(errorAnalyzer.allowedTools()).containsExactlyInAnyOrder(
                "payment.lookup", "payment.status", "routing.lookup", "reconciliation.status", "audit.search");
    }

    @Test
    void registry_containsAtLeastThePriorFiveAgents() {
        // Phase 4.6.0 added a sixth agent ("reconciliation-agent") - the exact total agent count
        // is now asserted in registry.ReconciliationAgentDefinitionTest.registry_containsExactlySixAgents;
        // this test only confirms fraud-detection-agent's own presence was not displaced by that addition.
        assertThat(agentRegistry.list()).extracting(AgentDefinition::agentId)
                .contains("default", "error-analyzer", "knowledge-assistant", "database-analysis-agent", "fraud-detection-agent");
    }
}
