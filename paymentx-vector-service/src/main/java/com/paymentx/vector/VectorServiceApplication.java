package com.paymentx.vector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * English:
 * The Spring Boot entry point for Vector Service - the fourth real AI
 * Platform backend service (Phase 3.5, following Prompt Service (3.2),
 * LLM Service (3.3), Embedding Service (3.4)). scanBasePackages matches
 * every other PaymentX module's exact pattern (includes
 * com.paymentx.common so paymentx-common-library's shared beans are
 * picked up). @ConfigurationPropertiesScan binds VectorProperties
 * (vector.*) the same way LlmServiceApplication/
 * EmbeddingServiceApplication bind their own properties. Unlike LLM/
 * Embedding Service, this module DOES need JPA/Liquibase auto-
 * configuration (it owns real tables) - no extra annotation is required
 * for that beyond spring-boot-starter-data-jpa being on the classpath;
 * @EnableJpaAuditing lives on JpaAuditingConfig, not here, matching
 * Prompt Service's exact split.
 * Why it exists: every PaymentX Spring Boot module needs one; this one
 * marks Phase 3.5's build-out per the Phase 3.0 architecture's
 * implementation order.
 * How it communicates with other components: boots the web server on
 * port 8095 (see application.yml) against the existing shared
 * `paymentx_ai` Postgres database (see application-dev.yml) - the same
 * database Prompt Service already uses, not a new one.
 *
 * Hinglish:
 * Vector Service ka Spring Boot entry point - chautha real AI Platform
 * backend service (Phase 3.5, Prompt Service (3.2), LLM Service (3.3),
 * Embedding Service (3.4) ke baad). scanBasePackages har doosre
 * PaymentX module ke exact pattern se match karta hai
 * (com.paymentx.common shamil hai taaki paymentx-common-library ke
 * shared beans pick up ho sakein). @ConfigurationPropertiesScan
 * VectorProperties (vector.*) ko usi tarah bind karta hai jaise
 * LlmServiceApplication/EmbeddingServiceApplication apni properties
 * bind karte hain. LLM/Embedding Service ke ulat, is module ko real JPA/
 * Liquibase auto-configuration chahiye (real tables ka malik hai) - iske
 * liye spring-boot-starter-data-jpa classpath par hone ke alawa koi
 * extra annotation nahi chahiye; @EnableJpaAuditing JpaAuditingConfig
 * par rehta hai, yahan nahi, Prompt Service ke exact split se match
 * karte hue.
 * Ye kyu hai: har PaymentX Spring Boot module ko ek chahiye; ye Phase
 * 3.5 ke build-out ko Phase 3.0 architecture ke implementation order ke
 * hisaab se mark karta hai.
 * Dusre components se kaise communicate karta hai: port 8095 par web
 * server boot karta hai (application.yml dekho) existing shared
 * `paymentx_ai` Postgres database ke against (application-dev.yml
 * dekho) - wahi database jo Prompt Service already use karti hai, ek
 * nayi nahi.
 */
@SpringBootApplication(scanBasePackages = {"com.paymentx.vector", "com.paymentx.common"})
@ConfigurationPropertiesScan
public class VectorServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(VectorServiceApplication.class, args);
    }
}
