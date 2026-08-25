package com.paymentx.agent.registry;

import com.paymentx.agent.AgentOrchestratorApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 4.8.0 - proves the REAL, deployed {@code agents.definitions} entry for
 * "incident-rca-agent" (application.yml) loads exactly as designed, mirroring
 * ReconciliationAgentDefinitionTest's own real-Spring-context pattern.
 */
@SpringBootTest(classes = AgentOrchestratorApplication.class)
class IncidentRcaAgentDefinitionTest {

    @Autowired
    private AgentRegistry agentRegistry;

    @Test
    void incidentRcaAgent_resolvesFromTheRealConfiguredYaml() {
        AgentDefinition definition = agentRegistry.resolve("incident-rca-agent");

        assertThat(definition.agentId()).isEqualTo("incident-rca-agent");
        assertThat(definition.enabled()).isTrue();
        assertThat(definition.promptKey()).isEqualTo("PAYMENT_INCIDENT_RCA");
    }

    @Test
    void incidentRcaAgent_allowedTools_excludesDatabaseStatistics_noWriteTool() {
        AgentDefinition definition = agentRegistry.resolve("incident-rca-agent");

        assertThat(definition.allowedTools()).containsExactlyInAnyOrder(
                "payment.lookup", "payment.status", "audit.search", "routing.lookup", "reconciliation.status");
        // database.statistics deliberately excluded - platform-wide only, not incident/payment scoped.
        assertThat(definition.allowedTools()).doesNotContain("database.statistics");
    }

    @Test
    void incidentRcaAgent_capabilities_reuseExistingLogAnalysisValue_noNewCapabilityAdded() {
        AgentDefinition definition = agentRegistry.resolve("incident-rca-agent");

        // Phase 4.8.0 - unlike Database Analysis (added DATABASE_ANALYSIS) and Fraud Detection
        // (added RISK_ANALYSIS), this agent needed no new AgentCapability value: LOG_ANALYSIS
        // already existed in the enum (reserved since Phase 4.1) but had never been used by any
        // agent until now.
        assertThat(definition.capabilities()).containsExactlyInAnyOrder(
                AgentCapability.LOG_ANALYSIS, AgentCapability.PAYMENT_ANALYSIS, AgentCapability.ROUTING_ANALYSIS,
                AgentCapability.RECONCILIATION_ANALYSIS, AgentCapability.KNOWLEDGE_RETRIEVAL);
    }

    @Test
    void incidentRcaAgent_usesPlatformDefaultLimits_noOverrideConfigured() {
        AgentDefinition definition = agentRegistry.resolve("incident-rca-agent");

        assertThat(definition.maxIterations()).isNull();
        assertThat(definition.timeoutMs()).isNull();
    }

    @Test
    void otherAgents_remainUnaffected() {
        assertThat(agentRegistry.getDefaultAgentId()).isEqualTo("default");
        assertThat(agentRegistry.resolve(null).agentId()).isEqualTo("default");

        AgentDefinition reconciliationAgent = agentRegistry.resolve("reconciliation-agent");
        assertThat(reconciliationAgent.allowedTools()).containsExactlyInAnyOrder(
                "payment.lookup", "payment.status", "audit.search", "reconciliation.status");

        AgentDefinition errorAnalyzer = agentRegistry.resolve("error-analyzer");
        assertThat(errorAnalyzer.allowedTools()).containsExactlyInAnyOrder(
                "payment.lookup", "payment.status", "routing.lookup", "reconciliation.status", "audit.search");

        AgentDefinition databaseAnalysisAgent = agentRegistry.resolve("database-analysis-agent");
        assertThat(databaseAnalysisAgent.allowedTools()).containsExactlyInAnyOrder(
                "payment.lookup", "payment.status", "audit.search", "reconciliation.status", "database.statistics");
    }

    @Test
    void registry_containsExactlyEightAgents() {
        assertThat(agentRegistry.list()).extracting(AgentDefinition::agentId)
                .containsExactlyInAnyOrder(
                        "default", "error-analyzer", "knowledge-assistant", "database-analysis-agent",
                        "fraud-detection-agent", "reconciliation-agent", "incident-rca-agent", "payment-test-agent");
    }
}
