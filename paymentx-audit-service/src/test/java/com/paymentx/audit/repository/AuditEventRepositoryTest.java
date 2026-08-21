package com.paymentx.audit.repository;

import com.paymentx.audit.config.JpaAuditingConfig;
import com.paymentx.audit.entity.AuditEvent;
import com.paymentx.audit.entity.AuditMetadata;
import com.paymentx.audit.entity.EventStatus;
import com.paymentx.audit.entity.EventType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @DataJpaTest against real Postgres (Testcontainers) - if any Liquibase
 * changeset were broken, context startup would fail before any @Test
 * method runs, making this class function as a Liquibase startup test
 * too (same rationale as Routing Service's RoutingRuleRepositoryTest).
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * AuditEventRepositoryTest is a JUnit test class in the audit module of PaymentX, package com.paymentx.audit.repository. It is used within audit's internal request/data flow, exercising real behaviour against Testcontainers-backed infrastructure to catch regressions before deployment.
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * AuditEventRepositoryTest PaymentX ke audit module ka ek JUnit test class hai, package com.paymentx.audit.repository me. Ye audit ke internal request/data flow me use hoti hai, Testcontainers-backed real infrastructure ke against actual behaviour test karke deployment se pehle regressions pakadti hai.
 * ====================================================================
 */
class AuditEventRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("paymentx_audit_repo_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private AuditEventRepository repository;

    @Test
    void uniqueConstraint_rejectsDuplicateSourceEventId() {
        repository.saveAndFlush(validEvent("evt-dup"));

        assertThatThrownBy(() -> repository.saveAndFlush(validEvent("evt-dup")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void nullSourceEventId_doesNotCollideAcrossMultipleRows() {
        AuditEvent first = validEvent(null);
        AuditEvent second = validEvent(null);

        assertThat(repository.saveAndFlush(first)).isNotNull();
        assertThat(repository.saveAndFlush(second)).isNotNull();
    }

    @Test
    void existsBySourceEventId_returnsTrueOnlyAfterInsert() {
        assertThat(repository.existsBySourceEventId("evt-check")).isFalse();
        repository.saveAndFlush(validEvent("evt-check"));
        assertThat(repository.existsBySourceEventId("evt-check")).isTrue();
    }

    @Test
    void jsonbPayload_persistsAndReloadsExactly() {
        AuditEvent event = validEvent("evt-json");
        event.setPayload("{\"amount\":100.50,\"currency\":\"USD\"}");
        AuditEvent saved = repository.saveAndFlush(event);

        AuditEvent reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getPayload()).contains("100.50").contains("USD");
    }

    private AuditEvent validEvent(String sourceEventId) {
        return AuditEvent.builder()
                .eventType(EventType.SYSTEM_EVENT)
                .eventStatus(EventStatus.RECORDED)
                .sourceService("audit-service")
                .sourceEventId(sourceEventId)
                .metadata(new AuditMetadata(null, "SYSTEM", null, null, null, null, null))
                .payload("{}")
                .occurredAt(OffsetDateTime.now())
                .build();
    }
}
