package com.paymentx.embedding.service.impl;

import com.paymentx.embedding.config.EmbeddingProperties;
import com.paymentx.embedding.dto.BatchEmbeddingItem;
import com.paymentx.embedding.dto.BatchEmbeddingRequest;
import com.paymentx.embedding.dto.BatchEmbeddingResponse;
import com.paymentx.embedding.dto.EmbeddingHealthResponse;
import com.paymentx.embedding.dto.EmbeddingRequest;
import com.paymentx.embedding.dto.EmbeddingResponse;
import com.paymentx.embedding.exception.EmbeddingErrorCodes;
import com.paymentx.embedding.exception.EmbeddingException;
import com.paymentx.embedding.metrics.EmbeddingMetrics;
import com.paymentx.embedding.provider.EmbeddingProvider;
import com.paymentx.embedding.provider.EmbeddingProviderRequest;
import com.paymentx.embedding.provider.EmbeddingProviderResult;
import com.paymentx.embedding.service.EmbeddingService;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * English:
 * The real implementation of EmbeddingService - matches LLM Service's
 * LlmServiceImpl shape exactly: resolve "is this even configured/valid"
 * up front (before any provider call), delegate to whichever
 * EmbeddingProvider is injected (today: OpenAiEmbeddingProvider - this
 * class never references OpenAI's raw JSON, per Step 11's isolation
 * requirement), then map the provider-agnostic EmbeddingProviderResult
 * into the public EmbeddingResponse/BatchEmbeddingResponse DTOs while
 * recording metrics/logs. Stateless by construction (Step 23) - no
 * field here holds request-scoped or cross-request state.
 * WHY "unsupported model" (Step 9) has no separate allowlist check
 * here: a model name OpenAI itself does not recognize is rejected by
 * the provider as a real 400/404 (mapped to EMBEDDING_INVALID_REQUEST
 * by OpenAiEmbeddingProvider); a model OpenAI does recognize but whose
 * real output dimension differs from embedding.openai.dimension is
 * rejected by the provider's own dimension check
 * (EMBEDDING_DIMENSION_MISMATCH) - both are already real, honest, specific
 * rejections without inventing a second allowlist that would just
 * duplicate what the provider already tells us authoritatively.
 * WHY normalization here is trim-only (Step 10): stripping only leading/
 * trailing whitespace removes accidental copy-paste padding without
 * touching internal whitespace/line breaks/formatting a document chunk
 * (Phase 3.5's future consumer) may depend on for semantic meaning -
 * "do NOT aggressively modify user/document text" per the brief.
 * WHY input validation runs before even `embeddingProvider.providerName()`
 * is called: the cheapest, earliest rejection (Step 9) means this
 * service touches the EmbeddingProvider collaborator - not just the
 * real network call, but even a metadata accessor - zero times for a
 * request that was never going to be valid, which is also what
 * EmbeddingServiceImplTest's verifyNoInteractions(embeddingProvider)
 * assertions actually prove.
 * Why it exists: PAYMENTX_PHASE_3_ARCHITECTURE.md-style EmbeddingService
 * layer - the piece EmbeddingController depends on instead of talking
 * to EmbeddingProvider directly.
 * How it communicates with other components: implements
 * EmbeddingService; injected into EmbeddingController; calls
 * EmbeddingProvider (OpenAiEmbeddingProvider) and EmbeddingMetrics.
 *
 * Hinglish:
 * EmbeddingService ki real implementation - LLM Service ke
 * LlmServiceImpl shape se exactly match karta hai: sabse pehle "kya ye
 * configured/valid bhi hai" resolve karta hai (kisi provider call se
 * pehle), jo bhi EmbeddingProvider inject hua hai use delegate karta hai
 * (aaj: OpenAiEmbeddingProvider - ye class kabhi OpenAI ka raw JSON
 * reference nahi karti, Step 11 ke isolation requirement ke hisaab se),
 * phir provider-agnostic EmbeddingProviderResult ko public
 * EmbeddingResponse/BatchEmbeddingResponse DTOs me map karta hai
 * metrics/logs record karte hue. Construction se hi stateless (Step 23)
 * - yahan koi field request-scoped ya cross-request state nahi rakhta.
 * "Unsupported model" (Step 9) ke liye yahan alag allowlist check KYU
 * nahi hai: ek model name jo OpenAI khud nahi pehchaanta wo provider
 * dwara ek real 400/404 ke roop me reject hota hai
 * (OpenAiEmbeddingProvider dwara EMBEDDING_INVALID_REQUEST par map kiya
 * gaya); ek model jo OpenAI pehchaanta hai lekin jiska real output
 * dimension embedding.openai.dimension se alag hai wo provider ke apne
 * dimension check se reject hota hai (EMBEDDING_DIMENSION_MISMATCH) -
 * dono already real, honest, specific rejections hain bina ek doosri
 * allowlist invent kiye jo bas wahi duplicate karti jo provider already
 * authoritatively bata deta hai.
 * Normalization yahan trim-only (Step 10) KYU hai: sirf leading/trailing
 * whitespace strip karna accidental copy-paste padding hata deta hai
 * bina internal whitespace/line breaks/formatting chhue jispar ek
 * document chunk (Phase 3.5 ka future consumer) semantic meaning ke
 * liye depend kar sakta hai - "user/document text ko aggressively
 * modify MAT karo" brief ke hisaab se.
 * Ye kyu hai: PAYMENTX_PHASE_3_ARCHITECTURE.md-style EmbeddingService
 * layer - wo piece jispar EmbeddingController depend karta hai seedhe
 * EmbeddingProvider se baat karne ke bajaye.
 * Dusre components se kaise communicate karta hai: EmbeddingService
 * implement karta hai; EmbeddingController me inject hota hai;
 * EmbeddingProvider (OpenAiEmbeddingProvider) aur EmbeddingMetrics ko
 * call karta hai.
 */
