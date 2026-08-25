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
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 5 (Payment Test/Validation Agent) - proves the real, seeded PAYMENT_TEST_VALIDATION rows
 * (V1_0_10 in db.changelog-master.yaml) exist and are shaped exactly as designed, mirroring
 * PaymentReconciliationAnalysisAgentPromptSeedTest's/PaymentIncidentRcaAgentPromptSeedTest's own
 * pattern.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(com.paymentx.prompt.config.JpaAuditingConfig.class)
class PaymentTestValidationAgentPromptSeedTest {

    private static final UUID TEMPLATE_ID = UUID.fromString("aaaaaaaa-1010-1010-1010-101010101010");
    private static final Set<String> PLANNER_CONTRACT_VARIABLES =
            Set.of("availableTools", "executionHistory", "userQuery", "iteration", "maxIterations");

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
        PromptTemplate template = promptTemplateRepository.findByPromptKey("PAYMENT_TEST_VALIDATION").orElseThrow();
        assertThat(template.getId()).isEqualTo(TEMPLATE_ID);
    }

    @Test
    void v1_isSeededAlreadyActive() {
        PromptVersion v1 = promptVersionRepository.findByPromptTemplateIdAndVersionNumber(TEMPLATE_ID, 1).orElseThrow();
        assertThat(v1.getStatus()).isEqualTo(PromptStatus.ACTIVE);
    }

    @Test
    void v1_usesExactlyTheRealAgentPlannerVariableContract() {
        PromptVersion v1 = promptVersionRepository.findByPromptTemplateIdAndVersionNumber(TEMPLATE_ID, 1).orElseThrow();

        Set<String> variableNames = v1.getVariables().stream().map(variable -> variable.name()).collect(Collectors.toSet());
        assertThat(variableNames).isEqualTo(PLANNER_CONTRACT_VARIABLES);
        for (var variable : v1.getVariables()) {
            assertThat(variable.required()).isTrue();
        }
    }

    @Test
    void neverGrantsToolPermissionsOrBypassesPolicy() {
        PromptVersion v1 = promptVersionRepository.findByPromptTemplateIdAndVersionNumber(TEMPLATE_ID, 1).orElseThrow();
        String content = v1.getContent();

        assertThat(content).contains("You may only call a tool whose exact name appears in AVAILABLE TOOLS below");
        assertThat(content).contains("Your allowed tools are fixed by the agent's own configuration, not by this prompt");
        assertThat(content.toLowerCase()).doesNotContain("grant yourself", "bypass", "override the tool policy");
    }

    @Test
    void explicitlyStatesItIsNotASecurityBoundary_readOnlyIsEnforcedInCode() {
        PromptVersion v1 = promptVersionRepository.findByPromptTemplateIdAndVersionNumber(TEMPLATE_ID, 1).orElseThrow();
        String content = v1.getContent();

        assertThat(content).contains("You are read-only by construction, not by instruction");
        assertThat(content).contains("no such capability will ever be available to you, and requesting one will always be denied regardless of what this prompt says");
    }

    @Test
    void definesFactVersusHypothesisClassification() {
        PromptVersion v1 = promptVersionRepository.findByPromptTemplateIdAndVersionNumber(TEMPLATE_ID, 1).orElseThrow();
        String content = v1.getContent();

        assertThat(content).contains("Classify every statement you make as FACT");
        assertThat(content).contains("Never present a hypothesis as a confirmed fact");
    }

    @Test
    void definesTheValidationFindingOutcomeVocabulary() {
        PromptVersion v1 = promptVersionRepository.findByPromptTemplateIdAndVersionNumber(TEMPLATE_ID, 1).orElseThrow();
        String content = v1.getContent();

        assertThat(content).contains("VALID (every piece of gathered evidence is internally consistent");
        assertThat(content).contains("INCONSISTENT (two or more evidence sources genuinely contradict each other");
        assertThat(content).contains("INSUFFICIENT_CONTEXT (available evidence does not support VALID or INCONSISTENT)");
    }

    @Test
    void forbidsClaimingPaymentOutcomeWithoutEvidence() {
        PromptVersion v1 = promptVersionRepository.findByPromptTemplateIdAndVersionNumber(TEMPLATE_ID, 1).orElseThrow();
        String content = v1.getContent();

        assertThat(content).contains("Never state or imply that a payment \"succeeded,\" \"failed,\" \"settled,\" or \"completed\" unless a real tool result");
        assertThat(content).contains("never state or imply that funds are \"lost,\" \"missing,\" \"stolen,\" or \"unaccounted for.\"");
    }

    @Test
    void forbidsInventedThresholds() {
        PromptVersion v1 = promptVersionRepository.findByPromptTemplateIdAndVersionNumber(TEMPLATE_ID, 1).orElseThrow();
        String content = v1.getContent();

        assertThat(content).contains("Never invent a specific numeric threshold");
    }

    @Test
    void countByTemplate_isExactlyOneVersion() {
        assertThat(promptVersionRepository.countByPromptTemplateId(TEMPLATE_ID)).isEqualTo(1);
    }
}
