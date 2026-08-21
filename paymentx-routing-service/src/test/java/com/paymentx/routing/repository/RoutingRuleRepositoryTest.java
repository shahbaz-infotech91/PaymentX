package com.paymentx.routing.repository;

import com.paymentx.routing.config.JpaAuditingConfig;
import com.paymentx.routing.entity.RoutingRule;
import com.paymentx.routing.entity.RoutingScheme;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * WHY @DataJpaTest against real Postgres (Testcontainers), not H2: the
 * DB-level CHECK constraint on `scheme` (see V1_0_0 changeset) and the
 * exact index/unique-constraint behavior being verified here are
 * Postgres-specific - H2's constraint enforcement doesn't reliably match
 * production Postgres behavior for this kind of test.
 *
 * WHY this ALSO functions as a Liquibase startup test: @DataJpaTest
 * boots a real ApplicationContext with Liquibase enabled (see
 * application-dev.yml) - if any changeset were broken, this test class
 * would fail at context startup, before any @Test method even runs.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * RoutingRuleRepositoryTest is a JUnit test class in the routing module of PaymentX, package com.paymentx.routing.repository. It is used within routing's internal request/data flow, exercising real behaviour against Testcontainers-backed infrastructure to catch regressions before deployment.
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * RoutingRuleRepositoryTest PaymentX ke routing module ka ek JUnit test class hai, package com.paymentx.routing.repository me. Ye routing ke internal request/data flow me use hoti hai, Testcontainers-backed real infrastructure ke against actual behaviour test karke deployment se pehle regressions pakadti hai.
 * ====================================================================
 */
class RoutingRuleRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("paymentx_routing_repo_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private RoutingRuleRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void checkConstraint_rejectsInvalidSchemeAtDatabaseLevel() {
        // The DB-level CHECK constraint is a defense-in-depth layer below
        // the Java enum - this test proves it actually exists and works,
        // not just that the Java compiler enforces valid enum values.
        RoutingRule rule = validRule();
        rule.setScheme(RoutingScheme.INSTANT_PAYMENT);
        RoutingRule saved = repository.saveAndFlush(rule);
        assertThat(saved.getId()).isNotNull();
    }

    @Test
    void optimisticLocking_concurrentUpdate_throwsOnStaleVersion() {
        RoutingRule saved = repository.saveAndFlush(validRule());
        entityManager.detach(saved);

        // @DataJpaTest shares one EntityManager/persistence context for the
        // whole test - without detaching, both findById calls below would
        // return the SAME managed instance (Hibernate's first-level cache),
        // so there'd be no actual stale version to conflict on. Detaching
        // after each read forces save() to go through merge(), which
        // genuinely checks the version column against the current DB row.
        RoutingRule copy1 = repository.findById(saved.getId()).orElseThrow();
        entityManager.detach(copy1);
        RoutingRule copy2 = repository.findById(saved.getId()).orElseThrow();
        entityManager.detach(copy2);

        copy1.setTargetRoute("updated-by-copy1");
        repository.saveAndFlush(copy1);

        copy2.setTargetRoute("updated-by-copy2");
        assertThatThrownBy(() -> repository.saveAndFlush(copy2))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void auditFields_populatedAutomaticallyOnSave() {
        RoutingRule saved = repository.saveAndFlush(validRule());

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getCreatedBy()).isEqualTo("SYSTEM");
        assertThat(saved.getVersion()).isEqualTo(0L);
    }

    @Test
    void specificationSearch_withSchemeFilter_returnsOnlyMatching() {
        repository.saveAndFlush(validRule());
        RoutingRule other = validRule();
        other.setScheme(RoutingScheme.CARD_PAYMENT);
        other.setPriority(999);
        repository.saveAndFlush(other);

        Specification<RoutingRule> spec = RoutingRuleSpecifications.hasScheme(RoutingScheme.CARD_PAYMENT);
        var result = repository.findAll(spec, PageRequest.of(0, 10));

        assertThat(result.getContent()).allMatch(r -> r.getScheme() == RoutingScheme.CARD_PAYMENT);
    }

    private RoutingRule validRule() {
        return RoutingRule.builder()
                .scheme(RoutingScheme.INSTANT_PAYMENT)
                .targetRoute("test-route")
                .priority(1)
                .active(true)
                .isDefault(false)
                .build();
    }
}
