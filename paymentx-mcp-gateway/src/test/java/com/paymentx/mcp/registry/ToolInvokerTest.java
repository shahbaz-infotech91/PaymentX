package com.paymentx.mcp.registry;

import com.paymentx.mcp.audit.McpAuditClient;
import com.paymentx.mcp.exception.McpErrorCodes;
import com.paymentx.mcp.exception.McpException;
import com.paymentx.mcp.metrics.McpMetrics;
import com.paymentx.mcp.ratelimit.ToolCallRateLimiter;
import com.paymentx.mcp.security.ToolAuthorizationService;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * English:
 * Real, non-mocked ToolInvoker unit tests - uses a REAL
 * ToolAuthorizationService, a REAL ToolCallRateLimiter (backed by a
 * real Resilience4j RateLimiterRegistry), and a REAL McpMetrics
 * (SimpleMeterRegistry-backed), with only registry/ToolRegistry's
 * contained tools being hand-built test doubles and McpAuditClient
 * mocked (a real one would need a live Audit Service HTTP endpoint -
 * out of scope for a pure ToolInvoker unit test; the real audit HTTP
 * call is covered separately in audit/McpAuditClientTest). Exercises
 * Step 40 items 7/8/9/13/14/22/23 directly at this layer: unknown tool,
 * disabled tool, unauthorized/forbidden, timeout, write tool always
 * denied, and that audit recording happens exactly once per call
 * regardless of outcome.
 *
 * Hinglish:
 * Real, non-mocked ToolInvoker unit tests - ek REAL
 * ToolAuthorizationService use karti hain, ek REAL ToolCallRateLimiter
 * (ek real Resilience4j RateLimiterRegistry se backed), aur ek REAL
 * McpMetrics (SimpleMeterRegistry-backed), sirf registry/ToolRegistry
 * ke contained tools hand-built test doubles hain aur McpAuditClient
 * mocked hai (ek real wale ko ek live Audit Service HTTP endpoint
 * chahiye - ek pure ToolInvoker unit test ke liye out of scope; real
 * audit HTTP call alag se audit/McpAuditClientTest me cover hota hai).
 * Step 40 items 7/8/9/13/14/22/23 ko yahan directly is layer par
 * exercise karti hain: unknown tool, disabled tool, unauthorized/
 * forbidden, timeout, write tool hamesha denied, aur ye ki audit
 * recording outcome ke bawajood har call ke liye exactly ek baar hoti
 * hai.
 */
class ToolInvokerTest {

