package com.paymentx.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.paymentx.mcp.client.ReconciliationServiceClient;
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
import java.util.Optional;
import java.util.UUID;

/**
 * English:
 * The real `reconciliation.status` tool (Step 9). `batchId` is a real
 * UUID (reconciliation-service's own primary key - validated with
 * UUID.fromString before ever reaching the client, Step 15/16). When
 * `includeSummary` is true, also calls
 * ReconciliationServiceClient.getSummary() (GET
 * /api/v1/reconciliation/batches/{id}/summary) and merges the real
 * aggregate counts into the same result - both real, already-existing
 * ReconciliationController endpoints (Step 10/11). No resource-boundary
 * check (Step 14) is applied here - reconciliation batches are a
 * platform-wide settlement operation, not owned by or scoped to a
 * single participant in this data model (BatchResponse carries no
 * participant id at all), so there is no real per-participant ownership
 * to enforce; access is controlled purely by the RECONCILIATION_READ
 * permission. A real "no such batch id" 404 is returned as found=false,
 * not thrown as an error (Step 33).
 * Why it exists: Step 9.
 * How it communicates with other components: registered by
 * registry/ToolRegistry as a Spring bean; invoked by ToolInvoker after
 * tool-level authorization/rate-limiting already passed.
 *
 * Hinglish:
 * Ye real `reconciliation.status` tool hai (Step 9). `batchId` ek real
 * UUID hai (reconciliation-service ki apni primary key - client tak
 * pahunchne se pehle hi UUID.fromString se validate hota hai, Step
 * 15/16). Jab `includeSummary` true hai, ReconciliationServiceClient.getSummary()
 * (GET /api/v1/reconciliation/batches/{id}/summary) bhi call hota hai
 * aur real aggregate counts ko usi result me merge karta hai - dono
 * real, already-existing ReconciliationController endpoints (Step
 * 10/11). Yahan koi resource-boundary check (Step 14) apply nahi hota -
 * reconciliation batches ek platform-wide settlement operation hain, is
 * data model me kisi ek participant se owned ya scoped nahi hain
 * (BatchResponse bilkul koi participant id carry nahi karta), isliye
 * enforce karne ke liye koi real per-participant ownership hai hi
 * nahi; access sirf RECONCILIATION_READ permission se control hota hai.
 * Ek real "aisa koi batch id nahi hai" 404 found=false ke roop me
 * return hota hai, ek error ke roop me throw nahi hota (Step 33).
 * Ye kyu hai: Step 9.
 * Dusre components se kaise communicate karta hai: registry/ToolRegistry
 * dwara ek Spring bean ke roop me register hota hai; ToolInvoker dwara
 * invoke hota hai tool-level authorization/rate-limiting already pass
 * hone ke baad.
 */
@Component
public class ReconciliationStatusTool implements PaymentXTool {

    private final ReconciliationServiceClient reconciliationServiceClient;
    private final McpToolDefinition definition;

    public ReconciliationStatusTool(ReconciliationServiceClient reconciliationServiceClient) {
        this.reconciliationServiceClient = reconciliationServiceClient;
        this.definition = new McpToolDefinition(
                "reconciliation.status",
                "Retrieve a reconciliation batch's status, optionally including its aggregate summary. "
                        + "This tool is read-only and does not modify reconciliation data or trigger reprocessing.",
                new McpSchema.JsonSchema("object", Map.of(
                        "batchId", Map.of("type", "string", "description", "The reconciliation batch's UUID."),
                        "includeSummary", Map.of("type", "boolean", "description", "Optional, defaults to false. When true, also includes the batch's aggregate mismatch-taxonomy summary.")
                ), List.of("batchId"), false, null, null),
                ToolPermissions.RECONCILIATION_READ,
                ToolRiskLevel.LOW,
                ToolReadWrite.READ_ONLY,
                Duration.ofSeconds(5),
                true,
                "RECONCILIATION_DATA_READ"
        );
    }

