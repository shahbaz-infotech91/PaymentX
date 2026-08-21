package com.paymentx.reconciliation.repository;

import com.paymentx.reconciliation.config.JpaAuditingConfig;
import com.paymentx.reconciliation.entity.BatchStatus;
import com.paymentx.reconciliation.entity.BatchType;
import com.paymentx.reconciliation.entity.ReconciliationBatch;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @DataJpaTest against real Postgres (Testcontainers) - if any of the 5
 * Liquibase changesets (or their foreign-key/check-constraint chain)
 * were broken, context startup would fail before any @Test method runs,
 * making this class function as the Liquibase startup test too.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * ReconciliationBatchRepositoryTest is a JUnit test class in the reconciliation module of PaymentX, package com.paymentx.reconciliation.repository. It is used within reconciliation's internal request/data flow, exercising real behaviour against Testcontainers-backed infrastructure to catch regressions before deployment.
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * ReconciliationBatchRepositoryTest PaymentX ke reconciliation module ka ek JUnit test class hai, package com.paymentx.reconciliation.repository me. Ye reconciliation ke internal request/data flow me use hoti hai, Testcontainers-backed real infrastructure ke against actual behaviour test karke deployment se pehle regressions pakadti hai.
 * ====================================================================
 */
class ReconciliationBatchRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("paymentx_reconciliation_repo_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private ReconciliationBatchRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void auditFields_populatedAutomaticallyOnSave() {
        ReconciliationBatch saved = repository.saveAndFlush(validBatch());

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getCreatedBy()).isEqualTo("SYSTEM");
        assertThat(saved.getVersion()).isEqualTo(0L);
    }

    @Test
    void optimisticLocking_concurrentUpdate_throwsOnStaleVersion() {
        ReconciliationBatch saved = repository.saveAndFlush(validBatch());
        entityManager.detach(saved);

        // @DataJpaTest shares one EntityManager/persistence context for the
        // whole test - without detaching, both findById calls below would
        // return the SAME managed instance (Hibernate's first-level cache),
        // so there'd be no actual stale version to conflict on. Detaching
        // after each read forces save() to go through merge(), which
        // genuinely checks the version column against the current DB row.
        ReconciliationBatch copy1 = repository.findById(saved.getId()).orElseThrow();
        entityManager.detach(copy1);
        ReconciliationBatch copy2 = repository.findById(saved.getId()).orElseThrow();
        entityManager.detach(copy2);

        copy1.setStatus(BatchStatus.RUNNING);
        repository.saveAndFlush(copy1);

        copy2.setStatus(BatchStatus.FAILED);
        assertThatThrownBy(() -> repository.saveAndFlush(copy2)).isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void checkConstraint_acceptsAllValidBatchTypes() {
        for (BatchType type : BatchType.values()) {
            ReconciliationBatch batch = validBatch();
            batch.setBatchType(type);
            assertThat(repository.saveAndFlush(batch).getId()).isNotNull();
        }
    }

    private ReconciliationBatch validBatch() {
        return ReconciliationBatch.builder()
                .batchType(BatchType.FULL)
                .status(BatchStatus.PENDING)
                .totalRecords(0)
                .matchedCount(0)
                .mismatchCount(0)
                .build();
    }
}
