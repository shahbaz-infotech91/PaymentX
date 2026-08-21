package com.paymentx.prompt.config;

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
 * dateTimeProvider matches Routing Service's/Payment Service's
 * JpaAuditingConfig exactly (REQUIRED, not optional: AuditableEntity's
 * createdAt/updatedAt are OffsetDateTime, Spring Data's default
 * DateTimeProvider produces LocalDateTime, which fails to convert -
 * every save() would fail without this bean).
 *
 * <p>auditorProvider() is a deliberate, small improvement over simply
 * hardcoding "SYSTEM" everywhere: HeaderRoleAuthenticationFilter
 * already sets the request's Authentication principal to the real
 * X-Participant-Id header value (when present) before any controller
 * method runs - reading that same principal here means createdBy/
 * updatedBy on prompt_template/prompt_version genuinely identify which
 * caller created or activated a prompt version whenever that header is
 * present, which is real, low-cost value for the auditability Step 2/16
 * of the Phase 3.2 brief asks for, not just a placeholder string.
 * Falls back to "SYSTEM" exactly like Routing Service's own
 * auditorProvider when no participant identity is available (an
 * unauthenticated/internal call, or before Auth Service exists for
 * real - see PAYMENTX_PHASE_3_ARCHITECTURE.md §1's finding that no
 * service re-validates a JWT today, this filter only ever sets what
 * the Gateway already vouched for).
 * Why it exists: Step 5's "createdBy/updatedBy" columns need a real
 * bean to populate them at all (no @CreatedBy/@LastModifiedBy value
 * is written without one - AuditableEntity's fields would stay null
 * and every insert would fail its NOT NULL constraint).
 * How it communicates with other components: consumed by Spring Data
 * JPA's AuditingEntityListener (see AuditableEntity in
 * paymentx-common-library) on every PromptTemplate/PromptVersion
 * save().
 *
 * Hinglish:
 * dateTimeProvider Routing Service/Payment Service ke JpaAuditingConfig
 * se exactly match karta hai (REQUIRED hai, optional nahi:
 * AuditableEntity ke createdAt/updatedAt OffsetDateTime hain, Spring
 * Data ka default DateTimeProvider LocalDateTime produce karta hai, jo
 * convert nahi hota - is bean ke bina har save() fail ho jaata).
 *
 * <p>auditorProvider() har jagah "SYSTEM" hardcode karne se ek
 * jaan-boojh kar chhota improvement hai: HeaderRoleAuthenticationFilter
 * already request ke Authentication principal ko real X-Participant-Id
 * header value set kar deta hai (jab present ho) kisi bhi controller
 * method chalne se pehle - yahan wahi principal padhna matlab
 * createdBy/updatedBy on prompt_template/prompt_version genuinely
 * identify karte hain ki kaun sa caller ne ek prompt version create ya
 * activate kiya, jab bhi wo header present ho, jo Phase 3.2 brief ke
 * Step 2/16 ki auditability ke liye real, low-cost value hai, sirf ek
 * placeholder string nahi. Jab koi participant identity available na
 * ho (ek unauthenticated/internal call, ya Auth Service ke real hone se
 * pehle - PAYMENTX_PHASE_3_ARCHITECTURE.md §1 ki finding dekho ki aaj
 * koi service JWT dobara validate nahi karti, ye filter sirf wahi set
 * karta hai jiski Gateway already vouch kar chuka hai) exactly Routing
 * Service ke apne auditorProvider ki tarah "SYSTEM" par fallback hota
 * hai.
 * Ye kyu hai: Step 5 ke "createdBy/updatedBy" columns ko unhe populate
 * karne ke liye bilkul ek real bean chahiye (iske bina koi
 * @CreatedBy/@LastModifiedBy value likhi hi nahi jaati - AuditableEntity
 * ke fields null reh jaate aur har insert apni NOT NULL constraint fail
 * kar deta).
 * Dusre components se kaise communicate karta hai: har PromptTemplate/
 * PromptVersion save() par Spring Data JPA ke AuditingEntityListener
 * (paymentx-common-library me AuditableEntity dekho) dwara consume
 * hota hai.
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
