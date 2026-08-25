package com.paymentx.agent.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.paymentx.agent.config.AgentOrchestratorProperties;
import com.paymentx.agent.state.AgentExecution;
import com.paymentx.agent.state.AgentState;
import com.paymentx.agent.state.ToolCallRecord;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Phase 4.2.3 - real wire-level tests for AgentAuditClient, focused on item 19's requirement
 * (latencyMs now reaches the audit payload - PAYMENTX_PHASE_4_1_AGENT_FOUNDATION.md §11's
 * already-flagged gap) and the pre-existing minimization discipline (never raw tool
 * arguments/results, never secrets).
 */
class AgentAuditClientTest {

    private static final String SECRET_SENTINEL = "TEST_SECRET_SHOULD_NEVER_APPEAR_IN_AUDIT_12345";

    private static WireMockServer auditService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeAll
    static void startServer() {
        auditService = new WireMockServer(0);
        auditService.start();
    }

    @AfterAll
    static void stopServer() {
        auditService.stop();
    }

    @BeforeEach
    void resetServer() {
        auditService.resetAll();
    }

    private AgentAuditClient newClient() {
        AgentOrchestratorProperties properties = new AgentOrchestratorProperties();
        properties.setAuditServiceUrl("http://localhost:" + auditService.port());
        return new AgentAuditClient(new RestTemplateBuilder(), properties);
    }

    @Test
    void recordAgentRun_realPayload_includesLatencyMsAndAgentId() throws Exception {
        auditService.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/api/v1/audit-events"))
                .willReturn(aResponse().withStatus(200)));

        AgentExecution execution = new AgentExecution("req-1", "corr-1", "trace-1", "user-1", "Why did PMT-1 fail?", "error-analyzer");
        execution.setIteration(2);
        execution.setStatus(AgentState.COMPLETED);
        execution.addToolCall(new ToolCallRecord("payment.lookup", Map.of("paymentReference", "PMT-1"), "SUCCESS",
                Map.of("found", true, "failureReason", "DUPLICATE_PAYMENT_REFERENCE"), null, 42L));

        newClient().recordAgentRun(execution, 1234L);

        auditService.verify(postRequestedFor(urlPathEqualTo("/api/v1/audit-events")));
        JsonNode body = parseLastRequestBody();
        assertThat(body.path("actorType").asText()).isEqualTo("AI_AGENT");
        JsonNode payload = objectMapper.readTree(body.path("payload").asText());
        assertThat(payload.path("agentId").asText()).isEqualTo("error-analyzer");
        assertThat(payload.path("latencyMs").asLong()).isEqualTo(1234L);
        assertThat(payload.path("iterations").asInt()).isEqualTo(2);
    }

    @Test
    void recordAgentRun_toolCallEvidence_neverIncludesRawArgumentsOrResults_onlyToolNameAndStatus() throws Exception {
        auditService.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/api/v1/audit-events"))
                .willReturn(aResponse().withStatus(200)));

        AgentExecution execution = new AgentExecution("req-2", "corr-2", null, "user-1", "hello", "default");
        execution.setStatus(AgentState.COMPLETED);
        execution.addToolCall(new ToolCallRecord("payment.lookup",
                Map.of("paymentReference", "PMT-1"), "FAILED",
                Map.of("internalDetail", "api_key=" + SECRET_SENTINEL), "TOOL_EXECUTION_FAILED", 10L));

        newClient().recordAgentRun(execution, 500L);

        JsonNode body = parseLastRequestBody();
        assertThat(body.toString()).doesNotContain(SECRET_SENTINEL);
        JsonNode payload = objectMapper.readTree(body.path("payload").asText());
        JsonNode toolCall = payload.path("toolCalls").get(0);
        assertThat(toolCall.path("tool").asText()).isEqualTo("payment.lookup");
        assertThat(toolCall.path("status").asText()).isEqualTo("FAILED");
        // Only "tool" and "status" keys - never "arguments" or "result".
        assertThat(toolCall.fieldNames()).toIterable().containsExactlyInAnyOrder("tool", "status");
    }

    @Test
    void recordAgentRun_auditServiceDown_neverThrows_fireAndForget() {
        AgentAuditClient client = newClient(); // capture the (still-live) URL before stopping the server
        auditService.stop();
        try {
            AgentExecution execution = new AgentExecution("req-3", "corr-3", null, "user-1", "hello", "default");
            execution.setStatus(AgentState.FAILED);

            assertThatCode(() -> client.recordAgentRun(execution, 100L)).doesNotThrowAnyException();
        } finally {
            auditService.start();
        }
    }

    private JsonNode parseLastRequestBody() {
        try {
            var requests = auditService.getAllServeEvents();
            String rawBody = requests.get(0).getRequest().getBodyAsString();
            return objectMapper.readTree(rawBody);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
