package com.paymentx.prompt;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * English:
 * The Spring Boot entry point for Prompt Service - the first real AI
 * Platform backend service (Phase 3.2; AI Chat Interface in Phase 3.1
 * was a contract stub inside paymentx-control-center/backend, not its
 * own service - see PAYMENTX_PHASE_3_ARCHITECTURE.md §16/§19).
 * scanBasePackages matches Routing Service's exact pattern (includes
 * com.paymentx.common so paymentx-common-library's shared beans are
 * picked up).
 * Why it exists: every PaymentX Spring Boot module needs one; this one
 * marks the real start of Phase 3's build-out, per the implementation
 * order PAYMENTX_PHASE_3_ARCHITECTURE.md §19 lays out.
 * How it communicates with other components: boots the web server on
 * port 8092 (see application.yml); does not call an LLM, does not call
 * any other PaymentX service - PromptController is the only inbound
 * surface.
 *
 * Hinglish:
 * Prompt Service ka Spring Boot entry point - pehla real AI Platform
 * backend service (Phase 3.2; Phase 3.1 me AI Chat Interface ek
 * contract stub tha paymentx-control-center/backend ke andar, apni
 * alag service nahi - PAYMENTX_PHASE_3_ARCHITECTURE.md §16/§19 dekho).
 * scanBasePackages Routing Service ke exact pattern se match karta hai
 * (com.paymentx.common shamil hai taaki paymentx-common-library ke
 * shared beans pick up ho sakein).
 * Ye kyu hai: har PaymentX Spring Boot module ko ek chahiye; ye Phase
 * 3 ke real build-out ki shuruaat mark karta hai,
 * PAYMENTX_PHASE_3_ARCHITECTURE.md §19 ke implementation order ke
 * hisaab se.
 * Dusre components se kaise communicate karta hai: port 8092 par web
 * server boot karta hai (application.yml dekho); koi LLM call nahi
 * karta, koi doosri PaymentX service call nahi karta - PromptController
 * hi ek hi inbound surface hai.
 */
@SpringBootApplication(scanBasePackages = {"com.paymentx.prompt", "com.paymentx.common"})
public class PromptServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PromptServiceApplication.class, args);
    }
}
