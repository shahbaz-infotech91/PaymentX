package com.paymentx.audit.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Matches Payment/Routing Service's JpaAuditingConfig exactly -
 * dateTimeProvider is REQUIRED (not optional): AuditableEntity's
 * createdAt/updatedAt are OffsetDateTime, but Spring Data's default
 * DateTimeProvider produces LocalDateTime, which fails to convert.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider", dateTimeProviderRef = "dateTimeProvider")
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * JpaAuditingConfig is a configuration class in the audit module of PaymentX. It lives in package com.paymentx.audit.config and participates in audit's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through audit's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * JpaAuditingConfig PaymentX ke audit module ka ek configuration class hai. Ye com.paymentx.audit.config package me hai aur audit ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise audit ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
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
