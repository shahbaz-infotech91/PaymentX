package com.paymentx.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentx.mcp.client.ReconciliationServiceClient;
import com.paymentx.mcp.exception.McpErrorCodes;
import com.paymentx.mcp.exception.McpException;
import com.paymentx.mcp.registry.ToolInvocationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 4.6.0 - unit tests for tool/ReconciliationStatusTool. Covers both the pre-existing
 * batchId behavior (regression - must be byte-for-byte unchanged) and the new paymentReference
 * branch (the paymentReference -> batchId bridge added this phase). ReconciliationServiceClient
 * is mocked, mirroring PaymentLookupToolTest/DatabaseStatisticsToolTest's own established
 * pattern for this test layer.
 */
@ExtendWith(MockitoExtension.class)
class ReconciliationStatusToolTest {

    @Mock
    private ReconciliationServiceClient reconciliationServiceClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ToolInvocationContext ctx() {
        return new ToolInvocationContext(Set.of("RECONCILIATION_READ"), null, "corr-1", "trace-1", "caller1");
    }

    // ---- batchId branch (pre-existing behavior, must be unaffected) ----

    @Test
    void execute_byBatchId_realResult_returnsBatchFields() throws Exception {
        ReconciliationStatusTool tool = new ReconciliationStatusTool(reconciliationServiceClient);
        UUID batchId = UUID.randomUUID();
        JsonNode batch = objectMapper.readTree("""
                {"batchType": "FULL", "status": "COMPLETED", "windowFrom": null, "windowTo": null,
                 "startedAt": "2026-08-01T00:00:00Z", "completedAt": "2026-08-01T00:05:00Z",
                 "totalRecords": 10, "matchedCount": 9, "mismatchCount": 1, "failureReason": null}
                """);
        when(reconciliationServiceClient.getBatchStatus(batchId.toString(), "corr-1")).thenReturn(Optional.of(batch));

        Map<String, Object> result = tool.execute(ctx(), Map.of("batchId", batchId.toString()));

        assertThat(result.get("found")).isEqualTo(true);
        assertThat(result.get("batchId")).isEqualTo(batchId.toString());
        assertThat(result.get("status")).isEqualTo("COMPLETED");
        verify(reconciliationServiceClient, never()).getRecordsByReference(anyString(), anyString());
    }

    @Test
    void execute_byBatchId_notFound_returnsFoundFalseNotAnException() {
        ReconciliationStatusTool tool = new ReconciliationStatusTool(reconciliationServiceClient);
        UUID batchId = UUID.randomUUID();
        when(reconciliationServiceClient.getBatchStatus(batchId.toString(), "corr-1")).thenReturn(Optional.empty());

        Map<String, Object> result = tool.execute(ctx(), Map.of("batchId", batchId.toString()));

        assertThat(result.get("found")).isEqualTo(false);
    }

    @Test
    void execute_byBatchId_invalidUuid_throwsInvalidToolArguments() {
        ReconciliationStatusTool tool = new ReconciliationStatusTool(reconciliationServiceClient);

        assertThatThrownBy(() -> tool.execute(ctx(), Map.of("batchId", "not-a-uuid")))
                .isInstanceOf(McpException.class)
                .extracting(ex -> ((McpException) ex).getErrorCode())
                .isEqualTo(McpErrorCodes.INVALID_TOOL_ARGUMENTS);
    }

    // ---- paymentReference branch (Phase 4.6.0 new capability) ----

    @Test
    void execute_byPaymentReference_realRecordExists_returnsRecordFieldsAndBridgesBatchId() throws Exception {
        ReconciliationStatusTool tool = new ReconciliationStatusTool(reconciliationServiceClient);
        UUID batchId = UUID.randomUUID();
        JsonNode records = objectMapper.readTree("""
                [{"id": "%s", "batchId": "%s", "paymentId": "internal-id-should-not-appear",
                  "referenceId": "PMT-BRIDGE-1", "participantId": "BANK001",
                  "internalAmount": 100.00, "externalAmount": 100.00,
                  "internalCurrency": "USD", "externalCurrency": "USD",
                  "internalStatus": "COMPLETED", "externalStatus": "SETTLED",
                  "internalSettlementDate": "2026-08-01T00:00:00Z", "externalSettlementDate": "2026-08-01T00:00:05Z",
                  "reconciliationStatus": "MATCHED"}]
                """.formatted(UUID.randomUUID(), batchId));
        when(reconciliationServiceClient.getRecordsByReference("PMT-BRIDGE-1", "corr-1")).thenReturn(Optional.of(records));

        Map<String, Object> result = tool.execute(ctx(), Map.of("paymentReference", "PMT-BRIDGE-1"));

        assertThat(result.get("found")).isEqualTo(true);
        assertThat(result.get("batchId")).isEqualTo(batchId.toString());
        assertThat(result.get("reconciliationStatus")).isEqualTo("MATCHED");
        assertThat(result.get("participantId")).isEqualTo("BANK001");
        assertThat(result).doesNotContainKey("paymentId");
        assertThat(result).doesNotContainKey("id");
    }

