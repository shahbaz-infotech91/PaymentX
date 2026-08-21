package com.paymentx.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * English:
 * The Spring Boot entry point for MCP Gateway - Phase 3.7's real
 * security boundary between an AI/LLM and PaymentX business APIs. Scans
 * com.paymentx.mcp (this module) and com.paymentx.common
 * (paymentx-common-library, for ApiResponse/PaymentXException/
 * HeaderConstants) exactly like every other AI Platform service.
 * @ConfigurationPropertiesScan binds McpGatewayProperties. Like LLM/
 * Embedding/RAG Service (and unlike Vector Service), this module owns
 * no database - it is a stateless request-time authorization/
 * validation/rate-limiting/audit boundary in front of five already-
 * built PaymentX business-service REST APIs, so no JPA/Liquibase
 * configuration exists here.
 * Why it exists: every PaymentX Spring Boot module needs one; marks
 * this Phase 3.7 build-out per the Phase 3.0 architecture's
 * implementation order.
 * How it communicates with other components: boots a web server on
 * port 8097 (see application.yml) that serves both the real MCP
 * protocol endpoint (config/McpServerConfig.java, servlet-registered at
 * /mcp) and one plain read-only REST endpoint
 * (controller/McpToolCatalogController.java); calls payment-service (via
 * API Gateway), routing-service, audit-service, and reconciliation-
 * service over REST - never a database, never a provider SDK directly.
 *
 * Hinglish:
 * MCP Gateway ka Spring Boot entry point - Phase 3.7 ki real security
 * boundary ek AI/LLM aur PaymentX business APIs ke beech. com.paymentx.mcp
 * (ye module) aur com.paymentx.common (paymentx-common-library, ApiResponse/
 * PaymentXException/HeaderConstants ke liye) scan karta hai, exactly har
 * doosri AI Platform service ki tarah. @ConfigurationPropertiesScan
 * McpGatewayProperties ko bind karta hai. LLM/Embedding/RAG Service ki
 * tarah (aur Vector Service ke ulat), is module ka koi database nahi hai
 * - ye paanch already-built PaymentX business-service REST APIs ke
 * saamne ek stateless request-time authorization/validation/rate-
 * limiting/audit boundary hai, isliye yahan koi JPA/Liquibase
 * configuration nahi hai.
 * Ye kyu hai: har PaymentX Spring Boot module ko ek chahiye; ye Phase
 * 3.7 ke build-out ko Phase 3.0 architecture ke implementation order ke
 * hisaab se mark karta hai.
 * Dusre components se kaise communicate karta hai: port 8097 par ek web
 * server boot karta hai (application.yml dekho) jo real MCP protocol
 * endpoint (config/McpServerConfig.java, /mcp par servlet-registered)
 * aur ek plain read-only REST endpoint
 * (controller/McpToolCatalogController.java) dono serve karta hai;
 * payment-service (API Gateway ke through), routing-service, audit-
 * service, aur reconciliation-service ko REST ke through call karta hai
 * - kabhi ek database nahi, kabhi seedhe ek provider SDK nahi.
 */
@SpringBootApplication(scanBasePackages = {"com.paymentx.mcp", "com.paymentx.common"})
@ConfigurationPropertiesScan
public class McpGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(McpGatewayApplication.class, args);
    }
}
