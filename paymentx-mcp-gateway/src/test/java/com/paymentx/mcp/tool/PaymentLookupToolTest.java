package com.paymentx.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentx.mcp.client.PaymentServiceClient;
import com.paymentx.mcp.exception.McpErrorCodes;
import com.paymentx.mcp.exception.McpException;
import com.paymentx.mcp.registry.ToolInvocationContext;
import com.paymentx.mcp.security.ToolAuthorizationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * English:
 * Mockito-based unit tests for tool/PaymentLookupTool - PaymentServiceClient
 * is mocked (its own real-wire behavior is covered by
 * client/PaymentServiceClientTest and the real end-to-end
 * controller/McpProtocolIntegrationTest), a REAL ToolAuthorizationService
 * is used so Step 14's resource-boundary enforcement is genuinely
 * exercised. Covers Step 12/15/17/18/19/33's account masking,
 * data-minimization, input-validation, business-not-found, and
 * resource-forbidden behavior directly at the tool layer.
 *
 * Hinglish:
 * tool/PaymentLookupTool ke liye Mockito-based unit tests -
 * PaymentServiceClient mocked hai (uska apna real-wire behavior
 * client/PaymentServiceClientTest aur real end-to-end
 * controller/McpProtocolIntegrationTest se cover hota hai), ek REAL
 * ToolAuthorizationService use hoti hai taaki Step 14 ka resource-
 * boundary enforcement genuinely exercise ho. Step 12/15/17/18/19/33 ka
 * account masking, data-minimization, input-validation, business-not-
 * found, aur resource-forbidden behavior seedhe tool layer par cover
 * karta hai.
 */
@ExtendWith(MockitoExtension.class)
class PaymentLookupToolTest {

    @Mock
    private PaymentServiceClient paymentServiceClient;

    private final ToolAuthorizationService authorizationService = new ToolAuthorizationService();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private JsonNode paymentJson(String debtorAccount, String debtorParticipantId, String creditorAccount, String creditorParticipantId) throws Exception {
        return objectMapper.readTree("""
                {"paymentReference": "PMT-1", "status": "COMPLETED", "scheme": "INSTANT_PAYMENT",
                 "amount": {"amount": 10.00, "currency": "USD"},
                 "debtorAccount": "%s", "debtorParticipantId": "%s",
                 "creditorAccount": "%s", "creditorParticipantId": "%s",
                 "failureReason": null, "createdAt": "2026-08-01T00:00:00Z", "updatedAt": "2026-08-01T00:01:00Z"}
                """.formatted(debtorAccount, debtorParticipantId, creditorAccount, creditorParticipantId));
    }

    @Test
    void execute_realResult_masksAccountNumbersAndOmitsInternalIds() throws Exception {
        PaymentLookupTool tool = new PaymentLookupTool(paymentServiceClient, authorizationService);
        when(paymentServiceClient.getByReference("PMT-1", "corr-1"))
                .thenReturn(Optional.of(paymentJson("1234567890", "P1", "9876543210", "P2")));

        ToolInvocationContext ctx = new ToolInvocationContext(Set.of("PAYMENT_READ"), null, "corr-1", "trace-1", "caller1");
        Map<String, Object> result = tool.execute(ctx, Map.of("paymentReference", "PMT-1"));

        assertThat(result.get("debtorAccountMasked")).isEqualTo("******7890");
        assertThat(result.get("creditorAccountMasked")).isEqualTo("******3210");
        assertThat(result).doesNotContainKey("id");
        assertThat(result).doesNotContainKey("traceId");
        assertThat(result).doesNotContainKey("correlationId");
    }

    @Test
    void execute_realNotFound_returnsFoundFalseNotAnException() {
        PaymentLookupTool tool = new PaymentLookupTool(paymentServiceClient, authorizationService);
        when(paymentServiceClient.getByReference("PMT-MISSING", "corr-1")).thenReturn(Optional.empty());

        ToolInvocationContext ctx = new ToolInvocationContext(Set.of("PAYMENT_READ"), null, "corr-1", "trace-1", "caller1");
        Map<String, Object> result = tool.execute(ctx, Map.of("paymentReference", "PMT-MISSING"));

        assertThat(result.get("found")).isEqualTo(false);
    }

    @Test
    void execute_callerScopedToUnrelatedParticipant_throwsResourceForbidden() throws Exception {
        PaymentLookupTool tool = new PaymentLookupTool(paymentServiceClient, authorizationService);
        when(paymentServiceClient.getByReference("PMT-1", "corr-1"))
                .thenReturn(Optional.of(paymentJson("1234567890", "P1", "9876543210", "P2")));

        ToolInvocationContext ctx = new ToolInvocationContext(Set.of("PAYMENT_READ"), "P9", "corr-1", "trace-1", "P9");

        assertThatThrownBy(() -> tool.execute(ctx, Map.of("paymentReference", "PMT-1")))
                .isInstanceOf(McpException.class)
                .extracting(ex -> ((McpException) ex).getErrorCode())
                .isEqualTo(McpErrorCodes.RESOURCE_FORBIDDEN);
    }

    @Test
    void execute_callerScopedAsDebtor_isAllowed() throws Exception {
        PaymentLookupTool tool = new PaymentLookupTool(paymentServiceClient, authorizationService);
        when(paymentServiceClient.getByReference("PMT-1", "corr-1"))
                .thenReturn(Optional.of(paymentJson("1234567890", "P1", "9876543210", "P2")));

        ToolInvocationContext ctx = new ToolInvocationContext(Set.of("PAYMENT_READ"), "P1", "corr-1", "trace-1", "P1");
        Map<String, Object> result = tool.execute(ctx, Map.of("paymentReference", "PMT-1"));

        assertThat(result.get("found")).isEqualTo(true);
    }

    @Test
    void execute_invalidPaymentReference_throwsInvalidToolArgumentsWithoutCallingClient() {
        PaymentLookupTool tool = new PaymentLookupTool(paymentServiceClient, authorizationService);
        ToolInvocationContext ctx = new ToolInvocationContext(Set.of("PAYMENT_READ"), null, "corr-1", "trace-1", "caller1");

        assertThatThrownBy(() -> tool.execute(ctx, Map.of("paymentReference", "not valid!")))
                .isInstanceOf(McpException.class)
                .extracting(ex -> ((McpException) ex).getErrorCode())
                .isEqualTo(McpErrorCodes.INVALID_TOOL_ARGUMENTS);
    }

    @Test
    void execute_missingPaymentReference_throwsInvalidToolArguments() {
        PaymentLookupTool tool = new PaymentLookupTool(paymentServiceClient, authorizationService);
        ToolInvocationContext ctx = new ToolInvocationContext(Set.of("PAYMENT_READ"), null, "corr-1", "trace-1", "caller1");

        assertThatThrownBy(() -> tool.execute(ctx, Map.of()))
                .isInstanceOf(McpException.class)
                .extracting(ex -> ((McpException) ex).getErrorCode())
                .isEqualTo(McpErrorCodes.INVALID_TOOL_ARGUMENTS);
    }
}
