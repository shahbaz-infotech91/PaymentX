package com.paymentx.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentx.mcp.client.AuditServiceClient;
import com.paymentx.mcp.config.McpGatewayProperties;
import com.paymentx.mcp.exception.McpErrorCodes;
import com.paymentx.mcp.exception.McpException;
import com.paymentx.mcp.registry.ToolInvocationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * English:
 * Mockito-based unit tests for tool/AuditSearchTool - AuditServiceClient
 * is mocked. Covers Step 14 (force-scoping the participantId filter for
 * a scoped caller, even overriding a caller-supplied mismatched value),
 * Step 17/18 (the real raw `payload` field is never present in the
 * output), and Step 44/45 (bounded pagination - size above the
 * configured max is rejected, never silently clamped, so the AI gets an
 * honest error rather than a quietly-truncated result set).
 *
 * Hinglish:
 * tool/AuditSearchTool ke liye Mockito-based unit tests -
 * AuditServiceClient mocked hai. Step 14 (ek scoped caller ke liye
 * participantId filter ko force-scope karna, ek caller-supplied
 * mismatched value ko bhi override karte hue), Step 17/18 (real raw
 * `payload` field output me kabhi present nahi hota), aur Step 44/45
 * (bounded pagination - configured max se upar ka size reject hota hai,
 * kabhi silently clamp nahi hota, taaki AI ko ek honest error mile, ek
 * quietly-truncated result set nahi) cover karta hai.
 */
@ExtendWith(MockitoExtension.class)
class AuditSearchToolTest {

    @Mock
    private AuditServiceClient auditServiceClient;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final McpGatewayProperties properties = new McpGatewayProperties();

    private JsonNode pageJson() throws Exception {
        return objectMapper.readTree("""
                {"content": [
                   {"id": "e1", "eventType": "API_REQUEST", "eventStatus": "RECORDED", "sourceService": "mcp-gateway",
                    "correlationId": "c1", "paymentId": "PMT-1", "participantId": "P1", "reference": "payment.lookup",
                    "payload": "{\\"secret\\":\\"should-never-appear\\"}", "occurredAt": "2026-08-01T00:00:00Z", "createdAt": "2026-08-01T00:00:00Z"}
                 ], "pageNumber": 0, "pageSize": 20, "totalElements": 1, "totalPages": 1, "first": true, "last": true}
                """);
    }

    @Test
    void execute_realResult_neverIncludesRawPayloadField() throws Exception {
        AuditSearchTool tool = new AuditSearchTool(auditServiceClient, properties);
        when(auditServiceClient.search(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), anyInt(), anyInt(), anyString()))
                .thenReturn(pageJson());

        ToolInvocationContext ctx = new ToolInvocationContext(Set.of("AUDIT_READ"), null, "corr-1", "trace-1", "unknown");
        Map<String, Object> result = tool.execute(ctx, Map.of());

        @SuppressWarnings("unchecked")
        var content = (java.util.List<Map<String, Object>>) result.get("content");
        assertThat(content).hasSize(1);
        assertThat(content.get(0)).doesNotContainKey("payload");
        assertThat(content.get(0).get("id")).isEqualTo("e1");
    }

    @Test
    void execute_scopedCaller_forcesParticipantIdFilterOverridingSuppliedValue() throws Exception {
        AuditSearchTool tool = new AuditSearchTool(auditServiceClient, properties);
        when(auditServiceClient.search(isNull(), isNull(), eq("P1"), isNull(), isNull(), isNull(), isNull(), isNull(), anyInt(), anyInt(), anyString()))
                .thenReturn(pageJson());

        ToolInvocationContext ctx = new ToolInvocationContext(Set.of("AUDIT_READ"), "P1", "corr-1", "trace-1", "P1");
        tool.execute(ctx, Map.of("participantId", "P2"));

        ArgumentCaptor<String> participantCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditServiceClient).search(isNull(), isNull(), participantCaptor.capture(), isNull(), isNull(), isNull(), isNull(), isNull(), anyInt(), anyInt(), anyString());
        assertThat(participantCaptor.getValue()).isEqualTo("P1");
    }

    @Test
    void execute_sizeAboveMax_throwsInvalidToolArgumentsRatherThanClamping() {
        AuditSearchTool tool = new AuditSearchTool(auditServiceClient, properties);
        ToolInvocationContext ctx = new ToolInvocationContext(Set.of("AUDIT_READ"), null, "corr-1", "trace-1", "unknown");

        assertThatThrownBy(() -> tool.execute(ctx, Map.of("size", properties.getAuditSearchMaxPageSize() + 1)))
                .isInstanceOf(McpException.class)
                .extracting(ex -> ((McpException) ex).getErrorCode())
                .isEqualTo(McpErrorCodes.INVALID_TOOL_ARGUMENTS);
    }

    @Test
    void execute_invalidStatusValue_throwsInvalidToolArguments() {
        AuditSearchTool tool = new AuditSearchTool(auditServiceClient, properties);
        ToolInvocationContext ctx = new ToolInvocationContext(Set.of("AUDIT_READ"), null, "corr-1", "trace-1", "unknown");

        assertThatThrownBy(() -> tool.execute(ctx, Map.of("status", "NOT_A_REAL_STATUS")))
                .isInstanceOf(McpException.class)
                .extracting(ex -> ((McpException) ex).getErrorCode())
                .isEqualTo(McpErrorCodes.INVALID_TOOL_ARGUMENTS);
    }

    @Test
    void execute_malformedDate_throwsInvalidToolArguments() {
        AuditSearchTool tool = new AuditSearchTool(auditServiceClient, properties);
        ToolInvocationContext ctx = new ToolInvocationContext(Set.of("AUDIT_READ"), null, "corr-1", "trace-1", "unknown");

        assertThatThrownBy(() -> tool.execute(ctx, Map.of("fromDate", "not-a-date")))
                .isInstanceOf(McpException.class)
                .extracting(ex -> ((McpException) ex).getErrorCode())
                .isEqualTo(McpErrorCodes.INVALID_TOOL_ARGUMENTS);
    }
}
