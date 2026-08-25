package com.paymentx.agent.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * English:
 * Hand-registered Micrometer meters for Agent Orchestrator - follows
 * every other AI Platform service's explicit-MeterRegistry-call pattern
 * (not @Timed/@Counted - see RagMetrics'/McpMetrics' javadoc for why).
 * Step 35's exact metric list. Never records userQuery text, LLM plan
 * JSON, tool arguments/results, or any chain-of-thought - only counts,
 * durations, and tags (Step 35 - "Do not log chain-of-thought").
 * Why it exists: Step 35's explicit metric list - agent_requests_total,
 * agent_success_total, agent_failure_total, agent_timeout_total,
 * agent_iterations_total, agent_tool_calls_total,
 * agent_tool_denied_total, agent_rag_calls_total, agent_llm_calls_total,
 * agent_execution_latency, agent_tool_latency, agent_context_size.
 * How it communicates with other components: injected into
 * orchestrator/AgentOrchestratorService; scraped by the same Prometheus
 * instance every other PaymentX service reports to
 * (infra/prometheus.yml, port 8098).
 *
 * Hinglish:
 * Agent Orchestrator ke liye hand-registered Micrometer meters - har
 * doosri AI Platform service ke explicit-MeterRegistry-call pattern ko
 * follow karte hain (@Timed/@Counted nahi - RagMetrics/McpMetrics ka
 * javadoc dekho ki kyun). Step 35 ki exact metric list. Kabhi userQuery
 * text, LLM plan JSON, tool arguments/results, ya koi chain-of-thought
 * record nahi karta - sirf counts, durations, aur tags (Step 35 -
 * "Chain-of-thought log mat karo").
 * Ye kyu hai: Step 35 ki explicit metric list - agent_requests_total,
 * agent_success_total, agent_failure_total, agent_timeout_total,
 * agent_iterations_total, agent_tool_calls_total,
 * agent_tool_denied_total, agent_rag_calls_total, agent_llm_calls_total,
 * agent_execution_latency, agent_tool_latency, agent_context_size.
 * Dusre components se kaise communicate karta hai: orchestrator/
 * AgentOrchestratorService me inject hota hai; usi Prometheus instance
 * dwara scrape hota hai jise har doosri PaymentX service report karti
 * hai (infra/prometheus.yml, port 8098).
 */
@Component
public class AgentMetrics {

    private static final String REQUESTS_COUNTER = "agent_requests_total";
    private static final String SUCCESS_COUNTER = "agent_success_total";
    private static final String FAILURE_COUNTER = "agent_failure_total";
    private static final String TIMEOUT_COUNTER = "agent_timeout_total";
    private static final String ITERATIONS_COUNTER = "agent_iterations_total";
    private static final String TOOL_CALLS_COUNTER = "agent_tool_calls_total";
    private static final String TOOL_DENIED_COUNTER = "agent_tool_denied_total";
    private static final String RAG_CALLS_COUNTER = "agent_rag_calls_total";
    private static final String LLM_CALLS_COUNTER = "agent_llm_calls_total";
    private static final String EXECUTION_LATENCY_TIMER = "agent_execution_latency";
    private static final String TOOL_LATENCY_TIMER = "agent_tool_latency";
    private static final String CONTEXT_SIZE_SUMMARY = "agent_context_size";
    // Phase 4.1 - agentId comes exclusively from registry.AgentDefinition.agentId(), a small,
    // bounded, configured set of values (never raw user input/userQuery text), so it is safe to
    // use as a Micrometer tag - unbounded cardinality from user-generated values is never tagged
    // anywhere in this class.
    private static final String PLAN_REJECTED_COUNTER = "agent_plan_rejected_total";
    // Phase 4.2.2 - distinct from RAG Service's own rag_insufficient_context_total (RagMetrics,
    // paymentx-rag-service): that meter fires whenever a SINGLE RAG retrieval finds nothing above
    // the relevance threshold, a normal, expected outcome of one RETRIEVE_KNOWLEDGE step this
    // agent may legitimately continue past. THIS meter fires only when the agent's OWN bounded
    // loop concludes with status=INSUFFICIENT_CONTEXT - i.e. the agent reached FINAL_RESPONSE
    // having attempted at least one tool/RAG call and had NONE of them succeed (see
    // AgentOrchestratorService.finalizeAnswer) - "the agent determined available evidence was
    // insufficient to support a conclusion", never merely "one RAG call returned zero chunks".
    private static final String INSUFFICIENT_CONTEXT_COUNTER = "agent_insufficient_context_total";

