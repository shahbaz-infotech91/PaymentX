package com.paymentx.embedding.provider.local;

import com.paymentx.embedding.config.EmbeddingProperties;
import com.paymentx.embedding.exception.EmbeddingErrorCodes;
import com.paymentx.embedding.exception.EmbeddingException;
import com.paymentx.embedding.provider.EmbeddingProviderRequest;
import com.paymentx.embedding.provider.EmbeddingProviderResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * English:
 * PHASE 3.4 LOCAL EMBEDDING MIGRATION. What this file does: real tests against
 * LocalEmbeddingProvider - a single shared instance loads the real
 * sentence-transformers/all-MiniLM-L6-v2 ONNX model once in {@code @BeforeAll} (real network
 * download on first run, cached by DJL thereafter; matches OpenAiEmbeddingProviderTest's
 * "load once, reuse across test methods" shape, except here the "server" is a real local model,
 * not WireMock). Every vector asserted on in this file comes from a real forward pass through
 * that model - none are fabricated. Why it exists: Step 11/12 of the local-embedding migration
 * brief - "real local embedding test... no fake vector... model unavailable behavior." How it
 * connects to the Embedding Service: exercises LocalEmbeddingProvider directly, the same
 * EmbeddingProvider implementation Spring wires into EmbeddingServiceImpl when
 * embedding.provider=local (the default as of this migration).
 *
 * Hinglish:
 * PHASE 3.4 LOCAL EMBEDDING MIGRATION. Ye file kya karti hai: LocalEmbeddingProvider ke against
 * real tests - ek shared instance real sentence-transformers/all-MiniLM-L6-v2 ONNX model ko
 * {@code @BeforeAll} me ek baar load karta hai (pehli run par real network download, uske baad DJL
 * cache karta hai). Is file me assert kiya gaya har vector us model se ek real forward pass se aata
 * hai - koi fabricated nahi. Ye project me kyu hai: migration brief ka Step 11/12 - "real local
 * embedding test... no fake vector... model unavailable behavior." Embedding Service ke saath kaise
 * connect hoti hai: LocalEmbeddingProvider ko seedhe exercise karti hai, wahi EmbeddingProvider
 * implementation jo Spring EmbeddingServiceImpl me wire karta hai jab embedding.provider=local ho
 * (is migration ke baad ka default).
 */
class LocalEmbeddingProviderTest {

    private static LocalEmbeddingProvider provider;
    private static EmbeddingProperties properties;

    @BeforeAll
    static void loadRealModel() {
        properties = new EmbeddingProperties();
        provider = new LocalEmbeddingProvider(properties);
        provider.init();
    }

    @AfterAll
    static void unloadModel() {
        provider.shutdown();
    }

    @Test
    void modelLoadedSuccessfully_isReadyReturnsTrue() {
        assertThat(provider.isReady()).isTrue();
        assertThat(provider.providerName()).isEqualTo("local");
        assertThat(provider.dimension()).isEqualTo(384);
        assertThat(provider.configuredModel()).isEqualTo("sentence-transformers/all-MiniLM-L6-v2");
    }

    @Test
    void embed_realText_returnsRealNonZeroFiniteVectorOfCorrectDimension() {
        EmbeddingProviderResult result = provider.embed(new EmbeddingProviderRequest(
                List.of("Duplicate payment detection prevents the same payment from being processed more than once."),
                null));

        assertThat(result.provider()).isEqualTo("local");
        assertThat(result.vectors()).hasSize(1);
        List<Float> vector = result.vectors().get(0);
        assertThat(vector).hasSize(384);
        assertThat(vector).allSatisfy(value -> assertThat(Float.isNaN(value) || Float.isInfinite(value)).isFalse());
        // A real model never returns an all-zero vector for real text - proves this is an actual
        // forward pass through the model, not a fake/placeholder array.
        assertThat(vector.stream().anyMatch(v -> v != 0.0f)).isTrue();
    }

    @Test
    void embed_sameTextTwice_deterministicRealOutput() {
        String text = "PaymentX routing determines which payment scheme handles a transaction.";
        List<Float> first = provider.embed(new EmbeddingProviderRequest(List.of(text), null)).vectors().get(0);
        List<Float> second = provider.embed(new EmbeddingProviderRequest(List.of(text), null)).vectors().get(0);

        assertThat(first).isEqualTo(second);
    }

