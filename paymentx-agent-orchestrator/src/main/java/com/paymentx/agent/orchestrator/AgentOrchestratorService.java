package com.paymentx.agent.orchestrator;

import com.paymentx.agent.audit.AgentAuditClient;
import com.paymentx.agent.client.LlmServiceClient;
import com.paymentx.agent.client.McpToolClient;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
 * additionally wrapped in a bounded CompletableFuture.get(overallTimeoutMs,
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
 * without a real plan) and ends the run immediately as FAILED/REFUSED.
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
 * bounded CompletableFuture.get(overallTimeoutMs, ...) me wrapped hai
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

    public AgentExecuteResponse execute(AgentExecuteRequest request, String correlationId, String traceId) {
        String requestId = java.util.UUID.randomUUID().toString();
        AgentExecution execution = new AgentExecution(requestId, correlationId, traceId, request.userId(), request.userQuery());

        metrics.recordRequest();
        Timer.Sample overallTimer = metrics.startTimer();
        long startTime = System.currentTimeMillis();
        McpToolClient.setCorrelationId(correlationId);

        try {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> runLoop(execution), executor);
            future.get(properties.getOverallTimeoutMs(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException timedOut) {
            execution.setStatus(AgentState.TIMEOUT);
            metrics.recordTimeout();
        } catch (ExecutionException wrapped) {
            log.error("Unexpected agent execution failure requestId={}", requestId, wrapped.getCause());
            execution.setStatus(AgentState.FAILED);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            execution.setStatus(AgentState.FAILED);
        } finally {
            McpToolClient.clearCorrelationId();
            metrics.stopExecutionTimer(overallTimer);
            auditClient.recordAgentRun(execution);
        }

        long totalLatencyMs = System.currentTimeMillis() - startTime;
        return buildResponse(execution, totalLatencyMs);
    }

    private void runLoop(AgentExecution execution) {
        execution.setCurrentStep(AgentState.RECEIVED);

        while (true) {
            if (execution.getIteration() >= properties.getMaxIterations()) {
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

            AgentPlan plan;
            try {
                metrics.recordLlmCall();
                plan = agentPlanner.plan(execution, execution.getCorrelationId());
                planValidator.validate(plan, discoveredTools);
            } catch (AgentException planFailure) {
                applyPlanningFailure(execution, planFailure);
                return;
            }

            switch (plan.action()) {
                case CALL_TOOL -> executeTool(execution, plan);
                case RETRIEVE_KNOWLEDGE -> executeRetrieval(execution, plan);
                case FINAL_RESPONSE -> {
                    execution.setCurrentStep(AgentState.GENERATING);
                    finalizeAnswer(execution, plan);
                    return;
                }
            }
        }
    }

    private void applyPlanningFailure(AgentExecution execution, AgentException planFailure) {
        AgentState status = switch (planFailure.getErrorCode()) {
            case AgentErrorCodes.LLM_REFUSED -> AgentState.REFUSED;
            case AgentErrorCodes.TOOL_NOT_ALLOWED -> AgentState.DENIED;
            default -> AgentState.FAILED;
        };
        execution.setStatus(status);
        metrics.recordFailure(status.name());
        log.warn("Agent planning failed requestId={} errorCode={} status={}", execution.getRequestId(), planFailure.getErrorCode(), status);
    }

    private void executeTool(AgentExecution execution, AgentPlan plan) {
        execution.setCurrentStep(AgentState.TOOL_EXECUTION);
        metrics.recordToolCall(plan.tool());
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
            RagServiceClient.RagQueryResult result = ragServiceClient.query(plan.ragQuery(), execution.getCorrelationId());
            List<String> sourceLabels = result.sources().stream().map(RagServiceClient.SourceRecord::source).toList();
            execution.addRagRetrieval(new RagRetrievalRecord(plan.ragQuery(), result.status(), result.answer(), sourceLabels));
        } catch (AgentException ragFailure) {
            execution.addRagRetrieval(new RagRetrievalRecord(plan.ragQuery(), "FAILED", null, List.of()));
        } finally {
            execution.setCurrentStep(AgentState.EVALUATING);
        }
    }

    private void finalizeAnswer(AgentExecution execution, AgentPlan plan) {
        execution.setFinalAnswer(plan.answer());

        boolean anyAttempted = !execution.getToolCalls().isEmpty() || !execution.getRetrievedContext().isEmpty();
        boolean anySucceeded = execution.getToolCalls().stream().anyMatch(t -> "SUCCESS".equals(t.status()))
                || execution.getRetrievedContext().stream().anyMatch(r -> "SUCCESS".equals(r.status()));

        AgentState status = (!anyAttempted || anySucceeded) ? AgentState.COMPLETED : AgentState.INSUFFICIENT_CONTEXT;
        execution.setStatus(status);
        if (status == AgentState.COMPLETED) {
            metrics.recordSuccess();
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

        return new AgentExecuteResponse(answer, status, sources, toolEvidence, executionMetadata);
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
