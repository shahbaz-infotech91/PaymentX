package com.paymentx.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.paymentx.mcp.client.ControlCenterClient;
import com.paymentx.mcp.exception.McpException;
import com.paymentx.mcp.registry.McpToolDefinition;
import com.paymentx.mcp.registry.PaymentXTool;
import com.paymentx.mcp.registry.ToolInvocationContext;
import com.paymentx.mcp.registry.ToolReadWrite;
import com.paymentx.mcp.registry.ToolRiskLevel;
import com.paymentx.mcp.security.ToolPermissions;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * English:
 * Phase 4.4's real `database.statistics` tool - the platform's first new MCP tool since Phase 3.7,
 * built only after direct source inspection confirmed none of the five existing tools (payment.lookup/
 * payment.status/routing.lookup/reconciliation.status/audit.search) provide aggregate/schema-level
 * database evidence (payment status distribution, table row counts) the way Control Center's own,
 * already-existing, read-only PostgresController does (see PAYMENTX_PHASE_4_4_DATABASE_ANALYSIS.md §3/§4
 * for the full gap analysis and why calling Control Center directly, bypassing MCP's own two-gate
 * chain, was rejected instead).
 * Exposes exactly two operations, both already-existing, already-hardcoded/parameterized Control
 * Center queries reused verbatim through client/ControlCenterClient - this tool contains zero SQL of
 * its own:
 *   - PAYMENT_STATUS_DISTRIBUTION: real GROUP BY status COUNT(*) aggregate over paymentx_payment.payment
 *     (Control Center's PaymentStatsSummary) - counts and percentages only, never a raw row.
 *   - TABLE_INFO: real information_schema.tables + COUNT(*) per table for one of the 7 real,
 *     Liquibase-managed PaymentX databases (Control Center's own PostgresDatabaseIdentifier allowlist,
 *     mirrored here as DATABASE_SLUGS since this module keeps no compile-time dependency on Control
 *     Center's own DTOs, matching client/PaymentServiceClient's own precedent) - table names and row
 *     counts only, never a raw row from any table.
 * Both operations are inherently, structurally read-only and bounded: neither Control Center query
 * accepts a caller-supplied predicate, LIMIT override, or column selection - the ENTIRE output shape is
 * fixed by the query itself, so there is no SQL-injection surface, no unbounded-extraction surface, and
 * no read-only-enforcement burden this tool needs to add on top - the safety already lives in
 * PostgresController/PostgresDataService's own hardcoded query methods (verified by direct source read),
 * not in any validation this tool performs.
 * Why it exists: Phase 4.4 - Database Analysis Agent's only new capability requirement.
 * How it communicates with other components: registered by registry/ToolRegistry as a Spring bean;
 * invoked by ToolInvoker after tool-level authorization/rate-limiting already passed (Step 12/13,
 * unchanged); calls client/ControlCenterClient, the ONE class in this module that talks Control
 * Center's real wire format.
 *
 * Hinglish:
 * Phase 4.4 ka real `database.statistics` tool - Phase 3.7 ke baad is platform ka pehla naya MCP tool,
 * sirf direct source inspection confirm karne ke baad banaya gaya ki maujooda paanch tools me se koi bhi
 * aggregate/schema-level database evidence nahi deta jo Control Center ka apna, already-existing,
 * read-only PostgresController deta hai. Exactly do operations expose karta hai, dono already-existing,
 * already-hardcoded/parameterized Control Center queries verbatim reuse karte hue - is tool me khud ka
 * zero SQL hai. Dono operations inherently, structurally read-only aur bounded hain: koi bhi Control
 * Center query caller-supplied predicate, LIMIT override, ya column selection accept nahi karti.
 */
@Component
public class DatabaseStatisticsTool implements PaymentXTool {

    /** Mirrors Control Center's own PostgresDatabaseIdentifier slugs exactly (7 real, Liquibase-managed
     * PaymentX databases) - kept as a local, fixed allowlist rather than a compile-time dependency on
     * Control Center's own enum, matching every other client class's "no cross-module DTO dependency"
     * precedent in this codebase. An unknown slug is rejected here, before any HTTP call is made -
     * never forwarded as an arbitrary path segment. */
    private static final Set<String> DATABASE_SLUGS = Set.of(
            "validation", "payment", "routing", "audit", "notification", "reconciliation", "reporting");
    private static final Set<String> OPERATIONS = Set.of("PAYMENT_STATUS_DISTRIBUTION", "TABLE_INFO");

    private final ControlCenterClient controlCenterClient;
    private final McpToolDefinition definition;

    public DatabaseStatisticsTool(ControlCenterClient controlCenterClient) {
        this.controlCenterClient = controlCenterClient;
        this.definition = new McpToolDefinition(
                "database.statistics",
                "Retrieve read-only, aggregate PaymentX database evidence: either the current payment "
                        + "status distribution (PAYMENT_STATUS_DISTRIBUTION) or table names and row counts for one "
                        + "of the real PaymentX databases (TABLE_INFO). Never returns raw rows, never accepts a SQL "
                        + "query, and never modifies any data.",
                new McpSchema.JsonSchema("object", Map.of(
                        "operation", Map.of(
                                "type", "string",
                                "enum", List.copyOf(OPERATIONS),
                                "description", "PAYMENT_STATUS_DISTRIBUTION for aggregate payment counts by status, "
                                        + "or TABLE_INFO for table names/row counts in one database."),
                        "database", Map.of(
                                "type", "string",
                                "enum", List.copyOf(DATABASE_SLUGS),
                                "description", "Required only for TABLE_INFO - one of the 7 real PaymentX database slugs.")
                ), List.of("operation"), false, null, null),
                ToolPermissions.DATABASE_READ,
                ToolRiskLevel.LOW,
                ToolReadWrite.READ_ONLY,
                Duration.ofSeconds(5),
                true,
                "DATABASE_METADATA_READ"
        );
    }

    @Override
    public McpToolDefinition definition() {
        return definition;
    }

    @Override
    public Map<String, Object> execute(ToolInvocationContext context, Map<String, Object> arguments) {
        Object rawOperation = arguments.get("operation");
        if (!(rawOperation instanceof String operation) || !OPERATIONS.contains(operation)) {
            throw McpException.invalidArguments("operation must be one of: " + OPERATIONS);
        }

        return switch (operation) {
            case "PAYMENT_STATUS_DISTRIBUTION" -> paymentStatusDistribution(context.correlationId());
            case "TABLE_INFO" -> tableInfo(arguments, context.correlationId());
            default -> throw McpException.invalidArguments("Unsupported operation: " + operation);
        };
    }

    private Map<String, Object> paymentStatusDistribution(String correlationId) {
        JsonNode stats = controlCenterClient.getPaymentStats(correlationId);
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("operation", "PAYMENT_STATUS_DISTRIBUTION");
        output.put("totalPayments", stats.path("totalPayments").asLong(0));
        output.put("successful", stats.path("successful").asLong(0));
        output.put("failed", stats.path("failed").asLong(0));
        output.put("pending", stats.path("pending").asLong(0));
        output.put("processing", stats.path("processing").asLong(0));
        output.put("successRatePercent", stats.path("successRatePercent").asDouble(0));
        output.put("failureRatePercent", stats.path("failureRatePercent").asDouble(0));
        output.put("averageLatencyMillis", stats.path("averageLatencyMillis").isMissingNode() || stats.path("averageLatencyMillis").isNull()
                ? null : stats.path("averageLatencyMillis").asDouble());
        output.put("paymentsLastHour", stats.path("paymentsLastHour").asLong(0));
        output.put("tps", stats.path("tps").asDouble(0));
        return output;
    }

    private Map<String, Object> tableInfo(Map<String, Object> arguments, String correlationId) {
        Object rawDatabase = arguments.get("database");
        if (!(rawDatabase instanceof String database) || !DATABASE_SLUGS.contains(database)) {
            throw McpException.invalidArguments("database is required for TABLE_INFO and must be one of: " + DATABASE_SLUGS);
        }

        JsonNode tables = controlCenterClient.getTableInfo(database, correlationId);
        List<Map<String, Object>> tableList = tables.isArray()
                ? java.util.stream.StreamSupport.stream(tables.spliterator(), false)
                        .map(t -> {
                            Map<String, Object> row = new LinkedHashMap<>();
                            row.put("tableName", t.path("tableName").asText(null));
                            row.put("rowCount", t.path("rowCount").asLong(0));
                            return (Map<String, Object>) row;
                        })
                        .toList()
                : List.of();

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("operation", "TABLE_INFO");
        output.put("database", database);
        output.put("tables", tableList);
        return output;
    }
}
