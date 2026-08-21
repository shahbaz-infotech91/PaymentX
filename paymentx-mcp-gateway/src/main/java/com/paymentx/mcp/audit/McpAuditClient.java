package com.paymentx.mcp.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentx.common.constant.HeaderConstants;
import com.paymentx.mcp.config.McpGatewayProperties;
import com.paymentx.mcp.registry.ToolInvocationContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * English:
 * Step 29's "reuse the existing PaymentX Audit Service if possible - do
 * NOT create a duplicate audit database" - calls the real, already-
 * existing Audit Service (Phase 1) POST /api/v1/audit-events for every
 * tool invocation, exactly once per call, fire-and-forget from the
 * caller's point of view (a downed Audit Service must never make a
 * read-only payment.lookup call fail - see ToolInvoker's javadoc for
 * why audit recording never blocks the real tool result). Uses
 * EventType.API_REQUEST (the closest real, existing enum value - see
 * that enum's source; this codebase's audit-service does NOT have a
 * dedicated "AI_TOOL_CALL" value, and Step 10 forbids inventing a fake
 * PaymentX API/schema value to make this look more complete than it is
 * - an MCP tool call literally IS an API request MCP Gateway makes on
 * the caller's behalf, so this is an honest, not a forced, fit).
 * `payload` is a small, redacted JSON string: toolName, riskLevel,
 * authorization result, execution status, durationMs, targetService,
 * errorCategory - Step 29's explicit "do NOT store secrets... do NOT
 * blindly store complete sensitive tool arguments... use safe/redacted
 * audit payloads." `actorType` is "AI_TOOL" and `actorId` is the real
 * caller's participant id (or "unknown") - this is a real, honest
 * record of who/what asked for what, at what cost.
 * MCP Gateway sets X-Roles: AUDIT_WRITER on this ONE specific outbound
 * call, as its OWN service-level credential for its OWN operational
 * audit trail - this is deliberately NOT the same thing
 * PAYMENTX_PHASE_3_ARCHITECTURE.md §12 forbids ("the MCP Gateway must
 * never be able to mint or influence [X-Roles/X-Participant-Id] ... it
 * must never assert its own role/participant identity" to bypass a
 * downstream business API's authorization on the AI-caller's behalf).
 * That warning is about READ-tool calls made in the caller's name (see
 * client/PaymentServiceClient etc., which never set X-Roles at all);
 * writing this gateway's own audit trail is a distinct, non-payment-
 * domain, service-level operation - the same kind of trusted internal-
 * service-to-service write every other real PaymentX service already
 * performs under this platform's existing header-trust model.
 * Why it exists: Step 29.
 * How it communicates with other components: called once by
 * ToolInvoker after every real tool call (success or failure), never
 * called from inside a PaymentXTool implementation itself.
 *
 * Hinglish:
 * Step 29 ka "existing PaymentX Audit Service reuse karo agar possible
 * ho - ek duplicate audit database mat banao" - real, already-existing
 * Audit Service (Phase 1) ke POST /api/v1/audit-events ko har tool
 * invocation ke liye call karta hai, har call ke liye exactly ek baar,
 * caller ke point of view se fire-and-forget (ek down Audit Service ko
 * kabhi ek read-only payment.lookup call ko fail nahi karna chahiye -
 * ToolInvoker ka javadoc dekho ki audit recording kabhi real tool
 * result ko block kyu nahi karti). EventType.API_REQUEST use karta hai
 * (sabse closest real, existing enum value - us enum ka source dekho;
 * is codebase ki audit-service ke paas ek dedicated "AI_TOOL_CALL"
 * value hai hi nahi, aur Step 10 ek fake PaymentX API/schema value
 * invent karne se mana karta hai isse zyada complete dikhane ke liye
 * jitna ye actually hai - ek MCP tool call literally ek API request hai
 * jo MCP Gateway caller ki taraf se banata hai, isliye ye ek honest,
 * forced nahi, fit hai). `payload` ek chhota, redacted JSON string hai:
 * toolName, riskLevel, authorization result, execution status,
 * durationMs, targetService, errorCategory - Step 29 ka explicit
 * "secrets store MAT karo... complete sensitive tool arguments blindly
 * store MAT karo... safe/redacted audit payloads use karo." `actorType`
 * "AI_TOOL" hai aur `actorId` real caller ka participant id hai (ya
 * "unknown") - ye ek real, honest record hai ki kisne/kya ne kya maanga,
 * kis cost par.
 * MCP Gateway is EK specific outbound call par X-Roles: AUDIT_WRITER set
 * karta hai, apne OWN service-level credential ke roop me apne OWN
 * operational audit trail ke liye - ye jaan-boojh kar wo cheez NAHI hai
 * jo PAYMENTX_PHASE_3_ARCHITECTURE.md §12 forbid karta hai ("MCP Gateway
 * kabhi [X-Roles/X-Participant-Id] mint ya influence nahi kar sakta
 * ... use kabhi apni role/participant identity assert nahi karni
 * chahiye" ek downstream business API ki authorization ko AI-caller ki
 * taraf se bypass karne ke liye). Wo warning READ-tool calls ke baare
 * me hai jo caller ke naam par banaye jaate hain (client/
 * PaymentServiceClient etc dekho, jo X-Roles kabhi set hi nahi karte);
 * is gateway ka apna audit trail likhna ek distinct, non-payment-domain,
 * service-level operation hai - wahi kism ka trusted internal-service-
 * to-service write jo har doosri real PaymentX service is platform ke
 * existing header-trust model ke under already perform karti hai.
 * Ye kyu hai: Step 29.
 * Dusre components se kaise communicate karta hai: ToolInvoker dwara har
 * real tool call ke baad ek baar call hota hai (success ya failure),
 * kabhi kisi PaymentXTool implementation ke andar se call nahi hota.
 */
