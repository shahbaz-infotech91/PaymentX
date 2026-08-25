package com.paymentx.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentx.mcp.client.ControlCenterClient;
import com.paymentx.mcp.exception.McpErrorCodes;
import com.paymentx.mcp.exception.McpException;
import com.paymentx.mcp.registry.ToolInvocationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Phase 4.4 - Mockito-based unit tests for tool/DatabaseStatisticsTool, matching
 * PaymentLookupToolTest's own established pattern (ControlCenterClient mocked, its own real-wire
 * behavior covered by client/ControlCenterClientTest).
 */
@ExtendWith(MockitoExtension.class)
class DatabaseStatisticsToolTest {

    @Mock
    private ControlCenterClient controlCenterClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ToolInvocationContext ctx() {
        return new ToolInvocationContext(Set.of("DATABASE_READ"), null, "corr-1", "trace-1", "caller1");
    }

    @Test
    void definition_isReadOnly_lowRisk_requiresDatabaseRead() {
        DatabaseStatisticsTool tool = new DatabaseStatisticsTool(controlCenterClient);

        assertThat(tool.definition().name()).isEqualTo("database.statistics");
        assertThat(tool.definition().readWrite()).isEqualTo(com.paymentx.mcp.registry.ToolReadWrite.READ_ONLY);
        assertThat(tool.definition().riskLevel()).isEqualTo(com.paymentx.mcp.registry.ToolRiskLevel.LOW);
        assertThat(tool.definition().requiredPermission()).isEqualTo("DATABASE_READ");
    }

    @Test
    void execute_paymentStatusDistribution_realResult_returnsAggregateCountsOnly() throws Exception {
        DatabaseStatisticsTool tool = new DatabaseStatisticsTool(controlCenterClient);
        JsonNode stats = objectMapper.readTree("""
                {"totalPayments": 100, "successful": 80, "failed": 10, "pending": 5, "processing": 5,
                 "successRatePercent": 80.0, "failureRatePercent": 10.0, "averageLatencyMillis": 1250.5,
                 "paymentsLastHour": 12, "tps": 0.0033}
                """);
        when(controlCenterClient.getPaymentStats("corr-1")).thenReturn(stats);

        Map<String, Object> result = tool.execute(ctx(), Map.of("operation", "PAYMENT_STATUS_DISTRIBUTION"));

        assertThat(result.get("totalPayments")).isEqualTo(100L);
        assertThat(result.get("successRatePercent")).isEqualTo(80.0);
        assertThat(result).doesNotContainKey("rawRows");
    }

    @Test
    void execute_tableInfo_realResult_returnsTableNamesAndRowCountsOnly() throws Exception {
        DatabaseStatisticsTool tool = new DatabaseStatisticsTool(controlCenterClient);
        JsonNode tables = objectMapper.readTree("""
                [{"tableName": "payment", "rowCount": 100}, {"tableName": "payment_status_history", "rowCount": 250}]
                """);
        when(controlCenterClient.getTableInfo("payment", "corr-1")).thenReturn(tables);

        Map<String, Object> result = tool.execute(ctx(), Map.of("operation", "TABLE_INFO", "database", "payment"));

        assertThat(result.get("database")).isEqualTo("payment");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tableList = (List<Map<String, Object>>) result.get("tables");
        assertThat(tableList).hasSize(2);
        assertThat(tableList.get(0)).containsOnlyKeys("tableName", "rowCount");
    }

    @Test
    void execute_tableInfo_unknownDatabaseSlug_throwsInvalidArgumentsWithoutCallingClient() {
        DatabaseStatisticsTool tool = new DatabaseStatisticsTool(controlCenterClient);

        assertThatThrownBy(() -> tool.execute(ctx(), Map.of("operation", "TABLE_INFO", "database", "not_a_real_database")))
                .isInstanceOf(McpException.class)
                .extracting(ex -> ((McpException) ex).getErrorCode())
                .isEqualTo(McpErrorCodes.INVALID_TOOL_ARGUMENTS);
    }

    @Test
    void execute_tableInfo_missingDatabase_throwsInvalidArguments() {
        DatabaseStatisticsTool tool = new DatabaseStatisticsTool(controlCenterClient);

        assertThatThrownBy(() -> tool.execute(ctx(), Map.of("operation", "TABLE_INFO")))
                .isInstanceOf(McpException.class)
                .extracting(ex -> ((McpException) ex).getErrorCode())
                .isEqualTo(McpErrorCodes.INVALID_TOOL_ARGUMENTS);
    }

