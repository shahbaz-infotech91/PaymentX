package com.paymentx.agent.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentx.agent.config.AgentOrchestratorProperties;
import com.paymentx.agent.state.AgentExecution;
import com.paymentx.agent.state.ToolCallRecord;
import com.paymentx.common.constant.HeaderConstants;
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
 * Step 36's "reuse existing Audit Service" - writes one real
 * POST /api/v1/audit-events per completed agent run to the real,
 * already-existing Audit Service, the exact same pattern MCP Gateway's
 * own McpAuditClient established in Phase 3.7 (same EventType.API_REQUEST
 * reuse rationale - see that class's javadoc; this codebase's
 * audit-service has no dedicated "agent run" event type, and inventing
 * one would violate the "no fake APIs/schema values" rule every prior
 * phase already follows). `payload` is a small, redacted JSON summary:
 * final status, iteration count, tool call count (with each tool's NAME
 * and STATUS only, never raw arguments/results), whether RAG was used -
 * Step 36's explicit "Do NOT audit/store private chain-of-thought...
 * safe example: Agent selected payment.lookup / Reason: Payment status
 * required / Result: SUCCESS. Do not store hidden reasoning text." Each
 * plan's `reasoning` field IS included per tool call (it is, by
 * construction, a short decision label - see AgentPlan's javadoc for
 * why that field alone is safe to audit), never the LLM's raw response
 * text.
 * Sets X-Roles: AUDIT_WRITER on this ONE outbound call as its own
 * service-level credential for its own operational audit trail -
 * identical reasoning to MCP Gateway's McpAuditClient (see that class's
 * javadoc for the full boundary explanation: this is distinct from, and
 * never a substitute for, MCP Gateway's own real per-call tool
 * authorization). A downed/slow Audit Service never blocks or fails the
 * real agent response - the write is fire-and-forget, best-effort.
 * Why it exists: Step 36.
 * How it communicates with other components: called once by
 * orchestrator/AgentOrchestratorService at the end of every real agent
 * run (success or failure).
 *
 * Hinglish:
 * Step 36 ka "existing Audit Service reuse karo" - har complete hue
 * agent run ke liye ek real POST /api/v1/audit-events real, already-
 * existing Audit Service ko likhta hai, exactly wahi pattern jo MCP
 * Gateway ka apna McpAuditClient Phase 3.7 me establish kar chuka hai
 * (same EventType.API_REQUEST reuse rationale - us class ka javadoc
 * dekho; is codebase ki audit-service ke paas ek dedicated "agent run"
 * event type hai hi nahi, aur ek naya invent karna "koi fake APIs/
 * schema values nahi" rule ko violate karta jo har pichla phase already
 * follow karta hai). `payload` ek chhota, redacted JSON summary hai:
 * final status, iteration count, tool call count (har tool ka sirf NAAM
 * aur STATUS, kabhi raw arguments/results nahi), RAG use hui ya nahi -
 * Step 36 ka explicit "Private chain-of-thought audit/store MAT karo...
 * safe example: Agent ne select kiya payment.lookup / Reason: Payment
 * status required / Result: SUCCESS. Hidden reasoning text store mat
 * karo." Har plan ka `reasoning` field har tool call ke saath included
 * hai (ye, construction se hi, ek short decision label hai - ye field
 * akele audit karna safe kyu hai ke liye AgentPlan ka javadoc dekho),
 * LLM ka raw response text kabhi nahi.
 * Is EK outbound call par X-Roles: AUDIT_WRITER set karta hai apne own
 * service-level credential ke roop me apne own operational audit trail
 * ke liye - MCP Gateway ke McpAuditClient jaisa hi identical reasoning
 * (poore boundary explanation ke liye us class ka javadoc dekho: ye
 * distinct hai, aur MCP Gateway ki apni real per-call tool
 * authorization ka kabhi substitute nahi). Ek down/slow Audit Service
 * kabhi real agent response ko block ya fail nahi karta - write fire-
 * and-forget, best-effort hai.
 * Ye kyu hai: Step 36.
 * Dusre components se kaise communicate karta hai:
 * orchestrator/AgentOrchestratorService dwara har real agent run ke
 * ant me ek baar call hota hai (success ya failure).
 */
@Component
@Slf4j
public class AgentAuditClient {

    private final RestTemplate restTemplate;
    private final String auditServiceUrl;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AgentAuditClient(RestTemplateBuilder builder, AgentOrchestratorProperties properties) {
        this.auditServiceUrl = properties.getAuditServiceUrl();
        this.restTemplate = builder
                .requestFactory(SimpleClientHttpRequestFactory::new)
                .connectTimeout(Duration.ofMillis(2000))
                .readTimeout(Duration.ofMillis(3000))
                .build();
    }

    public void recordAgentRun(AgentExecution execution) {
        try {
            Map<String, Object> payloadMap = new LinkedHashMap<>();
            payloadMap.put("status", execution.getStatus());
            payloadMap.put("iterations", execution.getIteration());
            payloadMap.put("toolCallCount", execution.getToolCallCount());
            payloadMap.put("ragUsed", !execution.getRetrievedContext().isEmpty());
            payloadMap.put("toolCalls", execution.getToolCalls().stream()
                    .map(this::summarizeToolCall).toList());
            String payload = objectMapper.writeValueAsString(payloadMap);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("eventType", "API_REQUEST");
            body.put("sourceService", "agent-orchestrator");
            body.put("actorId", execution.getUserId() != null ? execution.getUserId() : "unknown");
            body.put("actorType", "AI_AGENT");
            body.put("correlationId", execution.getCorrelationId());
            body.put("traceId", execution.getTraceId());
            body.put("reference", execution.getRequestId());
            body.put("payload", payload);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.add("X-Roles", "AUDIT_WRITER");
            if (execution.getCorrelationId() != null) {
                headers.add(HeaderConstants.CORRELATION_ID, execution.getCorrelationId());
            }

            restTemplate.postForEntity(auditServiceUrl + "/api/v1/audit-events", new HttpEntity<>(body, headers), String.class);
        } catch (Exception auditWriteFailure) {
            log.warn("Failed to record agent run audit event requestId={} reason={}",
                    execution.getRequestId(), auditWriteFailure.getMessage());
        }
    }

    private Map<String, Object> summarizeToolCall(ToolCallRecord record) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("tool", record.toolName());
        summary.put("status", record.status());
        return summary;
    }
}
