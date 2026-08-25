package com.paymentx.agent.registry;

import com.paymentx.agent.AgentOrchestratorApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 5 (Payment Test/Validation Agent) - proves the REAL, deployed {@code agents.definitions}
 * entry for "payment-test-agent" (application.yml) loads exactly as designed, mirroring
 * IncidentRcaAgentDefinitionTest's/ReconciliationAgentDefinitionTest's own real-Spring-context
 * pattern.
 */
@SpringBootTest(classes = AgentOrchestratorApplication.class)
class PaymentTestAgentDefinitionTest {

    @Autowired
    private AgentRegistry agentRegistry;

    @Test
    void paymentTestAgent_resolvesFromTheRealConfiguredYaml() {
        AgentDefinition definition = agentRegistry.resolve("payment-test-agent");

        assertThat(definition.agentId()).isEqualTo("payment-test-agent");
        assertThat(definition.enabled()).isTrue();
        assertThat(definition.promptKey()).isEqualTo("PAYMENT_TEST_VALIDATION");
    }

    @Test
    void paymentTestAgent_allowedTools_excludesRoutingAndDatabaseStatistics_noWriteTool() {
        AgentDefinition definition = agentRegistry.resolve("payment-test-agent");

        assertThat(definition.allowedTools()).containsExactlyInAnyOrder(
                "payment.lookup", "payment.status", "audit.search", "reconciliation.status");
        assertThat(definition.allowedTools()).doesNotContain("routing.lookup", "database.statistics");
    }

    @Test
    void paymentTestAgent_capabilities_reuseExistingValues_noNewCapabilityAdded() {
        AgentDefinition definition = agentRegistry.resolve("payment-test-agent");

        // Phase 5 - like reconciliation-agent before it, this agent needed no new AgentCapability
        // value: PAYMENT_ANALYSIS/RECONCILIATION_ANALYSIS/KNOWLEDGE_RETRIEVAL all already existed.
        assertThat(definition.capabilities()).containsExactlyInAnyOrder(
                AgentCapability.PAYMENT_ANALYSIS, AgentCapability.RECONCILIATION_ANALYSIS, AgentCapability.KNOWLEDGE_RETRIEVAL);
    }

    @Test
    void paymentTestAgent_usesPlatformDefaultLimits_noOverrideConfigured() {
        AgentDefinition definition = agentRegistry.resolve("payment-test-agent");

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

        AgentDefinition incidentRcaAgent = agentRegistry.resolve("incident-rca-agent");
        assertThat(incidentRcaAgent.allowedTools()).containsExactlyInAnyOrder(
                "payment.lookup", "payment.status", "audit.search", "routing.lookup", "reconciliation.status");

        AgentDefinition databaseAnalysisAgent = agentRegistry.resolve("database-analysis-agent");
        assertThat(databaseAnalysisAgent.allowedTools()).containsExactlyInAnyOrder(
                "payment.lookup", "payment.status", "audit.search", "reconciliation.status", "database.statistics");
    }

    @Test
    void registry_containsAtLeastThePriorSevenAgents() {
        // The exact total agent count is asserted in
        // registry.IncidentRcaAgentDefinitionTest.registry_containsExactlyEightAgents; this test
        // only confirms payment-test-agent's own presence did not displace any prior agent.
        assertThat(agentRegistry.list()).extracting(AgentDefinition::agentId)
                .contains("default", "error-analyzer", "knowledge-assistant", "database-analysis-agent",
                        "fraud-detection-agent", "reconciliation-agent", "incident-rca-agent", "payment-test-agent");
    }
}
