package com.paymentx.mcp.security;

import com.paymentx.mcp.audit.McpAuditClient;
import com.paymentx.mcp.client.AuditServiceClient;
import com.paymentx.mcp.client.PaymentServiceClient;
import com.paymentx.mcp.client.ReconciliationServiceClient;
import com.paymentx.mcp.client.RoutingServiceClient;
import com.paymentx.mcp.config.McpGatewayProperties;
import com.paymentx.mcp.exception.McpErrorCodes;
import com.paymentx.mcp.exception.McpException;
import com.paymentx.mcp.metrics.McpMetrics;
import com.paymentx.mcp.ratelimit.ToolCallRateLimiter;
import com.paymentx.mcp.registry.McpToolDefinition;
import com.paymentx.mcp.registry.PaymentXTool;
import com.paymentx.mcp.registry.ToolInvocationContext;
import com.paymentx.mcp.registry.ToolInvoker;
import com.paymentx.mcp.registry.ToolReadWrite;
import com.paymentx.mcp.registry.ToolRegistry;
import com.paymentx.mcp.registry.ToolRiskLevel;
import com.paymentx.mcp.tool.AuditSearchTool;
import com.paymentx.mcp.tool.PaymentLookupTool;
import com.paymentx.mcp.tool.PaymentStatusTool;
import com.paymentx.mcp.tool.ReconciliationStatusTool;
import com.paymentx.mcp.tool.RoutingLookupTool;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Phase 3.10.1 - Automated AI Security Test Suite, MCP Gateway surface.
 *
 * Consolidates the read-only-catalog / tool-authorization / malicious-parameter / secret-leakage
 * requirements of the Phase 3.10.1 test matrix into deterministic tests, built on top of (and deliberately
 * not duplicating) the existing real, non-mocked coverage in ToolAuthorizationServiceTest, ToolInvokerTest,
 * PaymentLookupToolTest, and AuditSearchToolTest. Every tool instance below is real - only the outbound
 * HTTP clients (PaymentServiceClient, RoutingServiceClient, ReconciliationServiceClient,
 * AuditServiceClient) are mocked, matching this module's established per-tool unit-test convention.
 */
class McpSecurityTest {

    private static final String SECRET_SENTINEL = "TEST_SECRET_SHOULD_NEVER_APPEAR_12345";

    private final PaymentServiceClient paymentServiceClient = mock(PaymentServiceClient.class);
    private final RoutingServiceClient routingServiceClient = mock(RoutingServiceClient.class);
    private final ReconciliationServiceClient reconciliationServiceClient = mock(ReconciliationServiceClient.class);
    private final AuditServiceClient auditServiceClient = mock(AuditServiceClient.class);
    private final McpGatewayProperties properties = new McpGatewayProperties();
    private final ToolAuthorizationService authorizationService = new ToolAuthorizationService();

    private List<PaymentXTool> realFiveTools() {
        return List.of(
                new PaymentLookupTool(paymentServiceClient, authorizationService),
                new PaymentStatusTool(paymentServiceClient),
                new RoutingLookupTool(routingServiceClient, authorizationService),
                new ReconciliationStatusTool(reconciliationServiceClient),
                new AuditSearchTool(auditServiceClient, properties));
    }

    // ================================================================
    // Read/write separation (Step 7 / matrix item 6)
    // ================================================================

    @Test
    void allRegisteredMcpTools_areReadOnly_noWriteToolIsEverExposed() {
        ToolRegistry registry = new ToolRegistry(realFiveTools());

        assertThat(registry.all()).hasSize(5);
        assertThat(registry.all()).allSatisfy(tool ->
                assertThat(tool.definition().readWrite()).isEqualTo(ToolReadWrite.READ_ONLY));
        assertThat(registry.all()).extracting(tool -> tool.definition().name())
                .containsExactlyInAnyOrder("payment.lookup", "payment.status", "routing.lookup",
                        "reconciliation.status", "audit.search");
    }

