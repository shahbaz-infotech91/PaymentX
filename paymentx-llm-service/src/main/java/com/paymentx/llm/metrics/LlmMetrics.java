package com.paymentx.llm.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * English:
 * Hand-registered Micrometer meters for LLM Service - follows Prompt
 * Service's PromptMetrics/Routing Service's RoutingMetrics pattern
 * exactly (explicit MeterRegistry calls, not @Timed/@Counted - see
 * PromptMetrics' javadoc for why: annotation-based metrics can't
 * distinguish success/failure/refusal the way an explicit call site
 * can). No new observability stack introduced - the same
 * io.micrometer.registry-prometheus dependency every other PaymentX
 * service already exposes via /actuator/prometheus. Resilience4j's own
 * circuit-breaker/retry state (llmProvider instance) is already
 * auto-exported by resilience4j-spring-boot3 as
 * resilience4j_circuitbreaker_state/resilience4j_retry_calls - this
 * class does NOT duplicate those, it only covers what Resilience4j
 * cannot: real token usage and business-level success/failure/refusal
 * counts.
 * Why it exists: Step 15's "track usage and latency" and Step 24's
 * "expose /actuator/prometheus metrics" requirements - 10 distinct
 * meters: llm_requests_total, llm_generate_success_total,
 * llm_generate_failure_total, llm_generate_refused_total,
 * llm_generate_latency, llm_provider_error_total,
 * llm_input_tokens_total, llm_output_tokens_total,
 * llm_not_configured_total, llm_health_check_total.
 * How it communicates with other components: injected into
 * LlmServiceImpl; scraped by the same Prometheus instance every other
 * PaymentX service reports to (see infra/prometheus.yml's new
 * paymentx-llm-service target, port 8093).
 *
 * Hinglish:
 * LLM Service ke liye hand-registered Micrometer meters - Prompt
 * Service ke PromptMetrics/Routing Service ke RoutingMetrics pattern se
 * exactly match karta hai (explicit MeterRegistry calls, @Timed/
 * @Counted nahi - PromptMetrics ka javadoc dekho ki kyun: annotation-
 * based metrics success/failure/refusal distinguish nahi kar sakte
 * jaise ek explicit call site kar sakti hai). Koi naya observability
 * stack introduce nahi kiya gaya - wahi io.micrometer.registry-
 * prometheus dependency jo har doosri PaymentX service already
 * /actuator/prometheus ke through expose karti hai. Resilience4j ka
 * apna circuit-breaker/retry state (llmProvider instance) already
 * resilience4j-spring-boot3 dwara auto-export hota hai
 * resilience4j_circuitbreaker_state/resilience4j_retry_calls ke roop
 * me - ye class unhe duplicate NAHI karti, sirf wo cover karti hai jo
 * Resilience4j nahi kar sakta: real token usage aur business-level
 * success/failure/refusal counts.
 * Ye kyu hai: Step 15 ki "usage aur latency track karo" aur Step 24 ki
 * "/actuator/prometheus metrics expose karo" requirements - 10 distinct
 * meters: llm_requests_total, llm_generate_success_total,
 * llm_generate_failure_total, llm_generate_refused_total,
 * llm_generate_latency, llm_provider_error_total,
 * llm_input_tokens_total, llm_output_tokens_total,
 * llm_not_configured_total, llm_health_check_total.
 * Dusre components se kaise communicate karta hai: LlmServiceImpl me
 * inject hota hai; usi Prometheus instance dwara scrape hota hai jise
 * har doosri PaymentX service report karti hai (infra/prometheus.yml ka
 * naya paymentx-llm-service target, port 8093, dekho).
 */
@Component
public class LlmMetrics {

    private static final String REQUESTS_COUNTER = "llm_requests_total";
    private static final String SUCCESS_COUNTER = "llm_generate_success_total";
    private static final String FAILURE_COUNTER = "llm_generate_failure_total";
    private static final String REFUSED_COUNTER = "llm_generate_refused_total";
    private static final String LATENCY_TIMER = "llm_generate_latency";
    private static final String PROVIDER_ERROR_COUNTER = "llm_provider_error_total";
    private static final String INPUT_TOKENS_COUNTER = "llm_input_tokens_total";
    private static final String OUTPUT_TOKENS_COUNTER = "llm_output_tokens_total";
    private static final String NOT_CONFIGURED_COUNTER = "llm_not_configured_total";
    private static final String HEALTH_CHECK_COUNTER = "llm_health_check_total";

    private final MeterRegistry meterRegistry;

    public LlmMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordRequest(String provider) {
        Counter.builder(REQUESTS_COUNTER).tag("provider", provider).register(meterRegistry).increment();
    }

    public void recordSuccess(String provider, String model) {
        Counter.builder(SUCCESS_COUNTER).tag("provider", provider).tag("model", model).register(meterRegistry).increment();
    }

    public void recordFailure(String provider, String errorCode) {
        Counter.builder(FAILURE_COUNTER).tag("provider", provider).tag("errorCode", errorCode).register(meterRegistry).increment();
    }

    public void recordRefused(String provider, String model) {
        Counter.builder(REFUSED_COUNTER).tag("provider", provider).tag("model", model).register(meterRegistry).increment();
    }

    public void recordProviderError(String provider, String errorCode) {
        Counter.builder(PROVIDER_ERROR_COUNTER).tag("provider", provider).tag("errorCode", errorCode).register(meterRegistry).increment();
    }

    public void recordTokens(String provider, String model, long inputTokens, long outputTokens) {
        Counter.builder(INPUT_TOKENS_COUNTER).tag("provider", provider).tag("model", model).register(meterRegistry).increment(inputTokens);
        Counter.builder(OUTPUT_TOKENS_COUNTER).tag("provider", provider).tag("model", model).register(meterRegistry).increment(outputTokens);
    }

    public void recordNotConfigured() {
        Counter.builder(NOT_CONFIGURED_COUNTER).register(meterRegistry).increment();
    }

    public void recordHealthCheck(String status) {
        Counter.builder(HEALTH_CHECK_COUNTER).tag("status", status).register(meterRegistry).increment();
    }

    public Timer.Sample startGenerateTimer() {
        return Timer.start(meterRegistry);
    }

    public void stopGenerateTimer(Timer.Sample sample, String provider) {
        sample.stop(Timer.builder(LATENCY_TIMER).tag("provider", provider).publishPercentileHistogram().register(meterRegistry));
    }
}