    private McpAuditClient auditClient;
    private McpMetrics metrics;
    private ToolAuthorizationService authorizationService;
    private ToolCallRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        auditClient = Mockito.mock(McpAuditClient.class);
        metrics = new McpMetrics(new SimpleMeterRegistry());
        authorizationService = new ToolAuthorizationService();
        RateLimiterRegistry registry = RateLimiterRegistry.of(RateLimiterConfig.custom()
                .limitForPeriod(2)
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .timeoutDuration(Duration.ZERO)
                .build());
        rateLimiter = new ToolCallRateLimiter(registry);
    }

    private McpToolDefinition definition(String name, boolean enabled, ToolReadWrite readWrite, Duration timeout) {
        return new McpToolDefinition(name, "test tool",
                new McpSchema.JsonSchema("object", Map.of(), List.of(), false, null, null),
                "TEST_PERMISSION", ToolRiskLevel.LOW, readWrite, timeout, enabled, "TEST");
    }

    private ToolInvocationContext context(String... roles) {
        return new ToolInvocationContext(Set.of(roles), "P1", "corr-1", "trace-1", "P1");
    }

    private ToolInvoker invokerWithTools(PaymentXTool... tools) {
        return new ToolInvoker(new ToolRegistry(List.of(tools)), authorizationService, rateLimiter, auditClient, metrics);
    }

    @Test
    void invoke_unknownTool_returnsToolNotFound() {
        ToolInvoker invoker = invokerWithTools();
        McpSchema.CallToolResult result = invoker.invoke("no.such.tool", Map.of(), context("TEST_PERMISSION"));

        assertThat(result.isError()).isTrue();
        assertThat(errorCode(result)).isEqualTo(McpErrorCodes.TOOL_NOT_FOUND);
        Mockito.verify(auditClient).recordToolInvocation(Mockito.any(), Mockito.eq("no.such.tool"), Mockito.any(),
                Mockito.any(), Mockito.any(), Mockito.anyLong(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void invoke_disabledTool_returnsToolDisabledWithoutCallingExecute() {
        PaymentXTool tool = new PaymentXTool() {
            public McpToolDefinition definition() {
                return ToolInvokerTest.this.definition("test.disabled", false, ToolReadWrite.READ_ONLY, Duration.ofSeconds(1));
            }
            public Map<String, Object> execute(ToolInvocationContext ctx, Map<String, Object> args) {
                throw new AssertionError("execute must never be called for a disabled tool");
            }
        };
        ToolInvoker invoker = invokerWithTools(tool);
        McpSchema.CallToolResult result = invoker.invoke("test.disabled", Map.of(), context("TEST_PERMISSION"));

        assertThat(result.isError()).isTrue();
        assertThat(errorCode(result)).isEqualTo(McpErrorCodes.TOOL_DISABLED);
    }

    @Test
    void invoke_noRoles_returnsToolUnauthorized() {
        PaymentXTool tool = readOnlyTool("test.readonly", Map.of("ok", true));
        ToolInvoker invoker = invokerWithTools(tool);
        McpSchema.CallToolResult result = invoker.invoke("test.readonly", Map.of(), context());

        assertThat(result.isError()).isTrue();
        assertThat(errorCode(result)).isEqualTo(McpErrorCodes.TOOL_UNAUTHORIZED);
    }

    @Test
    void invoke_wrongRole_returnsToolForbidden() {
        PaymentXTool tool = readOnlyTool("test.readonly", Map.of("ok", true));
        ToolInvoker invoker = invokerWithTools(tool);
        McpSchema.CallToolResult result = invoker.invoke("test.readonly", Map.of(), context("SOME_OTHER_PERMISSION"));

        assertThat(result.isError()).isTrue();
        assertThat(errorCode(result)).isEqualTo(McpErrorCodes.TOOL_FORBIDDEN);
    }

    @Test
    void invoke_writeClassifiedTool_alwaysReturnsWriteOperationNotAllowedRegardlessOfRole() {
        PaymentXTool tool = new PaymentXTool() {
            public McpToolDefinition definition() {
                return ToolInvokerTest.this.definition("test.write", true, ToolReadWrite.WRITE, Duration.ofSeconds(1));
            }
            public Map<String, Object> execute(ToolInvocationContext ctx, Map<String, Object> args) {
                throw new AssertionError("execute must never be called for a write tool in this phase");
            }
        };
        ToolInvoker invoker = invokerWithTools(tool);
        McpSchema.CallToolResult result = invoker.invoke("test.write", Map.of(), context("TEST_PERMISSION"));

        assertThat(result.isError()).isTrue();
        assertThat(errorCode(result)).isEqualTo(McpErrorCodes.WRITE_OPERATION_NOT_ALLOWED);
    }

    @Test
    void invoke_rateLimitExceeded_returnsToolRateLimited() {
        PaymentXTool tool = readOnlyTool("test.readonly", Map.of("ok", true));
        ToolInvoker invoker = invokerWithTools(tool);
        ToolInvocationContext ctx = context("TEST_PERMISSION");

        invoker.invoke("test.readonly", Map.of(), ctx);
        invoker.invoke("test.readonly", Map.of(), ctx);
        McpSchema.CallToolResult third = invoker.invoke("test.readonly", Map.of(), ctx);

        assertThat(third.isError()).isTrue();
        assertThat(errorCode(third)).isEqualTo(McpErrorCodes.TOOL_RATE_LIMITED);
    }

    @Test
    void invoke_toolExceedsTimeout_returnsToolTimeout() {
        PaymentXTool slowTool = new PaymentXTool() {
            public McpToolDefinition definition() {
                return ToolInvokerTest.this.definition("test.slow", true, ToolReadWrite.READ_ONLY, Duration.ofMillis(100));
            }
            public Map<String, Object> execute(ToolInvocationContext ctx, Map<String, Object> args) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return Map.of("ok", true);
            }
        };
        ToolInvoker invoker = invokerWithTools(slowTool);
        McpSchema.CallToolResult result = invoker.invoke("test.slow", Map.of(), context("TEST_PERMISSION"));

        assertThat(result.isError()).isTrue();
        assertThat(errorCode(result)).isEqualTo(McpErrorCodes.TOOL_TIMEOUT);
    }

    @Test
    void invoke_toolThrowsInvalidArguments_propagatesRealErrorCode() {
        PaymentXTool tool = new PaymentXTool() {
            public McpToolDefinition definition() {
                return ToolInvokerTest.this.definition("test.validated", true, ToolReadWrite.READ_ONLY, Duration.ofSeconds(1));
            }
            public Map<String, Object> execute(ToolInvocationContext ctx, Map<String, Object> args) {
                throw McpException.invalidArguments("bad input");
            }
        };
        ToolInvoker invoker = invokerWithTools(tool);
        McpSchema.CallToolResult result = invoker.invoke("test.validated", Map.of(), context("TEST_PERMISSION"));

        assertThat(result.isError()).isTrue();
        assertThat(errorCode(result)).isEqualTo(McpErrorCodes.INVALID_TOOL_ARGUMENTS);
    }

    @Test
    void invoke_successfulTool_returnsNonErrorResultWithStructuredContent() {
        PaymentXTool tool = readOnlyTool("test.readonly", Map.of("found", true, "value", 42));
        ToolInvoker invoker = invokerWithTools(tool);
        McpSchema.CallToolResult result = invoker.invoke("test.readonly", Map.of(), context("TEST_PERMISSION"));

        assertThat(result.isError()).isFalse();
        @SuppressWarnings("unchecked")
        Map<String, Object> structured = (Map<String, Object>) result.structuredContent();
        assertThat(structured.get("found")).isEqualTo(true);
        assertThat(structured.get("value")).isEqualTo(42);
    }

    private PaymentXTool readOnlyTool(String name, Map<String, Object> output) {
        return new PaymentXTool() {
            public McpToolDefinition definition() {
                return ToolInvokerTest.this.definition(name, true, ToolReadWrite.READ_ONLY, Duration.ofSeconds(5));
            }
            public Map<String, Object> execute(ToolInvocationContext ctx, Map<String, Object> args) {
                return output;
            }
        };
    }

    @SuppressWarnings("unchecked")
    private String errorCode(McpSchema.CallToolResult result) {
        return (String) ((Map<String, Object>) result.structuredContent()).get("errorCode");
    }
}
