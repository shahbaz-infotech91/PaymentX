package com.paymentx.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.paymentx.mcp.client.RoutingServiceClient;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * English:
 * The real `routing.lookup` tool (Step 9). `scheme` is required
 * (RoutingScheme is a real enum in routing-service, validated against
 * the same closed set the SDK itself rejects invalid values for);
 * `participantId` is optional - when present, calls
 * RoutingServiceClient.resolveForParticipant() (GET
 * /api/v1/routes/participant/{id}?scheme=X); when absent, calls
 * resolveDefault() (GET /api/v1/routes/default?scheme=X) - both real,
 * already-existing RoutingController endpoints, never a fabricated one
 * (Step 10). Step 14's resource boundary: when the caller is scoped to
 * one participant (non-null X-Participant-Id) AND explicitly asks for a
 * different participant's routing, this is denied - but a caller asking
 * for the SCHEME DEFAULT (no participantId argument at all) is not
 * asking about any specific participant's data, so that path is always
 * allowed regardless of the caller's own scoping (routing defaults are
 * scheme-level configuration, not participant-sensitive business data).
 * A real "no active rule, no default configured for that scheme" 404 is
 * returned as found=false, not thrown as an error (Step 33).
 * Why it exists: Step 9.
 * How it communicates with other components: registered by
 * registry/ToolRegistry as a Spring bean; invoked by ToolInvoker after
 * tool-level authorization/rate-limiting already passed.
 *
 * Hinglish:
 * Ye real `routing.lookup` tool hai (Step 9). `scheme` required hai
 * (RoutingScheme routing-service me ek real enum hai, usi closed set ke
 * against validate hota hai jiske liye SDK khud invalid values reject
 * karta hai); `participantId` optional hai - jab present ho,
 * RoutingServiceClient.resolveForParticipant() (GET
 * /api/v1/routes/participant/{id}?scheme=X) call hota hai; jab absent
 * ho, resolveDefault() (GET /api/v1/routes/default?scheme=X) call hota
 * hai - dono real, already-existing RoutingController endpoints, kabhi
 * ek fabricated wala nahi (Step 10). Step 14 ka resource boundary: jab
 * caller ek participant tak scoped hai (non-null X-Participant-Id) AUR
 * explicitly ek doosre participant ki routing maangta hai, ye deny
 * hota hai - lekin ek caller jo SCHEME DEFAULT maangta hai (bilkul koi
 * participantId argument nahi) kisi specific participant ke data ke
 * baare me nahi puchh raha, isliye ye path caller ki apni scoping ke
 * bawajood hamesha allowed hai (routing defaults scheme-level
 * configuration hain, participant-sensitive business data nahi). Ek
 * real "us scheme ke liye koi active rule, koi default configured nahi"
 * 404 found=false ke roop me return hota hai, ek error ke roop me throw
 * nahi hota (Step 33).
 * Ye kyu hai: Step 9.
 * Dusre components se kaise communicate karta hai: registry/ToolRegistry
 * dwara ek Spring bean ke roop me register hota hai; ToolInvoker dwara
 * invoke hota hai tool-level authorization/rate-limiting already pass
 * hone ke baad.
 */
@Component
public class RoutingLookupTool implements PaymentXTool {

    private static final Pattern PARTICIPANT_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");
    private static final List<String> ALLOWED_SCHEMES = List.of("INSTANT_PAYMENT", "REAL_TIME_PAYMENT", "CARD_PAYMENT");

    private final RoutingServiceClient routingServiceClient;
    private final ToolAuthorizationService authorizationService;
    private final McpToolDefinition definition;

    public RoutingLookupTool(RoutingServiceClient routingServiceClient, ToolAuthorizationService authorizationService) {
        this.routingServiceClient = routingServiceClient;
        this.authorizationService = authorizationService;
        this.definition = new McpToolDefinition(
                "routing.lookup",
                "Resolve the active routing rule for a payment scheme, optionally scoped to a participant "
                        + "(falling back to the scheme default when no participant is given). This tool is "
                        + "read-only and does not modify routing configuration.",
                new McpSchema.JsonSchema("object", Map.of(
                        "scheme", Map.of("type", "string", "description", "The routing scheme: INSTANT_PAYMENT, REAL_TIME_PAYMENT, or CARD_PAYMENT."),
                        "participantId", Map.of("type", "string", "description", "Optional. If omitted, resolves the scheme's default route.")
                ), List.of("scheme"), false, null, null),
                ToolPermissions.ROUTING_READ,
                ToolRiskLevel.LOW,
                ToolReadWrite.READ_ONLY,
                Duration.ofSeconds(5),
                true,
                "ROUTING_DATA_READ"
        );
    }

    @Override
    public McpToolDefinition definition() {
        return definition;
    }

    @Override
    public Map<String, Object> execute(ToolInvocationContext context, Map<String, Object> arguments) {
        Object rawScheme = arguments.get("scheme");
        if (!(rawScheme instanceof String scheme) || !ALLOWED_SCHEMES.contains(scheme.toUpperCase())) {
            throw McpException.invalidArguments("scheme must be one of " + ALLOWED_SCHEMES);
        }
        String normalizedScheme = scheme.toUpperCase();

        Object rawParticipantId = arguments.get("participantId");
        String participantId = null;
        if (rawParticipantId != null) {
            if (!(rawParticipantId instanceof String candidate) || !PARTICIPANT_ID_PATTERN.matcher(candidate).matches()) {
                throw McpException.invalidArguments("participantId must be a non-blank alphanumeric string up to 64 characters.");
            }
            participantId = candidate;
        }

        if (participantId != null) {
            authorizationService.checkResourceOwnership(context, definition.name(), participantId);
        }

        Optional<JsonNode> result = participantId != null
                ? routingServiceClient.resolveForParticipant(participantId, normalizedScheme, context.correlationId())
                : routingServiceClient.resolveDefault(normalizedScheme, context.correlationId());

        if (result.isEmpty()) {
            return Map.of("found", false, "scheme", normalizedScheme);
        }

        JsonNode rule = result.get();
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("found", true);
        output.put("participantId", rule.path("participantId").isMissingNode() ? null : rule.path("participantId").asText(null));
        output.put("targetRoute", rule.path("targetRoute").asText(null));
        output.put("priority", rule.path("priority").isMissingNode() ? null : rule.path("priority").asInt());
        output.put("active", rule.path("active").isMissingNode() ? null : rule.path("active").asBoolean());
        output.put("isDefault", rule.path("isDefault").isMissingNode() ? null : rule.path("isDefault").asBoolean());
        output.put("description", rule.path("description").isMissingNode() ? null : rule.path("description").asText(null));
        return output;
    }
}
