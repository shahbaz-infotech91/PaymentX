package com.paymentx.prompt.repository;

import com.paymentx.prompt.config.JpaAuditingConfig;
import com.paymentx.prompt.entity.PromptStatus;
import com.paymentx.prompt.entity.PromptTemplate;
import com.paymentx.prompt.entity.PromptType;
import com.paymentx.prompt.entity.PromptVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * English:
 * WHY @DataJpaTest against real Postgres (Testcontainers), not H2:
 * matches RoutingRuleRepositoryTest's own rationale exactly - the
 * partial unique index (WHERE status = 'ACTIVE', see
 * V1_0_0__create_prompt_tables.yaml) and the native jsonb column
 * mapping are genuinely Postgres-specific, H2 does not reliably
 * emulate either. WHY this also functions as a Liquibase startup
 * test: @DataJpaTest boots a real ApplicationContext with Liquibase
 * enabled - if V1_0_0/V1_0_1 were malformed YAML or invalid SQL, this
 * test class fails at context startup, before any @Test method runs
 * at all, exactly the same safety net RoutingRuleRepositoryTest
 * already relies on.
 * Why it exists: Step 20/21/25 of the Phase 3.2 brief - proving the
 * database, not just application code, prevents two ACTIVE versions
 * for the same prompt.
 * How it communicates with other components: a real, ephemeral
 * Postgres 16 container per test class run; the same
 * db.changelog-master.yaml this service's real startup uses.
 *
 * Hinglish:
 * Real Postgres (Testcontainers) ke against @DataJpaTest KYU, H2 nahi:
 * RoutingRuleRepositoryTest ke apne rationale se exactly match karta
 * hai - partial unique index (WHERE status = 'ACTIVE',
 * V1_0_0__create_prompt_tables.yaml dekho) aur native jsonb column
 * mapping genuinely Postgres-specific hain, H2 dono ko reliably
 * emulate nahi karta. Ye Liquibase startup test ka kaam bhi KYU karta
 * hai: @DataJpaTest ek real ApplicationContext boot karta hai Liquibase
 * enabled ke saath - agar V1_0_0/V1_0_1 malformed YAML ya invalid SQL
 * hote, ye test class context startup par hi fail ho jaata, kisi bhi
 * @Test method chalne se pehle, exactly wahi safety net jis par
 * RoutingRuleRepositoryTest already rely karta hai.
 * Ye kyu hai: Phase 3.2 brief ka Step 20/21/25 - ye prove karna ki
 * database, sirf application code nahi, ek hi prompt ke liye do ACTIVE
 * versions ko rokta hai.
 * Dusre components se kaise communicate karta hai: har test class run
 * ke liye ek real, ephemeral Postgres 16 container; wahi
 * db.changelog-master.yaml jo is service ka real startup use karta
 * hai.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class PromptVersionRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("paymentx_ai_repo_test")
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
    @Autowired
    private TestEntityManager entityManager;

    private PromptTemplate persistTemplate(String key) {
        PromptTemplate template = PromptTemplate.builder().promptKey(key).name(key).type(PromptType.SYSTEM).build();
        return promptTemplateRepository.saveAndFlush(template);
    }

    @Test
    void onlyOneActiveVersionAllowedPerTemplate_databaseRejectsTheSecond() {
        PromptTemplate template = persistTemplate("CONSTRAINT_TEST_ONE");

        PromptVersion v1 = PromptVersion.builder().promptTemplateId(template.getId()).versionNumber(1)
                .content("v1").status(PromptStatus.ACTIVE).variables(List.of()).build();
        promptVersionRepository.saveAndFlush(v1);

        PromptVersion v2 = PromptVersion.builder().promptTemplateId(template.getId()).versionNumber(2)
                .content("v2").status(PromptStatus.ACTIVE).variables(List.of()).build();

        assertThatThrownBy(() -> promptVersionRepository.saveAndFlush(v2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void differentTemplatesCanEachHaveTheirOwnActiveVersion() {
        PromptTemplate templateA = persistTemplate("CONSTRAINT_TEST_A");
        PromptTemplate templateB = persistTemplate("CONSTRAINT_TEST_B");

        PromptVersion activeA = PromptVersion.builder().promptTemplateId(templateA.getId()).versionNumber(1)
                .content("a").status(PromptStatus.ACTIVE).variables(List.of()).build();
        PromptVersion activeB = PromptVersion.builder().promptTemplateId(templateB.getId()).versionNumber(1)
                .content("b").status(PromptStatus.ACTIVE).variables(List.of()).build();

        promptVersionRepository.saveAndFlush(activeA);
        promptVersionRepository.saveAndFlush(activeB);

        assertThat(promptVersionRepository.findByPromptTemplateIdAndStatus(templateA.getId(), PromptStatus.ACTIVE)).isPresent();
        assertThat(promptVersionRepository.findByPromptTemplateIdAndStatus(templateB.getId(), PromptStatus.ACTIVE)).isPresent();
    }

    @Test
    void reactivatingAfterDeactivation_isAllowedByTheIndex() {
        PromptTemplate template = persistTemplate("CONSTRAINT_TEST_REACTIVATE");

        PromptVersion v1 = PromptVersion.builder().promptTemplateId(template.getId()).versionNumber(1)
                .content("v1").status(PromptStatus.ACTIVE).variables(List.of()).build();
        promptVersionRepository.saveAndFlush(v1);

        v1.setStatus(PromptStatus.INACTIVE);
        promptVersionRepository.saveAndFlush(v1);

        PromptVersion v2 = PromptVersion.builder().promptTemplateId(template.getId()).versionNumber(2)
                .content("v2").status(PromptStatus.ACTIVE).variables(List.of()).build();

        // Should not throw - only one row currently has status='ACTIVE' at this point.
        promptVersionRepository.saveAndFlush(v2);

        assertThat(promptVersionRepository.findByPromptTemplateIdAndStatus(template.getId(), PromptStatus.ACTIVE))
                .get().extracting(PromptVersion::getVersionNumber).isEqualTo(2);
    }

    @Test
    void duplicateVersionNumberForSameTemplate_databaseRejectsIt() {
        PromptTemplate template = persistTemplate("CONSTRAINT_TEST_DUP_VERSION");

        PromptVersion v1 = PromptVersion.builder().promptTemplateId(template.getId()).versionNumber(1)
                .content("v1").status(PromptStatus.DRAFT).variables(List.of()).build();
        promptVersionRepository.saveAndFlush(v1);

        PromptVersion v1Again = PromptVersion.builder().promptTemplateId(template.getId()).versionNumber(1)
                .content("v1-again").status(PromptStatus.DRAFT).variables(List.of()).build();

        assertThatThrownBy(() -> promptVersionRepository.saveAndFlush(v1Again))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findMaxVersionNumber_reflectsRealPersistedRows() {
        PromptTemplate template = persistTemplate("CONSTRAINT_TEST_MAX_VERSION");
        assertThat(promptVersionRepository.findMaxVersionNumber(template.getId())).isZero();

        promptVersionRepository.saveAndFlush(PromptVersion.builder().promptTemplateId(template.getId()).versionNumber(1)
                .content("v1").status(PromptStatus.DRAFT).variables(List.of()).build());
        promptVersionRepository.saveAndFlush(PromptVersion.builder().promptTemplateId(template.getId()).versionNumber(2)
                .content("v2").status(PromptStatus.DRAFT).variables(List.of()).build());

        assertThat(promptVersionRepository.findMaxVersionNumber(template.getId())).isEqualTo(2);
    }

    @Test
    void variablesJsonColumn_roundTripsRealValues() {
        PromptTemplate template = persistTemplate("CONSTRAINT_TEST_JSON_VARS");
        PromptVersion version = PromptVersion.builder().promptTemplateId(template.getId()).versionNumber(1)
                .content("Hi {{name}}").status(PromptStatus.DRAFT)
                .variables(List.of(new com.paymentx.prompt.dto.PromptVariable("name", true, "the recipient's name")))
                .build();
        PromptVersion saved = promptVersionRepository.saveAndFlush(version);
        entityManager.clear();

        PromptVersion reloaded = promptVersionRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getVariables()).hasSize(1);
        assertThat(reloaded.getVariables().get(0).name()).isEqualTo("name");
        assertThat(reloaded.getVariables().get(0).required()).isTrue();
    }
}
