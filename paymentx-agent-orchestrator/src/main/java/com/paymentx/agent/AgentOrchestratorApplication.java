package com.paymentx.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * English:
 * The Spring Boot entry point for Agent Orchestrator - Phase 3.8's
 * single agent loop tying RAG Service, MCP Gateway, and LLM Service
 * together behind a bounded, auditable state machine. Scans
 * com.paymentx.agent (this module) and com.paymentx.common
 * (paymentx-common-library, for ApiResponse/PaymentXException/
 * HeaderConstants) exactly like every other AI Platform service.
 * @ConfigurationPropertiesScan binds AgentOrchestratorProperties. Like
 * RAG Service and MCP Gateway (and unlike Vector Service), this module
 * owns no database - it holds only bounded, per-request execution state
 * (state/AgentExecution) that exists for the lifetime of one HTTP
 * request and is discarded afterward; there is no cross-request
 * memory, so no JPA/Liquibase configuration exists here (Step 21/44).
 * Why it exists: every PaymentX Spring Boot module needs one; marks
 * this Phase 3.8 build-out per the Phase 3.0 architecture's
 * implementation order.
 * How it communicates with other components: boots a web server on
 * port 8098 (see application.yml) that serves
 * controller/AgentController's two endpoints; calls RAG Service, MCP
 * Gateway (as a real MCP client, not a REST shortcut), Prompt Service,
 * and LLM Service over REST/MCP - never a database, never a provider
 * SDK directly.
 *
 * Hinglish:
 * Agent Orchestrator ka Spring Boot entry point - Phase 3.8 ka ek hi
 * agent loop jo RAG Service, MCP Gateway, aur LLM Service ko ek
 * bounded, auditable state machine ke peeche jodta hai. com.paymentx.agent
 * (ye module) aur com.paymentx.common (paymentx-common-library,
 * ApiResponse/PaymentXException/HeaderConstants ke liye) scan karta
 * hai, exactly har doosri AI Platform service ki tarah.
 * @ConfigurationPropertiesScan AgentOrchestratorProperties ko bind
 * karta hai. RAG Service aur MCP Gateway ki tarah (aur Vector Service ke
 * ulat), is module ka koi database nahi hai - ye sirf bounded, per-
 * request execution state (state/AgentExecution) rakhta hai jo ek HTTP
 * request ki lifetime tak exist karta hai aur baad me discard ho jaata
 * hai; koi cross-request memory nahi hai, isliye yahan koi JPA/
 * Liquibase configuration nahi hai (Step 21/44).
 * Ye kyu hai: har PaymentX Spring Boot module ko ek chahiye; ye Phase
 * 3.8 ke build-out ko Phase 3.0 architecture ke implementation order ke
 * hisaab se mark karta hai.
 * Dusre components se kaise communicate karta hai: port 8098 par ek web
 * server boot karta hai (application.yml dekho) jo
 * controller/AgentController ke do endpoints serve karta hai; RAG
 * Service, MCP Gateway (ek real MCP client ke roop me, ek REST shortcut
 * nahi), Prompt Service, aur LLM Service ko REST/MCP ke through call
 * karta hai - kabhi ek database nahi, kabhi seedhe ek provider SDK nahi.
 */
@SpringBootApplication(scanBasePackages = {"com.paymentx.agent", "com.paymentx.common"})
@ConfigurationPropertiesScan
public class AgentOrchestratorApplication {
    public static void main(String[] args) {
        SpringApplication.run(AgentOrchestratorApplication.class, args);
    }
}
