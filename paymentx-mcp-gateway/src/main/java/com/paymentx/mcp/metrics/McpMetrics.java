package com.paymentx.mcp.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * English:
 * Hand-registered Micrometer meters for MCP Gateway - follows every
 * other AI Platform service's explicit-MeterRegistry-call pattern (not
 * @Timed/@Counted - see PromptMetrics'/RagMetrics' javadoc for why).
 * Step 30's exact metric list. Every counter that varies by tool/error
 * carries a `tool`/`errorCode` tag rather than a separate meter per
 * tool, matching Micrometer's own tagging convention (and RagMetrics'
 * recordFailure(errorCode) precedent) - never records tool call
 * arguments, payment references, or any retrieved business data, only
 * counts/durations/tags.
 * Why it exists: Step 30's explicit metric list - mcp_requests_total,
 * mcp_tool_calls_total, mcp_tool_success_total, mcp_tool_failure_total,
 * mcp_tool_denied_total, mcp_tool_latency, mcp_tool_timeout_total,
 * mcp_tool_rate_limited_total, mcp_authorization_failures,
 * mcp_validation_failures.
 * How it communicates with other components: injected into ToolInvoker;
 * scraped by the same Prometheus instance every other PaymentX service
 * reports to (infra/prometheus.yml, port 8097).
 *
 * Hinglish:
 * MCP Gateway ke liye hand-registered Micrometer meters - har doosri AI
 * Platform service ke explicit-MeterRegistry-call pattern ko follow
 * karte hain (@Timed/@Counted nahi - PromptMetrics/RagMetrics ka
 * javadoc dekho ki kyun). Step 30 ki exact metric list. Har counter jo
 * tool/error ke hisaab se vary karta hai ek `tool`/`errorCode` tag
 * carry karta hai, har tool ke liye ek alag meter ke bajaye, Micrometer
 * ke apne tagging convention se match karte hue (aur RagMetrics ke
 * recordFailure(errorCode) precedent se) - kabhi tool call arguments,
 * payment references, ya koi retrieved business data record nahi karta,
 * sirf counts/durations/tags.
 * Ye kyu hai: Step 30 ki explicit metric list - mcp_requests_total,
 * mcp_tool_calls_total, mcp_tool_success_total, mcp_tool_failure_total,
 * mcp_tool_denied_total, mcp_tool_latency, mcp_tool_timeout_total,
 * mcp_tool_rate_limited_total, mcp_authorization_failures,
 * mcp_validation_failures.
 * Dusre components se kaise communicate karta hai: ToolInvoker me
 * inject hota hai; usi Prometheus instance dwara scrape hota hai jise
 * har doosri PaymentX service report karti hai (infra/prometheus.yml,
 * port 8097).
 */
@Component
public class McpMetrics {

    private static final String REQUESTS_COUNTER = "mcp_requests_total";
    private static final String TOOL_CALLS_COUNTER = "mcp_tool_calls_total";
    private static final String TOOL_SUCCESS_COUNTER = "mcp_tool_success_total";
    private static final String TOOL_FAILURE_COUNTER = "mcp_tool_failure_total";
    private static final String TOOL_DENIED_COUNTER = "mcp_tool_denied_total";
    private static final String TOOL_LATENCY_TIMER = "mcp_tool_latency";
    private static final String TOOL_TIMEOUT_COUNTER = "mcp_tool_timeout_total";
    private static final String TOOL_RATE_LIMITED_COUNTER = "mcp_tool_rate_limited_total";
    private static final String AUTHORIZATION_FAILURES_COUNTER = "mcp_authorization_failures";
    private static final String VALIDATION_FAILURES_COUNTER = "mcp_validation_failures";

    private final MeterRegistry meterRegistry;

    public McpMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordRequest() {
        Counter.builder(REQUESTS_COUNTER).register(meterRegistry).increment();
    }

    public void recordToolCall(String toolName) {
        Counter.builder(TOOL_CALLS_COUNTER).tag("tool", toolName).register(meterRegistry).increment();
    }

    public void recordSuccess(String toolName) {
        Counter.builder(TOOL_SUCCESS_COUNTER).tag("tool", toolName).register(meterRegistry).increment();
    }

    public void recordFailure(String toolName, String errorCode) {
        Counter.builder(TOOL_FAILURE_COUNTER).tag("tool", toolName).tag("errorCode", errorCode).register(meterRegistry).increment();
    }

    public void recordDenied(String toolName, String errorCode) {
        Counter.builder(TOOL_DENIED_COUNTER).tag("tool", toolName).tag("errorCode", errorCode).register(meterRegistry).increment();
    }

    public void recordTimeout(String toolName) {
        Counter.builder(TOOL_TIMEOUT_COUNTER).tag("tool", toolName).register(meterRegistry).increment();
    }

    public void recordRateLimited(String toolName) {
        Counter.builder(TOOL_RATE_LIMITED_COUNTER).tag("tool", toolName).register(meterRegistry).increment();
    }

    public void recordAuthorizationFailure(String toolName) {
        Counter.builder(AUTHORIZATION_FAILURES_COUNTER).tag("tool", toolName).register(meterRegistry).increment();
    }

    public void recordValidationFailure(String toolName) {
        Counter.builder(VALIDATION_FAILURES_COUNTER).tag("tool", toolName).register(meterRegistry).increment();
    }

    public Timer.Sample startTimer() {
        return Timer.start(meterRegistry);
    }

    public void stopTimer(Timer.Sample sample, String toolName) {
        sample.stop(Timer.builder(TOOL_LATENCY_TIMER).tag("tool", toolName).publishPercentileHistogram().register(meterRegistry));
    }
}
