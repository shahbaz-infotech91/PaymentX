package com.paymentx.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Matches Routing/Audit/Notification/Reconciliation/Reporting Service's
 * identical JpaAuditingConfig exactly - same "SYSTEM" interim value
 * AuditableEntity's own javadoc already documents as "pending real
 * identity propagation from Auth Service." This module IS that identity
 * propagation now existing - but createdBy/updatedBy on AuthUser rows
 * themselves still means "the system created this row" (e.g. via
 * Liquibase seed data or an admin API not yet built), not "a JWT subject
 * authenticated this write" - a different, unrelated concept from the
 * JWT issuance this module performs.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider", dateTimeProviderRef = "dateTimeProvider")
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> Optional.of("SYSTEM");
    }

    @Bean
    public DateTimeProvider dateTimeProvider() {
        return () -> Optional.of(OffsetDateTime.now());
    }
}
