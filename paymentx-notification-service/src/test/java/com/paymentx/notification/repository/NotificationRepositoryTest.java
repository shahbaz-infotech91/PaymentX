package com.paymentx.notification.repository;

import com.paymentx.notification.config.JpaAuditingConfig;
import com.paymentx.notification.entity.Notification;
import com.paymentx.notification.entity.NotificationChannel;
import com.paymentx.notification.entity.NotificationStatus;
import com.paymentx.notification.entity.SourceEventType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
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
 * too (same rationale as Routing/Audit Service's equivalent tests).
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * NotificationRepositoryTest is a JUnit test class in the notification module of PaymentX, package com.paymentx.notification.repository. It is used within notification's internal request/data flow, exercising real behaviour against Testcontainers-backed infrastructure to catch regressions before deployment.
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * NotificationRepositoryTest PaymentX ke notification module ka ek JUnit test class hai, package com.paymentx.notification.repository me. Ye notification ke internal request/data flow me use hoti hai, Testcontainers-backed real infrastructure ke against actual behaviour test karke deployment se pehle regressions pakadti hai.
 * ====================================================================
 */
class NotificationRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("paymentx_notification_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private NotificationRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void uniqueConstraint_rejectsDuplicateSourceEventId() {
        repository.saveAndFlush(validNotification("evt-dup"));

        assertThatThrownBy(() -> repository.saveAndFlush(validNotification("evt-dup")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void nullSourceEventId_doesNotCollideAcrossMultipleRows() {
        assertThat(repository.saveAndFlush(validNotification(null))).isNotNull();
        assertThat(repository.saveAndFlush(validNotification(null))).isNotNull();
    }

    @Test
    void optimisticLocking_concurrentUpdate_throwsOnStaleVersion() {
        Notification saved = repository.saveAndFlush(validNotification("evt-lock"));
        entityManager.detach(saved);

        // @DataJpaTest shares one EntityManager/persistence context for the
        // whole test - without detaching, both findById calls below would
        // return the SAME managed instance (Hibernate's first-level cache),
        // so there'd be no actual stale version to conflict on. Detaching
        // after each read forces save() to go through merge(), which
        // genuinely checks the version column against the current DB row.
        Notification copy1 = repository.findById(saved.getId()).orElseThrow();
        entityManager.detach(copy1);
        Notification copy2 = repository.findById(saved.getId()).orElseThrow();
        entityManager.detach(copy2);

        copy1.setStatus(NotificationStatus.SENT);
        repository.saveAndFlush(copy1);

        copy2.setStatus(NotificationStatus.FAILED);
        assertThatThrownBy(() -> repository.saveAndFlush(copy2)).isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void findByStatusAndNextRetryAtLessThanEqual_findsDueRetries() {
        Notification due = validNotification("evt-due");
        due.setStatus(NotificationStatus.RETRYING);
        due.setNextRetryAt(OffsetDateTime.now().minusMinutes(1));
        repository.saveAndFlush(due);

        var results = repository.findByStatusAndNextRetryAtLessThanEqual(NotificationStatus.RETRYING, OffsetDateTime.now());
        assertThat(results).extracting(Notification::getSourceEventId).contains("evt-due");
    }

    private Notification validNotification(String sourceEventId) {
        return Notification.builder()
                .sourceEventType(SourceEventType.PAYMENT_COMPLETED)
                .channel(NotificationChannel.EMAIL)
                .status(NotificationStatus.PENDING)
                .recipient("test@example.com")
                .sourceEventId(sourceEventId)
                .retryCount(0)
                .maxRetries(5)
                .build();
    }
}
