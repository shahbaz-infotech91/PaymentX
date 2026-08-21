package com.paymentx.mcp.controller;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * English:
 * Step 41's real end-to-end test - executes the REAL chain: a real MCP
 * client (io.modelcontextprotocol.sdk's own McpSyncClient, over the
 * real Streamable HTTP wire protocol - not a hand-rolled HTTP call
 * pretending to be MCP) -&gt; this service's real, unmodified servlet
 * (config/McpServerConfig) -&gt; real registry/ToolRegistry -&gt; real
 * security/ToolAuthorizationService -&gt; real client/PaymentServiceClient
 * -&gt; a real HTTP call to a WireMock stand-in for payment-service -&gt;
 * real response parsing -&gt; a real, normalized MCP CallToolResult handed
 * back to the client. Never mocks Spring beans (@SpringBootTest, full
 * real application context) and never mocks the MCP protocol layer
 * itself - only the one real external dependency (payment-service) is
 * stood in with WireMock, for the exact same reason
 * RagQueryIntegrationTest gave in Phase 3.6 (see that class's own
 * javadoc): this proves THIS module's own real orchestration/
 * authorization/wire-format logic, while payment-service's own behavior
 * is already proven for real in its own test suite - re-proving it here
 * would duplicate coverage at disproportionate cost, not add real
 * confidence.
 * Step 42 - only a real read-only GET (payment.lookup) is exercised;
 * no real or simulated payment mutation ever occurs.
 * Also covers Steps 1/2/3/4/19/20 (server startup, tool discovery,
 * authorized valid call, unauthorized call, resource-forbidden call)
 * in one real running server rather than five separate boots.
 *
 * Hinglish:
 * Step 41 ka real end-to-end test - REAL chain execute karta hai: ek
 * real MCP client (io.modelcontextprotocol.sdk ka apna McpSyncClient,
 * real Streamable HTTP wire protocol ke upar - ek hand-rolled HTTP call
 * nahi jo MCP hone ka dawa kare) -&gt; is service ka real, unmodified
 * servlet (config/McpServerConfig) -&gt; real registry/ToolRegistry -&gt;
 * real security/ToolAuthorizationService -&gt; real client/
 * PaymentServiceClient -&gt; payment-service ke ek WireMock stand-in ko ek
 * real HTTP call -&gt; real response parsing -&gt; client ko wapas di gayi ek
 * real, normalized MCP CallToolResult. Kabhi Spring beans mock nahi
 * karta (@SpringBootTest, poora real application context) aur kabhi MCP
 * protocol layer ko khud mock nahi karta - sirf ek real external
 * dependency (payment-service) WireMock se stand in hota hai, exactly
 * usi reason se jo RagQueryIntegrationTest ne Phase 3.6 me diya tha (us
 * class ka apna javadoc dekho): ye is module ki apni real orchestration/
 * authorization/wire-format logic prove karta hai, jabki payment-service
 * ka apna behavior already uski apni test suite me real proven hai -
 * yahan use dobara prove karna disproportionate cost par coverage
 * duplicate karta, real confidence add nahi karta.
 * Step 42 - sirf ek real read-only GET (payment.lookup) exercise hota
 * hai; koi real ya simulated payment mutation kabhi nahi hoti.
 * Steps 1/2/3/4/19/20 (server startup, tool discovery, authorized valid
 * call, unauthorized call, resource-forbidden call) bhi ek hi real
 * running server me cover karta hai, paanch alag boots ke bajaye.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class McpProtocolIntegrationTest {

    private static WireMockServer paymentServiceMock;

    @LocalServerPort
    private int port;

    @BeforeAll
    static void startWireMock() {
        paymentServiceMock = new WireMockServer(0);
        paymentServiceMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        paymentServiceMock.stop();
    }

    @BeforeEach
    void resetWireMock() {
        paymentServiceMock.resetAll();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("mcp.payment-service-url", () -> "http://localhost:" + paymentServiceMock.port());
    }

    private McpSyncClient buildClient(String roles, String participantId) {
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder(
                        "http://localhost:" + port + "/mcp")
                .customizeRequest(builder -> {
                    if (roles != null) {
                        builder.header("X-Roles", roles);
                    }
                    if (participantId != null) {
                        builder.header("X-Participant-Id", participantId);
                    }
                    builder.header("X-Correlation-Id", "test-corr-1");
                })
                .build();
        McpSyncClient client = McpClient.sync(transport)
                .clientInfo(new McpSchema.Implementation("mcp-gateway-integration-test", "1.0.0"))
                .build();
        client.initialize();
        return client;
    }

    @Test
    void toolDiscovery_realMcpListToolsCall_returnsFixedReadOnlyCatalog() {
        try (McpSyncClient client = buildClient("PAYMENT_READ,ROUTING_READ,RECONCILIATION_READ,AUDIT_READ", null)) {
            McpSchema.ListToolsResult tools = client.listTools();
            assertThat(tools.tools()).extracting(McpSchema.Tool::name)
                    .containsExactlyInAnyOrder("payment.lookup", "payment.status", "routing.lookup",
                            "reconciliation.status", "audit.search");
        }
    }

    @Test
    void callTool_authorizedRealPaymentLookup_executesRealChainAndReturnsNormalizedResult() {
        paymentServiceMock.stubFor(get(urlPathEqualTo("/api/v1/payments/PMT-777")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"success": true, "data": {
                          "id": "11111111-1111-1111-1111-111111111111",
                          "paymentReference": "PMT-777",
                          "traceId": "trace-1",
                          "correlationId": "corr-1",
                          "scheme": "INSTANT_PAYMENT",
                          "amount": {"amount": 100.50, "currency": "USD"},
                          "debtorAccount": "1234567890",
                          "debtorParticipantId": "P1",
                          "creditorAccount": "9876543210",
                          "creditorParticipantId": "P2",
                          "status": "COMPLETED",
                          "failureReason": null,
                          "createdAt": "2026-08-01T00:00:00Z",
                          "updatedAt": "2026-08-01T00:01:00Z"
                        }}
                        """)));

        try (McpSyncClient client = buildClient("PAYMENT_READ", null)) {
            McpSchema.CallToolResult result = client.callTool(
                    new McpSchema.CallToolRequest("payment.lookup", Map.of("paymentReference", "PMT-777")));

            assertThat(result.isError()).isFalse();
            @SuppressWarnings("unchecked")
            Map<String, Object> structured = (Map<String, Object>) result.structuredContent();
            assertThat(structured.get("found")).isEqualTo(true);
            assertThat(structured.get("status")).isEqualTo("COMPLETED");
            // Step 19 - real masking, never the raw account number.
            assertThat(structured.get("debtorAccountMasked")).isEqualTo("******7890");
        }

        paymentServiceMock.verify(getRequestedFor(urlPathEqualTo("/api/v1/payments/PMT-777"))
                .withHeader("X-Correlation-Id", com.github.tomakehurst.wiremock.client.WireMock.equalTo("test-corr-1")));
    }

    @Test
    void callTool_noRoles_returnsToolUnauthorizedWithoutCallingPaymentService() {
        try (McpSyncClient client = buildClient(null, null)) {
            McpSchema.CallToolResult result = client.callTool(
                    new McpSchema.CallToolRequest("payment.lookup", Map.of("paymentReference", "PMT-777")));

            assertThat(result.isError()).isTrue();
            @SuppressWarnings("unchecked")
            Map<String, Object> structured = (Map<String, Object>) result.structuredContent();
            assertThat(structured.get("errorCode")).isEqualTo("TOOL_UNAUTHORIZED");
        }
        paymentServiceMock.verify(0, getRequestedFor(urlPathEqualTo("/api/v1/payments/PMT-777")));
    }

    @Test
    void callTool_wrongParticipantResourceScope_returnsResourceForbidden() {
        paymentServiceMock.stubFor(get(urlPathEqualTo("/api/v1/payments/PMT-888")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"success": true, "data": {
                          "paymentReference": "PMT-888", "scheme": "INSTANT_PAYMENT",
                          "amount": {"amount": 50.00, "currency": "USD"},
                          "debtorAccount": "1111111111", "debtorParticipantId": "P1",
                          "creditorAccount": "2222222222", "creditorParticipantId": "P2",
                          "status": "COMPLETED", "createdAt": "2026-08-01T00:00:00Z", "updatedAt": "2026-08-01T00:01:00Z"
                        }}
                        """)));

        try (McpSyncClient client = buildClient("PAYMENT_READ", "P9")) {
            McpSchema.CallToolResult result = client.callTool(
                    new McpSchema.CallToolRequest("payment.lookup", Map.of("paymentReference", "PMT-888")));

            assertThat(result.isError()).isTrue();
            @SuppressWarnings("unchecked")
            Map<String, Object> structured = (Map<String, Object>) result.structuredContent();
            assertThat(structured.get("errorCode")).isEqualTo("RESOURCE_FORBIDDEN");
        }
    }

    @Test
    void callTool_realNotFoundFromPaymentService_returnsSuccessfulFoundFalseNotAnError() {
        paymentServiceMock.stubFor(get(urlPathEqualTo("/api/v1/payments/PMT-MISSING")).willReturn(aResponse()
                .withStatus(404)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"success": false, "error": {"errorCode": "RESOURCE_NOT_FOUND", "message": "Payment not found: PMT-MISSING"}}
                        """)));

        try (McpSyncClient client = buildClient("PAYMENT_READ", null)) {
            McpSchema.CallToolResult result = client.callTool(
                    new McpSchema.CallToolRequest("payment.lookup", Map.of("paymentReference", "PMT-MISSING")));

            // Step 33 - a real business "not found" is a successful tool result, never a thrown MCP error.
            assertThat(result.isError()).isFalse();
            @SuppressWarnings("unchecked")
            Map<String, Object> structured = (Map<String, Object>) result.structuredContent();
            assertThat(structured.get("found")).isEqualTo(false);
        }
    }

    @Test
    void callTool_paymentServiceDown_returnsTargetServiceUnavailable() {
        // Deliberately NOT stop()/start() - this WireMockServer was constructed with an ephemeral port
        // (`new WireMockServer(0)`) and its port was captured ONCE into mcp.payment-service-url via
        // @DynamicPropertySource at Spring context startup; restarting it is not guaranteed to preserve
        // the port (the exact gotcha documented in PAYMENTX_PHASE_3_6_RAG_SERVICE.md's Retry section),
        // which would silently break every OTHER test in this class sharing the same context. A
        // CONNECTION_RESET_BY_PEER fault simulates real unreachability without touching the server's
        // lifecycle or port at all.
        paymentServiceMock.stubFor(get(urlPathEqualTo("/api/v1/payments/PMT-DOWN")).willReturn(
                aResponse().withFault(com.github.tomakehurst.wiremock.http.Fault.CONNECTION_RESET_BY_PEER)));

        try (McpSyncClient client = buildClient("PAYMENT_READ", null)) {
            McpSchema.CallToolResult result = client.callTool(
                    new McpSchema.CallToolRequest("payment.lookup", Map.of("paymentReference", "PMT-DOWN")));

            assertThat(result.isError()).isTrue();
            @SuppressWarnings("unchecked")
            Map<String, Object> structured = (Map<String, Object>) result.structuredContent();
            assertThat(structured.get("errorCode")).isEqualTo("TARGET_SERVICE_UNAVAILABLE");
        }
    }

    @Test
    void callTool_invalidArguments_returnsInvalidToolArguments() {
        try (McpSyncClient client = buildClient("PAYMENT_READ", null)) {
            McpSchema.CallToolResult result = client.callTool(
                    new McpSchema.CallToolRequest("payment.lookup", Map.of("paymentReference", "has a space!")));

            assertThat(result.isError()).isTrue();
            @SuppressWarnings("unchecked")
            Map<String, Object> structured = (Map<String, Object>) result.structuredContent();
            assertThat(structured.get("errorCode")).isEqualTo("INVALID_TOOL_ARGUMENTS");
        }
        paymentServiceMock.verify(0, getRequestedFor(urlPathEqualTo("/api/v1/payments/has a space!")));
    }

    @Test
    void callTool_transientPaymentServiceFailure_retriesAndEventuallySucceeds() {
        String scenario = "payment-transient-failure";
        paymentServiceMock.stubFor(get(urlPathEqualTo("/api/v1/payments/PMT-RETRY"))
                .inScenario(scenario)
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"success": false, "error": {"errorCode": "SERVICE_UNAVAILABLE", "message": "transiently down"}}
                                """))
                .willSetStateTo("recovered"));
        paymentServiceMock.stubFor(get(urlPathEqualTo("/api/v1/payments/PMT-RETRY"))
                .inScenario(scenario)
                .whenScenarioStateIs("recovered")
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"success": true, "data": {
                                  "paymentReference": "PMT-RETRY", "scheme": "INSTANT_PAYMENT",
                                  "amount": {"amount": 10.00, "currency": "USD"},
                                  "debtorAccount": "1111111111", "debtorParticipantId": "P1",
                                  "creditorAccount": "2222222222", "creditorParticipantId": "P2",
                                  "status": "COMPLETED", "createdAt": "2026-08-01T00:00:00Z", "updatedAt": "2026-08-01T00:01:00Z"
                                }}
                                """)));

        // Real proof spring-boot-starter-aop actually weaves @Retry on this Spring-proxied bean (Step 24) -
        // the first call gets a real 503 from WireMock, and only a REAL retry (not this test) can make the
        // second stubbed response ever be reached.
        try (McpSyncClient client = buildClient("PAYMENT_READ", null)) {
            McpSchema.CallToolResult result = client.callTool(
                    new McpSchema.CallToolRequest("payment.lookup", Map.of("paymentReference", "PMT-RETRY")));

            assertThat(result.isError()).isFalse();
            @SuppressWarnings("unchecked")
            Map<String, Object> structured = (Map<String, Object>) result.structuredContent();
            assertThat(structured.get("found")).isEqualTo(true);
            assertThat(structured.get("status")).isEqualTo("COMPLETED");
        }
    }

    @Test
    void callTool_unknownToolName_rejectedAtProtocolLayerBeforeReachingToolInvoker() {
        // Real finding: the SDK's own server-side dispatcher validates a tool name against its
        // registered-tools map BEFORE ever invoking a callHandler, and rejects an unknown name as a
        // JSON-RPC protocol-level error (code -32602) - registry/ToolInvoker's own TOOL_NOT_FOUND branch
        // can never actually be reached through a real MCP client for this reason; it is real
        // defense-in-depth, exercised directly by registry/ToolInvokerTest's
        // invoke_unknownTool_returnsToolNotFound unit test instead.
        try (McpSyncClient client = buildClient("PAYMENT_READ", null)) {
            assertThatThrownBy(() -> client.callTool(new McpSchema.CallToolRequest("payment.refund", Map.of())))
                    .isInstanceOf(io.modelcontextprotocol.spec.McpError.class)
                    .hasMessageContaining("Unknown tool");
        }
    }
}
