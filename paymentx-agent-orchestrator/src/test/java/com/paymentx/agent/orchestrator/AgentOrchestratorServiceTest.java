package com.paymentx.agent.orchestrator;

import com.paymentx.agent.audit.AgentAuditClient;
import com.paymentx.agent.client.McpToolClient;
import com.paymentx.agent.client.RagServiceClient;
import com.paymentx.agent.config.AgentOrchestratorProperties;
import com.paymentx.agent.dto.AgentExecuteRequest;
import com.paymentx.agent.dto.AgentExecuteResponse;
import com.paymentx.agent.dto.AgentResponseStatus;
import com.paymentx.agent.exception.AgentException;
import com.paymentx.agent.metrics.AgentMetrics;
import com.paymentx.agent.planning.AgentPlanValidator;
import com.paymentx.agent.planning.AgentPlanner;
import com.paymentx.agent.state.AgentPlan;
import com.paymentx.agent.state.PlanAction;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * English:
 * Mockito-based unit tests for orchestrator/AgentOrchestratorService's
 * bounded loop - planning/AgentPlanner, planning/AgentPlanValidator,
 * client/McpToolClient, client/RagServiceClient, and
 * audit/AgentAuditClient are all mocked (their own real behavior is
 * covered elsewhere - planning/AgentPlannerTest,
 * planning/AgentPlanValidatorTest, client/*ClientTest, and the real
 * end-to-end controller/AgentE2EIntegrationTest); a REAL AgentMetrics
 * (SimpleMeterRegistry-backed) and REAL AgentOrchestratorProperties are
 * used. Covers Step 40 items directly at this layer that are
 * impractical or non-deterministic to exercise over real HTTP: a plain
 * conversational turn needing neither RAG nor tools, a denied tool
 * request stopping the loop immediately without ever reaching
 * client/McpToolClient (Step 30's core rule), max iterations, a real
 * RAG execution failure that the loop survives and continues past
 * (Step 17/18), and a genuine overall-timeout cutoff (Step 33).
 *
 * Hinglish:
 * orchestrator/AgentOrchestratorService ke bounded loop ke liye
 * Mockito-based unit tests - planning/AgentPlanner, planning/
 * AgentPlanValidator, client/McpToolClient, client/RagServiceClient, aur
 * audit/AgentAuditClient sab mocked hain (unka apna real behavior kahin
 * aur cover hota hai); ek REAL AgentMetrics (SimpleMeterRegistry-backed)
 * aur REAL AgentOrchestratorProperties use hote hain. Step 40 ke items
 * ko yahan directly cover karta hai jo real HTTP par exercise karna
 * impractical ya non-deterministic hai: ek plain conversational turn
 * jise na RAG na tools chahiye, ek denied tool request jo loop ko
 * turant rok deta hai bina kabhi client/McpToolClient tak pahunche
 * (Step 30 ka core rule), max iterations, ek real RAG execution failure
 * jise loop survive karta hai aur aage continue karta hai (Step 17/18),
 * aur ek genuine overall-timeout cutoff (Step 33).
 */
@ExtendWith(MockitoExtension.class)
class AgentOrchestratorServiceTest {

    @Mock
    private AgentPlanner agentPlanner;

    @Mock
    private AgentPlanValidator planValidator;

    @Mock
    private McpToolClient mcpToolClient;

    @Mock
    private RagServiceClient ragServiceClient;

    @Mock
    private AgentAuditClient auditClient;

    private AgentOrchestratorProperties properties;
    private AgentMetrics metrics;

    @BeforeEach
    void setUp() {
        properties = new AgentOrchestratorProperties();
        properties.setMaxIterations(5);
        properties.setMaxToolCalls(10);
        properties.setOverallTimeoutMs(10_000);
        metrics = new AgentMetrics(new SimpleMeterRegistry());
    }

    private AgentOrchestratorService newService() {
        return new AgentOrchestratorService(agentPlanner, planValidator, mcpToolClient, ragServiceClient,
                auditClient, metrics, properties, new RestTemplateBuilder());
    }

    @Test
    void execute_plainConversationalTurn_completesWithNoEvidence() {
        when(mcpToolClient.listTools()).thenReturn(List.of());
        when(agentPlanner.plan(any(), any())).thenReturn(
                new AgentPlan(PlanAction.FINAL_RESPONSE, "no PaymentX data or knowledge needed", null, null, null, "Hello! How can I help?"));

        AgentExecuteResponse response = newService().execute(new AgentExecuteRequest(null, "user-1", "hello"), "corr-1", null);

        assertThat(response.status()).isEqualTo(AgentResponseStatus.SUCCESS);
        assertThat(response.answer()).isEqualTo("Hello! How can I help?");
        assertThat(response.toolEvidence()).isEmpty();
        assertThat(response.sources()).isEmpty();
        // listTools() is always called once per iteration to build the discoveredTools list a CALL_TOOL
        // plan would need to be validated against - real tool EXECUTION is what must never happen here.
        verify(mcpToolClient, never()).callTool(any(), any());
        Mockito.verifyNoInteractions(ragServiceClient);
    }

    @Test
    void execute_deniedToolRequest_stopsImmediatelyWithoutCallingMcp() {
        when(mcpToolClient.listTools()).thenReturn(List.of(new McpToolClient.ToolSummary("payment.refund", "desc")));
        when(agentPlanner.plan(any(), any())).thenReturn(
                new AgentPlan(PlanAction.CALL_TOOL, "user asked for a refund", "payment.refund", Map.of("paymentReference", "PMT-123"), null, null));
        doThrow(AgentException.toolNotAllowed("denied")).when(planValidator).validate(any(), any());

        AgentExecuteResponse response = newService().execute(new AgentExecuteRequest(null, "user-1", "Refund PMT-123"), "corr-1", null);

        assertThat(response.status()).isEqualTo(AgentResponseStatus.DENIED);
        verify(mcpToolClient, never()).callTool(any(), any());
        verify(agentPlanner, times(1)).plan(any(), any());
    }

    @Test
    void execute_plannerNeverFinishes_stopsAtMaxIterations() {
        properties.setMaxIterations(3);
        when(mcpToolClient.listTools()).thenReturn(List.of(new McpToolClient.ToolSummary("audit.search", "desc")));
        when(agentPlanner.plan(any(), any())).thenReturn(
                new AgentPlan(PlanAction.RETRIEVE_KNOWLEDGE, "still looking", null, null, "some query", null));
        when(ragServiceClient.query(any(), any())).thenReturn(new RagServiceClient.RagQueryResult("no real answer", "INSUFFICIENT_CONTEXT", List.of()));

        AgentExecuteResponse response = newService().execute(new AgentExecuteRequest(null, "user-1", "keep asking"), "corr-1", null);

        assertThat(response.status()).isEqualTo(AgentResponseStatus.MAX_ITERATIONS);
        verify(agentPlanner, times(3)).plan(any(), any());
    }

    @Test
    void execute_ragCallThrows_loopSurvivesAndContinuesToNextIteration() {
        when(mcpToolClient.listTools()).thenReturn(List.of());
        when(agentPlanner.plan(any(), any()))
                .thenReturn(new AgentPlan(PlanAction.RETRIEVE_KNOWLEDGE, "need docs", null, null, "some query", null))
                .thenReturn(new AgentPlan(PlanAction.FINAL_RESPONSE, "RAG failed, answering honestly", null, null, null,
                        "I could not retrieve PaymentX knowledge right now."));
        when(ragServiceClient.query(any(), any())).thenThrow(AgentException.ragServiceUnavailable("down", true));

        AgentExecuteResponse response = newService().execute(new AgentExecuteRequest(null, "user-1", "explain something"), "corr-1", null);

        assertThat(response.status()).isEqualTo(AgentResponseStatus.INSUFFICIENT_CONTEXT);
        verify(agentPlanner, times(2)).plan(any(), any());
    }

    @Test
    void execute_plannerNeverReturns_hitsOverallTimeout() {
        properties.setOverallTimeoutMs(300);
        when(mcpToolClient.listTools()).thenReturn(List.of());
        when(agentPlanner.plan(any(), any())).thenAnswer(invocation -> {
            Thread.sleep(3000);
            return new AgentPlan(PlanAction.FINAL_RESPONSE, "too slow", null, null, null, "late answer");
        });

        AgentExecuteResponse response = newService().execute(new AgentExecuteRequest(null, "user-1", "slow question"), "corr-1", null);

        assertThat(response.status()).isEqualTo(AgentResponseStatus.TIMEOUT);
    }

    @Test
    void execute_alwaysRecordsAuditEventExactlyOnce() {
        when(mcpToolClient.listTools()).thenReturn(List.of());
        when(agentPlanner.plan(any(), any())).thenReturn(
                new AgentPlan(PlanAction.FINAL_RESPONSE, "reason", null, null, null, "answer"));

        newService().execute(new AgentExecuteRequest(null, "user-1", "hello"), "corr-1", null);

        verify(auditClient, times(1)).recordAgentRun(any());
    }
}
