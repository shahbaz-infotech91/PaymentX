package com.paymentx.embedding.service;

import com.paymentx.embedding.config.EmbeddingProperties;
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
import com.paymentx.embedding.service.impl.EmbeddingServiceImpl;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * English:
 * Unit tests for EmbeddingServiceImpl with a mocked EmbeddingProvider -
 * matches LlmServiceImplTest's/PromptServiceImplTest's exact pattern
 * (@Mock the collaborator, a real SimpleMeterRegistry-backed metrics
 * object rather than a mocked one). Proves the validation/orchestration
 * logic this layer alone is responsible for, decoupled from any real
 * HTTP behaviour (that's OpenAiEmbeddingProviderTest's job): disabled
 * config and blank/oversized/too-many-items input are all rejected
 * BEFORE EmbeddingProvider.embed is ever called (verifyNoInteractions -
 * Step 9's validation requirements enforced at the cheapest, earliest
 * point); a real EmbeddingProviderResult is mapped into
 * EmbeddingResponse/BatchEmbeddingResponse without any field being
 * silently dropped or invented; batch item ordering/index is preserved.
 * Why it exists: Step 27 of the Phase 3.4 brief.
 * How it communicates with other components: exercises
 * EmbeddingServiceImpl directly.
 *
 * Hinglish:
 * EmbeddingServiceImpl ke liye unit tests, ek mocked EmbeddingProvider
 * ke saath - LlmServiceImplTest/PromptServiceImplTest ke exact pattern
 * se match karta hai (collaborator ko @Mock karo, ek mocked metrics
 * object ke bajaye ek real SimpleMeterRegistry-backed metrics object).
 * Us validation/orchestration logic ko prove karta hai jiske liye sirf
 * ye layer responsible hai, kisi real HTTP behaviour se decoupled (wo
 * OpenAiEmbeddingProviderTest ka kaam hai): disabled config aur blank/
 * oversized/too-many-items input sab EmbeddingProvider.embed call hone
 * se PEHLE hi reject ho jaate hain (verifyNoInteractions - Step 9 ki
 * validation requirements sabse sasta, sabse jaldi point par enforce);
 * ek real EmbeddingProviderResult EmbeddingResponse/BatchEmbeddingResponse
 * me map hota hai bina kisi field ko silently drop ya invent kiye; batch
 * item ordering/index preserve hoti hai.
 * Ye kyu hai: Phase 3.4 brief ka Step 27.
 * Dusre components se kaise communicate karta hai: EmbeddingServiceImpl
 * ko seedhe exercise karta hai.
 */
@ExtendWith(MockitoExtension.class)
class EmbeddingServiceImplTest {

    @Mock
    private EmbeddingProvider embeddingProvider;

    private EmbeddingProperties properties;
    private EmbeddingServiceImpl embeddingService;

    @BeforeEach
    void setUp() {
        properties = new EmbeddingProperties();
        properties.setMaxInputLength(20);
        properties.setMaxBatchSize(3);
        EmbeddingMetrics metrics = new EmbeddingMetrics(new SimpleMeterRegistry());
        embeddingService = new EmbeddingServiceImpl(embeddingProvider, properties, metrics);
    }

    @Test
    void embed_delegatesToProviderAndMapsResultFields() {
        when(embeddingProvider.providerName()).thenReturn("openai");
        when(embeddingProvider.dimension()).thenReturn(3);
        when(embeddingProvider.embed(any(EmbeddingProviderRequest.class)))
                .thenReturn(new EmbeddingProviderResult("openai", "text-embedding-3-small", List.of(List.of(0.1f, 0.2f, 0.3f)), 120));

        EmbeddingResponse response = embeddingService.embed(new EmbeddingRequest("short text", null));

        assertThat(response.provider()).isEqualTo("openai");
        assertThat(response.model()).isEqualTo("text-embedding-3-small");
        assertThat(response.dimension()).isEqualTo(3);
        assertThat(response.embedding()).containsExactly(0.1f, 0.2f, 0.3f);

        verify(embeddingProvider).embed(any(EmbeddingProviderRequest.class));
    }

