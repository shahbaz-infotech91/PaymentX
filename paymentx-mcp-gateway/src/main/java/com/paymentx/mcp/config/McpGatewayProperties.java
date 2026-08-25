package com.paymentx.mcp.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * English:
 * Every runtime-tunable value this gateway reads - the four real
 * PaymentX business-service URLs the fixed tool catalog calls (Step 11
 * - MCP tools call real service APIs, never a database directly), a
 * bounded connect/read timeout per dependency (Step 23 - every tool
 * call is bounded, never allowed to hang), bounded pagination for
 * audit.search (Step 44/45), and the shared rate-limit window (Step
 * 26/27). Matches RagProperties'/PromptProperties' exact
 * @ConfigurationProperties(prefix) pattern.
 * Why it exists: centralizes every Step-23/26/44/45 bound in one place
 * instead of scattering magic numbers through client/tool classes.
 * How it communicates with other components: bound from application.yml's
 * `mcp:` block; injected into every client class in the client/
 * package and into ratelimit/ToolCallRateLimiter.
 *
 * Hinglish:
 * Har runtime-tunable value jo ye gateway padhta hai - char real
 * PaymentX business-service URLs jo fixed tool catalog call karta hai
 * (Step 11 - MCP tools real service APIs call karte hain, kabhi seedhe
 * ek database nahi), har dependency ke liye ek bounded connect/read
 * timeout (Step 23 - har tool call bounded hai, kabhi hang hone ki
 * ijazat nahi), audit.search ke liye bounded pagination (Step 44/45),
 * aur shared rate-limit window (Step 26/27). RagProperties/
 * PromptProperties ke exact @ConfigurationProperties(prefix) pattern se
 * match karta hai.
 * Ye kyu hai: har Step-23/26/44/45 bound ko ek jagah centralize karta
 * hai, client/tool classes me magic numbers bikherne ke bajaye.
 * Dusre components se kaise communicate karta hai: application.yml ke
 * `mcp:` block se bind hota hai; client/ package ki har client class
 * me aur ratelimit/ToolCallRateLimiter me inject hota hai.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "mcp")
public class McpGatewayProperties {

    private String paymentServiceUrl = "http://localhost:8080";
    private String routingServiceUrl = "http://localhost:8084";
    private String auditServiceUrl = "http://localhost:8085";
    private String reconciliationServiceUrl = "http://localhost:8087";
    // Phase 4.4 addition - Control Center's own, already-existing, read-only
    // GET /api/v1/postgres/payments/stats and GET /api/v1/postgres/databases/{database}/tables
    // endpoints (PostgresController/PostgresDataService) - called directly, same as routing/audit/
    // reconciliation-service above, never through the API Gateway (Control Center is not one of the
    // two services the Gateway routes to). Reuses these existing, hardcoded/parameterized,
    // already-bounded queries rather than adding any new SQL or database connection to this module.
    private String controlCenterUrl = "http://localhost:8089";

    /** Phase 3.9 addition - payment-service is called through the real API Gateway (see paymentServiceUrl's
     * comment), and the Gateway's real ApiKeyAuthenticationGlobalFilter rejects any request with no
     * Authorization/X-Api-Key header (a real Phase 3.9 E2E run surfaced this as TARGET_SERVICE_UNAVAILABLE /
     * "Authentication required" on every payment.lookup and payment.status call). routing-service,
     * audit-service and reconciliation-service are called directly (not through the Gateway) and their own
     * SecurityConfig currently permitAll()s every request, so they need no equivalent key. Empty by default -
     * exactly today's behavior (no header sent) - so this is inert unless an operator provisions a real key
     * via MCP_PAYMENT_SERVICE_API_KEY, using the same gateway:apikey:{key}-&gt;participantId Redis mechanism
     * paymentx-validation-suite/scripts/run-e2e.ps1 already seeds for its own real E2E runs.
     * HINGLISH: payment-service real API Gateway ke through call hota hai, jiska
     * ApiKeyAuthenticationGlobalFilter bina Authorization/X-Api-Key header wali har request reject karta hai.
     * Default empty hai - aaj jaisa behavior - MCP_PAYMENT_SERVICE_API_KEY set kiye bina koi header nahi
     * jaata. */
    private String paymentServiceApiKey = "";

    private int paymentConnectTimeoutMs = 3000;
    private int paymentReadTimeoutMs = 5000;
    private int routingConnectTimeoutMs = 3000;
    private int routingReadTimeoutMs = 5000;
    private int reconciliationConnectTimeoutMs = 3000;
    private int reconciliationReadTimeoutMs = 5000;
    private int auditSearchConnectTimeoutMs = 3000;
    private int auditSearchReadTimeoutMs = 8000;
    private int controlCenterConnectTimeoutMs = 3000;
    private int controlCenterReadTimeoutMs = 5000;
    private int auditWriteConnectTimeoutMs = 2000;
    private int auditWriteReadTimeoutMs = 3000;

    private int auditSearchMaxPageSize = 50;
    private int auditSearchDefaultPageSize = 20;

    private int rateLimitPermitsPerPeriod = 30;
    private int rateLimitPeriodSeconds = 60;
    private int rateLimitTimeoutMillis = 0;
}