    @Test
    void execute_byPaymentReference_neverReconciled_returnsFoundFalseNotAnException() throws Exception {
        ReconciliationStatusTool tool = new ReconciliationStatusTool(reconciliationServiceClient);
        JsonNode emptyArray = objectMapper.readTree("[]");
        when(reconciliationServiceClient.getRecordsByReference("PMT-NEVER-RECONCILED", "corr-1")).thenReturn(Optional.of(emptyArray));

        Map<String, Object> result = tool.execute(ctx(), Map.of("paymentReference", "PMT-NEVER-RECONCILED"));

        assertThat(result.get("found")).isEqualTo(false);
        assertThat(result.get("paymentReference")).isEqualTo("PMT-NEVER-RECONCILED");
    }

    @Test
    void execute_byPaymentReference_includeSummaryTrue_mergesRealSummary() throws Exception {
        ReconciliationStatusTool tool = new ReconciliationStatusTool(reconciliationServiceClient);
        UUID batchId = UUID.randomUUID();
        JsonNode records = objectMapper.readTree("""
                [{"batchId": "%s", "referenceId": "PMT-BRIDGE-2", "reconciliationStatus": "AMOUNT_MISMATCH"}]
                """.formatted(batchId));
        JsonNode summary = objectMapper.readTree("""
                {"totalRecords": 5, "matchedCount": 4, "amountMismatchCount": 1}
                """);
        when(reconciliationServiceClient.getRecordsByReference("PMT-BRIDGE-2", "corr-1")).thenReturn(Optional.of(records));
        when(reconciliationServiceClient.getSummary(batchId.toString(), "corr-1")).thenReturn(Optional.of(summary));

        Map<String, Object> result = tool.execute(ctx(), Map.of("paymentReference", "PMT-BRIDGE-2", "includeSummary", true));

        assertThat(result).containsKey("summary");
        @SuppressWarnings("unchecked")
        Map<String, Object> summaryOutput = (Map<String, Object>) result.get("summary");
        assertThat(summaryOutput.get("amountMismatchCount")).isEqualTo(1);
    }

    @Test
    void execute_invalidPaymentReference_throwsInvalidToolArgumentsWithoutCallingClient() {
        ReconciliationStatusTool tool = new ReconciliationStatusTool(reconciliationServiceClient);

        assertThatThrownBy(() -> tool.execute(ctx(), Map.of("paymentReference", "not valid!")))
                .isInstanceOf(McpException.class)
                .extracting(ex -> ((McpException) ex).getErrorCode())
                .isEqualTo(McpErrorCodes.INVALID_TOOL_ARGUMENTS);

        verify(reconciliationServiceClient, never()).getRecordsByReference(anyString(), anyString());
    }

    @Test
    void execute_neitherBatchIdNorPaymentReference_throwsInvalidToolArguments() {
        ReconciliationStatusTool tool = new ReconciliationStatusTool(reconciliationServiceClient);

        assertThatThrownBy(() -> tool.execute(ctx(), Map.of()))
                .isInstanceOf(McpException.class)
                .extracting(ex -> ((McpException) ex).getErrorCode())
                .isEqualTo(McpErrorCodes.INVALID_TOOL_ARGUMENTS);
    }

    @Test
    void execute_bothBatchIdAndPaymentReference_batchIdTakesPriority_noRegressionForExistingCallers() throws Exception {
        ReconciliationStatusTool tool = new ReconciliationStatusTool(reconciliationServiceClient);
        UUID batchId = UUID.randomUUID();
        JsonNode batch = objectMapper.readTree("""
                {"batchType": "FULL", "status": "COMPLETED", "totalRecords": 1, "matchedCount": 1, "mismatchCount": 0}
                """);
        when(reconciliationServiceClient.getBatchStatus(batchId.toString(), "corr-1")).thenReturn(Optional.of(batch));

        Map<String, Object> result = tool.execute(ctx(), Map.of("batchId", batchId.toString(), "paymentReference", "PMT-IGNORED"));

        assertThat(result.get("found")).isEqualTo(true);
        assertThat(result.get("batchId")).isEqualTo(batchId.toString());
        verify(reconciliationServiceClient, never()).getRecordsByReference(anyString(), anyString());
    }

}
