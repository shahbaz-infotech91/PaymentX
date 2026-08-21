package com.paymentx.embedding.provider.local;

import ai.djl.MalformedModelException;
import ai.djl.huggingface.translator.TextEmbeddingTranslatorFactory;
import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.TranslateException;
import com.paymentx.embedding.config.EmbeddingProperties;
import com.paymentx.embedding.exception.EmbeddingException;
import com.paymentx.embedding.provider.EmbeddingProvider;
import com.paymentx.embedding.provider.EmbeddingProviderRequest;
import com.paymentx.embedding.provider.EmbeddingProviderResult;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * English:
 * PHASE 3.4 LOCAL EMBEDDING MIGRATION. What this file does: implements EmbeddingProvider using a real,
 * self-hosted sentence-transformer model (sentence-transformers/all-MiniLM-L6-v2, 384 dimensions) run
 * in-process via Deep Java Library (DJL) + ONNX Runtime on CPU - every vector returned by embed() comes
 * from a real forward pass through this model, never a random/fake vector. Why it exists: this service's
 * runtime embedding generation must not require EMBEDDING_API_KEY or a network call to api.openai.com (the
 * migration's whole point) - see PAYMENTX_PHASE_3_9_AI_E2E_INTEGRATION.md's "Known limitations" for the
 * real OpenAI account-quota failure that motivated this, and PAYMENTX_PHASE_3_4_LOCAL_EMBEDDING.md for the
 * full model-selection rationale (license, dimension, CPU/memory footprint). How it connects to the
 * Embedding Service: implements the same EmbeddingProvider interface OpenAiEmbeddingProvider does - nothing
 * in EmbeddingServiceImpl, EmbeddingController, or any DTO changed to add this class (Step 5 of the
 * migration brief - "do not couple RAG/EmbeddingService directly to the local model").
 * <p>
 * The real model weights + tokenizer are never bundled into this repository - DJL resolves
 * `djl://ai.djl.huggingface.onnxruntime/{model}` through its own HuggingFace/ONNX Runtime model zoo on
 * first use and caches the downloaded files under DJL's own cache directory (default `~/.djl.ai`, always
 * outside C:\PaymentX; overridable via `embedding.local.cache-dir`, which sets DJL's own real
 * `DJL_CACHE_DIR` mechanism at startup - not a new convention invented here).
 * <p>
 * Model loading happens once, in {@link #init()}, and is intentionally NOT allowed to crash this service's
 * startup if it fails (no internet on first run, corrupted cache, unsupported platform, etc.) - {@code
 * ready} stays {@code false} and {@link #isReady()}/health() report that honestly (Step 9 of the brief:
 * never report healthy merely because the process started). A {@link Predictor} is not thread-safe for
 * concurrent {@code predict()} calls (DJL's own documented constraint), so every real call in {@link
 * #embed(EmbeddingProviderRequest)} is serialized through a single {@code synchronized} block - the
 * simplest correct option for this migration's scope (a single-operator developer/CI machine, not a
 * high-concurrency production deployment); a predictor pool is a real, identified follow-up if concurrent
 * throughput ever matters (see PAYMENTX_PHASE_3_4_LOCAL_EMBEDDING.md's Limitations section).
 *
 * Hinglish:
 * PHASE 3.4 LOCAL EMBEDDING MIGRATION. Ye file kya karti hai: EmbeddingProvider ko ek real, self-hosted
 * sentence-transformer model (sentence-transformers/all-MiniLM-L6-v2, 384 dimensions) se implement karti
 * hai, jo Deep Java Library (DJL) + ONNX Runtime ke through in-process, CPU par chalta hai - embed() se
 * return hone wala har vector is model ke through ek real forward pass se aata hai, kabhi ek random/fake
 * vector nahi. Ye project me kyu hai: is service ki runtime embedding generation ko EMBEDDING_API_KEY ya
 * api.openai.com tak ek network call ki zaroorat nahi honi chahiye (migration ka poora point hai) - real
 * OpenAI account-quota failure ke liye PAYMENTX_PHASE_3_9_AI_E2E_INTEGRATION.md ka "Known limitations"
 * dekho jisne isko motivate kiya, aur poore model-selection rationale (license, dimension, CPU/memory
 * footprint) ke liye PAYMENTX_PHASE_3_4_LOCAL_EMBEDDING.md dekho. Embedding Service ke saath kaise connect
 * hoti hai: wahi EmbeddingProvider interface implement karti hai jo OpenAiEmbeddingProvider karta hai -
 * ise add karne ke liye EmbeddingServiceImpl, EmbeddingController, ya kisi DTO me kuch nahi badla (migration
 * brief ka Step 5).
 */
@Component
@Slf4j
// Phase 3.4 local-embedding migration: this is now the default provider (matchIfMissing=true) - unset or
// explicitly "local" both select this bean; only an explicit embedding.provider=openai opts back out.
@ConditionalOnProperty(prefix = "embedding", name = "provider", havingValue = "local", matchIfMissing = true)
public class LocalEmbeddingProvider implements EmbeddingProvider {

    private static final String PROVIDER_NAME = "local";

    private final EmbeddingProperties properties;

    private ZooModel<String, float[]> model;
    private Predictor<String, float[]> predictor;
    private volatile boolean ready = false;
    private volatile String loadError;

    public LocalEmbeddingProvider(EmbeddingProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void init() {
        EmbeddingProperties.Local config = properties.getLocal();
        if (config.getCacheDir() != null && !config.getCacheDir().isBlank()) {
            // DJL's own real, documented cache-location mechanism (ai.djl.util.Utils reads this system
            // property before falling back to ~/.djl.ai) - not a new convention invented here.
            System.setProperty("DJL_CACHE_DIR", config.getCacheDir());
        }
        try {
            Criteria<String, float[]> criteria = Criteria.builder()
                    .setTypes(String.class, float[].class)
                    .optModelUrls("djl://ai.djl.huggingface.onnxruntime/" + config.getModel())
                    .optEngine("OnnxRuntime")
                    .optTranslatorFactory(new TextEmbeddingTranslatorFactory())
                    .optArgument("maxLength", String.valueOf(config.getMaxSequenceLength()))
                    .build();
            this.model = criteria.loadModel();
            this.predictor = model.newPredictor();
            this.ready = true;
            log.info("Local embedding model loaded and ready model={} dimension={}", config.getModel(), config.getDimension());
        } catch (ModelNotFoundException | MalformedModelException | IOException | RuntimeException loadFailure) {
            // Deliberately not rethrown - a failed local model load must not crash this service's startup
            // (Step 9 of the brief); isReady()/health() report the real failure instead.
            this.ready = false;
            this.loadError = loadFailure.getClass().getSimpleName() + ": " + loadFailure.getMessage();
            log.warn("Local embedding model failed to load model={} reason={}", config.getModel(), loadError);
        }
    }

    @PreDestroy
    void shutdown() {
        if (predictor != null) {
            predictor.close();
        }
        if (model != null) {
            model.close();
        }
    }

    @Override
    public String providerName() {
        return PROVIDER_NAME;
    }

    @Override
    public int dimension() {
        return properties.getLocal().getDimension();
    }

    @Override
    public String configuredModel() {
        return properties.getLocal().getModel();
    }

    @Override
    public boolean isReady() {
        return ready;
    }

    @Override
    public EmbeddingProviderResult embed(EmbeddingProviderRequest request) {
        if (!ready) {
            throw EmbeddingException.notConfigured(
                    "Local embedding model is not loaded and usable (" + (loadError != null ? loadError : "not ready yet")
                            + ") - cannot generate a real vector.");
        }

        int expectedDimension = properties.getLocal().getDimension();
        String model = properties.getLocal().getModel();
        long start = System.currentTimeMillis();
        List<List<Float>> vectors = new ArrayList<>(request.texts().size());
        try {
            synchronized (this) {
                for (String text : request.texts()) {
                    float[] raw = predictor.predict(text);
                    vectors.add(toValidatedVector(raw, expectedDimension, model));
                }
            }
        } catch (TranslateException inferenceFailure) {
            throw EmbeddingException.internalError("Local embedding model inference failed: " + inferenceFailure.getMessage());
        }
        long latencyMs = System.currentTimeMillis() - start;
        return new EmbeddingProviderResult(PROVIDER_NAME, model, vectors, latencyMs);
    }

    private List<Float> toValidatedVector(float[] raw, int expectedDimension, String model) {
        if (raw.length != expectedDimension) {
            throw EmbeddingException.dimensionMismatch("Expected a " + expectedDimension + "-dimension vector from local model "
                    + model + " but received " + raw.length + " values.");
        }
        List<Float> vector = new ArrayList<>(raw.length);
        for (float value : raw) {
            if (Float.isNaN(value) || Float.isInfinite(value)) {
                throw EmbeddingException.responseInvalid("Local embedding model produced a non-finite value.");
            }
            vector.add(value);
        }
        return vector;
    }
}
