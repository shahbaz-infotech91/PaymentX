package com.paymentx.rag.service;

import com.paymentx.rag.client.EmbeddingServiceClient;
import com.paymentx.rag.client.LlmServiceClient;
import com.paymentx.rag.client.PromptServiceClient;
import com.paymentx.rag.client.VectorServiceClient;
import com.paymentx.rag.config.RagProperties;
import com.paymentx.rag.context.ContextBuilder;
import com.paymentx.rag.dto.RagQueryRequest;
import com.paymentx.rag.dto.RagQueryResponse;
import com.paymentx.rag.dto.RagQueryStatus;
import com.paymentx.rag.dto.RetrievedChunk;
import com.paymentx.rag.exception.RagErrorCodes;
import com.paymentx.rag.exception.RagException;
import com.paymentx.rag.metrics.RagMetrics;
import com.paymentx.rag.service.impl.RagServiceImpl;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * English:
 * Unit tests for RagServiceImpl with all four downstream clients
 * mocked - matches every other AI Platform service's ThingServiceImplTest
 * pattern (@Mock the collaborators, a real SimpleMeterRegistry-backed
 * RagMetrics rather than a mocked one, a real ContextBuilder since it
 * is pure logic with no external dependency of its own). Proves the
 * orchestration/validation logic this layer alone is responsible for,
 * decoupled from any real HTTP behaviour (that is
 * RagQueryIntegrationTest's job): query validation rejects before any
 * client is touched; the CRITICAL PRINCIPLE is structural -
 * PromptServiceClient/LlmServiceClient are NEVER called when the
 * filtered result set is empty (verifyNoInteractions, not just "the
 * response happens to say INSUFFICIENT_CONTEXT"); relevance-threshold
 * rejection counting is exact; a downstream failure from any of the
 * four clients propagates as the correct RagException; a refusal from
 * LLM Service maps to RagQueryStatus.REFUSED with empty sources.
 * Why it exists: Step 37 of the Phase 3.6 brief.
 * How it communicates with other components: exercises RagServiceImpl
 * directly.
 *
 * Hinglish:
 * RagServiceImpl ke liye unit tests, char downstream clients mocked ke
 * saath - har doosri AI Platform service ke ThingServiceImplTest
 * pattern se match karta hai (collaborators ko @Mock karo, ek mocked
 * RagMetrics ke bajaye ek real SimpleMeterRegistry-backed RagMetrics,
 * ek real ContextBuilder kyunki wo pure logic hai apni koi external
 * dependency ke bina). Us orchestration/validation logic ko prove karta
 * hai jiske liye sirf ye layer responsible hai, kisi real HTTP
 * behaviour se decoupled (wo RagQueryIntegrationTest ka kaam hai):
 * query validation kisi bhi client ko touch hone se pehle reject karti
 * hai; CRITICAL PRINCIPLE structural hai - PromptServiceClient/
 * LlmServiceClient KABHI call nahi hote jab filtered result set khali
 * ho (verifyNoInteractions, sirf "response INSUFFICIENT_CONTEXT kehta
 * hai" nahi); relevance-threshold rejection counting exact hai; char
 * clients me se kisi ek se ek downstream failure sahi RagException ke
 * roop me propagate hota hai; LLM Service se ek refusal
 * RagQueryStatus.REFUSED par khali sources ke saath map hota hai.
 * Ye kyu hai: Phase 3.6 brief ka Step 37.
 * Dusre components se kaise communicate karta hai: RagServiceImpl ko
 * seedhe exercise karta hai.
 */
@ExtendWith(MockitoExtension.class)
class RagServiceImplTest {

    @Mock
    private EmbeddingServiceClient embeddingServiceClient;
    @Mock
    private VectorServiceClient vectorServiceClient;
    @Mock
    private PromptServiceClient promptServiceClient;
    @Mock
    private LlmServiceClient llmServiceClient;
    @Mock
    private com.paymentx.rag.audit.RagAuditClient auditClient;

    private RagProperties properties;
    private RagServiceImpl ragService;

    @BeforeEach
    void setUp() {
        properties = new RagProperties();
        properties.setMinScore(0.5);
        ContextBuilder contextBuilder = new ContextBuilder(properties);
        RagMetrics metrics = new RagMetrics(new SimpleMeterRegistry());
        ragService = new RagServiceImpl(embeddingServiceClient, vectorServiceClient, promptServiceClient,
                llmServiceClient, auditClient, contextBuilder, properties, metrics, new RestTemplateBuilder());
    }

    private static RetrievedChunk chunk(String chunkId, String content, double score) {
        return new RetrievedChunk("doc-1", chunkId, "doc-key-1", content, score, 1.0 - score);
    }

    @Test
    void query_blankQuery_throwsInvalidQueryWithoutCallingAnyClient() {
        assertThatThrownBy(() -> ragService.query(new RagQueryRequest("   ", null, null)))
                .isInstanceOf(RagException.class)
                .satisfies(ex -> assertThat(((RagException) ex).getErrorCode()).isEqualTo(RagErrorCodes.INVALID_QUERY));

        verifyNoInteractions(embeddingServiceClient, vectorServiceClient, promptServiceClient, llmServiceClient);
    }

    @Test
    void query_topKAboveConfiguredMaximum_throwsInvalidQuery() {
        RagQueryRequest request = new RagQueryRequest("a valid question", 500, null);

        assertThatThrownBy(() -> ragService.query(request))
                .isInstanceOf(RagException.class)
                .satisfies(ex -> assertThat(((RagException) ex).getErrorCode()).isEqualTo(RagErrorCodes.INVALID_QUERY));
    }

    @Test
    void query_filterWithNestedObjectValue_throwsInvalidQuery() {
        RagQueryRequest request = new RagQueryRequest("a valid question", null, Map.of("bad", Map.of("nested", "value")));

        assertThatThrownBy(() -> ragService.query(request))
                .isInstanceOf(RagException.class)
                .satisfies(ex -> assertThat(((RagException) ex).getErrorCode()).isEqualTo(RagErrorCodes.INVALID_QUERY));
    }

    @Test
    void query_embeddingServiceFails_propagatesRagException() {
        when(embeddingServiceClient.embed(anyString(), anyString(), anyString(), any()))
                .thenThrow(RagException.embeddingServiceUnavailable("down", true));

        assertThatThrownBy(() -> ragService.query(new RagQueryRequest("a valid question", null, null)))
                .isInstanceOf(RagException.class)
                .satisfies(ex -> assertThat(((RagException) ex).getErrorCode()).isEqualTo(RagErrorCodes.EMBEDDING_SERVICE_UNAVAILABLE));

        verifyNoInteractions(vectorServiceClient, promptServiceClient, llmServiceClient);
    }

    @Test
    void query_vectorServiceFails_propagatesRagException() {
        when(embeddingServiceClient.embed(anyString(), anyString(), anyString(), any())).thenReturn(List.of(0.1f, 0.2f));
        when(vectorServiceClient.search(anyString(), anyList(), anyString(), anyString(), anyInt(), any(), any()))
                .thenThrow(RagException.vectorServiceUnavailable("down", true));

        assertThatThrownBy(() -> ragService.query(new RagQueryRequest("a valid question", null, null)))
                .isInstanceOf(RagException.class)
                .satisfies(ex -> assertThat(((RagException) ex).getErrorCode()).isEqualTo(RagErrorCodes.VECTOR_SERVICE_UNAVAILABLE));

        verifyNoInteractions(promptServiceClient, llmServiceClient);
    }

    @Test
    void query_allResultsBelowThreshold_returnsInsufficientContextWithoutCallingPromptOrLlm() {
        when(embeddingServiceClient.embed(anyString(), anyString(), anyString(), any())).thenReturn(List.of(0.1f, 0.2f));
        when(vectorServiceClient.search(anyString(), anyList(), anyString(), anyString(), anyInt(), any(), any()))
                .thenReturn(List.of(chunk("c1", "barely relevant", 0.3), chunk("c2", "also low", 0.2)));

        RagQueryResponse response = ragService.query(new RagQueryRequest("a valid question", null, null));

        assertThat(response.status()).isEqualTo(RagQueryStatus.INSUFFICIENT_CONTEXT);
        assertThat(response.sources()).isEmpty();
        assertThat(response.answer()).isNotBlank();
        assertThat(response.metadata().retrievedChunks()).isEqualTo(2);
        assertThat(response.metadata().rejectedByThreshold()).isEqualTo(2);
        assertThat(response.metadata().contextChunksUsed()).isZero();

        verifyNoInteractions(promptServiceClient, llmServiceClient);
    }

    @Test
    void query_noResultsAtAll_returnsInsufficientContext() {
        when(embeddingServiceClient.embed(anyString(), anyString(), anyString(), any())).thenReturn(List.of(0.1f, 0.2f));
        when(vectorServiceClient.search(anyString(), anyList(), anyString(), anyString(), anyInt(), any(), any())).thenReturn(List.of());

        RagQueryResponse response = ragService.query(new RagQueryRequest("a valid question", null, null));

        assertThat(response.status()).isEqualTo(RagQueryStatus.INSUFFICIENT_CONTEXT);
        verifyNoInteractions(promptServiceClient, llmServiceClient);
    }

    @Test
    void query_relevantResultsFound_callsPromptThenLlmAndReturnsGroundedAnswer() {
        when(embeddingServiceClient.embed(anyString(), anyString(), anyString(), any())).thenReturn(List.of(0.1f, 0.2f));
        when(vectorServiceClient.search(anyString(), anyList(), anyString(), anyString(), anyInt(), any(), any()))
                .thenReturn(List.of(chunk("c1", "Duplicate payment requests are rejected when the same idempotency key is reused.", 0.9)));
        when(promptServiceClient.render(anyString(), anyString(), anyString(), anyString(), any())).thenReturn("rendered prompt text");
        when(llmServiceClient.generate(anyString(), eq("rendered prompt text"), any()))
                .thenReturn(new LlmServiceClient.LlmAnswer("Duplicate payments are rejected due to idempotency key reuse.", false, "claude-opus-5", "anthropic"));

        RagQueryResponse response = ragService.query(new RagQueryRequest("Why was a payment rejected as duplicate?", null, null));

        assertThat(response.status()).isEqualTo(RagQueryStatus.SUCCESS);
        assertThat(response.answer()).isEqualTo("Duplicate payments are rejected due to idempotency key reuse.");
        assertThat(response.sources()).hasSize(1);
        assertThat(response.sources().get(0).chunkId()).isEqualTo("c1");
        assertThat(response.metadata().contextChunksUsed()).isEqualTo(1);

        verify(promptServiceClient).render(anyString(), anyString(), org.mockito.ArgumentMatchers.contains("idempotency"), eq("Why was a payment rejected as duplicate?"), any());
        verify(llmServiceClient).generate(anyString(), eq("rendered prompt text"), any());
    }

    @Test
    void query_llmRefuses_returnsRefusedStatusWithEmptySources() {
        when(embeddingServiceClient.embed(anyString(), anyString(), anyString(), any())).thenReturn(List.of(0.1f, 0.2f));
        when(vectorServiceClient.search(anyString(), anyList(), anyString(), anyString(), anyInt(), any(), any()))
                .thenReturn(List.of(chunk("c1", "some relevant content", 0.9)));
        when(promptServiceClient.render(anyString(), anyString(), anyString(), anyString(), any())).thenReturn("rendered prompt text");
        when(llmServiceClient.generate(anyString(), anyString(), any()))
                .thenReturn(new LlmServiceClient.LlmAnswer("", true, "claude-opus-5", "anthropic"));

        RagQueryResponse response = ragService.query(new RagQueryRequest("a valid question", null, null));

        assertThat(response.status()).isEqualTo(RagQueryStatus.REFUSED);
        assertThat(response.sources()).isEmpty();
        assertThat(response.answer()).isNotBlank();
    }

    @Test
    void query_promptServiceFails_propagatesWithoutCallingLlm() {
        when(embeddingServiceClient.embed(anyString(), anyString(), anyString(), any())).thenReturn(List.of(0.1f, 0.2f));
        when(vectorServiceClient.search(anyString(), anyList(), anyString(), anyString(), anyInt(), any(), any()))
                .thenReturn(List.of(chunk("c1", "relevant content", 0.9)));
        when(promptServiceClient.render(anyString(), anyString(), anyString(), anyString(), any()))
                .thenThrow(RagException.promptServiceUnavailable("down", true));

        assertThatThrownBy(() -> ragService.query(new RagQueryRequest("a valid question", null, null)))
                .isInstanceOf(RagException.class)
                .satisfies(ex -> assertThat(((RagException) ex).getErrorCode()).isEqualTo(RagErrorCodes.PROMPT_SERVICE_UNAVAILABLE));

        verify(llmServiceClient, never()).generate(anyString(), anyString(), any());
    }

    @Test
    void query_llmServiceTimesOut_propagatesLlmTimeoutErrorCode() {
        when(embeddingServiceClient.embed(anyString(), anyString(), anyString(), any())).thenReturn(List.of(0.1f, 0.2f));
        when(vectorServiceClient.search(anyString(), anyList(), anyString(), anyString(), anyInt(), any(), any()))
                .thenReturn(List.of(chunk("c1", "relevant content", 0.9)));
        when(promptServiceClient.render(anyString(), anyString(), anyString(), anyString(), any())).thenReturn("rendered prompt text");
        when(llmServiceClient.generate(anyString(), anyString(), any())).thenThrow(RagException.llmTimeout("provider timed out"));

        assertThatThrownBy(() -> ragService.query(new RagQueryRequest("a valid question", null, null)))
                .isInstanceOf(RagException.class)
                .satisfies(ex -> assertThat(((RagException) ex).getErrorCode()).isEqualTo(RagErrorCodes.LLM_TIMEOUT));
    }
}
