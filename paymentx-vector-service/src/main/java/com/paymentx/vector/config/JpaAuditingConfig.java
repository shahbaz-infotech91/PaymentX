package com.paymentx.vector.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * English:
 * Matches Prompt Service's JpaAuditingConfig exactly (see that class's
 * javadoc for the full rationale on why dateTimeProvider is REQUIRED,
 * not optional, and why auditorProvider reads the real X-Participant-Id
 * principal HeaderRoleAuthenticationFilter already sets rather than
 * hardcoding "SYSTEM" everywhere).
 * Why it exists: AiDocument/AiDocumentChunk/AiDocumentEmbedding's
 * inherited createdBy/updatedBy/createdAt/updatedAt columns need a real
 * bean to populate them at all - without this, every save() fails its
 * NOT NULL constraint.
 * How it communicates with other components: consumed by Spring Data
 * JPA's AuditingEntityListener (see AuditableEntity in
 * paymentx-common-library) on every entity save() in this service.
 *
 * Hinglish:
 * Prompt Service ke JpaAuditingConfig se exactly match karta hai (poore
 * rationale ke liye us class ka javadoc dekho ki dateTimeProvider
 * REQUIRED kyun hai, optional nahi, aur auditorProvider real
 * X-Participant-Id principal kyun padhta hai jise
 * HeaderRoleAuthenticationFilter already set karta hai, har jagah
 * "SYSTEM" hardcode karne ke bajaye).
 * Ye kyu hai: AiDocument/AiDocumentChunk/AiDocumentEmbedding ke
 * inherited createdBy/updatedBy/createdAt/updatedAt columns ko unhe
 * populate karne ke liye bilkul ek real bean chahiye - iske bina, har
 * save() apni NOT NULL constraint fail kar deta.
 * Dusre components se kaise communicate karta hai: is service me har
 * entity save() par Spring Data JPA ke AuditingEntityListener
 * (paymentx-common-library me AuditableEntity dekho) dwara consume hota
 * hai.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider", dateTimeProviderRef = "dateTimeProvider")
public class JpaAuditingConfig {

    private static final String FALLBACK_AUDITOR = "SYSTEM";

    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getName() != null && !authentication.getName().isBlank()) {
                return Optional.of(authentication.getName());
            }
            return Optional.of(FALLBACK_AUDITOR);
        };
    }

    @Bean
    public DateTimeProvider dateTimeProvider() {
        return () -> Optional.of(OffsetDateTime.now());
    }
}
