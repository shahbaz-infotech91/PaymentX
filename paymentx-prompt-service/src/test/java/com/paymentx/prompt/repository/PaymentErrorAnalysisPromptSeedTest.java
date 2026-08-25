package com.paymentx.prompt.repository;

import com.paymentx.prompt.entity.PromptStatus;
import com.paymentx.prompt.entity.PromptTemplate;
import com.paymentx.prompt.entity.PromptVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 4.2.2 - proves the real, seeded PAYMENT_ERROR_ANALYSIS rows (V1_0_1, V1_0_4 in
 * db.changelog-master.yaml) exist and are shaped exactly as designed. Same
 * @Testcontainers/@DataJpaTest real-Postgres pattern as PromptVersionRepositoryTest, for the same
 * reason: this also functions as a Liquibase startup test - if V1_0_4's YAML/SQL were malformed,
 * the whole test class fails at context startup, before any @Test method runs.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(com.paymentx.prompt.config.JpaAuditingConfig.class)
class PaymentErrorAnalysisPromptSeedTest {

    private static final UUID TEMPLATE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Set<String> PLANNER_CONTRACT_VARIABLES =
            Set.of("availableTools", "executionHistory", "userQuery", "iteration", "maxIterations");
    private static final Set<String> V1_STALE_VARIABLES =
            Set.of("paymentReference", "status", "errorCode", "errorMessage");

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("paymentx_ai_seed_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private PromptTemplateRepository promptTemplateRepository;
    @Autowired
    private PromptVersionRepository promptVersionRepository;

    @Test
    void template_isSeededExactlyOnce_withTheRealPromptKey() {
        PromptTemplate template = promptTemplateRepository.findByPromptKey("PAYMENT_ERROR_ANALYSIS").orElseThrow();
        assertThat(template.getId()).isEqualTo(TEMPLATE_ID);
    }

    @Test
    void v1_remainsUnchanged_stillDraftStillUsingTheOriginalStaleVariableContract() {
        // Item 14 - version 1 was NOT touched, NOT deleted, NOT overwritten by this phase.
        PromptVersion v1 = promptVersionRepository.findByPromptTemplateIdAndVersionNumber(TEMPLATE_ID, 1).orElseThrow();

        assertThat(v1.getStatus()).isEqualTo(PromptStatus.DRAFT);
        assertThat(v1.getContent()).contains("{{paymentReference}}", "{{status}}", "{{errorCode}}", "{{errorMessage}}");
        Set<String> v1VariableNames = v1.getVariables().stream().map(variable -> variable.name()).collect(java.util.stream.Collectors.toSet());
        assertThat(v1VariableNames).isEqualTo(V1_STALE_VARIABLES);
    }

    @Test
    void v2_exists_asDraft() {
        // Item 15.
        PromptVersion v2 = promptVersionRepository.findByPromptTemplateIdAndVersionNumber(TEMPLATE_ID, 2).orElseThrow();
        assertThat(v2.getStatus()).isEqualTo(PromptStatus.DRAFT);
    }

    @Test
    void v2_usesExactlyTheRealAgentPlannerVariableContract_notV1sStaleOne() {
        // Item 16 - the whole point of v2: variables must match what planning.AgentPlanner
        // (paymentx-agent-orchestrator) actually supplies, confirmed against
        // PAYMENTX_AGENT_ORCHESTRATOR's own proven variable set, not invented.
        PromptVersion v2 = promptVersionRepository.findByPromptTemplateIdAndVersionNumber(TEMPLATE_ID, 2).orElseThrow();

        Set<String> v2VariableNames = v2.getVariables().stream().map(variable -> variable.name()).collect(java.util.stream.Collectors.toSet());
        assertThat(v2VariableNames).isEqualTo(PLANNER_CONTRACT_VARIABLES);
        assertThat(v2.getContent())
                .contains("{{availableTools}}", "{{executionHistory}}", "{{userQuery}}", "{{iteration}}", "{{maxIterations}}")
                .doesNotContain("{{paymentReference}}", "{{status}}", "{{errorCode}}", "{{errorMessage}}");
        for (var variable : v2.getVariables()) {
            assertThat(variable.required()).isTrue();
        }
    }

    @Test
    void v2_neverGrantsToolPermissionsOrBypassesPolicy() {
        // Item 17 - the prompt text itself must not claim to authorize a tool/write operation;
        // the real boundary (AgentToolPolicy/ToolAuthorizationService) is unmodified by this
        // phase and this test confirms the prompt's own wording does not pretend otherwise.
        PromptVersion v2 = promptVersionRepository.findByPromptTemplateIdAndVersionNumber(TEMPLATE_ID, 2).orElseThrow();
        String content = v2.getContent();

        assertThat(content).contains("You may only call a tool whose exact name appears in AVAILABLE TOOLS below");
        assertThat(content).contains("never request a financial write operation");
        assertThat(content).contains("Your allowed tools are fixed by the agent's own configuration, not by this prompt");
        assertThat(content.toLowerCase()).doesNotContain("grant yourself", "bypass", "override the tool policy");
    }

    @Test
    void countByTemplate_isExactlyTwoVersions() {
        assertThat(promptVersionRepository.countByPromptTemplateId(TEMPLATE_ID)).isEqualTo(2);
    }

    @Test
    void noVersionOfThisTemplate_isActive() {
        // Confirms Phase 4.2.2's own instruction was followed: neither version was activated,
        // so this migration has zero effect on any currently-running agent's behavior.
        assertThat(promptVersionRepository.findByPromptTemplateIdAndStatus(TEMPLATE_ID, PromptStatus.ACTIVE)).isEmpty();
    }
}