    @Test
    void embed_batchTexts_preservesCountAndOrder() {
        EmbeddingProviderResult result = provider.embed(new EmbeddingProviderRequest(
                List.of("Payment settlement", "Reconciliation mismatch", "Routing rule"), null));

        assertThat(result.vectors()).hasSize(3);
        result.vectors().forEach(v -> assertThat(v).hasSize(384));
        // Different real inputs must not collapse to an identical vector.
        assertThat(result.vectors().get(0)).isNotEqualTo(result.vectors().get(1));
        assertThat(result.vectors().get(1)).isNotEqualTo(result.vectors().get(2));
    }

    @Test
    void embed_veryLongInput_truncatedNotRejected() {
        String longText = "PaymentX processes payments. ".repeat(400); // ~2400 words, well over the 256-token limit

        EmbeddingProviderResult result = provider.embed(new EmbeddingProviderRequest(List.of(longText), null));

        assertThat(result.vectors()).hasSize(1);
        assertThat(result.vectors().get(0)).hasSize(384);
    }

    /**
     * Step 13 of the migration brief - real semantic similarity, computed from two real vectors this
     * model actually produced, not asserted on faith. Cosine similarity between two differently-worded
     * but semantically related questions must be meaningfully higher than similarity to an unrelated
     * sentence about a different PaymentX domain entirely.
     */
    @Test
    void embed_semanticallyRelatedQueries_moreSimilarThanUnrelatedQuery() {
        List<Float> queryA = provider.embed(new EmbeddingProviderRequest(
                List.of("How does PaymentX prevent duplicate payments?"), null)).vectors().get(0);
        List<Float> queryB = provider.embed(new EmbeddingProviderRequest(
                List.of("How does idempotency stop the same payment from being processed twice?"), null)).vectors().get(0);
        List<Float> unrelated = provider.embed(new EmbeddingProviderRequest(
                List.of("What color is the PaymentX Control Center dashboard theme?"), null)).vectors().get(0);

        double relatedSimilarity = cosineSimilarity(queryA, queryB);
        double unrelatedSimilarity = cosineSimilarity(queryA, unrelated);

        assertThat(relatedSimilarity).isGreaterThan(unrelatedSimilarity);
        assertThat(relatedSimilarity).isGreaterThan(0.5);
    }

    @Test
    void notYetInitialized_isReadyFalseAndEmbedThrowsHonestError() {
        LocalEmbeddingProvider uninitialized = new LocalEmbeddingProvider(new EmbeddingProperties());

        assertThat(uninitialized.isReady()).isFalse();
        assertThatThrownBy(() -> uninitialized.embed(new EmbeddingProviderRequest(List.of("text"), null)))
                .isInstanceOf(EmbeddingException.class)
                .satisfies(ex -> assertThat(((EmbeddingException) ex).getErrorCode()).isEqualTo(EmbeddingErrorCodes.EMBEDDING_NOT_CONFIGURED));
    }

    @Test
    void modelFailsToLoad_isReadyFalseNeverCrashesStartup() {
        EmbeddingProperties badProperties = new EmbeddingProperties();
        badProperties.getLocal().setModel("this-is-not-a-real-huggingface-model-id/does-not-exist");
        LocalEmbeddingProvider brokenProvider = new LocalEmbeddingProvider(badProperties);

        brokenProvider.init(); // must not throw - Step 9 of the brief

        assertThat(brokenProvider.isReady()).isFalse();
        assertThatThrownBy(() -> brokenProvider.embed(new EmbeddingProviderRequest(List.of("text"), null)))
                .isInstanceOf(EmbeddingException.class)
                .satisfies(ex -> assertThat(((EmbeddingException) ex).getErrorCode()).isEqualTo(EmbeddingErrorCodes.EMBEDDING_NOT_CONFIGURED));
    }

    private static double cosineSimilarity(List<Float> a, List<Float> b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.size(); i++) {
            dot += a.get(i) * b.get(i);
            normA += a.get(i) * a.get(i);
            normB += b.get(i) * b.get(i);
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
