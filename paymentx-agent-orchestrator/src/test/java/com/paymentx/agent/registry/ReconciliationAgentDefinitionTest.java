package com.paymentx.agent.registry;

import com.paymentx.agent.AgentOrchestratorApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 4.6.0 - proves the REAL, deployed {@code agents.definitions} entry for
 * "reconciliation-agent" (application.yml) loads exactly as designed, mirroring
 * FraudDetectionAgentDefinitionTest's own real-Spring-context pattern.
 */
@SpringBootTest(classes = AgentOrchestratorApplication.class)
class ReconciliationAgentDefinitionTest {

    @Autowired
    private AgentRegistry agentRegistry;

    @Test
    void reconciliationAgent_resolvesFromTheRealConfiguredYaml() {
        AgentDefinition definition = agentRegistry.resolve("reconciliation-agent");

        assertThat(definition.agentId()).isEqualTo("reconciliation-agent");
        assertThat(definition.enabled()).isTrue();
        assertThat(definition.promptKey()).isEqualTo("PAYMENT_RECONCILIATION_ANALYSIS");
    }

    @Test
    void reconciliationAgent_allowedTools_excludesRoutingAndDatabaseStatistics_noWriteTool() {
        AgentDefinition definition = agentRegistry.resolve("reconciliation-agent");

        assertThat(definition.allowedTools()).containsExactlyInAnyOrder(
                "payment.lookup", "payment.status", "audit.search", "reconciliation.status");
        assertThat(definition.allowedTools()).doesNotContain("routing.lookup", "database.statistics");
    }

    @Test
    void reconciliationAgent_capabilities_reuseExistingValues_noNewCapabilityAdded() {
        AgentDefinition definition = agentRegistry.resolve("reconciliation-agent");

        // Phase 4.6.0 - unlike Database Analysis (added DATABASE_ANALYSIS) and Fraud Detection
        // (added RISK_ANALYSIS), this agent needed no new AgentCapability value: RECONCILIATION_ANALYSIS
        // already existed (used by error-analyzer/database-analysis-agent/fraud-detection-agent).
        assertThat(definition.capabilities()).containsExactlyInAnyOrder(
                AgentCapability.RECONCILIATION_ANALYSIS, AgentCapability.PAYMENT_ANALYSIS, AgentCapability.KNOWLEDGE_RETRIEVAL);
    }

    @Test
    void reconciliationAgent_usesPlatformDefaultLimits_noOverrideConfigured() {
        AgentDefinition definition = agentRegistry.resolve("reconciliation-agent");

        assertThat(definition.maxIterations()).isNull();
        assertThat(definition.timeoutMs()).isNull();
    }

    @Test
    void otherAgents_remainUnaffected() {
        assertThat(agentRegistry.getDefaultAgentId()).isEqualTo("default");
        assertThat(agentRegistry.resolve(null).agentId()).isEqualTo("default");

        AgentDefinition fraudDetectionAgent = agentRegistry.resolve("fraud-detection-agent");
        assertThat(fraudDetectionAgent.allowedTools()).containsExactlyInAnyOrder(
                "payment.lookup", "payment.status", "audit.search", "reconciliation.status");

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
    void registry_containsAtLeastThePriorSixAgents() {
        // Phase 4.8.0 added a seventh agent ("incident-rca-agent") - the exact total agent count
        // is now asserted in registry.IncidentRcaAgentDefinitionTest.registry_containsExactlySevenAgents;
        // this test only confirms reconciliation-agent's own presence was not displaced by that addition.
        assertThat(agentRegistry.list()).extracting(AgentDefinition::agentId)
                .contains("default", "error-analyzer", "knowledge-assistant", "database-analysis-agent",
                        "fraud-detection-agent", "reconciliation-agent");
    }
}
