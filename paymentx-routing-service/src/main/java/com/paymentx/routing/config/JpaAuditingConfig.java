package com.paymentx.routing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Matches Payment Service's JpaAuditingConfig exactly - see that class's
 * javadoc for the full rationale, particularly on why dateTimeProvider
 * is REQUIRED (not optional) here: AuditableEntity's createdAt/updatedAt
 * are OffsetDateTime, but Spring Data's default DateTimeProvider
 * produces LocalDateTime, which fails to convert - every save() on a
 * RoutingRule would fail without this bean.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider", dateTimeProviderRef = "dateTimeProvider")
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * JpaAuditingConfig is a configuration class in the routing module of PaymentX. It lives in package com.paymentx.routing.config and participates in routing's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through routing's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * JpaAuditingConfig PaymentX ke routing module ka ek configuration class hai. Ye com.paymentx.routing.config package me hai aur routing ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise routing ke REST API ya Kafka events ke through indirectly use karti hain).
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
