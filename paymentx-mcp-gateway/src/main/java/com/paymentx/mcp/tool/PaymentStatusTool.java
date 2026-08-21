package com.paymentx.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.paymentx.mcp.client.PaymentServiceClient;
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
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * English:
 * The real `payment.status` tool (Step 9's #2 priority) - a lightweight,
 * status-only read via PaymentServiceClient.getStatus() (GET
 * /api/v1/payments/{reference}/status through the API Gateway), matching
 * payment-service's own "suitable for polling" lightweight endpoint -
 * never re-implements that as a full lookup + field-trimming (Step 11 -
 * call the real API that already does exactly this, don't reinvent it).
 * KNOWN LIMITATION (documented honestly, not silently): unlike
 * tool/PaymentLookupTool, this endpoint's response carries no
 * participant identity at all (payment-service's own
 * PaymentStatusResponse is paymentReference/status/updatedAt only), so
 * Step 14's resource-boundary check cannot be enforced here without
 * making an additional full-lookup call just to check ownership - which
 * would silently turn every status poll into a full lookup and defeat
 * the point of this lightweight endpoint existing at all. A caller that
 * needs resource-scoped enforcement on a status check should use
 * payment.lookup instead (see PAYMENTX_PHASE_3_7_MCP_GATEWAY.md's Known
 * Limitations section).
 * Why it exists: Step 9.
 * How it communicates with other components: registered by
 * registry/ToolRegistry as a Spring bean; invoked by ToolInvoker after
 * tool-level authorization/rate-limiting already passed.
 *
 * Hinglish:
 * Ye real `payment.status` tool hai (Step 9 ki #2 priority) - ek
 * lightweight, status-only read PaymentServiceClient.getStatus() (GET
 * /api/v1/payments/{reference}/status, API Gateway ke through) se,
 * payment-service ke apne "polling ke liye suitable" lightweight
 * endpoint se match karte hue - kabhi ise ek full lookup + field-
 * trimming ke roop me re-implement nahi karta (Step 11 - real API call
 * karo jo already exactly yehi karta hai, use reinvent mat karo).
 * KNOWN LIMITATION (honestly documented, silently nahi): tool/
 * PaymentLookupTool ke ulat, is endpoint ka response bilkul koi
 * participant identity carry nahi karta (payment-service ka apna
 * PaymentStatusResponse sirf paymentReference/status/updatedAt hai),
 * isliye Step 14 ka resource-boundary check yahan enforce nahi ho sakta
 * bina ek additional full-lookup call kiye sirf ownership check karne
 * ke liye - jo silently har status poll ko ek full lookup me badal deta
 * aur is lightweight endpoint ke exist karne ka point hi defeat kar
 * deta. Ek caller jise ek status check par resource-scoped enforcement
 * chahiye use payment.lookup use karna chahiye
 * (PAYMENTX_PHASE_3_7_MCP_GATEWAY.md ka Known Limitations section
 * dekho).
 * Ye kyu hai: Step 9.
 * Dusre components se kaise communicate karta hai: registry/ToolRegistry
 * dwara ek Spring bean ke roop me register hota hai; ToolInvoker dwara
 * invoke hota hai tool-level authorization/rate-limiting already pass
 * hone ke baad.
 */
@Component
public class PaymentStatusTool implements PaymentXTool {

    private static final Pattern PAYMENT_REFERENCE_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    private final PaymentServiceClient paymentServiceClient;
    private final McpToolDefinition definition;

    public PaymentStatusTool(PaymentServiceClient paymentServiceClient) {
        this.paymentServiceClient = paymentServiceClient;
        this.definition = new McpToolDefinition(
                "payment.status",
                "Retrieve the current status of a payment using its payment reference. "
                        + "This tool is read-only, lightweight, and suitable for polling; it does not modify payment state.",
                new McpSchema.JsonSchema("object", Map.of(
                        "paymentReference", Map.of("type", "string", "description", "The payment reference assigned by the originating bank/participant.")
                ), java.util.List.of("paymentReference"), false, null, null),
                ToolPermissions.PAYMENT_READ,
                ToolRiskLevel.LOW,
                ToolReadWrite.READ_ONLY,
                Duration.ofSeconds(5),
                true,
                "PAYMENT_DATA_READ"
        );
    }

    @Override
    public McpToolDefinition definition() {
        return definition;
    }

    @Override
    public Map<String, Object> execute(ToolInvocationContext context, Map<String, Object> arguments) {
        Object rawReference = arguments.get("paymentReference");
        if (!(rawReference instanceof String paymentReference) || !PAYMENT_REFERENCE_PATTERN.matcher(paymentReference).matches()) {
            throw McpException.invalidArguments("paymentReference must be a non-blank alphanumeric string up to 64 characters.");
        }

        Optional<JsonNode> result = paymentServiceClient.getStatus(paymentReference, context.correlationId());
        if (result.isEmpty()) {
            return Map.of("found", false, "paymentReference", paymentReference);
        }

        JsonNode status = result.get();
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("found", true);
        output.put("paymentReference", status.path("paymentReference").asText(null));
        output.put("status", status.path("status").asText(null));
        output.put("updatedAt", status.path("updatedAt").asText(null));
        return output;
    }
}