    @Test
    void execute_unknownOperation_throwsInvalidArgumentsWithoutCallingClient() {
        DatabaseStatisticsTool tool = new DatabaseStatisticsTool(controlCenterClient);

        assertThatThrownBy(() -> tool.execute(ctx(), Map.of("operation", "DELETE_ALL_PAYMENTS")))
                .isInstanceOf(McpException.class)
                .extracting(ex -> ((McpException) ex).getErrorCode())
                .isEqualTo(McpErrorCodes.INVALID_TOOL_ARGUMENTS);
    }

    @Test
    void execute_missingOperation_throwsInvalidArguments() {
        DatabaseStatisticsTool tool = new DatabaseStatisticsTool(controlCenterClient);

        assertThatThrownBy(() -> tool.execute(ctx(), Map.of()))
                .isInstanceOf(McpException.class)
                .extracting(ex -> ((McpException) ex).getErrorCode())
                .isEqualTo(McpErrorCodes.INVALID_TOOL_ARGUMENTS);
    }

    @Test
    void execute_sqlInjectionShapedOperationValue_rejectedAsInvalidArgument_neverReachesClient() {
        DatabaseStatisticsTool tool = new DatabaseStatisticsTool(controlCenterClient);

        assertThatThrownBy(() -> tool.execute(ctx(),
                Map.of("operation", "PAYMENT_STATUS_DISTRIBUTION; DROP TABLE payment;--")))
                .isInstanceOf(McpException.class)
                .extracting(ex -> ((McpException) ex).getErrorCode())
                .isEqualTo(McpErrorCodes.INVALID_TOOL_ARGUMENTS);
    }

    @Test
    void execute_sqlInjectionShapedDatabaseValue_rejectedAsInvalidArgument_neverReachesClient() {
        DatabaseStatisticsTool tool = new DatabaseStatisticsTool(controlCenterClient);

        assertThatThrownBy(() -> tool.execute(ctx(),
                Map.of("operation", "TABLE_INFO", "database", "payment; DROP TABLE payment;--")))
                .isInstanceOf(McpException.class)
                .extracting(ex -> ((McpException) ex).getErrorCode())
                .isEqualTo(McpErrorCodes.INVALID_TOOL_ARGUMENTS);
    }

    // ================================================================
    // Phase 4.4 task section 18, items 6-12 - this tool NEVER accepts a SQL string at all, only an
    // "operation" argument constrained to exactly {PAYMENT_STATUS_DISTRIBUTION, TABLE_INFO} and,
    // for TABLE_INFO, a "database" argument constrained to a fixed 7-value slug allowlist. There is
    // therefore no SQL parser to bypass with comments/multiple-statements/case tricks - the tool's
    // own strict Set.contains() equality check rejects every one of these for the same structural
    // reason it rejects any other unrecognized value. A stronger property than a regex-based SQL
    // validator would provide (task's own instruction: "Do not create a weak regex-only security
    // layer if the existing architecture provides a stronger approach").
    // ================================================================

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "INSERT INTO payment (status) VALUES ('SETTLED')",
            "UPDATE payment SET status='SETTLED'",
            "DELETE FROM payment",
            "DROP TABLE payment",
            "TRUNCATE payment",
            "ALTER TABLE payment ADD COLUMN hacked boolean",
            "CREATE TABLE evil (id int)",
            "GRANT ALL ON payment TO public",
            "REVOKE ALL ON payment FROM public",
            "CALL some_mutating_procedure()",
            "PAYMENT_STATUS_DISTRIBUTION; DELETE FROM payment;",
            "TABLE_INFO -- DROP TABLE payment",
            "TABLE_INFO /* comment */",
            "PAYMENT_STATUS_DISTRIBUTION#comment",
            "payment_status_distribution", "Payment_Status_Distribution",
            " PAYMENT_STATUS_DISTRIBUTION", "PAYMENT_STATUS_DISTRIBUTION ",
            "table_info", "Table_Info"
    })
    void execute_everyWriteDdlInjectionCommentCaseWhitespaceVariant_rejectedAsInvalidOperation_neverReachesClient(String malicious) {
        DatabaseStatisticsTool tool = new DatabaseStatisticsTool(controlCenterClient);

        assertThatThrownBy(() -> tool.execute(ctx(), Map.of("operation", malicious)))
                .isInstanceOf(McpException.class)
                .extracting(ex -> ((McpException) ex).getErrorCode())
                .isEqualTo(McpErrorCodes.INVALID_TOOL_ARGUMENTS);
        org.mockito.Mockito.verifyNoInteractions(controlCenterClient);
    }
}
