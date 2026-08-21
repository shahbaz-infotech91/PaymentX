package com.paymentx.embedding.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * English:
 * Hand-registered Micrometer meters for Embedding Service - follows LLM
 * Service's LlmMetrics/Prompt Service's PromptMetrics exact pattern
 * (explicit MeterRegistry calls, not @Timed/@Counted - see PromptMetrics'
 * javadoc for why). No new observability stack introduced. Resilience4j's
 * own circuit-breaker/retry state (embeddingProvider instance) is
 * already auto-exported separately - not duplicated here.
 * `embedding_input_size`/`embedding_dimension` are DistributionSummary
 * (not Counter) since they are magnitudes per call, not event counts -
 * recording character length only, never the text content itself (Step
 * 20 - "do not expose sensitive text in metrics").
 * Why it exists: Step 20's explicit metric list - 9 distinct meters:
 * embedding_requests_total, embedding_success_total,
 * embedding_failure_total, embedding_timeout_total, embedding_latency,
 * embedding_input_size, embedding_dimension,
 * batch_embedding_requests_total, provider_errors.
 * How it communicates with other components: injected into
 * EmbeddingServiceImpl; scraped by the same Prometheus instance every
 * other PaymentX service reports to (infra/prometheus.yml, port 8094).
 *
 * Hinglish:
 * Embedding Service ke liye hand-registered Micrometer meters - LLM
 * Service ke LlmMetrics/Prompt Service ke PromptMetrics ke exact
 * pattern ko follow karte hain (explicit MeterRegistry calls,
 * @Timed/@Counted nahi - PromptMetrics ka javadoc dekho ki kyun). Koi
 * naya observability stack introduce nahi kiya gaya. Resilience4j ka
 * apna circuit-breaker/retry state (embeddingProvider instance) already
 * alag se auto-export hota hai - yahan duplicate nahi kiya gaya.
 * `embedding_input_size`/`embedding_dimension` DistributionSummary hain
 * (Counter nahi) kyunki wo har call ke magnitudes hain, event counts
 * nahi - sirf character length record hoti hai, kabhi text content khud
 * nahi (Step 20 - "metrics me sensitive text expose mat karo").
 * Ye kyu hai: Step 20 ki explicit metric list - 9 distinct meters:
 * embedding_requests_total, embedding_success_total,
 * embedding_failure_total, embedding_timeout_total, embedding_latency,
 * embedding_input_size, embedding_dimension,
 * batch_embedding_requests_total, provider_errors.
 * Dusre components se kaise communicate karta hai: EmbeddingServiceImpl
 * me inject hota hai; usi Prometheus instance dwara scrape hota hai jise
 * har doosri PaymentX service report karti hai (infra/prometheus.yml,
 * port 8094).
 */
@Component
public class EmbeddingMetrics {

    private static final String REQUESTS_COUNTER = "embedding_requests_total";
    private static final String SUCCESS_COUNTER = "embedding_success_total";
    private static final String FAILURE_COUNTER = "embedding_failure_total";
    private static final String TIMEOUT_COUNTER = "embedding_timeout_total";
    private static final String LATENCY_TIMER = "embedding_latency";
    private static final String INPUT_SIZE_SUMMARY = "embedding_input_size";
    private static final String DIMENSION_SUMMARY = "embedding_dimension";
    private static final String BATCH_REQUESTS_COUNTER = "batch_embedding_requests_total";
    private static final String PROVIDER_ERRORS_COUNTER = "provider_errors";

    private final MeterRegistry meterRegistry;

    public EmbeddingMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordRequest(String provider) {
        Counter.builder(REQUESTS_COUNTER).tag("provider", provider).register(meterRegistry).increment();
    }

    public void recordBatchRequest(String provider, int itemCount) {
        Counter.builder(BATCH_REQUESTS_COUNTER).tag("provider", provider).register(meterRegistry).increment();
        DistributionSummary.builder("batch_embedding_size").tag("provider", provider).register(meterRegistry).record(itemCount);
    }

    public void recordSuccess(String provider, String model) {
        Counter.builder(SUCCESS_COUNTER).tag("provider", provider).tag("model", model).register(meterRegistry).increment();
    }

    public void recordFailure(String provider, String errorCode) {
        Counter.builder(FAILURE_COUNTER).tag("provider", provider).tag("errorCode", errorCode).register(meterRegistry).increment();
    }

    public void recordTimeout(String provider) {
        Counter.builder(TIMEOUT_COUNTER).tag("provider", provider).register(meterRegistry).increment();
    }

    public void recordProviderError(String provider, String errorCode) {
        Counter.builder(PROVIDER_ERRORS_COUNTER).tag("provider", provider).tag("errorCode", errorCode).register(meterRegistry).increment();
    }

    public void recordInputSize(String provider, int characterLength) {
        DistributionSummary.builder(INPUT_SIZE_SUMMARY).tag("provider", provider).register(meterRegistry).record(characterLength);
    }

    public void recordDimension(String provider, String model, int dimension) {
        DistributionSummary.builder(DIMENSION_SUMMARY).tag("provider", provider).tag("model", model).register(meterRegistry).record(dimension);
    }

    public Timer.Sample startLatencyTimer() {
        return Timer.start(meterRegistry);
    }

    public void stopLatencyTimer(Timer.Sample sample, String provider) {
        sample.stop(Timer.builder(LATENCY_TIMER).tag("provider", provider).publishPercentileHistogram().register(meterRegistry));
    }
}
