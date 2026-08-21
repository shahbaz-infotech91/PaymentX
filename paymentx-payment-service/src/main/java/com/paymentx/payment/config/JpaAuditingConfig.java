package com.paymentx.payment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * WHY @EnableJpaAuditing + AuditorAware instead of setting created_by/
 * updated_by manually in every service method: manual setting is exactly
 * the kind of repetitive, easy-to-forget boilerplate that eventually
 * produces a row with a null/wrong audit trail because one code path
 * missed it. Declaring it once here means EVERY entity extending our
 * Auditable mapped superclass gets correct audit columns automatically,
 * with no possibility of a call site forgetting to set them.
 *
 * WHY this AuditorAware returns the fixed string "SYSTEM" right now:
 * Payment Service currently has no authenticated caller identity to
 * attribute changes to - internal service-to-service calls are not yet
 * carrying a propagated identity (that arrives with Auth Service).
 * "SYSTEM" is an honest, correct value for "an automated process made
 * this change" in the interim - not a stub standing in for missing
 * behavior, but the CORRECT current answer given what identity
 * information actually exists in the system today. When Auth Service
 * introduces service-to-service identity propagation, this bean is the
 * single place that changes - it will read the identity from the
 * request's security context instead.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider", dateTimeProviderRef = "dateTimeProvider")
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * JpaAuditingConfig is a configuration class in the payment module of PaymentX. It lives in package com.paymentx.payment.config and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * JpaAuditingConfig PaymentX ke payment module ka ek configuration class hai. Ye com.paymentx.payment.config package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> Optional.of("SYSTEM");
    }

    /**
     * WHY needed: Auditable's createdAt/updatedAt are OffsetDateTime, but
     * Spring Data's default DateTimeProvider produces a LocalDateTime -
     * its converter doesn't support LocalDateTime -> OffsetDateTime, so
     * every save() on an Auditable entity fails. Returning OffsetDateTime
     * here matches the field type exactly, so no conversion is needed.
     */
    @Bean
    public DateTimeProvider dateTimeProvider() {
        return () -> Optional.of(OffsetDateTime.now());
    }
}