@Service
@Slf4j
public class EmbeddingServiceImpl implements EmbeddingService {

    private final EmbeddingProvider embeddingProvider;
    private final EmbeddingProperties properties;
    private final EmbeddingMetrics metrics;

    public EmbeddingServiceImpl(EmbeddingProvider embeddingProvider, EmbeddingProperties properties, EmbeddingMetrics metrics) {
        this.embeddingProvider = embeddingProvider;
        this.properties = properties;
        this.metrics = metrics;
    }

    @Override
    public EmbeddingResponse embed(EmbeddingRequest request) {
        String text = normalize(request.text());
        validateEnabled();
        validateText(text, -1);

        String provider = embeddingProvider.providerName();
        metrics.recordRequest(provider);
        metrics.recordInputSize(provider, text.length());

        Timer.Sample timerSample = metrics.startLatencyTimer();
        try {
            EmbeddingProviderResult result = embeddingProvider.embed(new EmbeddingProviderRequest(List.of(text), request.model()));
            metrics.stopLatencyTimer(timerSample, provider);
            metrics.recordSuccess(provider, result.model());
            metrics.recordDimension(provider, result.model(), embeddingProvider.dimension());
            log.info("Embedding generated provider={} model={} dimension={} inputLength={} latencyMs={}",
                    provider, result.model(), embeddingProvider.dimension(), text.length(), result.latencyMs());
            return new EmbeddingResponse(result.vectors().get(0), embeddingProvider.dimension(), result.model(), provider);
        } catch (EmbeddingException ex) {
            metrics.stopLatencyTimer(timerSample, provider);
            recordFailureMetrics(provider, ex);
            throw ex;
        }
    }