    @Override
    public McpToolDefinition definition() {
        return definition;
    }

    @Override
    public Map<String, Object> execute(ToolInvocationContext context, Map<String, Object> arguments) {
        Object rawBatchId = arguments.get("batchId");
        UUID batchId;
        if (!(rawBatchId instanceof String batchIdText)) {
            throw McpException.invalidArguments("batchId is required.");
        }
        try {
            batchId = UUID.fromString(batchIdText);
        } catch (IllegalArgumentException notAUuid) {
            throw McpException.invalidArguments("batchId must be a valid UUID.");
        }

        boolean includeSummary = Boolean.TRUE.equals(arguments.get("includeSummary"));

        Optional<JsonNode> result = reconciliationServiceClient.getBatchStatus(batchId.toString(), context.correlationId());
        if (result.isEmpty()) {
            return Map.of("found", false, "batchId", batchId.toString());
        }

        JsonNode batch = result.get();
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("found", true);
        output.put("batchId", batchId.toString());
        output.put("batchType", batch.path("batchType").asText(null));
        output.put("status", batch.path("status").asText(null));
        output.put("windowFrom", batch.path("windowFrom").asText(null));
        output.put("windowTo", batch.path("windowTo").asText(null));
        output.put("startedAt", batch.path("startedAt").asText(null));
        output.put("completedAt", batch.path("completedAt").isMissingNode() ? null : batch.path("completedAt").asText(null));
        output.put("totalRecords", batch.path("totalRecords").isMissingNode() ? null : batch.path("totalRecords").asInt());
        output.put("matchedCount", batch.path("matchedCount").isMissingNode() ? null : batch.path("matchedCount").asInt());
        output.put("mismatchCount", batch.path("mismatchCount").isMissingNode() ? null : batch.path("mismatchCount").asInt());
        output.put("failureReason", batch.path("failureReason").isMissingNode() ? null : batch.path("failureReason").asText(null));

        if (includeSummary) {
            Optional<JsonNode> summaryResult = reconciliationServiceClient.getSummary(batchId.toString(), context.correlationId());
            summaryResult.ifPresent(summary -> {
                Map<String, Object> summaryOutput = new LinkedHashMap<>();
                summaryOutput.put("totalRecords", summary.path("totalRecords").isMissingNode() ? null : summary.path("totalRecords").asInt());
                summaryOutput.put("matchedCount", summary.path("matchedCount").isMissingNode() ? null : summary.path("matchedCount").asInt());
                summaryOutput.put("missingCount", summary.path("missingCount").isMissingNode() ? null : summary.path("missingCount").asInt());
                summaryOutput.put("duplicateCount", summary.path("duplicateCount").isMissingNode() ? null : summary.path("duplicateCount").asInt());
                summaryOutput.put("amountMismatchCount", summary.path("amountMismatchCount").isMissingNode() ? null : summary.path("amountMismatchCount").asInt());
                summaryOutput.put("currencyMismatchCount", summary.path("currencyMismatchCount").isMissingNode() ? null : summary.path("currencyMismatchCount").asInt());
                summaryOutput.put("statusMismatchCount", summary.path("statusMismatchCount").isMissingNode() ? null : summary.path("statusMismatchCount").asInt());
                summaryOutput.put("settlementDelayCount", summary.path("settlementDelayCount").isMissingNode() ? null : summary.path("settlementDelayCount").asInt());
                summaryOutput.put("lateSettlementCount", summary.path("lateSettlementCount").isMissingNode() ? null : summary.path("lateSettlementCount").asInt());
                summaryOutput.put("orphanCount", summary.path("orphanCount").isMissingNode() ? null : summary.path("orphanCount").asInt());
                summaryOutput.put("unexpectedSettlementCount", summary.path("unexpectedSettlementCount").isMissingNode() ? null : summary.path("unexpectedSettlementCount").asInt());
                output.put("summary", summaryOutput);
            });
        }

        return output;
    }
}
