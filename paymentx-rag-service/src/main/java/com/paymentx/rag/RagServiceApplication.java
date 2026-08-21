package com.paymentx.rag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * English:
 * The Spring Boot entry point for RAG Service - the fifth real AI
 * Platform backend service (Phase 3.6, following Prompt Service (3.2),
 * LLM Service (3.3), Embedding Service (3.4), Vector Service (3.5)).
 * scanBasePackages matches every other PaymentX module's exact pattern.
 * @ConfigurationPropertiesScan binds RagProperties (rag.*) the same way
 * every other AI Platform service binds its own properties. Like LLM/
 * Embedding Service (and unlike Vector Service), this service owns NO
 * database - it is pure orchestration over four already-built services,
 * so there is deliberately no JPA/Liquibase configuration here at all.
 * Why it exists: every PaymentX Spring Boot module needs one; this one
 * marks Phase 3.6's build-out per the Phase 3.0 architecture's
 * implementation order.
 * How it communicates with other components: boots the web server on
 * port 8096 (see application.yml); calls Embedding Service (8094),
 * Vector Service (8095), Prompt Service (8092), and LLM Service (8093)
 * over REST - never a database, never a provider SDK directly.
 *
 * Hinglish:
 * RAG Service ka Spring Boot entry point - paanchvi real AI Platform
 * backend service (Phase 3.6, Prompt Service (3.2), LLM Service (3.3),
 * Embedding Service (3.4), Vector Service (3.5) ke baad).
 * scanBasePackages har doosre PaymentX module ke exact pattern se match
 * karta hai. @ConfigurationPropertiesScan RagProperties (rag.*) ko usi
 * tarah bind karta hai jaise har doosri AI Platform service apni
 * properties bind karti hai. LLM/Embedding Service ki tarah (aur Vector
 * Service ke ulat), is service ka koi database nahi hai - ye char
 * already-built services ke upar pure orchestration hai, isliye yahan
 * jaan-boojh kar koi JPA/Liquibase configuration nahi hai.
 * Ye kyu hai: har PaymentX Spring Boot module ko ek chahiye; ye Phase
 * 3.6 ke build-out ko Phase 3.0 architecture ke implementation order ke
 * hisaab se mark karta hai.
 * Dusre components se kaise communicate karta hai: port 8096 par web
 * server boot karta hai (application.yml dekho); Embedding Service
 * (8094), Vector Service (8095), Prompt Service (8092), aur LLM Service
 * (8093) ko REST ke through call karta hai - kabhi ek database nahi,
 * kabhi seedhe ek provider SDK nahi.
 */
@SpringBootApplication(scanBasePackages = {"com.paymentx.rag", "com.paymentx.common"})
@ConfigurationPropertiesScan
public class RagServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(RagServiceApplication.class, args);
    }
}
