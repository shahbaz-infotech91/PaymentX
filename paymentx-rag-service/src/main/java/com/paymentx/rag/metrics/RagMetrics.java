package com.paymentx.rag.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * English:
 * Hand-registered Micrometer meters for RAG Service - follows every
 * other AI Platform service's explicit-MeterRegistry-call pattern (not
 * @Timed/@Counted - see PromptMetrics' javadoc for why). Step 26's
 * exact metric list, plus the four per-dependency latency timers
 * distinguished by name (embedding/vector-search/prompt-render/LLM) so
 * an operator can see WHICH of the four downstream calls in a slow
 * request was actually slow, not just the total. Never records query
 * text, retrieved chunk content, or the answer itself - only counts,
 * scores, and durations.
 * Why it exists: Step 26's explicit metric list - 11 distinct meters:
 * rag_requests_total, rag_success_total, rag_failure_total,
 * rag_insufficient_context_total, rag_embedding_latency,
 * rag_vector_search_latency, rag_prompt_render_latency, rag_llm_latency,
 * rag_total_latency, rag_context_chunks, rag_context_size,
 * rag_relevance_threshold_rejections.
 * How it communicates with other components: injected into
 * RagServiceImpl; scraped by the same Prometheus instance every other
 * PaymentX service reports to (infra/prometheus.yml, port 8096).
 *
 * Hinglish:
 * RAG Service ke liye hand-registered Micrometer meters - har doosri AI
 * Platform service ke explicit-MeterRegistry-call pattern ko follow
 * karte hain (@Timed/@Counted nahi - PromptMetrics ka javadoc dekho ki
 * kyun). Step 26 ki exact metric list, plus char per-dependency latency
 * timers naam se distinguish kiye gaye (embedding/vector-search/
 * prompt-render/LLM) taaki ek operator dekh sake ki ek slow request me
 * char downstream calls me se KAUN sa actually slow tha, sirf total
 * nahi. Kabhi query text, retrieved chunk content, ya khud answer
 * record nahi karta - sirf counts, scores, aur durations.
 * Ye kyu hai: Step 26 ki explicit metric list - 11 distinct meters:
 * rag_requests_total, rag_success_total, rag_failure_total,
 * rag_insufficient_context_total, rag_embedding_latency,
 * rag_vector_search_latency, rag_prompt_render_latency, rag_llm_latency,
 * rag_total_latency, rag_context_chunks, rag_context_size,
 * rag_relevance_threshold_rejections.
 * Dusre components se kaise communicate karta hai: RagServiceImpl me
 * inject hota hai; usi Prometheus instance dwara scrape hota hai jise
 * har doosri PaymentX service report karti hai (infra/prometheus.yml,
 * port 8096).
 */
@Component
public class RagMetrics {

    private static final String REQUESTS_COUNTER = "rag_requests_total";
    private static final String SUCCESS_COUNTER = "rag_success_total";
    private static final String FAILURE_COUNTER = "rag_failure_total";
    private static final String INSUFFICIENT_CONTEXT_COUNTER = "rag_insufficient_context_total";
    private static final String EMBEDDING_LATENCY_TIMER = "rag_embedding_latency";
    private static final String VECTOR_SEARCH_LATENCY_TIMER = "rag_vector_search_latency";
    private static final String PROMPT_RENDER_LATENCY_TIMER = "rag_prompt_render_latency";
    private static final String LLM_LATENCY_TIMER = "rag_llm_latency";
    private static final String TOTAL_LATENCY_TIMER = "rag_total_latency";
    private static final String CONTEXT_CHUNKS_SUMMARY = "rag_context_chunks";
    private static final String CONTEXT_SIZE_SUMMARY = "rag_context_size";
    private static final String THRESHOLD_REJECTIONS_COUNTER = "rag_relevance_threshold_rejections";

    private final MeterRegistry meterRegistry;

    public RagMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordRequest() {
        Counter.builder(REQUESTS_COUNTER).register(meterRegistry).increment();
    }

    public void recordSuccess() {
        Counter.builder(SUCCESS_COUNTER).register(meterRegistry).increment();
    }

    public void recordFailure(String errorCode) {
        Counter.builder(FAILURE_COUNTER).tag("errorCode", errorCode).register(meterRegistry).increment();
    }

    public void recordInsufficientContext() {
        Counter.builder(INSUFFICIENT_CONTEXT_COUNTER).register(meterRegistry).increment();
    }

    public void recordThresholdRejections(int rejectedCount) {
        Counter.builder(THRESHOLD_REJECTIONS_COUNTER).register(meterRegistry).increment(rejectedCount);
    }

    public void recordContext(int chunkCount, int characterCount) {
        DistributionSummary.builder(CONTEXT_CHUNKS_SUMMARY).register(meterRegistry).record(chunkCount);
        DistributionSummary.builder(CONTEXT_SIZE_SUMMARY).register(meterRegistry).record(characterCount);
    }

    public Timer.Sample startTimer() {
        return Timer.start(meterRegistry);
    }

    public void stopEmbeddingTimer(Timer.Sample sample) {
        sample.stop(Timer.builder(EMBEDDING_LATENCY_TIMER).register(meterRegistry));
    }

    public void stopVectorSearchTimer(Timer.Sample sample) {
        sample.stop(Timer.builder(VECTOR_SEARCH_LATENCY_TIMER).register(meterRegistry));
    }

    public void stopPromptRenderTimer(Timer.Sample sample) {
        sample.stop(Timer.builder(PROMPT_RENDER_LATENCY_TIMER).register(meterRegistry));
    }

    public void stopLlmTimer(Timer.Sample sample) {
        sample.stop(Timer.builder(LLM_LATENCY_TIMER).register(meterRegistry));
    }

    public void stopTotalTimer(Timer.Sample sample) {
        sample.stop(Timer.builder(TOTAL_LATENCY_TIMER).publishPercentileHistogram().register(meterRegistry));
    }
}
