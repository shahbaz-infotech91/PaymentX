package com.paymentx.agent.orchestrator;

import com.paymentx.agent.audit.AgentAuditClient;
import com.paymentx.agent.client.McpToolClient;
import com.paymentx.agent.client.PaymentScheme;
import com.paymentx.agent.client.RagQueryFilters;
import com.paymentx.agent.client.RagServiceClient;
import com.paymentx.agent.config.AgentOrchestratorProperties;
import com.paymentx.agent.dto.AgentExecuteRequest;
import com.paymentx.agent.dto.AgentExecuteResponse;
import com.paymentx.agent.dto.AgentResponseStatus;
import com.paymentx.agent.exception.AgentException;
import com.paymentx.agent.metrics.AgentMetrics;
import com.paymentx.agent.planning.AgentPlanValidator;
import com.paymentx.agent.planning.AgentPlanner;
import com.paymentx.agent.registry.AgentCapability;
import com.paymentx.agent.registry.AgentDefinition;
import com.paymentx.agent.registry.AgentRiskLevel;
import com.paymentx.agent.state.AgentExecution;
import com.paymentx.agent.state.AgentPlan;
import com.paymentx.agent.state.PlanAction;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;

import java.util.List;
import java.util.Map;
import java.util.Set;

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
    // Phase 4.2.2 - kept as a field (distinct from `metrics`) so tests can inspect real recorded
    // meter values directly, the same real-SimpleMeterRegistry approach this file already used,
    // just with the registry itself now reachable after a run instead of only the wrapper.
    private SimpleMeterRegistry meterRegistry;

    // Phase 4.1 - planValidator/agentPlanner are Mockito mocks in this file (this suite tests the
    // ORCHESTRATOR's own loop/state-machine behavior, not real tool-policy enforcement - that is
    // AIAgentSecurityTest's job, below, with a REAL AgentPlanValidator), so DEFINITION's exact
    // allowedTools content is not itself under test here. maxIterations/timeoutMs are left null
    // so effectiveMaxIterations/effectiveTimeoutMs fall back to `properties`, preserving every
    // original test's exact behavior (e.g. properties.setMaxIterations(3) below).
    private static final AgentDefinition DEFINITION = new AgentDefinition("default", "Default", "desc", "1.0",
            Set.of(AgentCapability.PAYMENT_ANALYSIS), Set.of("payment.lookup"),
            "PAYMENTX_AGENT_ORCHESTRATOR", AgentRiskLevel.LOW, true, null, null);

    @BeforeEach
    void setUp() {
        properties = new AgentOrchestratorProperties();
        properties.setMaxIterations(5);
        properties.setMaxToolCalls(10);
        properties.setOverallTimeoutMs(10_000);
        meterRegistry = new SimpleMeterRegistry();
        metrics = new AgentMetrics(meterRegistry);
    }

    private double insufficientContextCount() {
        io.micrometer.core.instrument.Counter counter = meterRegistry.find("agent_insufficient_context_total").counter();
        return counter == null ? 0.0 : counter.count();
    }

    private AgentOrchestratorService newService() {
        return new AgentOrchestratorService(agentPlanner, planValidator, mcpToolClient, ragServiceClient,
                auditClient, metrics, properties, new RestTemplateBuilder());
    }

    @Test
    void execute_plainConversationalTurn_completesWithNoEvidence() {
        when(mcpToolClient.listTools()).thenReturn(List.of());
        when(agentPlanner.plan(any(), any(), any())).thenReturn(
                new AgentPlan(PlanAction.FINAL_RESPONSE, "no PaymentX data or knowledge needed", null, null, null, "Hello! How can I help?"));

        AgentExecuteResponse response = newService().execute(new AgentExecuteRequest(null, "user-1", "hello"), DEFINITION, "corr-1", null);

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
        when(agentPlanner.plan(any(), any(), any())).thenReturn(
                new AgentPlan(PlanAction.CALL_TOOL, "user asked for a refund", "payment.refund", Map.of("paymentReference", "PMT-123"), null, null));
        doThrow(AgentException.toolNotAllowed("denied")).when(planValidator).validate(any(), any(), any());

        AgentExecuteResponse response = newService().execute(new AgentExecuteRequest(null, "user-1", "Refund PMT-123"), DEFINITION, "corr-1", null);

        assertThat(response.status()).isEqualTo(AgentResponseStatus.DENIED);
        verify(mcpToolClient, never()).callTool(any(), any());
        verify(agentPlanner, times(1)).plan(any(), any(), any());
    }

    @Test
    void execute_plannerNeverFinishes_stopsAtMaxIterations() {
        properties.setMaxIterations(3);
        when(mcpToolClient.listTools()).thenReturn(List.of(new McpToolClient.ToolSummary("audit.search", "desc")));
        when(agentPlanner.plan(any(), any(), any())).thenReturn(
                new AgentPlan(PlanAction.RETRIEVE_KNOWLEDGE, "still looking", null, null, "some query", null));
        when(ragServiceClient.query(any(), any(), any())).thenReturn(new RagServiceClient.RagQueryResult("no real answer", "INSUFFICIENT_CONTEXT", List.of()));

        AgentExecuteResponse response = newService().execute(new AgentExecuteRequest(null, "user-1", "keep asking"), DEFINITION, "corr-1", null);

        assertThat(response.status()).isEqualTo(AgentResponseStatus.MAX_ITERATIONS);
        verify(agentPlanner, times(3)).plan(any(), any(), any());
    }

    @Test
    void execute_ragCallThrows_loopSurvivesAndContinuesToNextIteration() {
        when(mcpToolClient.listTools()).thenReturn(List.of());
        when(agentPlanner.plan(any(), any(), any()))
                .thenReturn(new AgentPlan(PlanAction.RETRIEVE_KNOWLEDGE, "need docs", null, null, "some query", null))
                .thenReturn(new AgentPlan(PlanAction.FINAL_RESPONSE, "RAG failed, answering honestly", null, null, null,
                        "I could not retrieve PaymentX knowledge right now."));
        when(ragServiceClient.query(any(), any(), any())).thenThrow(AgentException.ragServiceUnavailable("down", true));

        AgentExecuteResponse response = newService().execute(new AgentExecuteRequest(null, "user-1", "explain something"), DEFINITION, "corr-1", null);

        assertThat(response.status()).isEqualTo(AgentResponseStatus.INSUFFICIENT_CONTEXT);
        verify(agentPlanner, times(2)).plan(any(), any(), any());
        // Phase 4.2.2 item 9 - the agent's own INSUFFICIENT_CONTEXT outcome increments the metric.
        assertThat(insufficientContextCount()).isEqualTo(1.0);
    }

    @Test
    void execute_plannerNeverReturns_hitsOverallTimeout() {
        properties.setOverallTimeoutMs(300);
        when(mcpToolClient.listTools()).thenReturn(List.of());
        when(agentPlanner.plan(any(), any(), any())).thenAnswer(invocation -> {
            Thread.sleep(3000);
            return new AgentPlan(PlanAction.FINAL_RESPONSE, "too slow", null, null, null, "late answer");
        });

        AgentExecuteResponse response = newService().execute(new AgentExecuteRequest(null, "user-1", "slow question"), DEFINITION, "corr-1", null);

        assertThat(response.status()).isEqualTo(AgentResponseStatus.TIMEOUT);
    }

    // Regression test for the TIMEOUT audit/history investigation: previously, on a genuine
    // TimeoutException, the background executor thread running runLoop() was never cancelled -
    // it kept running (and could keep making real LLM/MCP calls, burning quota invisibly, and
    // mutating the AgentExecution the audit write/response were about to read) indefinitely after
    // the caller had already been told TIMEOUT. execute() now calls future.cancel(true) in the
    // TimeoutException handler; this proves the background thread genuinely observes that
    // interrupt rather than merely asserting the caller-visible TIMEOUT status (already covered
    // by execute_plannerNeverReturns_hitsOverallTimeout above).
    @Test
    void execute_timeout_interruptsTheBackgroundPlanningThread() throws InterruptedException {
        properties.setOverallTimeoutMs(300);
        when(mcpToolClient.listTools()).thenReturn(List.of());
        java.util.concurrent.atomic.AtomicBoolean backgroundThreadWasInterrupted = new java.util.concurrent.atomic.AtomicBoolean(false);
        when(agentPlanner.plan(any(), any(), any())).thenAnswer(invocation -> {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException interrupted) {
                backgroundThreadWasInterrupted.set(true);
                throw interrupted;
            }
            return new AgentPlan(PlanAction.FINAL_RESPONSE, "too slow", null, null, null, "late answer");
        });

        AgentExecuteResponse response = newService().execute(new AgentExecuteRequest(null, "user-1", "slow question"), DEFINITION, "corr-1", null);
        assertThat(response.status()).isEqualTo(AgentResponseStatus.TIMEOUT);

        // Give the interrupted background thread a moment to actually observe/handle the interrupt.
        Thread.sleep(500);
        assertThat(backgroundThreadWasInterrupted.get()).isTrue();
    }

    @Test
    void execute_alwaysRecordsAuditEventExactlyOnce() {
        when(mcpToolClient.listTools()).thenReturn(List.of());
        when(agentPlanner.plan(any(), any(), any())).thenReturn(
                new AgentPlan(PlanAction.FINAL_RESPONSE, "reason", null, null, null, "answer"));

        newService().execute(new AgentExecuteRequest(null, "user-1", "hello"), DEFINITION, "corr-1", null);

        verify(auditClient, times(1)).recordAgentRun(any(), org.mockito.ArgumentMatchers.anyLong());
    }

    // ================================================================
    // Phase 4.2.2 - INSUFFICIENT_CONTEXT metric semantics (Part D items 10-13)
    // ================================================================

    @Test
    void execute_successfulResult_doesNotIncrementInsufficientContextMetric() {
        // Item 10.
        when(mcpToolClient.listTools()).thenReturn(List.of());
        when(agentPlanner.plan(any(), any(), any())).thenReturn(
                new AgentPlan(PlanAction.FINAL_RESPONSE, "no PaymentX data or knowledge needed", null, null, null, "Hello! How can I help?"));

        AgentExecuteResponse response = newService().execute(new AgentExecuteRequest(null, "user-1", "hello"), DEFINITION, "corr-1", null);

        assertThat(response.status()).isEqualTo(AgentResponseStatus.SUCCESS);
        assertThat(insufficientContextCount()).isZero();
    }

    @Test
    void execute_llmPlanningFailure_doesNotIncrementInsufficientContextMetric() {
        // Item 11 - a planning-layer failure (LLM/Prompt/RAG-during-planning unavailable) with NO
        // prior successful evidence ends the run FAILED via applyPlanningFailure. (Phase 5.x -
        // applyPlanningFailure can now record this metric too, but only when real evidence had
        // already succeeded before the failure - see AgentOrchestratorServiceTest's dedicated
        // "partial evidence" tests below; this test has zero evidence, so FAILED is still correct.)
        when(mcpToolClient.listTools()).thenReturn(List.of());
        when(agentPlanner.plan(any(), any(), any())).thenThrow(AgentException.llmServiceUnavailable("down", true));

        AgentExecuteResponse response = newService().execute(new AgentExecuteRequest(null, "user-1", "hello"), DEFINITION, "corr-1", null);

        assertThat(response.status()).isEqualTo(AgentResponseStatus.FAILED);
        assertThat(insufficientContextCount()).isZero();
    }

    @Test
    void execute_ragFailsButAToolCallSucceeds_completesSuccessfullyAndDoesNotIncrementInsufficientContextMetric() {
        // Item 12 - a RAG transport failure alone must not be conflated with the agent's own
        // outcome: here a tool call succeeds in the same run, so the agent's real conclusion is
        // COMPLETED (anySucceeded=true), even though one RAG attempt genuinely failed.
        when(mcpToolClient.listTools()).thenReturn(List.of(new McpToolClient.ToolSummary("payment.lookup", "desc")));
        when(mcpToolClient.callTool(any(), any())).thenReturn(
                new McpToolClient.ToolCallOutcome(false, Map.of("found", true), null));
        when(agentPlanner.plan(any(), any(), any()))
                .thenReturn(new AgentPlan(PlanAction.CALL_TOOL, "lookup", "payment.lookup", Map.of("paymentReference", "PMT-1"), null, null))
                .thenReturn(new AgentPlan(PlanAction.RETRIEVE_KNOWLEDGE, "also check docs", null, null, "some query", null))
                .thenReturn(new AgentPlan(PlanAction.FINAL_RESPONSE, "have enough evidence", null, null, null, "Here is the answer."));
        when(ragServiceClient.query(any(), any(), any())).thenThrow(AgentException.ragServiceUnavailable("down", true));

        AgentExecuteResponse response = newService().execute(new AgentExecuteRequest(null, "user-1", "investigate PMT-1"), DEFINITION, "corr-1", null);

        assertThat(response.status()).isEqualTo(AgentResponseStatus.SUCCESS);
        assertThat(insufficientContextCount()).isZero();
    }

    @Test
    void execute_insufficientContextMetric_isTaggedOnlyByBoundedAgentIdNeverFreeText() {
        // Item 13 - the only tag on this counter is "agent", sourced from
        // registry.AgentDefinition.agentId() (a small, configured value set), never userQuery or
        // any other unbounded, user-generated text.
        when(mcpToolClient.listTools()).thenReturn(List.of());
        when(agentPlanner.plan(any(), any(), any()))
                .thenReturn(new AgentPlan(PlanAction.RETRIEVE_KNOWLEDGE, "need docs", null, null, "some query", null))
                .thenReturn(new AgentPlan(PlanAction.FINAL_RESPONSE, "no evidence found", null, null, null, "insufficient"));
        when(ragServiceClient.query(any(), any(), any())).thenThrow(AgentException.ragServiceUnavailable("down", true));

        newService().execute(new AgentExecuteRequest(null, "user-1", "a free-text question that must never become a tag value"),
                DEFINITION, "corr-1", null);

        io.micrometer.core.instrument.Counter counter = meterRegistry.find("agent_insufficient_context_total").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.getId().getTags()).extracting(io.micrometer.core.instrument.Tag::getKey).containsExactly("agent");
        assertThat(counter.getId().getTag("agent")).isEqualTo(DEFINITION.agentId());
    }

    // ================================================================
    // Phase 4.2.3 - Error Analyzer: RAG filter derivation from real evidence
    // ================================================================

    @Test
    void executeRetrieval_afterSuccessfulPaymentLookupWithScheme_derivesPaymentSchemeFilterFromRealEvidence() {
        when(mcpToolClient.listTools()).thenReturn(List.of(new McpToolClient.ToolSummary("payment.lookup", "desc")));
        when(mcpToolClient.callTool("payment.lookup", Map.of("paymentReference", "PMT-1"))).thenReturn(
                new McpToolClient.ToolCallOutcome(false, Map.of("found", true, "scheme", "INSTANT_PAYMENT"), null));
        when(agentPlanner.plan(any(), any(), any()))
                .thenReturn(new AgentPlan(PlanAction.CALL_TOOL, "lookup", "payment.lookup", Map.of("paymentReference", "PMT-1"), null, null))
                .thenReturn(new AgentPlan(PlanAction.RETRIEVE_KNOWLEDGE, "check docs", null, null, "why did it fail", null))
                .thenReturn(new AgentPlan(PlanAction.FINAL_RESPONSE, "done", null, null, null, "answer"));
        when(ragServiceClient.query(any(), any(), any())).thenReturn(
                new RagServiceClient.RagQueryResult("grounded answer", "SUCCESS", List.of()));

        newService().execute(new AgentExecuteRequest(null, "user-1", "Why did PMT-1 fail?"), DEFINITION, "corr-1", null);

        ArgumentCaptor<RagQueryFilters> filtersCaptor = ArgumentCaptor.forClass(RagQueryFilters.class);
        verify(ragServiceClient).query(any(), filtersCaptor.capture(), any());
        assertThat(filtersCaptor.getValue()).isNotNull();
        assertThat(filtersCaptor.getValue().paymentScheme()).isEqualTo(PaymentScheme.INSTANT_PAYMENT);
    }

    @Test
    void executeRetrieval_noPriorPaymentLookup_passesNullFilters_identicalToPrePhase4_2_3Behavior() {
        when(mcpToolClient.listTools()).thenReturn(List.of());
        when(agentPlanner.plan(any(), any(), any()))
                .thenReturn(new AgentPlan(PlanAction.RETRIEVE_KNOWLEDGE, "check docs", null, null, "general question", null))
                .thenReturn(new AgentPlan(PlanAction.FINAL_RESPONSE, "done", null, null, null, "answer"));
        when(ragServiceClient.query(any(), any(), any())).thenReturn(
                new RagServiceClient.RagQueryResult("answer", "SUCCESS", List.of()));

        newService().execute(new AgentExecuteRequest(null, "user-1", "general question"), DEFINITION, "corr-1", null);

        ArgumentCaptor<RagQueryFilters> filtersCaptor = ArgumentCaptor.forClass(RagQueryFilters.class);
        verify(ragServiceClient).query(any(), filtersCaptor.capture(), any());
        assertThat(filtersCaptor.getValue()).isNull();
    }

    @Test
    void executeRetrieval_paymentLookupFoundFalse_noSchemeToDerive_passesNullFilters() {
        // A real "payment not found" result carries no scheme field at all - must not fabricate one.
        when(mcpToolClient.listTools()).thenReturn(List.of(new McpToolClient.ToolSummary("payment.lookup", "desc")));
        when(mcpToolClient.callTool("payment.lookup", Map.of("paymentReference", "PMT-404"))).thenReturn(
                new McpToolClient.ToolCallOutcome(false, Map.of("found", false, "paymentReference", "PMT-404"), null));
        when(agentPlanner.plan(any(), any(), any()))
                .thenReturn(new AgentPlan(PlanAction.CALL_TOOL, "lookup", "payment.lookup", Map.of("paymentReference", "PMT-404"), null, null))
                .thenReturn(new AgentPlan(PlanAction.RETRIEVE_KNOWLEDGE, "check docs anyway", null, null, "general question", null))
                .thenReturn(new AgentPlan(PlanAction.FINAL_RESPONSE, "done", null, null, null, "answer"));
        when(ragServiceClient.query(any(), any(), any())).thenReturn(
                new RagServiceClient.RagQueryResult("answer", "SUCCESS", List.of()));

        newService().execute(new AgentExecuteRequest(null, "user-1", "Why did PMT-404 fail?"), DEFINITION, "corr-1", null);

        ArgumentCaptor<RagQueryFilters> filtersCaptor = ArgumentCaptor.forClass(RagQueryFilters.class);
        verify(ragServiceClient).query(any(), filtersCaptor.capture(), any());
        assertThat(filtersCaptor.getValue()).isNull();
    }

    // ================================================================
    // Phase 4.2.3 - Error Analyzer: paymentReference grounds the effective query
    // ================================================================

    @Test
    void execute_paymentReferenceSupplied_isWovenIntoTheEffectiveQueryThePlannerSees() {
        when(mcpToolClient.listTools()).thenReturn(List.of());
        when(agentPlanner.plan(any(), any(), any())).thenReturn(
                new AgentPlan(PlanAction.FINAL_RESPONSE, "answered", null, null, null, "answer"));

        newService().execute(new AgentExecuteRequest(null, "user-1", "Why did this fail?", null, "PMT-777"), DEFINITION, "corr-1", null);

        ArgumentCaptor<AgentExecution> executionCaptor = ArgumentCaptor.forClass(AgentExecution.class);
        verify(agentPlanner).plan(executionCaptor.capture(), any(), any());
        assertThat(executionCaptor.getValue().getUserQuery()).isEqualTo("Payment reference: PMT-777. Why did this fail?");
    }

    @Test
    void execute_noPaymentReference_userQueryPassedThroughUnchanged_backwardCompatible() {
        when(mcpToolClient.listTools()).thenReturn(List.of());
        when(agentPlanner.plan(any(), any(), any())).thenReturn(
                new AgentPlan(PlanAction.FINAL_RESPONSE, "answered", null, null, null, "answer"));

        newService().execute(new AgentExecuteRequest(null, "user-1", "Why did PMT-123 fail?"), DEFINITION, "corr-1", null);

        ArgumentCaptor<AgentExecution> executionCaptor = ArgumentCaptor.forClass(AgentExecution.class);
        verify(agentPlanner).plan(executionCaptor.capture(), any(), any());
        assertThat(executionCaptor.getValue().getUserQuery()).isEqualTo("Why did PMT-123 fail?");
    }

    // ================================================================
    // Phase 5.x - real live E2E defect (PAYMENTX_PHASE_5_PAYMENT_TEST_VALIDATION_AGENT.md):
    // a successful tool call followed by a real tool timeout, followed by the NEXT planning
    // iteration itself breaking (LLM/parse failure), must not discard the already-successful
    // evidence into a blanket FAILED - see AgentOrchestratorService.applyPlanningFailure.
    // ================================================================

    private void mockPaymentStatusSucceedsThenLookupTimesOutThenPlanningBreaks() {
        when(mcpToolClient.listTools()).thenReturn(List.of(
                new McpToolClient.ToolSummary("payment.status", "desc"),
                new McpToolClient.ToolSummary("payment.lookup", "desc")));
        when(mcpToolClient.callTool("payment.status", Map.of("paymentReference", "PMT-1")))
                .thenReturn(new McpToolClient.ToolCallOutcome(false, Map.of("found", true, "status", "SETTLED"), null));
        when(mcpToolClient.callTool("payment.lookup", Map.of("paymentReference", "PMT-1")))
                .thenReturn(new McpToolClient.ToolCallOutcome(true,
                        Map.of("message", "Tool call timed out after PT5S: payment.lookup", "errorCode", "TOOL_TIMEOUT"), null));
        when(agentPlanner.plan(any(), any(), any()))
                .thenReturn(new AgentPlan(PlanAction.CALL_TOOL, "check status", "payment.status", Map.of("paymentReference", "PMT-1"), null, null))
                .thenReturn(new AgentPlan(PlanAction.CALL_TOOL, "check lookup", "payment.lookup", Map.of("paymentReference", "PMT-1"), null, null))
                .thenThrow(AgentException.planParseFailed("LLM planning response did not contain a JSON object."));
    }

    @Test
    void execute_toolTimeoutAfterSuccessfulTool_thenPlanningBreaks_preservesSuccessfulEvidence() {
        // Test 1
        mockPaymentStatusSucceedsThenLookupTimesOutThenPlanningBreaks();

        AgentExecuteResponse response = newService().execute(
                new AgentExecuteRequest(null, "user-1", "What is the status of PMT-1?"), DEFINITION, "corr-1", null);

        assertThat(response.toolEvidence()).hasSize(2);
        assertThat(response.toolEvidence()).anySatisfy(e -> {
            assertThat(e.toolName()).isEqualTo("payment.status");
            assertThat(e.status()).isEqualTo("SUCCESS");
            assertThat(e.result()).containsEntry("status", "SETTLED");
        });
        assertThat(response.toolEvidence()).anySatisfy(e -> {
            assertThat(e.toolName()).isEqualTo("payment.lookup");
            assertThat(e.status()).isEqualTo("FAILED");
        });
    }

    @Test
    void execute_toolTimeoutAfterSuccessfulTool_thenPlanningBreaks_answerDoesNotFabricateFailedToolData() {
        // Test 2
        mockPaymentStatusSucceedsThenLookupTimesOutThenPlanningBreaks();

        AgentExecuteResponse response = newService().execute(
                new AgentExecuteRequest(null, "user-1", "What is the status of PMT-1?"), DEFINITION, "corr-1", null);

        assertThat(response.answer()).contains("payment.status").contains("SETTLED");
        // The failed tool's own real error is named explicitly ("payment.lookup (TOOL_TIMEOUT)"),
        // never a fabricated result dump standing in for the data payment.lookup never returned.
        assertThat(response.answer()).contains("payment.lookup (TOOL_TIMEOUT)");
        assertThat(response.answer()).doesNotContain("payment.lookup: {");
    }

    @Test
    void execute_toolTimeoutAfterSuccessfulTool_thenPlanningBreaks_classifiesInsufficientContextNotFailed() {
        // Test 3
        mockPaymentStatusSucceedsThenLookupTimesOutThenPlanningBreaks();

        AgentExecuteResponse response = newService().execute(
                new AgentExecuteRequest(null, "user-1", "What is the status of PMT-1?"), DEFINITION, "corr-1", null);

        assertThat(response.status()).isEqualTo(AgentResponseStatus.INSUFFICIENT_CONTEXT);
        assertThat(insufficientContextCount()).isEqualTo(1.0);
    }

    @Test
    void execute_allToolsFailThenPlanningBreaks_stillClassifiesFailed() {
        // Test 4 - no successful evidence at all (both tool calls FAILED, not just one), so the
        // planning-stage failure that follows must still, correctly, become FAILED - proves the
        // fix does not turn every planning failure into a false partial success.
        when(mcpToolClient.listTools()).thenReturn(List.of(
                new McpToolClient.ToolSummary("payment.status", "desc"),
                new McpToolClient.ToolSummary("payment.lookup", "desc")));
        when(mcpToolClient.callTool("payment.status", Map.of("paymentReference", "PMT-1")))
                .thenReturn(new McpToolClient.ToolCallOutcome(true, Map.of("errorCode", "TOOL_TIMEOUT"), null));
        when(mcpToolClient.callTool("payment.lookup", Map.of("paymentReference", "PMT-1")))
                .thenReturn(new McpToolClient.ToolCallOutcome(true, Map.of("errorCode", "TOOL_TIMEOUT"), null));
        when(agentPlanner.plan(any(), any(), any()))
                .thenReturn(new AgentPlan(PlanAction.CALL_TOOL, "check status", "payment.status", Map.of("paymentReference", "PMT-1"), null, null))
                .thenReturn(new AgentPlan(PlanAction.CALL_TOOL, "check lookup", "payment.lookup", Map.of("paymentReference", "PMT-1"), null, null))
                .thenThrow(AgentException.llmServiceUnavailable("down", true));

        AgentExecuteResponse response = newService().execute(
                new AgentExecuteRequest(null, "user-1", "What is the status of PMT-1?"), DEFINITION, "corr-1", null);

        assertThat(response.status()).isEqualTo(AgentResponseStatus.FAILED);
        assertThat(insufficientContextCount()).isZero();
    }

    @Test
    void execute_successfulToolPlusNonTimeoutRecoverableFailure_thenPlanningBreaks_preservesSuccessfulEvidence() {
        // Test 5 - a different, non-TOOL_TIMEOUT recoverable tool failure proves the fix is
        // generic (any FAILED tool evidence alongside a SUCCESS), not TOOL_TIMEOUT-specific.
        when(mcpToolClient.listTools()).thenReturn(List.of(
                new McpToolClient.ToolSummary("payment.status", "desc"),
                new McpToolClient.ToolSummary("audit.search", "desc")));
        when(mcpToolClient.callTool("payment.status", Map.of("paymentReference", "PMT-1")))
                .thenReturn(new McpToolClient.ToolCallOutcome(false, Map.of("found", true, "status", "SETTLED"), null));
        when(mcpToolClient.callTool("audit.search", Map.of("paymentReference", "PMT-1")))
                .thenReturn(new McpToolClient.ToolCallOutcome(true, Map.of("errorCode", "UPSTREAM_UNAVAILABLE"), null));
        when(agentPlanner.plan(any(), any(), any()))
                .thenReturn(new AgentPlan(PlanAction.CALL_TOOL, "check status", "payment.status", Map.of("paymentReference", "PMT-1"), null, null))
                .thenReturn(new AgentPlan(PlanAction.CALL_TOOL, "search audit", "audit.search", Map.of("paymentReference", "PMT-1"), null, null))
                .thenThrow(AgentException.planParseFailed("LLM planning response did not contain a JSON object."));

        AgentExecuteResponse response = newService().execute(
                new AgentExecuteRequest(null, "user-1", "What is the status of PMT-1?"), DEFINITION, "corr-1", null);

        assertThat(response.status()).isEqualTo(AgentResponseStatus.INSUFFICIENT_CONTEXT);
        assertThat(response.toolEvidence()).anySatisfy(e -> {
            assertThat(e.toolName()).isEqualTo("payment.status");
            assertThat(e.status()).isEqualTo("SUCCESS");
        });
        assertThat(response.answer()).contains("UPSTREAM_UNAVAILABLE");
    }

    @Test
    void execute_multiToolSuccessNoPlanningFailure_noRegression_completesSuccessfully() {
        // Test 6 - the ordinary, non-broken multi-tool path (both tools succeed, planning never
        // fails) must remain exactly SUCCESS/COMPLETED, unaffected by this fix.
        when(mcpToolClient.listTools()).thenReturn(List.of(
                new McpToolClient.ToolSummary("payment.status", "desc"),
                new McpToolClient.ToolSummary("payment.lookup", "desc")));
        when(mcpToolClient.callTool("payment.status", Map.of("paymentReference", "PMT-1")))
                .thenReturn(new McpToolClient.ToolCallOutcome(false, Map.of("found", true, "status", "SETTLED"), null));
        when(mcpToolClient.callTool("payment.lookup", Map.of("paymentReference", "PMT-1")))
                .thenReturn(new McpToolClient.ToolCallOutcome(false, Map.of("found", true, "paymentReference", "PMT-1"), null));
        when(agentPlanner.plan(any(), any(), any()))
                .thenReturn(new AgentPlan(PlanAction.CALL_TOOL, "check status", "payment.status", Map.of("paymentReference", "PMT-1"), null, null))
                .thenReturn(new AgentPlan(PlanAction.CALL_TOOL, "check lookup", "payment.lookup", Map.of("paymentReference", "PMT-1"), null, null))
                .thenReturn(new AgentPlan(PlanAction.FINAL_RESPONSE, "have enough evidence", null, null, null, "Payment PMT-1 is SETTLED."));

        AgentExecuteResponse response = newService().execute(
                new AgentExecuteRequest(null, "user-1", "What is the status of PMT-1?"), DEFINITION, "corr-1", null);

        assertThat(response.status()).isEqualTo(AgentResponseStatus.SUCCESS);
        assertThat(response.answer()).isEqualTo("Payment PMT-1 is SETTLED.");
        assertThat(insufficientContextCount()).isZero();
    }

    @Test
    void execute_toolTimeoutAfterSuccessfulTool_thenPlanningBreaks_auditRecordsBothToolCalls() {
        // Test 7 - the audit/history trail (AgentAuditClient reads execution.getToolCalls()
        // directly) must still carry both the successful and the failed tool call, unaffected by
        // the final status/answer becoming INSUFFICIENT_CONTEXT instead of FAILED.
        mockPaymentStatusSucceedsThenLookupTimesOutThenPlanningBreaks();

        newService().execute(new AgentExecuteRequest(null, "user-1", "What is the status of PMT-1?"), DEFINITION, "corr-1", null);

        ArgumentCaptor<AgentExecution> executionCaptor = ArgumentCaptor.forClass(AgentExecution.class);
        verify(auditClient).recordAgentRun(executionCaptor.capture(), org.mockito.ArgumentMatchers.anyLong());
        AgentExecution auditedExecution = executionCaptor.getValue();
        assertThat(auditedExecution.getToolCalls()).hasSize(2);
        assertThat(auditedExecution.getToolCalls()).extracting("toolName", "status")
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("payment.status", "SUCCESS"),
                        org.assertj.core.groups.Tuple.tuple("payment.lookup", "FAILED"));
        assertThat(auditedExecution.getStatus().name()).isEqualTo("INSUFFICIENT_CONTEXT");
    }
}
