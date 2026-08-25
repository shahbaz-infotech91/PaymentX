package com.paymentx.reconciliation.repository;

import com.paymentx.reconciliation.config.JpaAuditingConfig;
import com.paymentx.reconciliation.entity.BatchStatus;
import com.paymentx.reconciliation.entity.BatchType;
import com.paymentx.reconciliation.entity.ReconciliationBatch;
import com.paymentx.reconciliation.entity.ReconciliationRecord;
import com.paymentx.reconciliation.entity.ReconciliationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 4.6.0 - proves the real paymentReference -> batchId bridge
 * (findByReferenceIdOrderByCreatedAtDesc) against real Postgres, not a mock repository. Mirrors
 * ReconciliationBatchRepositoryTest's own Testcontainers pattern.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class ReconciliationRecordRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("paymentx_reconciliation_record_repo_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private ReconciliationRecordRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findByReferenceIdOrderByCreatedAtDesc_realRow_isFoundByReference() {
        UUID batchId = persistBatch();
        ReconciliationRecord record = repository.saveAndFlush(recordFor("PMT-BRIDGE-1", batchId, ReconciliationStatus.MATCHED));

        List<ReconciliationRecord> found = repository.findByReferenceIdOrderByCreatedAtDesc("PMT-BRIDGE-1");

        assertThat(found).hasSize(1);
        assertThat(found.get(0).getId()).isEqualTo(record.getId());
        assertThat(found.get(0).getReconciliationStatus()).isEqualTo(ReconciliationStatus.MATCHED);
    }

    @Test
    void findByReferenceIdOrderByCreatedAtDesc_unknownReference_returnsEmptyNotNull() {
        List<ReconciliationRecord> found = repository.findByReferenceIdOrderByCreatedAtDesc("PMT-NEVER-RECONCILED");

        assertThat(found).isNotNull().isEmpty();
    }

    @Test
    void findByReferenceIdOrderByCreatedAtDesc_multipleBatches_mostRecentFirst() throws InterruptedException {
        ReconciliationRecord older = repository.saveAndFlush(recordFor("PMT-BRIDGE-2", persistBatch(), ReconciliationStatus.AMOUNT_MISMATCH));
        // Real, distinct createdAt values - JpaAuditingConfig stamps createdAt at save time.
        Thread.sleep(5);
        ReconciliationRecord newer = repository.saveAndFlush(recordFor("PMT-BRIDGE-2", persistBatch(), ReconciliationStatus.MATCHED));

        List<ReconciliationRecord> found = repository.findByReferenceIdOrderByCreatedAtDesc("PMT-BRIDGE-2");

        assertThat(found).hasSize(2);
        assertThat(found.get(0).getId()).isEqualTo(newer.getId());
        assertThat(found.get(1).getId()).isEqualTo(older.getId());
    }

    private UUID persistBatch() {
        return entityManager.persistFlushFind(ReconciliationBatch.builder()
                        .batchType(BatchType.FULL)
                        .status(BatchStatus.COMPLETED)
                        .totalRecords(0)
                        .matchedCount(0)
                        .mismatchCount(0)
                        .build())
                .getId();
    }

    private ReconciliationRecord recordFor(String referenceId, UUID batchId, ReconciliationStatus status) {
        return ReconciliationRecord.builder()
                .batchId(batchId)
                .paymentId(UUID.randomUUID().toString())
                .referenceId(referenceId)
                .participantId("BANK001")
                .internalSettlementDate(OffsetDateTime.now())
                .reconciliationStatus(status)
                .build();
    }
}
