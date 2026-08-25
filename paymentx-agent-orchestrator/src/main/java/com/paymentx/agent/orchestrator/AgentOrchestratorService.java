package com.paymentx.agent.orchestrator;

import com.paymentx.agent.audit.AgentAuditClient;
import com.paymentx.agent.client.LlmServiceClient;
import com.paymentx.agent.client.McpToolClient;
import com.paymentx.agent.client.PaymentScheme;
import com.paymentx.agent.client.RagQueryFilters;
import com.paymentx.agent.client.RagServiceClient;
import com.paymentx.agent.config.AgentOrchestratorProperties;
import com.paymentx.agent.dto.AgentExecuteRequest;
import com.paymentx.agent.dto.AgentExecuteResponse;
import com.paymentx.agent.dto.AgentHealthResponse;
import com.paymentx.agent.dto.AgentResponseStatus;
import com.paymentx.agent.dto.ExecutionMetadata;
import com.paymentx.agent.dto.SourceEvidence;
import com.paymentx.agent.dto.ToolEvidence;
import com.paymentx.agent.exception.AgentErrorCodes;
import com.paymentx.agent.exception.AgentException;
import com.paymentx.agent.planning.AgentPlanValidator;
import com.paymentx.agent.planning.AgentPlanner;
import com.paymentx.agent.registry.AgentDefinition;
import com.paymentx.agent.state.AgentExecution;
import com.paymentx.agent.state.AgentPlan;
import com.paymentx.agent.state.AgentState;
import com.paymentx.agent.state.RagRetrievalRecord;
import com.paymentx.agent.state.ToolCallRecord;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * English:
 * THE single bounded agent loop (Step 11) - the real implementation of
 * every responsibility Step 22 of the brief lists conceptually as
 * `AgentOrchestrator`. One real state/AgentExecution is created per
 * call and driven through state/AgentState transitions until it reaches
 * a real terminal outcome (COMPLETED/DENIED/INSUFFICIENT_CONTEXT/
 * MAX_ITERATIONS/TIMEOUT/FAILED) - never an unbounded `while(true)`
 * (Step 11's explicit prohibition): the loop condition checks
 * iteration &lt; maxIterations on every pass, and the ENTIRE loop is
 * additionally wrapped in a bounded ExecutorService Future.get(overallTimeoutMs,
 * ...) (Step 33 - the same bounded-execution pattern MCP Gateway's own
 * ToolInvoker already established in Phase 3.7, reused here rather than
 * inventing a second mechanism).
 * One planning decision per iteration (planning/AgentPlanner + Step
 * 22/23's planning/AgentPlanValidator) drives exactly one of three
 * outcomes:
 * - CALL_TOOL: validated against policy/AgentToolPolicy (Step 9/30 -
 *   checked BEFORE client/McpToolClient is ever touched; a denied
 *   request terminates the run immediately as DENIED, per Step 43's own
 *   expected test behavior - "Refund PMT-123" must produce
 *   WRITE_OPERATION_NOT_ALLOWED-equivalent DENIED, never a retried
 *   attempt at a different phrasing), then executed for real through
 *   MCP Gateway.
 * - RETRIEVE_KNOWLEDGE: delegated entirely to client/RagServiceClient
 *   (Step 5 - never re-implemented here).
 * - FINAL_RESPONSE: ends the loop. The SAME planning LLM call that
 *   decided FINAL_RESPONSE also produced the answer text (Step 22's
 *   plan schema carries `answer` directly) - by the time that decision
 *   is reached, the prompt already included the full execution history
 *   (all tool/RAG evidence gathered so far via
 *   {{executionHistory}}), so this one answer field is already the real
 *   synthesis Step 13 asks for; no separate "final synthesis" LLM call
 *   exists as a distinct step, avoiding a redundant, unneeded second
 *   generation call for the common case.
 * A genuine RAG or tool EXECUTION failure (the call itself throwing,
 * not being denied) is recorded as a real failed evidence entry and the
 * loop CONTINUES - the next planning iteration sees the real failure in
 * EXECUTION HISTORY and can decide how to proceed (Step 17/18 - "do not
 * invent fallback data... return a truthful failure"). A PLANNING
 * failure (Prompt/LLM Service unreachable, the LLM's response could not
 * be parsed/validated, or the LLM itself refused) cannot be recovered
 * from within the loop at all (there is no way to decide a next step
 * without a real plan) and ends the run immediately - FAILED/REFUSED,
 * unless real tool/RAG evidence had already succeeded in an earlier
 * iteration, in which case the run instead ends INSUFFICIENT_CONTEXT
 * with a truthful partial answer built from that evidence (Phase 5.x -
 * see applyPlanningFailure) rather than discarding it.
 * SUCCESS vs INSUFFICIENT_CONTEXT at FINAL_RESPONSE time is decided
 * honestly from what was actually gathered (Step 16/18): if RAG/tool
 * evidence was attempted at all but NONE of it succeeded, the run is
 * reported INSUFFICIENT_CONTEXT even though the LLM produced text -
 * never silently upgraded to a false SUCCESS. A plain conversational
 * turn that never needed RAG/tools at all (Step 4 case A) is COMPLETED
 * with no evidence, which is the honest, correct outcome for that case.
 * Why it exists: Step 4/5/6/7/8/9/10/11/12/13/16/17/18/19/22/23/27/28/
 * 29/30/33/34/36.
 * How it communicates with other components: called by
 * controller/AgentController.execute(...); calls
 * planning/AgentPlanner, planning/AgentPlanValidator,
 * policy/AgentToolPolicy (indirectly, via the validator),
 * client/McpToolClient, client/RagServiceClient, audit/AgentAuditClient,
 * and metrics/AgentMetrics.
 *
 * Hinglish:
 * Ye EK bounded agent loop hai (Step 11) - brief ke Step 22 me
 * conceptually `AgentOrchestrator` ke roop me list ki gayi har
 * responsibility ka real implementation. Har call ke liye ek real
 * state/AgentExecution banaya jaata hai aur state/AgentState transitions
 * ke through drive kiya jaata hai jab tak ye ek real terminal outcome
 * (COMPLETED/DENIED/INSUFFICIENT_CONTEXT/MAX_ITERATIONS/TIMEOUT/FAILED)
 * tak nahi pahunch jaata - kabhi ek unbounded `while(true)` nahi (Step
 * 11 ka explicit prohibition): loop condition har pass par iteration
 * &lt; maxIterations check karta hai, aur POORA loop additionally ek
 * bounded ExecutorService Future.get(overallTimeoutMs, ...) me wrapped hai
 * (Step 33 - wahi bounded-execution pattern jo MCP Gateway ka apna
 * ToolInvoker Phase 3.7 me already establish kar chuka hai, yahan reuse
 * kiya gaya, ek doosra mechanism invent karne ke bajaye).
 * Har iteration me ek planning decision (planning/AgentPlanner + Step
 * 22/23 ka planning/AgentPlanValidator) teen outcomes me se exactly ek
 * ko drive karta hai:
 * - CALL_TOOL: policy/AgentToolPolicy ke against validate hota hai
 *   (Step 9/30 - client/McpToolClient ko kabhi touch hone se PEHLE
 *   check hota hai; ek denied request run ko turant DENIED ke roop me
 *   terminate kar deti hai, Step 43 ke apne expected test behavior ke
 *   hisaab se - "Refund PMT-123" ko WRITE_OPERATION_NOT_ALLOWED-
 *   equivalent DENIED produce karna chahiye, kabhi ek doosri phrasing
 *   ka retried attempt nahi), phir MCP Gateway ke through really
 *   execute hota hai.
 * - RETRIEVE_KNOWLEDGE: poori tarah client/RagServiceClient ko delegate
 *   hota hai (Step 5 - yahan kabhi re-implement nahi hota).
 * - FINAL_RESPONSE: loop khatam karta hai. Wahi planning LLM call jisne
 *   FINAL_RESPONSE decide kiya usne answer text bhi produce kiya (Step
 *   22 ka plan schema `answer` seedhe carry karta hai) - jis pal wo
 *   decision reach hoti hai, prompt me pehle se hi poori execution
 *   history shamil hoti hai (ab tak ka saara tool/RAG evidence,
 *   {{executionHistory}} ke through), isliye ye ek answer field pehle
 *   se hi wo real synthesis hai jo Step 13 maangta hai; ek alag "final
 *   synthesis" LLM call ek distinct step ke roop me exist nahi karta,
 *   common case ke liye ek redundant, na-chahiye doosri generation call
 *   avoid karte hue.
 * Ek genuine RAG ya tool EXECUTION failure (call khud throw kare, deny
 * na ho) ek real failed evidence entry ke roop me record hoti hai aur
 * loop CONTINUE karta hai - agli planning iteration EXECUTION HISTORY
 * me real failure dekhti hai aur decide kar sakti hai ki aage kaise
 * badhna hai (Step 17/18 - "fallback data invent mat karo... ek truthful
 * failure return karo"). Ek PLANNING failure (Prompt/LLM Service
 * unreachable, LLM ka response parse/validate nahi ho saka, ya LLM ne
 * khud refuse kiya) loop ke andar se recover nahi ki ja sakti (bina ek
 * real plan ke agla step decide karne ka koi tareeka nahi hai) aur run
 * ko turant FAILED/REFUSED ke roop me khatam kar deti hai.
 * FINAL_RESPONSE time par SUCCESS vs INSUFFICIENT_CONTEXT honestly
 * decide hota hai ki actually kya gather hua (Step 16/18): agar RAG/tool
 * evidence bilkul attempt hua lekin usme se KUCH bhi succeed nahi hua,
 * run INSUFFICIENT_CONTEXT report hoti hai chahe LLM ne text produce
 * kiya ho - kabhi silently ek false SUCCESS me upgrade nahi hoti. Ek
 * plain conversational turn jise RAG/tools ki zaroorat hi nahi thi
 * (Step 4 case A) COMPLETED hai bina kisi evidence ke, jo us case ke
 * liye honest, correct outcome hai.
 * Ye kyu hai: Step 4/5/6/7/8/9/10/11/12/13/16/17/18/19/22/23/27/28/29/
 * 30/33/34/36.
 * Dusre components se kaise communicate karta hai:
 * controller/AgentController.execute(...) dwara call hota hai;
 * planning/AgentPlanner, planning/AgentPlanValidator,
 * policy/AgentToolPolicy (indirectly, validator ke through),
 * client/McpToolClient, client/RagServiceClient, audit/AgentAuditClient,
 * aur metrics/AgentMetrics ko call karta hai.
 */
@Service
@Slf4j
public class AgentOrchestratorService {

    private final AgentPlanner agentPlanner;
    private final AgentPlanValidator planValidator;
    private final McpToolClient mcpToolClient;
    private final RagServiceClient ragServiceClient;
    private final AgentAuditClient auditClient;
    private final com.paymentx.agent.metrics.AgentMetrics metrics;
    private final AgentOrchestratorProperties properties;
    private final RestTemplate healthRestTemplate;

    private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "agent-exec");
        thread.setDaemon(true);
        return thread;
    });

    public AgentOrchestratorService(AgentPlanner agentPlanner, AgentPlanValidator planValidator,
                                     McpToolClient mcpToolClient, RagServiceClient ragServiceClient,
                                     AgentAuditClient auditClient, com.paymentx.agent.metrics.AgentMetrics metrics,
                                     AgentOrchestratorProperties properties, RestTemplateBuilder restTemplateBuilder) {
        this.agentPlanner = agentPlanner;
        this.planValidator = planValidator;
        this.mcpToolClient = mcpToolClient;
        this.ragServiceClient = ragServiceClient;
        this.auditClient = auditClient;
        this.metrics = metrics;
        this.properties = properties;
        this.healthRestTemplate = restTemplateBuilder
                .requestFactory(SimpleClientHttpRequestFactory::new)
                .connectTimeout(Duration.ofMillis(2000))
                .readTimeout(Duration.ofMillis(2000))
                .build();
    }

    public AgentExecuteResponse execute(AgentExecuteRequest request, AgentDefinition definition, String correlationId, String traceId) {
        String requestId = java.util.UUID.randomUUID().toString();
        AgentExecution execution = new AgentExecution(
                requestId, correlationId, traceId, request.userId(), effectiveUserQuery(request), definition.agentId());
        execution.setPaymentReference(request.paymentReference());

        metrics.recordRequest(definition.agentId());
        Timer.Sample overallTimer = metrics.startTimer();
        long startTime = System.currentTimeMillis();
        McpToolClient.setCorrelationId(correlationId);

        long totalLatencyMs = 0;
        // Deliberately executor.submit(...) (a real java.util.concurrent.Future), not
        // CompletableFuture.runAsync(...): CompletableFuture.cancel(true)'s own javadoc states the
        // mayInterruptIfRunning flag "has no effect in this implementation" - it marks the future
        // cancelled but never calls Thread.interrupt() on the task actually running it, so the
        // background runLoop() would keep executing (and could keep making real LLM/MCP calls,
        // burning scarce quota invisibly) after the caller was already told TIMEOUT. A real
        // ExecutorService Future's cancel(true) does interrupt the worker thread.
        Future<?> future = executor.submit(() -> runLoop(execution, definition));
        try {
            future.get(effectiveTimeoutMs(definition), TimeUnit.MILLISECONDS);
        } catch (TimeoutException timedOut) {
            execution.setStatus(AgentState.TIMEOUT);
            metrics.recordTimeout(definition.agentId());
            // Best-effort: a thread blocked in a plain blocking HTTP call does not always observe
            // the interrupt immediately, but it stops the loop as soon as the running call next
            // checks its interrupted status, and this class's own CopyOnWriteArrayList fields
            // ensure that any residual race window never corrupts the audit/response read below.
            future.cancel(true);
        } catch (ExecutionException wrapped) {
            log.error("Unexpected agent execution failure requestId={}", requestId, wrapped.getCause());
            execution.setStatus(AgentState.FAILED);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            execution.setStatus(AgentState.FAILED);
        } finally {
            McpToolClient.clearCorrelationId();
            metrics.stopExecutionTimer(overallTimer, definition.agentId());
            // Phase 4.2.3 item 19 - latency is now known before the audit write, closing the gap
            // Phase 4.1's own design doc already flagged (PAYMENTX_PHASE_4_1_AGENT_FOUNDATION.md
            // §11): "not currently in the agent-run payload... worth adding, cheap addition."
            totalLatencyMs = System.currentTimeMillis() - startTime;
            auditClient.recordAgentRun(execution, totalLatencyMs);
        }

        return buildResponse(execution, totalLatencyMs);
    }

    // Phase 4.2.3 - grounds the investigation directly when a caller supplies paymentReference
    // (see dto.AgentExecuteRequest's own javadoc), rather than relying solely on the planning
    // LLM to extract one from free text. Purely a text-composition step - it does not change
    // agent identity, tool access, or any bounded-execution limit, and when paymentReference is
    // absent this returns request.userQuery() completely unchanged (byte-identical to every
    // pre-Phase-4.2.3 call).
    private String effectiveUserQuery(AgentExecuteRequest request) {
        if (request.paymentReference() == null || request.paymentReference().isBlank()) {
            return request.userQuery();
        }
        return "Payment reference: " + request.paymentReference() + ". " + request.userQuery();
    }

    // Phase 4.1 - an AgentDefinition's own maxIterations/timeoutMs override the platform default
    // when set; both remain trusted, resolved values that a plan/LLM output can never influence.
    private int effectiveMaxIterations(AgentDefinition definition) {
        return definition.maxIterations() != null ? definition.maxIterations() : properties.getMaxIterations();
    }

    private long effectiveTimeoutMs(AgentDefinition definition) {
        return definition.timeoutMs() != null ? definition.timeoutMs() : properties.getOverallTimeoutMs();
    }

    private void runLoop(AgentExecution execution, AgentDefinition definition) {
        execution.setCurrentStep(AgentState.RECEIVED);

        while (true) {
            if (execution.getIteration() >= effectiveMaxIterations(definition)) {
                execution.setStatus(AgentState.MAX_ITERATIONS);
                return;
            }
            if (execution.getToolCallCount() >= properties.getMaxToolCalls()) {
                // Step 3's state list has no distinct "max tool calls" terminal state - reusing
                // MAX_ITERATIONS here since both are the same real thing from the caller's point of
                // view: the bounded loop's budget ran out before FINAL_RESPONSE, a safe stop, not a hang.
                execution.setStatus(AgentState.MAX_ITERATIONS);
                return;
            }

            execution.setIteration(execution.getIteration() + 1);
            metrics.recordIteration();
            execution.setCurrentStep(execution.getIteration() == 1 ? AgentState.CLASSIFYING : AgentState.PLANNING);

            List<McpToolClient.ToolSummary> discoveredTools;
            try {
                discoveredTools = mcpToolClient.listTools();
            } catch (AgentException toolListUnavailable) {
                // Degrade gracefully - a RAG-only conversation does not need MCP Gateway to be up at all.
                discoveredTools = List.of();
            }

            AgentPlan plan = null;
            try {
                metrics.recordLlmCall();
                plan = agentPlanner.plan(execution, definition, execution.getCorrelationId());
                planValidator.validate(plan, discoveredTools, definition);
            } catch (AgentException planFailure) {
                applyPlanningFailure(execution, definition, planFailure, plan);
                return;
            }

            switch (plan.action()) {
                case CALL_TOOL -> executeTool(execution, definition, plan);
                case RETRIEVE_KNOWLEDGE -> executeRetrieval(execution, plan);
                case FINAL_RESPONSE -> {
                    execution.setCurrentStep(AgentState.GENERATING);
                    finalizeAnswer(execution, definition, plan);
                    return;
                }
            }
        }
    }

    // Phase 5.x - a planning-stage infrastructure failure (LLM/Prompt Service unavailable, MCP
    // tool discovery down, an unparseable/invalid LLM plan) used to collapse straight to FAILED
    // regardless of what had already been gathered, silently discarding a genuine, already-
    // SUCCEEDED tool/RAG result from an earlier iteration (e.g. a real payment.status SETTLED
    // read, followed by a payment.lookup TOOL_TIMEOUT and then a broken next planning call) -
    // see PAYMENTX_PHASE_5_PAYMENT_TEST_VALIDATION_AGENT.md's live E2E finding. When real
    // evidence already succeeded, the honest outcome is a truthful partial answer
    // (INSUFFICIENT_CONTEXT), never a blanket FAILED that hides evidence the caller already has a
    // right to see. When nothing succeeded, FAILED remains the correct, unchanged outcome - this
    // never upgrades a genuinely empty-handed run into a false partial success.
    private void applyPlanningFailure(AgentExecution execution, AgentDefinition definition, AgentException planFailure, AgentPlan rejectedPlan) {
        String errorCode = planFailure.getErrorCode();
        AgentState status;
        if (AgentErrorCodes.LLM_REFUSED.equals(errorCode)) {
            status = AgentState.REFUSED;
        } else if (AgentErrorCodes.TOOL_NOT_ALLOWED.equals(errorCode)) {
            status = AgentState.DENIED;
        } else if (anyToolOrRagSucceeded(execution)) {
            status = AgentState.INSUFFICIENT_CONTEXT;
            execution.setFinalAnswer(buildPartialAnswer(execution, planFailure));
        } else {
            status = AgentState.FAILED;
        }
        execution.setStatus(status);

        if (status == AgentState.INSUFFICIENT_CONTEXT) {
            metrics.recordInsufficientContext(definition.agentId());
        } else {
            metrics.recordFailure(definition.agentId(), status.name());
        }
        if (AgentErrorCodes.PLAN_INVALID.equals(errorCode) || AgentErrorCodes.TOOL_NOT_ALLOWED.equals(errorCode)) {
            metrics.recordPlanRejected(definition.agentId(), errorCode);
        }
        if (AgentErrorCodes.TOOL_NOT_ALLOWED.equals(errorCode)) {
            metrics.recordToolDenied(definition.agentId(), rejectedPlan != null ? rejectedPlan.tool() : null);
        }
        log.warn("Agent planning failed requestId={} agentId={} errorCode={} status={}",
                execution.getRequestId(), definition.agentId(), errorCode, status);
    }

    // Shared by finalizeAnswer (a normal FINAL_RESPONSE outcome) and applyPlanningFailure (a
    // broken later iteration) - both need the same honest "did anything real actually succeed"
    // check, never independently reimplemented.
    private boolean anyToolOrRagSucceeded(AgentExecution execution) {
        return execution.getToolCalls().stream().anyMatch(t -> "SUCCESS".equals(t.status()))
                || execution.getRetrievedContext().stream().anyMatch(r -> "SUCCESS".equals(r.status()));
    }

    // Builds a truthful partial answer purely from already-gathered, already-sanitized evidence -
    // deliberately never a new LLM call (the planning/LLM path is exactly what just failed, and
    // calling it again here would either fail again or spend quota outside the caller's control).
    // Successful evidence is reported verbatim; every non-SUCCESS tool/RAG entry is named
    // explicitly (never hidden) along with its most specific known error, preferring the tool's
    // own structured errorCode (e.g. TOOL_TIMEOUT) over the generic TOOL_EXECUTION_FAILED label
    // when MCP Gateway's response carried one.
    private String buildPartialAnswer(AgentExecution execution, AgentException planFailure) {
        StringBuilder sb = new StringBuilder();
        sb.append("This request could not be fully completed, but the following evidence was already gathered:\n");
        for (ToolCallRecord toolCall : execution.getToolCalls()) {
            if ("SUCCESS".equals(toolCall.status())) {
                sb.append("- ").append(toolCall.toolName()).append(": ").append(toolCall.result()).append('\n');
            }
        }
        for (RagRetrievalRecord rag : execution.getRetrievedContext()) {
            if ("SUCCESS".equals(rag.status())) {
                sb.append("- Knowledge retrieval for \"").append(rag.query()).append("\": ").append(rag.answer()).append('\n');
            }
        }
        sb.append("The following could not provide evidence:\n");
        for (ToolCallRecord toolCall : execution.getToolCalls()) {
            if (!"SUCCESS".equals(toolCall.status())) {
                sb.append("- ").append(toolCall.toolName()).append(" (").append(toolFailureReason(toolCall)).append(")\n");
            }
        }
        for (RagRetrievalRecord rag : execution.getRetrievedContext()) {
            if (!"SUCCESS".equals(rag.status())) {
                sb.append("- Knowledge retrieval for \"").append(rag.query()).append("\" (").append(rag.status()).append(")\n");
            }
        }
        sb.append("Additionally, the assistant's own planning step could not complete (")
                .append(planFailure.getErrorCode())
                .append("), so no further investigation could be performed this run.");
        return sb.toString();
    }

    private String toolFailureReason(ToolCallRecord toolCall) {
        Object specificErrorCode = toolCall.result() != null ? toolCall.result().get("errorCode") : null;
        if (specificErrorCode instanceof String specific && !specific.isBlank()) {
            return specific;
        }
        return toolCall.errorCode() != null ? toolCall.errorCode() : toolCall.status();
    }

    private void executeTool(AgentExecution execution, AgentDefinition definition, AgentPlan plan) {
        execution.setCurrentStep(AgentState.TOOL_EXECUTION);
        metrics.recordToolCall(definition.agentId(), plan.tool());
        Timer.Sample toolTimer = metrics.startTimer();
        long start = System.currentTimeMillis();
        try {
            McpToolClient.ToolCallOutcome outcome = mcpToolClient.callTool(plan.tool(), plan.arguments());
            long latency = System.currentTimeMillis() - start;
            String status = outcome.isError() ? "FAILED" : "SUCCESS";
            execution.addToolCall(new ToolCallRecord(plan.tool(), plan.arguments(), status, outcome.structuredContent(),
                    outcome.isError() ? "TOOL_EXECUTION_FAILED" : null, latency));
        } catch (AgentException toolFailure) {
            long latency = System.currentTimeMillis() - start;
            execution.addToolCall(new ToolCallRecord(plan.tool(), plan.arguments(), "FAILED", Map.of(),
                    toolFailure.getErrorCode(), latency));
        } finally {
            metrics.stopToolTimer(toolTimer, plan.tool());
            execution.setCurrentStep(AgentState.EVALUATING);
        }
    }

    private void executeRetrieval(AgentExecution execution, AgentPlan plan) {
        execution.setCurrentStep(AgentState.RETRIEVING);
        metrics.recordRagCall();
        try {
            RagQueryFilters filters = deriveRagFilters(execution);
            RagServiceClient.RagQueryResult result = ragServiceClient.query(plan.ragQuery(), filters, execution.getCorrelationId());
            List<String> sourceLabels = result.sources().stream().map(RagServiceClient.SourceRecord::source).toList();
            execution.addRagRetrieval(new RagRetrievalRecord(plan.ragQuery(), result.status(), result.answer(), sourceLabels));
        } catch (AgentException ragFailure) {
            execution.addRagRetrieval(new RagRetrievalRecord(plan.ragQuery(), "FAILED", null, List.of()));
        } finally {
            execution.setCurrentStep(AgentState.EVALUATING);
        }
    }

    // Phase 4.2.3 - derives RAG retrieval filters exclusively from real, already-gathered tool
    // EVIDENCE in this execution - never from anything the plan/LLM itself asserts (plan has no
    // filter field at all; see state.AgentPlan). Only `paymentScheme` is derived, and only from a
    // real payment.lookup SUCCESS result's own `scheme` field - the one filter with a clean,
    // lossless, unambiguous mapping from real tool output to a real client.PaymentScheme value.
    // Deliberately does NOT attempt to derive errorCode/service/severity/retryable from evidence
    // in this phase: failureReason is free text (see docs/ai/error-analyzer/payment-errors.md),
    // and pattern-matching it into a specific errorCode would itself be a kind of fabrication -
    // exactly what this phase's "do not fabricate filter values" instruction rules out. Applies
    // to every agent's RETRIEVE_KNOWLEDGE step, not only error-analyzer's - harmless and never
    // behavior-changing for an execution where no payment.lookup succeeded (filters stay empty,
    // producing the exact same unfiltered request as before this phase).
    private RagQueryFilters deriveRagFilters(AgentExecution execution) {
        for (ToolCallRecord toolCall : execution.getToolCalls()) {
            if ("payment.lookup".equals(toolCall.toolName()) && "SUCCESS".equals(toolCall.status())) {
                Object rawScheme = toolCall.result() != null ? toolCall.result().get("scheme") : null;
                if (rawScheme instanceof String schemeText) {
                    try {
                        PaymentScheme scheme = PaymentScheme.valueOf(schemeText);
                        return new RagQueryFilters(null, null, scheme, null, null, null);
                    } catch (IllegalArgumentException notARealScheme) {
                        // Real payment.lookup only ever returns one of the three real schemes
                        // (payment-service's own PaymentScheme enum) - an unrecognized value here
                        // means don't filter, never guess/fabricate a scheme.
                        return null;
                    }
                }
            }
        }
        return null;
    }

    private void finalizeAnswer(AgentExecution execution, AgentDefinition definition, AgentPlan plan) {
        execution.setFinalAnswer(plan.answer());

        boolean anyAttempted = !execution.getToolCalls().isEmpty() || !execution.getRetrievedContext().isEmpty();
        boolean anySucceeded = anyToolOrRagSucceeded(execution);

        AgentState status = (!anyAttempted || anySucceeded) ? AgentState.COMPLETED : AgentState.INSUFFICIENT_CONTEXT;
        execution.setStatus(status);
        if (status == AgentState.COMPLETED) {
            metrics.recordSuccess(definition.agentId());
        } else {
            // Phase 4.2.2 - fired here specifically because this IS the point the agent's own
            // outcome is determined (anyAttempted && !anySucceeded), not merely because some
            // upstream call returned zero results - see AgentMetrics.recordInsufficientContext's
            // own javadoc for the distinction from RAG Service's per-call metric.
            metrics.recordInsufficientContext(definition.agentId());
        }
    }

    private AgentExecuteResponse buildResponse(AgentExecution execution, long totalLatencyMs) {
        AgentResponseStatus status = mapStatus(execution.getStatus());

        List<SourceEvidence> sources = new ArrayList<>();
        for (RagRetrievalRecord rag : execution.getRetrievedContext()) {
            // Individual RagSource-level detail is not retained on RagRetrievalRecord (only labels) -
            // sourceLabels already carries what RAG Service itself calls each source; a fuller
            // document/chunk/score breakdown would require RagRetrievalRecord to carry the full
            // RagServiceClient.SourceRecord list, which is a reasonable Phase 3.9 enhancement, not
            // required for this phase's evidence requirement (Step 15 only asks for traceability).
            for (String label : rag.sourceLabels()) {
                sources.add(new SourceEvidence(null, null, label, 0.0));
            }
        }

        List<ToolEvidence> toolEvidence = execution.getToolCalls().stream()
                .map(t -> new ToolEvidence(t.toolName(), t.status(), t.result()))
                .toList();

        ExecutionMetadata executionMetadata = new ExecutionMetadata(
                execution.getIteration(), execution.getToolCallCount(), !execution.getRetrievedContext().isEmpty(), totalLatencyMs);

        String answer = execution.getFinalAnswer() != null ? execution.getFinalAnswer() : honestFallbackAnswer(status);

        return new AgentExecuteResponse(answer, status, sources, toolEvidence, executionMetadata,
                execution.getRequestId(), execution.getCorrelationId(), execution.getAgentId(),
                execution.getLastLlmProvider(), execution.isFallbackUsedInExecution(), execution.getLastFallbackReason());
    }

    private String honestFallbackAnswer(AgentResponseStatus status) {
        return switch (status) {
            case DENIED -> "This request requires an operation that is not permitted for the AI assistant.";
            case MAX_ITERATIONS -> "This question required more investigation steps than are currently allowed.";
            case TIMEOUT -> "This request took too long to process.";
            case REFUSED -> "The assistant declined to answer this request.";
            case INSUFFICIENT_CONTEXT -> "There is not enough PaymentX knowledge or data available to answer this reliably.";
            default -> "This request could not be completed.";
        };
    }

    private AgentResponseStatus mapStatus(AgentState state) {
        return switch (state) {
            case COMPLETED -> AgentResponseStatus.SUCCESS;
            case INSUFFICIENT_CONTEXT -> AgentResponseStatus.INSUFFICIENT_CONTEXT;
            case REFUSED -> AgentResponseStatus.REFUSED;
            case DENIED -> AgentResponseStatus.DENIED;
            case MAX_ITERATIONS -> AgentResponseStatus.MAX_ITERATIONS;
            case TIMEOUT -> AgentResponseStatus.TIMEOUT;
            default -> AgentResponseStatus.FAILED;
        };
    }

    public AgentHealthResponse health() {
        Map<String, String> dependencies = new LinkedHashMap<>();
        dependencies.put("ragService", probe(properties.getRagServiceUrl()));
        dependencies.put("mcpGateway", probe(properties.getMcpGatewayUrl()));
        dependencies.put("promptService", probe(properties.getPromptServiceUrl()));
        dependencies.put("llmService", probe(properties.getLlmServiceUrl()));

        boolean allUp = dependencies.values().stream().allMatch("UP"::equals);
        return new AgentHealthResponse(allUp ? "UP" : "DEGRADED", dependencies, OffsetDateTime.now());
    }

    private String probe(String baseUrl) {
        try {
            ResponseEntity<JsonNode> response = healthRestTemplate.exchange(
                    baseUrl + "/actuator/health", HttpMethod.GET, HttpEntity.EMPTY, JsonNode.class);
            String status = response.getBody() != null ? response.getBody().path("status").asText(null) : null;
            return "UP".equals(status) ? "UP" : "DOWN";
        } catch (Exception probeFailure) {
            return "DOWN";
        }
    }
}
