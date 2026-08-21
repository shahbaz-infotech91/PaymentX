package com.paymentx.embedding;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * English:
 * The Spring Boot entry point for Embedding Service - the third real AI
 * Platform backend service (Phase 3.4, following Prompt Service in
 * Phase 3.2 and LLM Service in Phase 3.3 - see
 * PAYMENTX_PHASE_3_ARCHITECTURE.md §16/§19's implementation order).
 * scanBasePackages matches every other PaymentX module's exact pattern
 * (includes com.paymentx.common so paymentx-common-library's shared
 * beans are picked up). @ConfigurationPropertiesScan binds
 * EmbeddingProperties (embedding.*) the same way LlmServiceApplication
 * binds LlmProperties.
 * Why it exists: every PaymentX Spring Boot module needs one; this one
 * marks Phase 3.4's build-out per the Phase 3.0 architecture's
 * implementation order.
 * How it communicates with other components: boots the web server on
 * port 8094 (see application.yml); deliberately no
 * @EnableJpaAuditing/@EnableJpaRepositories - this service owns no
 * database, matching LLM Service (Step 23: stay stateless).
 *
 * Hinglish:
 * Embedding Service ka Spring Boot entry point - teesra real AI
 * Platform backend service (Phase 3.4, Phase 3.2 me Prompt Service aur
 * Phase 3.3 me LLM Service ke baad - PAYMENTX_PHASE_3_ARCHITECTURE.md
 * §16/§19 ka implementation order dekho). scanBasePackages har doosre
 * PaymentX module ke exact pattern se match karta hai (com.paymentx.common
 * shamil hai taaki paymentx-common-library ke shared beans pick up ho
 * sakein). @ConfigurationPropertiesScan EmbeddingProperties
 * (embedding.*) ko usi tarah bind karta hai jaise LlmServiceApplication
 * LlmProperties bind karta hai.
 * Ye kyu hai: har PaymentX Spring Boot module ko ek chahiye; ye Phase
 * 3.4 ke build-out ko Phase 3.0 architecture ke implementation order ke
 * hisaab se mark karta hai.
 * Dusre components se kaise communicate karta hai: port 8094 par web
 * server boot karta hai (application.yml dekho); jaan-boojh kar koi
 * @EnableJpaAuditing/@EnableJpaRepositories nahi - is service ka koi
 * database nahi hai, LLM Service ki tarah (Step 23: stateless raho).
 */
@SpringBootApplication(scanBasePackages = {"com.paymentx.embedding", "com.paymentx.common"})
@ConfigurationPropertiesScan
public class EmbeddingServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(EmbeddingServiceApplication.class, args);
    }
}
