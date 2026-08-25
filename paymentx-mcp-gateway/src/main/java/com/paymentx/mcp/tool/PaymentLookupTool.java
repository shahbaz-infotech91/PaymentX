package com.paymentx.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.paymentx.common.security.DataMaskingUtils;
import com.paymentx.common.security.MaskStrategy;
import com.paymentx.mcp.client.PaymentServiceClient;
import com.paymentx.mcp.exception.McpException;
import com.paymentx.mcp.registry.McpToolDefinition;
import com.paymentx.mcp.registry.PaymentXTool;
import com.paymentx.mcp.registry.ToolInvocationContext;
import com.paymentx.mcp.registry.ToolReadWrite;
import com.paymentx.mcp.registry.ToolRiskLevel;
import com.paymentx.mcp.security.ToolAuthorizationService;
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
 * The real `payment.lookup` tool (Step 6's own worked example, Step 9's
 * #1 priority). Retrieves a payment's full current snapshot via the
 * real, already-existing PaymentServiceClient (GET
 * /api/v1/payments/{reference} through the API Gateway) - never a
 * fabricated response (Step 10). Output is deliberately narrower than
 * payment-service's own PaymentResponse (Step 17/18 - data
 * minimization): excludes the internal `id` UUID, `traceId`,
 * `correlationId` entirely; masks `debtorAccount`/`creditorAccount`
 * with the platform's own real com.paymentx.common.security.DataMaskingUtils
 * (PARTIAL strategy, e.g. "****1234" - Step 19's own worked example,
 * never an invented masking rule). `failureReason` IS included - Step
 * 9's `payment.error.lookup` is documented NOT AVAILABLE (no dedicated
 * error-code-lookup API exists anywhere in payment-service - see
 * PAYMENTX_PHASE_3_7_MCP_GATEWAY.md's Tool List section), so this is
 * the one honest place investigation-relevant failure information can
 * come from without inventing a new business API. Enforces Step 14's
 * resource boundary AFTER fetching: a caller scoped to one participant
 * (non-null X-Participant-Id) may only see a payment where they are the
 * debtor or creditor - checked via security/ToolAuthorizationService,
 * never leaking whether the payment exists to an unauthorized caller
 * beyond a generic RESOURCE_FORBIDDEN (Step 14 - "MCP must not become an
 * authorization bypass").
 * A real "no such payment" is NOT an error - PaymentServiceClient
 * returns Optional.empty() for a genuine 404, and this tool returns a
 * successful CallToolResult content with found=false (Step 33 -
 * business result, not MCP infrastructure failure).
 * Why it exists: Step 6/9.
 * How it communicates with other components: registered by
 * registry/ToolRegistry as a Spring bean; invoked by ToolInvoker after
 * tool-level authorization/rate-limiting already passed.
 *
 * Hinglish:
 * Ye real `payment.lookup` tool hai (Step 6 ka apna worked example,
 * Step 9 ki #1 priority). Ek payment ka poora current snapshot real,
 * already-existing PaymentServiceClient (GET
 * /api/v1/payments/{reference}, API Gateway ke through) se retrieve
 * karta hai - kabhi ek fabricated response nahi (Step 10). Output
 * jaan-boojh kar payment-service ke apne PaymentResponse se narrower
 * hai (Step 17/18 - data minimization): internal `id` UUID, `traceId`,
 * `correlationId` bilkul exclude karta hai; `debtorAccount`/
 * `creditorAccount` ko platform ke apne real
 * com.paymentx.common.security.DataMaskingUtils se mask karta hai
 * (PARTIAL strategy, jaise "****1234" - Step 19 ka apna worked example,
 * kabhi ek invented masking rule nahi). `failureReason` SHAMIL hai -
 * Step 9 ka `payment.error.lookup` NOT AVAILABLE document kiya gaya hai
 * (payment-service me kahin bhi koi dedicated error-code-lookup API
 * exist nahi karta - PAYMENTX_PHASE_3_7_MCP_GATEWAY.md ka Tool List
 * section dekho), isliye ye ek honest jagah hai jahan se
 * investigation-relevant failure information ek nayi business API
 * invent kiye bina aa sakti hai. Fetch karne ke BAAD Step 14 ka resource
 * boundary enforce karta hai: ek caller jo ek participant tak scoped hai
 * (non-null X-Participant-Id) sirf wahi payment dekh sakta hai jahan wo
 * debtor ya creditor hai - security/ToolAuthorizationService ke through
 * check hota hai, kabhi ek unauthorized caller ko ye leak nahi hone
 * deta ki payment exist karta hai ya nahi, sirf ek generic
 * RESOURCE_FORBIDDEN se aage (Step 14 - "MCP ek authorization bypass
 * nahi banna chahiye").
 * Ek real "aisa koi payment nahi hai" ek error NAHI hai -
 * PaymentServiceClient ek genuine 404 ke liye Optional.empty() return
 * karta hai, aur ye tool ek successful CallToolResult content
 * found=false ke saath return karta hai (Step 33 - business result, MCP
 * infrastructure failure nahi).
 * Ye kyu hai: Step 6/9.
 * Dusre components se kaise communicate karta hai: registry/ToolRegistry
 * dwara ek Spring bean ke roop me register hota hai; ToolInvoker dwara
 * invoke hota hai tool-level authorization/rate-limiting already pass
 * hone ke baad.
 */
@Component
public class PaymentLookupTool implements PaymentXTool {

    private static final Pattern PAYMENT_REFERENCE_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    private final PaymentServiceClient paymentServiceClient;
    private final ToolAuthorizationService authorizationService;
    private final McpToolDefinition definition;

    public PaymentLookupTool(PaymentServiceClient paymentServiceClient, ToolAuthorizationService authorizationService) {
        this.paymentServiceClient = paymentServiceClient;
        this.authorizationService = authorizationService;
        this.definition = new McpToolDefinition(
                "payment.lookup",
                "Retrieve the full current snapshot of a payment using its payment reference, optionally including "
                        + "its full ordered status-transition history/timeline. "
                        + "This tool is read-only and does not modify payment state.",
                new McpSchema.JsonSchema("object", Map.of(
                        "paymentReference", Map.of("type", "string", "description", "The payment reference assigned by the originating bank/participant."),
                        "includeHistory", Map.of("type", "boolean", "description", "Optional, defaults to false. When true, also includes the payment's full ordered status-transition timeline (fromStatus, toStatus, reason, transitionedAt) - Phase 4.8.0.")
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

        Optional<JsonNode> result = paymentServiceClient.getByReference(paymentReference, context.correlationId());
        if (result.isEmpty()) {
            return Map.of("found", false, "paymentReference", paymentReference);
        }

        JsonNode payment = result.get();
        String debtorParticipantId = payment.path("debtorParticipantId").asText(null);
        String creditorParticipantId = payment.path("creditorParticipantId").asText(null);
        authorizationService.checkResourceOwnership(context, definition.name(), debtorParticipantId, creditorParticipantId);

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("found", true);
        output.put("paymentReference", payment.path("paymentReference").asText(null));
        output.put("status", payment.path("status").asText(null));
        output.put("scheme", payment.path("scheme").asText(null));
        output.put("amount", payment.path("amount").path("amount").isMissingNode() ? null : payment.path("amount").path("amount").decimalValue());
        output.put("currency", payment.path("amount").path("currency").asText(null));
        output.put("debtorParticipantId", debtorParticipantId);
        output.put("creditorParticipantId", creditorParticipantId);
        output.put("debtorAccountMasked", maskAccount(payment.path("debtorAccount").asText(null)));
        output.put("creditorAccountMasked", maskAccount(payment.path("creditorAccount").asText(null)));
        output.put("failureReason", payment.path("failureReason").isMissingNode() ? null : payment.path("failureReason").asText(null));
        output.put("createdAt", payment.path("createdAt").asText(null));
        output.put("updatedAt", payment.path("updatedAt").asText(null));

        // Phase 4.8.0 - real, ordered status-transition timeline, only fetched when explicitly
        // requested (a second real HTTP call) - never fabricated, never merged silently by default.
        if (Boolean.TRUE.equals(arguments.get("includeHistory"))) {
            paymentServiceClient.getHistory(paymentReference, context.correlationId()).ifPresent(historyResult -> {
                java.util.List<Map<String, Object>> transitions = new java.util.ArrayList<>();
                historyResult.path("history").forEach(item -> {
                    Map<String, Object> transition = new LinkedHashMap<>();
                    transition.put("fromStatus", item.path("fromStatus").isMissingNode() || item.path("fromStatus").isNull() ? null : item.path("fromStatus").asText(null));
                    transition.put("toStatus", item.path("toStatus").asText(null));
                    transition.put("reason", item.path("reason").isMissingNode() ? null : item.path("reason").asText(null));
                    transition.put("transitionedAt", item.path("transitionedAt").asText(null));
                    transitions.add(transition);
                });
                output.put("history", transitions);
            });
        }
        return output;
    }

    private String maskAccount(String account) {
        return account == null ? null : DataMaskingUtils.mask(account, MaskStrategy.PARTIAL);
    }
}