    @Test
    void embed_disabled_throwsNotConfiguredWithoutCallingProvider() {
        properties.setEnabled(false);

        assertThatThrownBy(() -> embeddingService.embed(new EmbeddingRequest("text", null)))
                .isInstanceOf(EmbeddingException.class)
                .satisfies(ex -> assertThat(((EmbeddingException) ex).getErrorCode()).isEqualTo(EmbeddingErrorCodes.EMBEDDING_NOT_CONFIGURED));

        verifyNoInteractions(embeddingProvider);
    }

    @Test
    void embed_blankTextAfterTrim_throwsInvalidRequestWithoutCallingProvider() {
        assertThatThrownBy(() -> embeddingService.embed(new EmbeddingRequest("   ", null)))
                .isInstanceOf(EmbeddingException.class)
                .satisfies(ex -> assertThat(((EmbeddingException) ex).getErrorCode()).isEqualTo(EmbeddingErrorCodes.EMBEDDING_INVALID_REQUEST));

        verifyNoInteractions(embeddingProvider);
    }

    @Test
    void embed_textExceedsMaxInputLength_throwsInvalidRequestWithoutCallingProvider() {
        String tooLong = "this text is definitely longer than twenty characters";

        assertThatThrownBy(() -> embeddingService.embed(new EmbeddingRequest(tooLong, null)))
                .isInstanceOf(EmbeddingException.class)
                .satisfies(ex -> assertThat(((EmbeddingException) ex).getErrorCode()).isEqualTo(EmbeddingErrorCodes.EMBEDDING_INVALID_REQUEST));

        verifyNoInteractions(embeddingProvider);
    }

    @Test
    void embed_trimsLeadingAndTrailingWhitespaceOnlyBeforeSendingToProvider() {
        when(embeddingProvider.providerName()).thenReturn("openai");
        when(embeddingProvider.dimension()).thenReturn(1);
        when(embeddingProvider.embed(any(EmbeddingProviderRequest.class)))
                .thenReturn(new EmbeddingProviderResult("openai", "text-embedding-3-small", List.of(List.of(0.5f)), 50));

        embeddingService.embed(new EmbeddingRequest("  hello world  ", null));

        verify(embeddingProvider).embed(new EmbeddingProviderRequest(List.of("hello world"), null));
    }

    @Test
    void embedBatch_preservesOrderAndIndex() {
        when(embeddingProvider.providerName()).thenReturn("openai");
        when(embeddingProvider.dimension()).thenReturn(2);
        when(embeddingProvider.embed(any(EmbeddingProviderRequest.class))).thenReturn(new EmbeddingProviderResult(
                "openai", "text-embedding-3-small", List.of(List.of(0.1f, 0.1f), List.of(0.2f, 0.2f)), 90));

        BatchEmbeddingResponse response = embeddingService.embedBatch(new BatchEmbeddingRequest(List.of("first", "second"), null));

        assertThat(response.embeddings()).hasSize(2);
        assertThat(response.embeddings().get(0).index()).isEqualTo(0);
        assertThat(response.embeddings().get(0).embedding()).containsExactly(0.1f, 0.1f);
        assertThat(response.embeddings().get(1).index()).isEqualTo(1);
        assertThat(response.embeddings().get(1).embedding()).containsExactly(0.2f, 0.2f);
    }

    @Test
    void embedBatch_exceedsMaxBatchSize_throwsInvalidRequestWithoutCallingProvider() {
        BatchEmbeddingRequest request = new BatchEmbeddingRequest(List.of("a", "b", "c", "d"), null);

        assertThatThrownBy(() -> embeddingService.embedBatch(request))
                .isInstanceOf(EmbeddingException.class)
                .satisfies(ex -> assertThat(((EmbeddingException) ex).getErrorCode()).isEqualTo(EmbeddingErrorCodes.EMBEDDING_INVALID_REQUEST));

        verifyNoInteractions(embeddingProvider);
    }

