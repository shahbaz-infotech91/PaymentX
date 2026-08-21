package com.paymentx.rag.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.paymentx.rag.audit.RagAuditClient;
import com.paymentx.rag.audit.RagAuditEvent;
import com.paymentx.rag.client.EmbeddingServiceClient;
import com.paymentx.rag.client.LlmServiceClient;
import com.paymentx.rag.client.PromptServiceClient;
import com.paymentx.rag.client.VectorServiceClient;
import com.paymentx.rag.config.CorrelationIdFilter;
import com.paymentx.rag.config.RagProperties;
import com.paymentx.rag.context.ContextBuilder;
import com.paymentx.rag.dto.RagHealthResponse;
import com.paymentx.rag.dto.RagQueryMetadata;
import com.paymentx.rag.dto.RagQueryRequest;
import com.paymentx.rag.dto.RagQueryResponse;
import com.paymentx.rag.dto.RagQueryStatus;
import com.paymentx.rag.dto.RagSource;
import com.paymentx.rag.dto.RetrievedChunk;
import com.paymentx.rag.exception.RagException;
import com.paymentx.rag.metrics.RagMetrics;
import com.paymentx.rag.service.RagService;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * English:
 * The real implementation of the Phase 3.6 target architecture (Step
 * 2): query -> embed -> vector search -> relevance threshold -> context
 * build -> prompt render -> LLM generate -> grounded answer. Every
 * downstream call goes through exactly one of the four dedicated
 * clients (EmbeddingServiceClient/VectorServiceClient/
 * PromptServiceClient/LlmServiceClient) - this class never touches
 * pgvector, an embedding provider, or an LLM provider directly (Step 1/
 * 9/19). THE CRITICAL PRINCIPLE, made structural: if the filtered
 * (post-threshold) result set is empty, this method returns
 * immediately with RagQueryStatus.INSUFFICIENT_CONTEXT - `promptServiceClient`/
 * `llmServiceClient` are simply never called on that path (see the
 * early return before ContextBuilder is even invoked), so there is no
 * code path where the LLM could paper over a genuine retrieval gap with
 * its own general knowledge (Step 22's "DO NOT call LLM" is enforced by
 * the method never reaching that call, not by a comment asking future
 * maintainers to remember not to).
 * WHY the relevance threshold is applied HERE, client-side, against the
 * raw top-K Vector Service returned (not delegated to Vector Service's
 * own `minScore` search parameter): this class needs to see and count
 * the rejected candidates for `rag_relevance_threshold_rejections` and
 * RagQueryMetadata.rejectedByThreshold - see VectorServiceClient's own
 * javadoc for the identical reasoning from the client side.
 * WHY `refused` (LLM Service's own field) maps to RagQueryStatus.REFUSED,
 * a THIRD state distinct from both SUCCESS and INSUFFICIENT_CONTEXT:
 * a refusal only happens after context WAS found and passed to the LLM
 * - conflating it with INSUFFICIENT_CONTEXT would misreport "the
 * knowledge base had nothing" when the real story is "the model
 * declined to use what it was given," a materially different failure
 * mode an operator needs to distinguish (see RagQueryStatus' own
 * javadoc).
 * Why it exists: PAYMENTX_PHASE_3_ARCHITECTURE.md-style RagService layer
 * - the piece RagController depends on instead of talking to the four
 * clients directly.
 * How it communicates with other components: implements RagService;
 * injected into RagController; calls EmbeddingServiceClient/
 * VectorServiceClient/PromptServiceClient/LlmServiceClient,
 * ContextBuilder, and RagMetrics.
 *
 * Hinglish:
 * Phase 3.6 target architecture (Step 2) ki real implementation: query
 * -> embed -> vector search -> relevance threshold -> context build ->
 * prompt render -> LLM generate -> grounded answer. Har downstream call
 * char dedicated clients me se exactly ek se guzarti hai
 * (EmbeddingServiceClient/VectorServiceClient/PromptServiceClient/
 * LlmServiceClient) - ye class kabhi seedhe pgvector, ek embedding
 * provider, ya ek LLM provider ko nahi chhuti (Step 1/9/19). CRITICAL
 * PRINCIPLE, structural banaya gaya: agar filtered (post-threshold)
 * result set khali hai, ye method turant RagQueryStatus.INSUFFICIENT_CONTEXT
 * ke saath return hota hai - us path par `promptServiceClient`/
 * `llmServiceClient` simply kabhi call hi nahi hote (ContextBuilder ke
 * invoke hone se pehle wala early return dekho), isliye koi code path
 * nahi hai jahan LLM apne general knowledge se ek genuine retrieval gap
 * ko paper over kar sake (Step 22 ka "LLM ko call MAT karo" method ke
 * us call tak kabhi na pahunchne se enforce hota hai, ek comment se
 * nahi jo future maintainers se yaad rakhne ko kahe).
 * Relevance threshold YAHAN, client-side, Vector Service ke return kiye
 * raw top-K ke against KYU apply hota hai (Vector Service ke apne
 * `minScore` search parameter ko delegate nahi kiya gaya): is class ko
 * rejected candidates dekhne aur count karne hote hain
 * `rag_relevance_threshold_rejections` aur
 * RagQueryMetadata.rejectedByThreshold ke liye - client side se identical
 * reasoning ke liye VectorServiceClient ka apna javadoc dekho.
 * `refused` (LLM Service ka apna field) RagQueryStatus.REFUSED par KYU
 * map hota hai, SUCCESS aur INSUFFICIENT_CONTEXT dono se alag ek TEESRA
 * state: ek refusal sirf tab hoti hai jab context MILA THA aur LLM ko
 * pass kiya gaya - ise INSUFFICIENT_CONTEXT se conflate karna "knowledge
 * base ke paas kuch nahi tha" misreport karega jab real story hai
 * "model ne jo diya gaya wo use karne se mana kar diya," ek materially
 * different failure mode jise ek operator distinguish kar sakna chahiye
 * (RagQueryStatus ka apna javadoc dekho).
 * Ye kyu hai: PAYMENTX_PHASE_3_ARCHITECTURE.md-style RagService layer -
 * wo piece jispar RagController depend karta hai seedhe char clients se
 * baat karne ke bajaye.
 * Dusre components se kaise communicate karta hai: RagService implement
 * karta hai; RagController me inject hota hai; EmbeddingServiceClient/
 * VectorServiceClient/PromptServiceClient/LlmServiceClient,
 * ContextBuilder, aur RagMetrics ko call karta hai.
 */
@Service
@Slf4j
public class RagServiceImpl implements RagService {

    private static final String INSUFFICIENT_CONTEXT_ANSWER =
            "I couldn't find enough relevant information in the PaymentX knowledge base to answer this reliably.";
    private static final String REFUSED_ANSWER = "The AI assistant declined to answer this question.";

    private final EmbeddingServiceClient embeddingServiceClient;
    private final VectorServiceClient vectorServiceClient;
    private final PromptServiceClient promptServiceClient;
    private final LlmServiceClient llmServiceClient;
    private final RagAuditClient auditClient;
    private final ContextBuilder contextBuilder;
    private final RagProperties properties;
    private final RagMetrics metrics;
    private final RestTemplate healthRestTemplate;

    public RagServiceImpl(EmbeddingServiceClient embeddingServiceClient,
                           VectorServiceClient vectorServiceClient,
                           PromptServiceClient promptServiceClient,
                           LlmServiceClient llmServiceClient,
                           RagAuditClient auditClient,
                           ContextBuilder contextBuilder,
                           RagProperties properties,
                           RagMetrics metrics,
                           RestTemplateBuilder restTemplateBuilder) {
        this.embeddingServiceClient = embeddingServiceClient;
        this.vectorServiceClient = vectorServiceClient;
        this.promptServiceClient = promptServiceClient;
        this.llmServiceClient = llmServiceClient;
        this.auditClient = auditClient;
        this.contextBuilder = contextBuilder;
        this.properties = properties;
        this.metrics = metrics;
        // A short, dedicated, low-timeout RestTemplate for health() only - deliberately separate from
        // the four downstream clients' own timeout-tuned RestTemplates (a slow health probe should never
        // block as long as a real query is allowed to).
        this.healthRestTemplate = restTemplateBuilder
                .requestFactory(SimpleClientHttpRequestFactory::new)
                .connectTimeout(Duration.ofSeconds(2))
                .readTimeout(Duration.ofSeconds(2))
                .build();
    }

    @Override
    public RagQueryResponse query(RagQueryRequest request) {
        metrics.recordRequest();
        long startTime = System.currentTimeMillis();
        Timer.Sample totalTimer = metrics.startTimer();
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        // Phase 3.10.2 - a fresh per-query identifier for the audit trail, distinct from correlationId,
        // the same "one UUID per operation" convention AgentOrchestratorService already establishes
        // elsewhere in this platform (RAG Service had no per-query identifier of its own before this).
        String requestId = java.util.UUID.randomUUID().toString();
        boolean retrievalAuditEmitted = false;

        try {
            String query = validateQuery(request.query());
            int topK = resolveTopK(request.topK());
            Map<String, Object> filters = validateFilters(request.filters());

            Timer.Sample embedTimer = metrics.startTimer();
            List<Float> queryEmbedding = embeddingServiceClient.embed(
                    properties.getEmbeddingServiceUrl(), query, properties.getEmbeddingModel(), correlationId);
            metrics.stopEmbeddingTimer(embedTimer);

            Timer.Sample searchTimer = metrics.startTimer();
            List<RetrievedChunk> retrieved = vectorServiceClient.search(
                    properties.getVectorServiceUrl(), queryEmbedding, properties.getEmbeddingProvider(),
                    properties.getEmbeddingModel(), topK, filters, correlationId);
            metrics.stopVectorSearchTimer(searchTimer);

            List<RetrievedChunk> relevant = retrieved.stream().filter(chunk -> chunk.score() >= properties.getMinScore()).toList();
            int rejectedByThreshold = retrieved.size() - relevant.size();
            if (rejectedByThreshold > 0) {
                metrics.recordThresholdRejections(rejectedByThreshold);
            }

            // Phase 3.10.2 RAG audit layer - emitted here, immediately after the retrieval step
            // completes and BEFORE context construction/prompt render/LLM generation, so this event
            // represents the RETRIEVAL operation only (never the LLM conversation - see RagAuditEvent's
            // javadoc). latencyMs here is deliberately just validation+embedding+vector-search elapsed
            // time, not the full request (that is RagQueryMetadata.totalLatencyMs, computed separately
            // below). Fires exactly once per query() call regardless of what happens afterward - a later
            // LLM/prompt failure is never retroactively reported as a retrieval failure (see the
            // retrievalAuditEmitted guard in the catch block below).
            long retrievalElapsedMs = System.currentTimeMillis() - startTime;
            auditClient.recordRetrieval(new RagAuditEvent(correlationId, requestId, retrieved.size(),
                    retrieved.stream().map(RetrievedChunk::chunkId).toList(),
                    retrieved.stream().map(RetrievedChunk::score).toList(),
                    retrievalElapsedMs, relevant.isEmpty() ? RagAuditClient.STATUS_NO_RESULTS : RagAuditClient.STATUS_SUCCESS));
            retrievalAuditEmitted = true;

            if (relevant.isEmpty()) {
                metrics.recordInsufficientContext();
                metrics.stopTotalTimer(totalTimer);
                long elapsed = System.currentTimeMillis() - startTime;
                log.info("RAG query insufficient context retrievedChunks={} rejectedByThreshold={} latencyMs={}",
                        retrieved.size(), rejectedByThreshold, elapsed);
                return new RagQueryResponse(INSUFFICIENT_CONTEXT_ANSWER, RagQueryStatus.INSUFFICIENT_CONTEXT, List.of(),
                        new RagQueryMetadata(retrieved.size(), 0, rejectedByThreshold, elapsed));
            }

            ContextBuilder.BuiltContext builtContext = contextBuilder.build(relevant);
            metrics.recordContext(builtContext.includedChunks().size(), builtContext.contextText().length());

            Timer.Sample renderTimer = metrics.startTimer();
            String renderedPrompt = promptServiceClient.render(
                    properties.getPromptServiceUrl(), properties.getRagPromptKey(), builtContext.contextText(), query, correlationId);
            metrics.stopPromptRenderTimer(renderTimer);

            Timer.Sample llmTimer = metrics.startTimer();
            LlmServiceClient.LlmAnswer answer = llmServiceClient.generate(properties.getLlmServiceUrl(), renderedPrompt, correlationId);
            metrics.stopLlmTimer(llmTimer);

            List<RagSource> sources = builtContext.includedChunks().stream()
                    .map(chunk -> new RagSource(chunk.documentId(), chunk.chunkId(),
                            chunk.documentKey() != null ? chunk.documentKey() : chunk.documentId(), chunk.score()))
                    .toList();

            metrics.recordSuccess();
            metrics.stopTotalTimer(totalTimer);
            long elapsed = System.currentTimeMillis() - startTime;

            RagQueryStatus status = answer.refused() ? RagQueryStatus.REFUSED : RagQueryStatus.SUCCESS;
            String finalAnswer = answer.refused() ? REFUSED_ANSWER : answer.content();
            log.info("RAG query completed status={} retrievedChunks={} contextChunksUsed={} rejectedByThreshold={} latencyMs={}",
                    status, retrieved.size(), builtContext.includedChunks().size(), rejectedByThreshold, elapsed);

            return new RagQueryResponse(finalAnswer, status, answer.refused() ? List.of() : sources,
                    new RagQueryMetadata(retrieved.size(), builtContext.includedChunks().size(), rejectedByThreshold, elapsed));
        } catch (RagException ex) {
            metrics.stopTotalTimer(totalTimer);
            metrics.recordFailure(ex.getErrorCode());
            // Phase 3.10.2 - only report a retrieval FAILURE if the failure actually occurred before or
            // during retrieval itself (query validation, embedding, or vector search). A RagException
            // thrown AFTER a successful retrieval (prompt render / LLM generation) must never be
            // misreported as a retrieval failure - the retrieval-scoped audit event above already fired,
            // accurately, before that later failure occurred.
            if (!retrievalAuditEmitted) {
                long elapsed = System.currentTimeMillis() - startTime;
                auditClient.recordRetrieval(new RagAuditEvent(correlationId, requestId, 0, List.of(), List.of(),
                        elapsed, RagAuditClient.STATUS_FAILURE));
            }
            throw ex;
        }
    }

    @Override
    public RagHealthResponse health() {
        Map<String, String> dependencies = new LinkedHashMap<>();
        dependencies.put("embeddingService", probe(properties.getEmbeddingServiceUrl()));
        dependencies.put("vectorService", probe(properties.getVectorServiceUrl()));
        dependencies.put("promptService", probe(properties.getPromptServiceUrl()));
        dependencies.put("llmService", probe(properties.getLlmServiceUrl()));

        boolean allUp = dependencies.values().stream().allMatch("UP"::equals);
        return new RagHealthResponse(allUp ? "UP" : "DEGRADED", dependencies, OffsetDateTime.now());
    }

    private String probe(String baseUrl) {
        try {
            ResponseEntity<JsonNode> response = healthRestTemplate.getForEntity(baseUrl + "/actuator/health", JsonNode.class);
            String status = response.getBody() != null ? response.getBody().path("status").asText(null) : null;
            return "UP".equals(status) ? "UP" : "DOWN";
        } catch (Exception probeFailure) {
            log.debug("RAG dependency health probe failed baseUrl={} reason={}", baseUrl, probeFailure.getMessage());
            return "DOWN";
        }
    }

    private String validateQuery(String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        if (query.isEmpty()) {
            throw RagException.invalidQuery("query must not be blank.");
        }
        if (query.length() > 2000) {
            throw RagException.invalidQuery("query must be at most 2,000 characters.");
        }
        return query;
    }

    private int resolveTopK(Integer requestedTopK) {
        int topK = requestedTopK != null ? requestedTopK : properties.getDefaultTopK();
        if (topK < 1 || topK > properties.getMaxTopK()) {
            throw RagException.invalidQuery("topK must be between 1 and " + properties.getMaxTopK() + " (got " + topK + ").");
        }
        return topK;
    }

    private Map<String, Object> validateFilters(Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) {
            return Map.of();
        }
        if (filters.size() > 10) {
            throw RagException.invalidQuery("at most 10 filters may be supplied in a single query.");
        }
        for (Map.Entry<String, Object> entry : filters.entrySet()) {
            Object value = entry.getValue();
            if (!(value instanceof String || value instanceof Number || value instanceof Boolean)) {
                throw RagException.invalidQuery("filter '" + entry.getKey() + "' must be a simple string, number, or boolean value.");
            }
        }
        return filters;
    }
}