    @Override
    public BatchEmbeddingResponse embedBatch(BatchEmbeddingRequest request) {
        List<String> texts = request.texts().stream().map(this::normalize).toList();
        validateEnabled();
        validateBatch(texts);

        String provider = embeddingProvider.providerName();
        metrics.recordRequest(provider);
        metrics.recordBatchRequest(provider, texts.size());

        Timer.Sample timerSample = metrics.startLatencyTimer();
        try {
            EmbeddingProviderResult result = embeddingProvider.embed(new EmbeddingProviderRequest(texts, request.model()));
            metrics.stopLatencyTimer(timerSample, provider);
            metrics.recordSuccess(provider, result.model());

            List<BatchEmbeddingItem> items = new ArrayList<>(result.vectors().size());
            for (int i = 0; i < result.vectors().size(); i++) {
                items.add(new BatchEmbeddingItem(i, result.vectors().get(i), null, null));
            }
            log.info("Batch embedding generated provider={} model={} itemCount={} latencyMs={}",
                    provider, result.model(), texts.size(), result.latencyMs());
            return new BatchEmbeddingResponse(items, embeddingProvider.dimension(), result.model(), provider);
        } catch (EmbeddingException ex) {
            metrics.stopLatencyTimer(timerSample, provider);
            recordFailureMetrics(provider, ex);
            throw ex;
        }
    }

    /**
     * Phase 3.4 local-embedding migration: generalized to ask embeddingProvider.isReady()/configuredModel()
     * instead of reading properties.getOpenai() directly - this method used to hardcode OpenAI's config
     * section, which was correct when exactly one provider existed but would have been meaningless for a
     * provider (local) with no API key at all. For the local provider, isReady() is a REAL, honest signal -
     * it is true only once the ONNX model has actually finished loading in LocalEmbeddingProvider.init(),
     * never merely because this process started (Step 9 of the migration brief).
     */
    @Override
    public EmbeddingHealthResponse health() {
        boolean ready = properties.isEnabled() && embeddingProvider.isReady();
        boolean local = "local".equals(embeddingProvider.providerName());
        String status = local
                ? (ready ? "LOCAL_EMBEDDING_READY" : "EMBEDDING_NOT_READY")
                : (ready ? "CONFIGURED" : "NOT_CONFIGURED");
        String note = local
                ? "This checks whether the local embedding model has actually finished loading and is usable - "
                        + "a real, verifiable in-memory signal, not merely that this process started. Call POST "
                        + "/api/v1/embeddings for a real generated vector."
                : "This check confirms whether embedding generation is enabled and an API key is configured, not "
                        + "whether it is valid or whether the provider is currently reachable - Phase 3.4 deliberately "
                        + "does not make a real (billed) API call just to answer a health check. Call POST "
                        + "/api/v1/embeddings for a real signal.";
        return new EmbeddingHealthResponse(
                status,
                embeddingProvider.providerName(),
                embeddingProvider.configuredModel(),
                embeddingProvider.dimension(),
                ready,
                note,
                OffsetDateTime.now());
    }

    private String normalize(String text) {
        return text == null ? "" : text.trim();
    }

    private void validateEnabled() {
        if (!properties.isEnabled()) {
            throw EmbeddingException.notConfigured("Embedding Service is disabled (embedding.enabled=false).");
        }
    }

    private void validateText(String text, int batchIndex) {
        String position = batchIndex < 0 ? "" : " at index " + batchIndex;
        if (text.isEmpty()) {
            throw EmbeddingException.invalidRequest("text" + position + " must not be blank after trimming.");
        }
        if (text.length() > properties.getMaxInputLength()) {
            throw EmbeddingException.invalidRequest(
                    "text" + position + " exceeds the configured maximum length of " + properties.getMaxInputLength() + " characters.");
        }
    }

    private void validateBatch(List<String> texts) {
        if (texts.isEmpty()) {
            throw EmbeddingException.invalidRequest("texts must not be empty.");
        }
        if (texts.size() > properties.getMaxBatchSize()) {
            throw EmbeddingException.invalidRequest(
                    "batch size " + texts.size() + " exceeds the configured maximum of " + properties.getMaxBatchSize() + ".");
        }
        for (int i = 0; i < texts.size(); i++) {
            validateText(texts.get(i), i);
        }
    }

    private void recordFailureMetrics(String provider, EmbeddingException ex) {
        metrics.recordFailure(provider, ex.getErrorCode());
        metrics.recordProviderError(provider, ex.getErrorCode());
        if (EmbeddingErrorCodes.EMBEDDING_PROVIDER_TIMEOUT.equals(ex.getErrorCode())) {
            metrics.recordTimeout(provider);
        }
    }
}