    @Test
    void embedBatch_oneBlankItemAmongValidOnes_rejectsWholeRequestBeforeCallingProvider() {
        BatchEmbeddingRequest request = new BatchEmbeddingRequest(List.of("valid", "   "), null);

        assertThatThrownBy(() -> embeddingService.embedBatch(request))
                .isInstanceOf(EmbeddingException.class)
                .satisfies(ex -> assertThat(((EmbeddingException) ex).getErrorCode()).isEqualTo(EmbeddingErrorCodes.EMBEDDING_INVALID_REQUEST));

        verifyNoInteractions(embeddingProvider);
    }

    @Test
    void embed_providerThrowsEmbeddingException_propagatesWithoutFallbackVector() {
        when(embeddingProvider.providerName()).thenReturn("openai");
        when(embeddingProvider.embed(any(EmbeddingProviderRequest.class)))
                .thenThrow(EmbeddingException.rateLimited("too many requests"));

        assertThatThrownBy(() -> embeddingService.embed(new EmbeddingRequest("text", null)))
                .isInstanceOf(EmbeddingException.class)
                .satisfies(ex -> assertThat(((EmbeddingException) ex).getErrorCode()).isEqualTo(EmbeddingErrorCodes.EMBEDDING_RATE_LIMITED));
    }

    @Test
    void health_providerReadyAndEnabled_reportsConfigured() {
        when(embeddingProvider.providerName()).thenReturn("openai");
        when(embeddingProvider.configuredModel()).thenReturn("text-embedding-3-small");
        when(embeddingProvider.dimension()).thenReturn(1536);
        when(embeddingProvider.isReady()).thenReturn(true);

        EmbeddingHealthResponse health = embeddingService.health();

        assertThat(health.status()).isEqualTo("CONFIGURED");
        assertThat(health.apiKeyPresent()).isTrue();
        assertThat(health.dimension()).isEqualTo(1536);
    }

    @Test
    void health_providerNotReady_reportsNotConfigured() {
        when(embeddingProvider.providerName()).thenReturn("openai");
        when(embeddingProvider.isReady()).thenReturn(false);

        EmbeddingHealthResponse health = embeddingService.health();

        assertThat(health.status()).isEqualTo("NOT_CONFIGURED");
        assertThat(health.apiKeyPresent()).isFalse();
    }

    @Test
    void health_disabledEvenWhenProviderReady_reportsNotConfigured() {
        properties.setEnabled(false);
        when(embeddingProvider.providerName()).thenReturn("openai");
        // isReady() deliberately not stubbed: properties.isEnabled() short-circuits health()'s
        // `properties.isEnabled() && embeddingProvider.isReady()` check before isReady() is ever called.

        EmbeddingHealthResponse health = embeddingService.health();

        assertThat(health.status()).isEqualTo("NOT_CONFIGURED");
    }

    @Test
    void health_localProviderReady_reportsLocalEmbeddingReady() {
        when(embeddingProvider.providerName()).thenReturn("local");
        when(embeddingProvider.configuredModel()).thenReturn("sentence-transformers/all-MiniLM-L6-v2");
        when(embeddingProvider.dimension()).thenReturn(384);
        when(embeddingProvider.isReady()).thenReturn(true);

        EmbeddingHealthResponse health = embeddingService.health();

        assertThat(health.status()).isEqualTo("LOCAL_EMBEDDING_READY");
        assertThat(health.dimension()).isEqualTo(384);
    }

    @Test
    void health_localProviderNotReady_reportsEmbeddingNotReady() {
        when(embeddingProvider.providerName()).thenReturn("local");
        when(embeddingProvider.isReady()).thenReturn(false);

        EmbeddingHealthResponse health = embeddingService.health();

        assertThat(health.status()).isEqualTo("EMBEDDING_NOT_READY");
    }
}
