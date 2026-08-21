package com.paymentx.reporting.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * ====================================================================
 * ENGLISH: Enables createdAt/updatedAt/createdBy/updatedBy
 * auto-population on AuditableEntity subclasses. dateTimeProvider is
 * REQUIRED (not optional) because these fields are OffsetDateTime,
 * while Spring Data's default provider produces LocalDateTime - every
 * save() would fail without this bean, matching every other service's
 * identical fix.
 *
 * HINGLISH: AuditableEntity subclasses pe createdAt/updatedAt/
 * createdBy/updatedBy auto-populate karta hai. dateTimeProvider
 * REQUIRED hai (optional nahi) kyunki ye fields OffsetDateTime hain,
 * jabki Spring Data ka default provider LocalDateTime deta hai - is
 * bean ke bina har save() fail ho jaata, baaki har service ke identical
 * fix jaisa.
 * ====================================================================
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
