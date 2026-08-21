package com.paymentx.mcp.security;

import com.paymentx.mcp.exception.McpErrorCodes;
import com.paymentx.mcp.exception.McpException;
import com.paymentx.mcp.registry.McpToolDefinition;
import com.paymentx.mcp.registry.ToolInvocationContext;
import com.paymentx.mcp.registry.ToolReadWrite;
import com.paymentx.mcp.registry.ToolRiskLevel;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * English:
 * Pure unit tests for the real Step 12/13/14/20/22 enforcement point -
 * no mocks, no Spring context, just the real class against real
 * registry/McpToolDefinition and registry/ToolInvocationContext values.
 *
 * Hinglish:
 * Real Step 12/13/14/20/22 enforcement point ke liye pure unit tests -
 * koi mocks nahi, koi Spring context nahi, bas real class real
 * registry/McpToolDefinition aur registry/ToolInvocationContext values
 * ke against.
 */
class ToolAuthorizationServiceTest {

    private final ToolAuthorizationService service = new ToolAuthorizationService();

    private McpToolDefinition readOnlyDefinition(String permission) {
        return new McpToolDefinition("test.tool", "desc",
                new McpSchema.JsonSchema("object", Map.of(), List.of(), false, null, null),
                permission, ToolRiskLevel.LOW, ToolReadWrite.READ_ONLY, Duration.ofSeconds(5), true, "TEST");
    }

    @Test
    void checkPermission_callerHasRequiredRole_passes() {
        ToolInvocationContext ctx = new ToolInvocationContext(Set.of("PAYMENT_READ"), null, "c1", "t1", "caller1");
        service.checkPermission(ctx, readOnlyDefinition("PAYMENT_READ"));
    }

    @Test
    void checkPermission_noRolesAtAll_throwsToolUnauthorized() {
        ToolInvocationContext ctx = new ToolInvocationContext(Set.of(), null, "c1", "t1", "caller1");
        assertThatThrownBy(() -> service.checkPermission(ctx, readOnlyDefinition("PAYMENT_READ")))
                .isInstanceOf(McpException.class)
                .extracting(ex -> ((McpException) ex).getErrorCode())
                .isEqualTo(McpErrorCodes.TOOL_UNAUTHORIZED);
    }

    @Test
    void checkPermission_wrongRole_throwsToolForbidden() {
        ToolInvocationContext ctx = new ToolInvocationContext(Set.of("ROUTING_READ"), null, "c1", "t1", "caller1");
        assertThatThrownBy(() -> service.checkPermission(ctx, readOnlyDefinition("PAYMENT_READ")))
                .isInstanceOf(McpException.class)
                .extracting(ex -> ((McpException) ex).getErrorCode())
                .isEqualTo(McpErrorCodes.TOOL_FORBIDDEN);
    }

    @Test
    void checkPermission_writeClassifiedTool_alwaysThrowsWriteOperationNotAllowed() {
        McpToolDefinition writeDefinition = new McpToolDefinition("payment.refund", "desc",
                new McpSchema.JsonSchema("object", Map.of(), List.of(), false, null, null),
                "PAYMENT_REFUND", ToolRiskLevel.CRITICAL, ToolReadWrite.WRITE, Duration.ofSeconds(5), true, "TEST");
        ToolInvocationContext ctx = new ToolInvocationContext(Set.of("PAYMENT_REFUND"), null, "c1", "t1", "caller1");

        assertThatThrownBy(() -> service.checkPermission(ctx, writeDefinition))
                .isInstanceOf(McpException.class)
                .extracting(ex -> ((McpException) ex).getErrorCode())
                .isEqualTo(McpErrorCodes.WRITE_OPERATION_NOT_ALLOWED);
    }

    @Test
    void checkResourceOwnership_callerScopedAndOwnsResource_passes() {
        ToolInvocationContext ctx = new ToolInvocationContext(Set.of("PAYMENT_READ"), "P1", "c1", "t1", "P1");
        service.checkResourceOwnership(ctx, "payment.lookup", "P1", "P2");
    }

    @Test
    void checkResourceOwnership_callerScopedButDoesNotOwnResource_throwsResourceForbidden() {
        ToolInvocationContext ctx = new ToolInvocationContext(Set.of("PAYMENT_READ"), "P9", "c1", "t1", "P9");
        assertThatThrownBy(() -> service.checkResourceOwnership(ctx, "payment.lookup", "P1", "P2"))
                .isInstanceOf(McpException.class)
                .extracting(ex -> ((McpException) ex).getErrorCode())
                .isEqualTo(McpErrorCodes.RESOURCE_FORBIDDEN);
    }

    @Test
    void checkResourceOwnership_unscopedCaller_alwaysPasses() {
        ToolInvocationContext ctx = new ToolInvocationContext(Set.of("PAYMENT_READ"), null, "c1", "t1", "unknown");
        service.checkResourceOwnership(ctx, "payment.lookup", "P1", "P2");
        assertThat(ctx.participantId()).isNull();
    }
}
