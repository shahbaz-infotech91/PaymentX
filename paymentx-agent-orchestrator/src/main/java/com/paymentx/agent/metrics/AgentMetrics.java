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

    private final MeterRegistry meterRegistry;

    public AgentMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordRequest() {
        Counter.builder(REQUESTS_COUNTER).register(meterRegistry).increment();
    }

    public void recordSuccess() {
        Counter.builder(SUCCESS_COUNTER).register(meterRegistry).increment();
    }

    public void recordFailure(String status) {
        Counter.builder(FAILURE_COUNTER).tag("status", status).register(meterRegistry).increment();
    }

    public void recordTimeout() {
        Counter.builder(TIMEOUT_COUNTER).register(meterRegistry).increment();
    }

    public void recordIteration() {
        Counter.builder(ITERATIONS_COUNTER).register(meterRegistry).increment();
    }

    public void recordToolCall(String toolName) {
        Counter.builder(TOOL_CALLS_COUNTER).tag("tool", toolName).register(meterRegistry).increment();
    }

    public void recordToolDenied(String toolName) {
        Counter.builder(TOOL_DENIED_COUNTER).tag("tool", toolName == null ? "unknown" : toolName).register(meterRegistry).increment();
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

    public void stopExecutionTimer(Timer.Sample sample) {
        sample.stop(Timer.builder(EXECUTION_LATENCY_TIMER).publishPercentileHistogram().register(meterRegistry));
    }

    public void stopToolTimer(Timer.Sample sample, String toolName) {
        sample.stop(Timer.builder(TOOL_LATENCY_TIMER).tag("tool", toolName).register(meterRegistry));
    }
}