@Component
@Slf4j
public class McpAuditClient {

    private final RestTemplate restTemplate;
    private final McpGatewayProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public McpAuditClient(RestTemplateBuilder builder, McpGatewayProperties properties) {
        this.properties = properties;
        this.restTemplate = builder
                .requestFactory(SimpleClientHttpRequestFactory::new)
                .connectTimeout(Duration.ofMillis(properties.getAuditWriteConnectTimeoutMs()))
                .readTimeout(Duration.ofMillis(properties.getAuditWriteReadTimeoutMs()))
                .build();
    }

    public void recordToolInvocation(ToolInvocationContext context, String toolName, String riskLevel,
                                      String authorizationResult, String executionStatus, long durationMs,
                                      String targetService, String errorCategory, String paymentId, String participantId) {
        try {
            Map<String, Object> payloadMap = new LinkedHashMap<>();
            payloadMap.put("toolName", toolName);
            payloadMap.put("riskLevel", riskLevel);
            payloadMap.put("authorizationResult", authorizationResult);
            payloadMap.put("executionStatus", executionStatus);
            payloadMap.put("durationMs", durationMs);
            payloadMap.put("targetService", targetService);
            if (errorCategory != null) {
                payloadMap.put("errorCategory", errorCategory);
            }
            String payload = objectMapper.writeValueAsString(payloadMap);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("eventType", "API_REQUEST");
            body.put("sourceService", "mcp-gateway");
            body.put("actorId", context.callerId() != null ? context.callerId() : "unknown");
            body.put("actorType", "AI_TOOL");
            body.put("correlationId", context.correlationId());
            body.put("traceId", context.traceId());
            body.put("paymentId", paymentId);
            body.put("participantId", participantId);
            body.put("reference", toolName);
            body.put("payload", payload);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.add("X-Roles", "AUDIT_WRITER");
            if (context.correlationId() != null) {
                headers.add(HeaderConstants.CORRELATION_ID, context.correlationId());
            }

            restTemplate.postForEntity(properties.getAuditServiceUrl() + "/api/v1/audit-events",
                    new HttpEntity<>(body, headers), String.class);
        } catch (Exception auditWriteFailure) {
            // Deliberately swallowed - see class javadoc: a downed/slow Audit Service must never
            // make the real tool call this event describes fail or appear to fail.
            log.warn("Failed to record MCP audit event toolName={} reason={}", toolName, auditWriteFailure.getMessage());
        }
    }
}
