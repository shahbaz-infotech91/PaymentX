package com.paymentx.llm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * English:
 * The Spring Boot entry point for LLM Service - the second real AI
 * Platform backend service (Phase 3.3, following Prompt Service in
 * Phase 3.2 - see PAYMENTX_PHASE_3_ARCHITECTURE.md §16/§19's
 * implementation order). scanBasePackages matches every other
 * PaymentX module's exact pattern (includes com.paymentx.common so
 * paymentx-common-library's shared beans are picked up).
 * @ConfigurationPropertiesScan binds LlmProperties (llm.*) the same way
 * Control Center's ControlCenterApplication binds ControlCenterProperties
 * - no @EnableConfigurationProperties boilerplate needed.
 * Why it exists: every PaymentX Spring Boot module needs one; this one
 * marks Phase 3.3's build-out per the Phase 3.0 architecture's
 * implementation order.
 * How it communicates with other components: boots the web server on
 * port 8093 (see application.yml); deliberately no
 * @EnableJpaAuditing/@EnableJpaRepositories - this service owns no
 * database, unlike Prompt Service (Step 24: stay stateless).
 *
 * Hinglish:
 * LLM Service ka Spring Boot entry point - dusra real AI Platform
 * backend service (Phase 3.3, Phase 3.2 me Prompt Service ke baad -
 * PAYMENTX_PHASE_3_ARCHITECTURE.md §16/§19 ka implementation order
 * dekho). scanBasePackages har doosre PaymentX module ke exact pattern
 * se match karta hai (com.paymentx.common shamil hai taaki paymentx-
 * common-library ke shared beans pick up ho sakein).
 * @ConfigurationPropertiesScan LlmProperties (llm.*) ko usi tarah bind
 * karta hai jaise Control Center ka ControlCenterApplication
 * ControlCenterProperties bind karta hai - koi
 * @EnableConfigurationProperties boilerplate nahi chahiye.
 * Ye kyu hai: har PaymentX Spring Boot module ko ek chahiye; ye Phase
 * 3.3 ke build-out ko Phase 3.0 architecture ke implementation order ke
 * hisaab se mark karta hai.
 * Dusre components se kaise communicate karta hai: port 8093 par web
 * server boot karta hai (application.yml dekho); jaan-boojh kar koi
 * @EnableJpaAuditing/@EnableJpaRepositories nahi - is service ka koi
 * database nahi hai, Prompt Service ke ulat (Step 24: stateless raho).
 */
@SpringBootApplication(scanBasePackages = {"com.paymentx.llm", "com.paymentx.common"})
@ConfigurationPropertiesScan
public class LlmServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(LlmServiceApplication.class, args);
    }
}
