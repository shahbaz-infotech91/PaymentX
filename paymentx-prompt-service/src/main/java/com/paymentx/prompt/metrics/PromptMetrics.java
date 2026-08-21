package com.paymentx.prompt.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * English:
 * Hand-registered Micrometer meters for Prompt Service - the exact
 * metric names Step 17 of the Phase 3.2 brief lists
 * (prompt_requests_total, prompt_render_success_total,
 * prompt_render_failure_total, prompt_render_latency,
 * active_prompt_lookup_latency, validation_failure_count), following
 * Routing Service's RoutingMetrics pattern (explicit MeterRegistry
 * calls, not @Timed/@Counted annotations - see that class's javadoc for
 * why: annotation-based metrics only capture whole-method timing and
 * can't distinguish success/failure or cache-style outcomes the way an
 * explicit call site can). No new observability stack is introduced -
 * this is the exact same io.micrometer.registry-prometheus dependency
 * (already on this module's pom.xml) every other PaymentX service
 * exposes via /actuator/prometheus.
 * Why it exists: Step 17's explicit metric list, and "preserve
 * correlation ID, trace ID, structured logging, existing health
 * conventions" - this class is the metrics half of that requirement.
 * How it communicates with other components: injected into
 * PromptServiceImpl and PromptController; scraped by the same
 * Prometheus instance every other PaymentX service already reports to
 * (see infra/prometheus.yml, a new scrape target added for this
 * service's real port).
 *
 * Hinglish:
 * Prompt Service ke liye hand-registered Micrometer meters - Phase 3.2
 * brief ke Step 17 me listed exact metric names
 * (prompt_requests_total, prompt_render_success_total,
 * prompt_render_failure_total, prompt_render_latency,
 * active_prompt_lookup_latency, validation_failure_count), Routing
 * Service ke RoutingMetrics pattern ko follow karte hue (explicit
 * MeterRegistry calls, @Timed/@Counted annotations nahi - us class ka
 * javadoc dekho ki kyun: annotation-based metrics sirf whole-method
 * timing capture karte hain aur success/failure ya cache-style outcomes
 * distinguish nahi kar sakte jaise ek explicit call site kar sakti
 * hai). Koi naya observability stack introduce nahi kiya gaya - ye
 * exactly wahi io.micrometer.registry-prometheus dependency hai (is
 * module ke pom.xml me already hai) jo har doosri PaymentX service
 * /actuator/prometheus ke through expose karti hai.
 * Ye kyu hai: Step 17 ki explicit metric list, aur "correlation ID,
 * trace ID, structured logging, existing health conventions preserve
 * karo" - ye class us requirement ka metrics half hai.
 * Dusre components se kaise communicate karta hai: PromptServiceImpl
 * aur PromptController me inject hota hai; usi Prometheus instance
 * dwara scrape hota hai jise har doosri PaymentX service already report
 * karti hai (infra/prometheus.yml dekho, is service ke real port ke
 * liye ek naya scrape target add kiya gaya).
 */
@Component
public class PromptMetrics {

    private static final String REQUESTS_COUNTER = "prompt_requests_total";
    private static final String RENDER_SUCCESS_COUNTER = "prompt_render_success_total";
    private static final String RENDER_FAILURE_COUNTER = "prompt_render_failure_total";
    private static final String RENDER_LATENCY_TIMER = "prompt_render_latency";
    private static final String ACTIVE_LOOKUP_LATENCY_TIMER = "active_prompt_lookup_latency";
    private static final String VALIDATION_FAILURE_COUNTER = "validation_failure_count";

    private final MeterRegistry meterRegistry;

    public PromptMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordRequest(String operation) {
        Counter.builder(REQUESTS_COUNTER).tag("operation", operation).register(meterRegistry).increment();
    }

    public void recordRenderSuccess(String promptKey) {
        Counter.builder(RENDER_SUCCESS_COUNTER).tag("promptKey", promptKey).register(meterRegistry).increment();
    }

    public void recordRenderFailure(String promptKey, String errorCode) {
        Counter.builder(RENDER_FAILURE_COUNTER).tag("promptKey", promptKey).tag("errorCode", errorCode).register(meterRegistry).increment();
    }

    public void recordValidationFailure(String errorCode) {
        Counter.builder(VALIDATION_FAILURE_COUNTER).tag("errorCode", errorCode).register(meterRegistry).increment();
    }

    public Timer.Sample startRenderTimer() {
        return Timer.start(meterRegistry);
    }

    public void stopRenderTimer(Timer.Sample sample, String promptKey) {
        sample.stop(Timer.builder(RENDER_LATENCY_TIMER).tag("promptKey", promptKey).publishPercentileHistogram().register(meterRegistry));
    }

    public Timer.Sample startActiveLookupTimer() {
        return Timer.start(meterRegistry);
    }

    public void stopActiveLookupTimer(Timer.Sample sample, String promptKey) {
        sample.stop(Timer.builder(ACTIVE_LOOKUP_LATENCY_TIMER).tag("promptKey", promptKey).publishPercentileHistogram().register(meterRegistry));
    }
}
