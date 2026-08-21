package com.paymentx.vector.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * English:
 * Hand-registered Micrometer meters for Vector Service - follows LLM/
 * Embedding/Prompt Service's exact explicit-MeterRegistry-call pattern
 * (not @Timed/@Counted - see PromptMetrics' javadoc for why). No new
 * observability stack introduced. Never records vector values or chunk
 * content (Step 30 - "Do not log vectors").
 * Why it exists: Step 30's explicit metric list - 10 distinct meters:
 * vector_store_requests_total, vector_store_success_total,
 * vector_store_failure_total, vector_search_total,
 * vector_search_latency, vector_insert_latency, vector_update_total,
 * vector_delete_total, vector_search_results, vector_dimension_errors.
 * How it communicates with other components: injected into
 * VectorStoreServiceImpl; scraped by the same Prometheus instance every
 * other PaymentX service reports to (infra/prometheus.yml, port 8095).
 *
 * Hinglish:
 * Vector Service ke liye hand-registered Micrometer meters - LLM/
 * Embedding/Prompt Service ke exact explicit-MeterRegistry-call pattern
 * ko follow karte hain (@Timed/@Counted nahi - PromptMetrics ka javadoc
 * dekho ki kyun). Koi naya observability stack introduce nahi kiya
 * gaya. Kabhi vector values ya chunk content record nahi karta (Step 30
 * - "vectors log mat karo").
 * Ye kyu hai: Step 30 ki explicit metric list - 10 distinct meters:
 * vector_store_requests_total, vector_store_success_total,
 * vector_store_failure_total, vector_search_total,
 * vector_search_latency, vector_insert_latency, vector_update_total,
 * vector_delete_total, vector_search_results, vector_dimension_errors.
 * Dusre components se kaise communicate karta hai: VectorStoreServiceImpl
 * me inject hota hai; usi Prometheus instance dwara scrape hota hai jise
 * har doosri PaymentX service report karti hai (infra/prometheus.yml,
 * port 8095).
 */
@Component
public class VectorMetrics {

    private static final String STORE_REQUESTS_COUNTER = "vector_store_requests_total";
    private static final String STORE_SUCCESS_COUNTER = "vector_store_success_total";
    private static final String STORE_FAILURE_COUNTER = "vector_store_failure_total";
    private static final String SEARCH_COUNTER = "vector_search_total";
    private static final String SEARCH_LATENCY_TIMER = "vector_search_latency";
    private static final String INSERT_LATENCY_TIMER = "vector_insert_latency";
    private static final String UPDATE_COUNTER = "vector_update_total";
    private static final String DELETE_COUNTER = "vector_delete_total";
    private static final String SEARCH_RESULTS_SUMMARY = "vector_search_results";
    private static final String DIMENSION_ERRORS_COUNTER = "vector_dimension_errors";

    private final MeterRegistry meterRegistry;

    public VectorMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordStoreRequest() {
        Counter.builder(STORE_REQUESTS_COUNTER).register(meterRegistry).increment();
    }

    public void recordStoreSuccess(boolean created) {
        Counter.builder(STORE_SUCCESS_COUNTER).tag("outcome", created ? "created" : "updated").register(meterRegistry).increment();
        Counter.builder(created ? "vector_insert_total" : UPDATE_COUNTER).register(meterRegistry).increment();
    }

    public void recordStoreFailure(String errorCode) {
        Counter.builder(STORE_FAILURE_COUNTER).tag("errorCode", errorCode).register(meterRegistry).increment();
        if ("VECTOR_DIMENSION_MISMATCH".equals(errorCode) || "VECTOR_INVALID_VALUE".equals(errorCode)) {
            Counter.builder(DIMENSION_ERRORS_COUNTER).register(meterRegistry).increment();
        }
    }

    public void recordDelete() {
        Counter.builder(DELETE_COUNTER).register(meterRegistry).increment();
    }

    public void recordSearch(int resultCount) {
        Counter.builder(SEARCH_COUNTER).register(meterRegistry).increment();
        DistributionSummary.builder(SEARCH_RESULTS_SUMMARY).register(meterRegistry).record(resultCount);
    }

    public Timer.Sample startSearchTimer() {
        return Timer.start(meterRegistry);
    }

    public void stopSearchTimer(Timer.Sample sample) {
        sample.stop(Timer.builder(SEARCH_LATENCY_TIMER).publishPercentileHistogram().register(meterRegistry));
    }

    public Timer.Sample startInsertTimer() {
        return Timer.start(meterRegistry);
    }

    public void stopInsertTimer(Timer.Sample sample) {
        sample.stop(Timer.builder(INSERT_LATENCY_TIMER).publishPercentileHistogram().register(meterRegistry));
    }
}
