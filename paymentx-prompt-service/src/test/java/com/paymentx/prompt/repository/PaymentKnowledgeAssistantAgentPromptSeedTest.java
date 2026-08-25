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
 * Phase 4.3 - proves the real, seeded PAYMENT_KNOWLEDGE_ASSISTANT rows (V1_0_5 in
 * db.changelog-master.yaml) exist and are shaped exactly as designed. Same
 * @Testcontainers/@DataJpaTest real-Postgres pattern PaymentErrorAnalysisPromptSeedTest already
 * established - this also functions as a Liquibase startup test.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(com.paymentx.prompt.config.JpaAuditingConfig.class)
class PaymentKnowledgeAssistantAgentPromptSeedTest {

    private static final UUID TEMPLATE_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
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
        PromptTemplate template = promptTemplateRepository.findByPromptKey("PAYMENT_KNOWLEDGE_ASSISTANT").orElseThrow();
        assertThat(template.getId()).isEqualTo(TEMPLATE_ID);
    }

    @Test
    void isDistinctFrom_theUnrelatedRagServiceInternalPrompt() {
        // PAYMENTX_KNOWLEDGE_ASSISTANT (V1_0_2, note the "X") is RAG Service's own internal
        // answer-synthesis prompt - a different key, different template id, untouched by this
        // migration.
        assertThat(promptTemplateRepository.findByPromptKey("PAYMENTX_KNOWLEDGE_ASSISTANT")).isPresent();
        PromptTemplate ragInternal = promptTemplateRepository.findByPromptKey("PAYMENTX_KNOWLEDGE_ASSISTANT").orElseThrow();
        assertThat(ragInternal.getId()).isNotEqualTo(TEMPLATE_ID);
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
        assertThat(v1.getContent())
                .contains("{{availableTools}}", "{{executionHistory}}", "{{userQuery}}", "{{iteration}}", "{{maxIterations}}");
        for (var variable : v1.getVariables()) {
            assertThat(variable.required()).isTrue();
        }
    }

    @Test
    void neverGrantsToolPermissionsOrBypassesPolicy() {
        PromptVersion v1 = promptVersionRepository.findByPromptTemplateIdAndVersionNumber(TEMPLATE_ID, 1).orElseThrow();
        String content = v1.getContent();

        assertThat(content).contains("You may only call a tool whose exact name appears in AVAILABLE TOOLS below");
        assertThat(content).contains("never request a financial write operation");
        assertThat(content).contains("Your allowed tools are fixed by the agent's own configuration, not by this prompt");
        assertThat(content.toLowerCase()).doesNotContain("grant yourself", "bypass", "override the tool policy");
    }

    @Test
    void enforcesTheThreeRealPaymentSchemes_andExplicitlyExcludesOthersByName() {
        PromptVersion v1 = promptVersionRepository.findByPromptTemplateIdAndVersionNumber(TEMPLATE_ID, 1).orElseThrow();
        String content = v1.getContent();

        assertThat(content).contains("INSTANT_PAYMENT", "REAL_TIME_PAYMENT", "CARD_PAYMENT");
        assertThat(content).contains("exactly three payment schemes");
        // The forbidden scheme names DO appear - inside the explicit negation rule itself ("Never
        // state or imply that any other scheme (ACH, FedNow, ...) is part of PaymentX"), the same
        // guardrail pattern docs/ai/error-analyzer/payment-schemes.md already uses. What matters is
        // that they only ever appear as part of that negation, never as a claimed real scheme.
        assertThat(content).contains("Never state or imply that any other scheme");
        assertThat(content).containsOnlyOnce("ACH");
        assertThat(content).containsOnlyOnce("FedNow");
    }

    @Test
    void enforcesRagFirstBehavior_andEvidenceSeparation() {
        PromptVersion v1 = promptVersionRepository.findByPromptTemplateIdAndVersionNumber(TEMPLATE_ID, 1).orElseThrow();
        String content = v1.getContent();

        assertThat(content).contains("primarily a KNOWLEDGE assistant");
        assertThat(content).contains("RAG KNOWLEDGE versus what came from RUNTIME MCP EVIDENCE");
        assertThat(content).contains("RAG knowledge is documentation, never a substitute for it");
    }

    @Test
    void countByTemplate_isExactlyOneVersion() {
        assertThat(promptVersionRepository.countByPromptTemplateId(TEMPLATE_ID)).isEqualTo(1);
    }
}
