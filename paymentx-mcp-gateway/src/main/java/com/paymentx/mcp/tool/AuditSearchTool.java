package com.paymentx.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.paymentx.mcp.client.AuditServiceClient;
import com.paymentx.mcp.config.McpGatewayProperties;
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
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * English:
 * The real `audit.search` tool (Step 9) - a bounded, paginated search
 * over the real, already-existing audit-service GET /api/v1/audit-events
 * (Step 10/11). Step 44/45's bounded output: `size` is clamped to
 * McpGatewayProperties.auditSearchMaxPageSize (default 50, hard cap -
 * never "all events"). Step 17/18's data minimization: the raw
 * `payload` field of each real audit event (which can legitimately
 * contain the full body of any API request/response the platform
 * recorded, including data this tool has no business surfacing to an
 * AI wholesale) is deliberately NEVER included in this tool's output -
 * only id/eventType/eventStatus/sourceService/correlationId/paymentId/
 * participantId/reference/occurredAt are returned. Step 14's resource
 * boundary is enforced by FORCING, not merely validating, the
 * participantId filter: when the caller is scoped to one participant
 * (non-null X-Participant-Id), that value always replaces whatever
 * participantId argument (if any) the AI supplied - this is stricter
 * than "reject a mismatched filter," because it also prevents a scoped
 * caller from omitting the filter entirely to see every participant's
 * events. An unscoped (platform/operator-level) caller's filter passes
 * through unchanged.
 * Why it exists: Step 9/14/17/18/44/45.
 * How it communicates with other components: registered by
 * registry/ToolRegistry as a Spring bean; invoked by ToolInvoker after
 * tool-level authorization/rate-limiting already passed.
 *
 * Hinglish:
 * Ye real `audit.search` tool hai (Step 9) - real, already-existing
 * audit-service GET /api/v1/audit-events ke upar ek bounded, paginated
 * search (Step 10/11). Step 44/45 ka bounded output: `size`
 * McpGatewayProperties.auditSearchMaxPageSize (default 50, hard cap -
 * kabhi "sabhi events" nahi) tak clamp hota hai. Step 17/18 ki data
 * minimization: har real audit event ka raw `payload` field (jo
 * legitimately platform ne record ki gayi kisi bhi API request/response
 * ki poori body carry kar sakta hai, including aisa data jise is tool
 * ka AI ko wholesale surface karne ka koi business nahi hai) jaan-boojh
 * kar is tool ke output me KABHI include nahi hota - sirf
 * id/eventType/eventStatus/sourceService/correlationId/paymentId/
 * participantId/reference/occurredAt return hote hain. Step 14 ka
 * resource boundary participantId filter ko FORCE karke enforce hota
 * hai, sirf validate karke nahi: jab caller ek participant tak scoped
 * hai (non-null X-Participant-Id), wo value hamesha kisi bhi
 * participantId argument (agar koi ho) ko replace kar deti hai jo AI ne
 * diya tha - ye "ek mismatched filter reject karo" se strict hai,
 * kyunki ye ek scoped caller ko filter bilkul omit karke har
 * participant ke events dekhne se bhi rokta hai. Ek unscoped
 * (platform/operator-level) caller ka filter unchanged pass hota hai.
 * Ye kyu hai: Step 9/14/17/18/44/45.
 * Dusre components se kaise communicate karta hai: registry/ToolRegistry
 * dwara ek Spring bean ke roop me register hota hai; ToolInvoker dwara
 * invoke hota hai tool-level authorization/rate-limiting already pass
 * hone ke baad.
 */
@Component
public class AuditSearchTool implements PaymentXTool {

    private static final List<String> ALLOWED_STATUSES = List.of("RECORDED", "PROCESSING_FAILED", "ARCHIVED");
    private static final List<String> ALLOWED_EVENT_TYPES = List.of(
            "PAYMENT_CREATED", "PAYMENT_UPDATED", "PAYMENT_ROUTED", "PAYMENT_COMPLETED", "PAYMENT_FAILED",
            "PAYMENT_CANCELLED", "PAYMENT_REFUNDED", "VALIDATION_COMPLETED", "PARTICIPANT_UPDATED",
            "ROUTING_RULE_CHANGED", "SECURITY_EVENT", "API_REQUEST", "API_RESPONSE", "SYSTEM_EVENT", "KAFKA_EVENT");

    private final AuditServiceClient auditServiceClient;
    private final McpGatewayProperties properties;
    private final McpToolDefinition definition;