    private final MeterRegistry meterRegistry;

    public AgentMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordRequest(String agentId) {
        Counter.builder(REQUESTS_COUNTER).tag("agent", safeTag(agentId)).register(meterRegistry).increment();
    }

    public void recordSuccess(String agentId) {
        Counter.builder(SUCCESS_COUNTER).tag("agent", safeTag(agentId)).register(meterRegistry).increment();
    }

    public void recordFailure(String agentId, String status) {
        Counter.builder(FAILURE_COUNTER).tag("agent", safeTag(agentId)).tag("status", status).register(meterRegistry).increment();
    }

    public void recordTimeout(String agentId) {
        Counter.builder(TIMEOUT_COUNTER).tag("agent", safeTag(agentId)).register(meterRegistry).increment();
    }

    public void recordIteration() {
        Counter.builder(ITERATIONS_COUNTER).register(meterRegistry).increment();
    }

    public void recordToolCall(String agentId, String toolName) {
        Counter.builder(TOOL_CALLS_COUNTER).tag("agent", safeTag(agentId)).tag("tool", toolName).register(meterRegistry).increment();
    }

    public void recordToolDenied(String agentId, String toolName) {
        Counter.builder(TOOL_DENIED_COUNTER).tag("agent", safeTag(agentId)).tag("tool", safeTag(toolName)).register(meterRegistry).increment();
    }

    /** Fired when planning/AgentPlanValidator rejects a plan - distinguishes "the model proposed
     * something invalid or unauthorized" from other failure causes (a downstream outage, a
     * timeout). {@code reason} is always one of AgentErrorCodes' fixed constants, never
     * free-text, so cardinality stays bounded. */
    public void recordPlanRejected(String agentId, String reason) {
        Counter.builder(PLAN_REJECTED_COUNTER).tag("agent", safeTag(agentId)).tag("reason", safeTag(reason)).register(meterRegistry).increment();
    }

    /** Fired at most once per run when the agent's own outcome concludes
     * AgentState.INSUFFICIENT_CONTEXT - never for a single RAG/tool call's own failure (those are
     * agent_tool_calls_total/agent_rag_calls_total's concern). Reached from two places:
     * AgentOrchestratorService.finalizeAnswer (a normal FINAL_RESPONSE with attempted-but-
     * unsuccessful evidence), and (Phase 5.x) applyPlanningFailure when a later planning-stage
     * failure follows evidence that had already succeeded - a truthful partial outcome, not a
     * downstream-failure metric in its own right. */
    public void recordInsufficientContext(String agentId) {
        Counter.builder(INSUFFICIENT_CONTEXT_COUNTER).tag("agent", safeTag(agentId)).register(meterRegistry).increment();
    }

    public void recordRagCall() {
        Counter.builder(RAG_CALLS_COUNTER).register(meterRegistry).increment();
    }

    public void recordLlmCall() {
        Counter.builder(LLM_CALLS_COUNTER).register(meterRegistry).increment();
    }

    public void recordContextSize(int characters) {
        DistributionSummary.builder(CONTEXT_SIZE_SUMMARY).register(meterRegistry).record(characters);
    }

    public Timer.Sample startTimer() {
        return Timer.start(meterRegistry);
    }

    public void stopExecutionTimer(Timer.Sample sample, String agentId) {
        sample.stop(Timer.builder(EXECUTION_LATENCY_TIMER).tag("agent", safeTag(agentId)).publishPercentileHistogram().register(meterRegistry));
    }

    private String safeTag(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    public void stopToolTimer(Timer.Sample sample, String toolName) {
        sample.stop(Timer.builder(TOOL_LATENCY_TIMER).tag("tool", toolName).register(meterRegistry));
    }
}