    @Test
    void syntheticUnauthorizedWriteOperation_isRejectedBeforeAnyExecution() {
        // No write tool is ever registered by real code (proved above) - this proves the independent,
        // second layer: even IF one were, ToolAuthorizationService itself refuses to let it execute,
        // regardless of the caller's role. No business mutation is simulated or attempted anywhere here.
        McpToolDefinition syntheticWriteTool = new McpToolDefinition("payment.refund", "hypothetical write tool",
                new McpSchema.JsonSchema("object", Map.of(), List.of(), false, null, null),
                "PAYMENT_REFUND", ToolRiskLevel.CRITICAL, ToolReadWrite.WRITE, Duration.ofSeconds(5), true, "TEST");
        ToolInvocationContext adminCaller = new ToolInvocationContext(Set.of("PAYMENT_REFUND"), null, "c1", "t1", "caller1");

        assertThatThrownBy(() -> authorizationService.checkPermission(adminCaller, syntheticWriteTool))
                .isInstanceOf(McpException.class)
                .extracting(ex -> ((McpException) ex).getErrorCode())
                .isEqualTo(McpErrorCodes.WRITE_OPERATION_NOT_ALLOWED);
    }

    // ================================================================
    // Tool authorization matrix (Step 6 / matrix items 4-5)
    // ================================================================

    @Test
    void authorizationMatrix_everyRealToolDeniesCallerWithNoRoles() {
        ToolInvocationContext noRoles = new ToolInvocationContext(Set.of(), null, "c1", "t1", "caller1");

        for (PaymentXTool tool : realFiveTools()) {
            assertThatThrownBy(() -> authorizationService.checkPermission(noRoles, tool.definition()))
                    .as("tool=%s", tool.definition().name())
                    .isInstanceOf(McpException.class)
                    .extracting(ex -> ((McpException) ex).getErrorCode())
                    .isEqualTo(McpErrorCodes.TOOL_UNAUTHORIZED);
        }
    }

    @Test
    void authorizationMatrix_everyRealToolRequiresItsOwnPermissionAndRejectsAnyOther() {
        Set<String> allRealPermissions = Set.of("PAYMENT_READ", "ROUTING_READ", "RECONCILIATION_READ", "AUDIT_READ");

        for (PaymentXTool tool : realFiveTools()) {
            McpToolDefinition definition = tool.definition();
            ToolInvocationContext correctRole = new ToolInvocationContext(Set.of(definition.requiredPermission()), null, "c1", "t1", "caller1");
            authorizationService.checkPermission(correctRole, definition); // does not throw

            for (String otherPermission : allRealPermissions) {
                if (otherPermission.equals(definition.requiredPermission())) {
                    continue;
                }
                ToolInvocationContext wrongRole = new ToolInvocationContext(Set.of(otherPermission), null, "c1", "t1", "caller1");
                assertThatThrownBy(() -> authorizationService.checkPermission(wrongRole, definition))
                        .as("tool=%s wrongRole=%s", definition.name(), otherPermission)
                        .isInstanceOf(McpException.class)
                        .extracting(ex -> ((McpException) ex).getErrorCode())
                        .isEqualTo(McpErrorCodes.TOOL_FORBIDDEN);
            }
        }
    }

    // ================================================================
    // Malicious tool parameters (Step 8 / matrix item 7)
    // ================================================================