    public AuditSearchTool(AuditServiceClient auditServiceClient, McpGatewayProperties properties) {
        this.auditServiceClient = auditServiceClient;
        this.properties = properties;
        this.definition = new McpToolDefinition(
                "audit.search",
                "Search the PaymentX audit trail with optional filters (correlationId, paymentId, "
                        + "participantId, reference, status, eventType, fromDate, toDate) and bounded pagination. "
                        + "This tool is read-only and never returns raw event payloads.",
                new McpSchema.JsonSchema("object", Map.ofEntries(
                        Map.entry("correlationId", Map.of("type", "string")),
                        Map.entry("paymentId", Map.of("type", "string")),
                        Map.entry("participantId", Map.of("type", "string")),
                        Map.entry("reference", Map.of("type", "string")),
                        Map.entry("status", Map.of("type", "string", "description", "One of: " + ALLOWED_STATUSES)),
                        Map.entry("eventType", Map.of("type", "string", "description", "One of: " + ALLOWED_EVENT_TYPES)),
                        Map.entry("fromDate", Map.of("type", "string", "description", "ISO-8601 date-time.")),
                        Map.entry("toDate", Map.of("type", "string", "description", "ISO-8601 date-time.")),
                        Map.entry("page", Map.of("type", "integer", "description", "Zero-based page number, defaults to 0.")),
                        Map.entry("size", Map.of("type", "integer", "description", "Page size, defaults to " + properties.getAuditSearchDefaultPageSize()
                                + ", capped at " + properties.getAuditSearchMaxPageSize() + "."))
                ), List.of(), false, null, null),
                ToolPermissions.AUDIT_READ,
                ToolRiskLevel.LOW,
                ToolReadWrite.READ_ONLY,
                Duration.ofSeconds(8),
                true,
                "AUDIT_DATA_READ"
        );
    }

    @Override
    public McpToolDefinition definition() {
        return definition;
    }

    @Override
    public Map<String, Object> execute(ToolInvocationContext context, Map<String, Object> arguments) {
        String correlationIdFilter = stringArg(arguments, "correlationId");
        String paymentId = stringArg(arguments, "paymentId");
        String reference = stringArg(arguments, "reference");
        String fromDate = validateDate(stringArg(arguments, "fromDate"));
        String toDate = validateDate(stringArg(arguments, "toDate"));

        String status = stringArg(arguments, "status");
        if (status != null && !ALLOWED_STATUSES.contains(status.toUpperCase())) {
            throw McpException.invalidArguments("status must be one of " + ALLOWED_STATUSES);
        }
        String eventType = stringArg(arguments, "eventType");
        if (eventType != null && !ALLOWED_EVENT_TYPES.contains(eventType.toUpperCase())) {
            throw McpException.invalidArguments("eventType must be one of " + ALLOWED_EVENT_TYPES);
        }

        String participantId = stringArg(arguments, "participantId");
        if (context.participantId() != null && !context.participantId().isBlank()) {
            // Step 14 - force-scope, never merely validate; see class javadoc.
            participantId = context.participantId();
        }

        int page = intArg(arguments, "page", 0);
        if (page < 0) {
            throw McpException.invalidArguments("page must be >= 0.");
        }
        int size = intArg(arguments, "size", properties.getAuditSearchDefaultPageSize());
        if (size < 1 || size > properties.getAuditSearchMaxPageSize()) {
            throw McpException.invalidArguments("size must be between 1 and " + properties.getAuditSearchMaxPageSize() + ".");
        }

        JsonNode page1 = auditServiceClient.search(correlationIdFilter, paymentId, participantId, reference,
                status == null ? null : status.toUpperCase(), eventType == null ? null : eventType.toUpperCase(),
                fromDate, toDate, page, size, context.correlationId());

        List<Map<String, Object>> content = new ArrayList<>();
        for (JsonNode event : page1.path("content")) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", event.path("id").asText(null));
            item.put("eventType", event.path("eventType").asText(null));
            item.put("eventStatus", event.path("eventStatus").asText(null));
            item.put("sourceService", event.path("sourceService").asText(null));
            item.put("correlationId", event.path("correlationId").isMissingNode() ? null : event.path("correlationId").asText(null));
            item.put("paymentId", event.path("paymentId").isMissingNode() ? null : event.path("paymentId").asText(null));
            item.put("participantId", event.path("participantId").isMissingNode() ? null : event.path("participantId").asText(null));
            item.put("reference", event.path("reference").isMissingNode() ? null : event.path("reference").asText(null));
            item.put("occurredAt", event.path("occurredAt").asText(null));
            content.add(item);
        }

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("content", content);
        output.put("pageNumber", page1.path("pageNumber").isMissingNode() ? page : page1.path("pageNumber").asInt());
        output.put("pageSize", page1.path("pageSize").isMissingNode() ? size : page1.path("pageSize").asInt());
        output.put("totalElements", page1.path("totalElements").isMissingNode() ? content.size() : page1.path("totalElements").asLong());
        output.put("totalPages", page1.path("totalPages").isMissingNode() ? 1 : page1.path("totalPages").asInt());
        return output;
    }

    private String stringArg(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String text) || text.isBlank()) {
            throw McpException.invalidArguments(key + " must be a non-blank string.");
        }
        return text;
    }

    private int intArg(Map<String, Object> arguments, String key, int defaultValue) {
        Object value = arguments.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        throw McpException.invalidArguments(key + " must be an integer.");
    }

    private String validateDate(String value) {
        if (value == null) {
            return null;
        }
        try {
            OffsetDateTime.parse(value);
            return value;
        } catch (DateTimeParseException notIso) {
            throw McpException.invalidArguments("Date fields must be ISO-8601 date-time (e.g. 2026-08-01T00:00:00Z).");
        }
    }
}