    private static ToolInvocationContext unscopedCaller() {
        return new ToolInvocationContext(Set.of("PAYMENT_READ"), null, "corr-1", "trace-1", "caller1");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", // oversized (86 chars, over the 64-char cap)
            "PMT-1' OR '1'='1",                    // SQL-injection-like
            "'; DROP TABLE payments;--",            // SQL-injection-like
            "../../etc/passwd",                     // path-traversal-like
            "..\\..\\windows\\system32",             // path-traversal-like (Windows-style)
            "PMT-1\nX-Roles: ADMIN",                 // header/CRLF-injection-like
            "",                                      // empty
            "   ",                                   // blank/whitespace-only
    })
    void paymentLookup_maliciousOrMalformedPaymentReference_rejectedWithoutEverCallingPaymentService(String maliciousReference) {
        PaymentLookupTool tool = new PaymentLookupTool(paymentServiceClient, authorizationService);

        assertThatThrownBy(() -> tool.execute(unscopedCaller(), Map.of("paymentReference", maliciousReference)))
                .isInstanceOf(McpException.class)
                .extracting(ex -> ((McpException) ex).getErrorCode())
                .isEqualTo(McpErrorCodes.INVALID_TOOL_ARGUMENTS);

        verifyNoInteractions(paymentServiceClient);
    }

    @Test
    void paymentLookup_unexpectedParameterType_rejectedWithoutEverCallingPaymentService() {
        PaymentLookupTool tool = new PaymentLookupTool(paymentServiceClient, authorizationService);

        assertThatThrownBy(() -> tool.execute(unscopedCaller(), Map.of("paymentReference", 12345)))
                .isInstanceOf(McpException.class)
                .extracting(ex -> ((McpException) ex).getErrorCode())
                .isEqualTo(McpErrorCodes.INVALID_TOOL_ARGUMENTS);

        verifyNoInteractions(paymentServiceClient);
    }

    @Test
    void auditSearch_negativePage_rejectedWithoutEverCallingAuditService() {
        AuditSearchTool tool = new AuditSearchTool(auditServiceClient, properties);
        ToolInvocationContext ctx = new ToolInvocationContext(Set.of("AUDIT_READ"), null, "corr-1", "trace-1", "unknown");

        assertThatThrownBy(() -> tool.execute(ctx, Map.of("page", -1)))
                .isInstanceOf(McpException.class)
                .extracting(ex -> ((McpException) ex).getErrorCode())
                .isEqualTo(McpErrorCodes.INVALID_TOOL_ARGUMENTS);

        verifyNoInteractions(auditServiceClient);
    }

    @Test
    void auditSearch_unexpectedParameterType_rejectedWithoutEverCallingAuditService() {
        AuditSearchTool tool = new AuditSearchTool(auditServiceClient, properties);
        ToolInvocationContext ctx = new ToolInvocationContext(Set.of("AUDIT_READ"), null, "corr-1", "trace-1", "unknown");

        assertThatThrownBy(() -> tool.execute(ctx, Map.of("size", "not-a-number")))
                .isInstanceOf(McpException.class)
                .extracting(ex -> ((McpException) ex).getErrorCode())
                .isEqualTo(McpErrorCodes.INVALID_TOOL_ARGUMENTS);

        verifyNoInteractions(auditServiceClient);
    }

    @Test
    void auditSearch_injectionLikeEventType_rejectedByAllowListWithoutEverCallingAuditService() {
        AuditSearchTool tool = new AuditSearchTool(auditServiceClient, properties);
        ToolInvocationContext ctx = new ToolInvocationContext(Set.of("AUDIT_READ"), null, "corr-1", "trace-1", "unknown");

        assertThatThrownBy(() -> tool.execute(ctx, Map.of("eventType", "'; DROP TABLE audit_events;--")))
                .isInstanceOf(McpException.class)
                .extracting(ex -> ((McpException) ex).getErrorCode())
                .isEqualTo(McpErrorCodes.INVALID_TOOL_ARGUMENTS);

        verifyNoInteractions(auditServiceClient);
    }

    // ================================================================
    // Secret leakage on unanticipated tool failures (Step 9 / matrix item 8)
    // ================================================================

    @Test
    void unexpectedToolFailureCarryingSecretInMessage_neverLeaksIntoTheMcpErrorResult() {
        McpAuditClient auditClient = Mockito.mock(McpAuditClient.class);
        McpMetrics metrics = new McpMetrics(new SimpleMeterRegistry());
        RateLimiterRegistry rateLimiterRegistry = RateLimiterRegistry.of(RateLimiterConfig.custom()
                .limitForPeriod(100)
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .timeoutDuration(Duration.ZERO)
                .build());
        ToolCallRateLimiter rateLimiter = new ToolCallRateLimiter(rateLimiterRegistry);

        PaymentXTool leakyTool = new PaymentXTool() {
            public McpToolDefinition definition() {
                return new McpToolDefinition("test.leaky", "desc",
                        new McpSchema.JsonSchema("object", Map.of(), List.of(), false, null, null),
                        "TEST_PERMISSION", ToolRiskLevel.LOW, ToolReadWrite.READ_ONLY, Duration.ofSeconds(5), true, "TEST");
            }

            public Map<String, Object> execute(ToolInvocationContext ctx, Map<String, Object> arguments) {
                // Simulates an unanticipated bug (NPE, parsing failure, etc.) whose message happens to
                // carry sensitive internal detail - the worst realistic case for this boundary.
                throw new RuntimeException("internal failure api_key=" + SECRET_SENTINEL);
            }
        };
        ToolInvoker invoker = new ToolInvoker(new ToolRegistry(List.of(leakyTool)), authorizationService, rateLimiter, auditClient, metrics);

        McpSchema.CallToolResult result = invoker.invoke("test.leaky", Map.of(),
                new ToolInvocationContext(Set.of("TEST_PERMISSION"), null, "c1", "t1", "caller1"));

        assertThat(result.isError()).isTrue();
        assertThat(result.structuredContent().toString()).doesNotContain(SECRET_SENTINEL);
    }
}
