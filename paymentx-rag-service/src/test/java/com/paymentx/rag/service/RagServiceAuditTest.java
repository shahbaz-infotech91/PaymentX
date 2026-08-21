package com.paymentx.rag.service;

import com.paymentx.rag.audit.RagAuditClient;
import com.paymentx.rag.audit.RagAuditEvent;
import com.paymentx.rag.client.EmbeddingServiceClient;
import com.paymentx.rag.client.LlmServiceClient;
import com.paymentx.rag.client.PromptServiceClient;
import com.paymentx.rag.client.VectorServiceClient;
import com.paymentx.rag.config.RagProperties;
import com.paymentx.rag.context.ContextBuilder;
import com.paymentx.rag.dto.RagQueryRequest;
import com.paymentx.rag.dto.RetrievedChunk;
import com.paymentx.rag.exception.RagException;
import com.paymentx.rag.metrics.RagMetrics;
import com.paymentx.rag.service.impl.RagServiceImpl;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 3.10.2 - Tests RagServiceImpl's integration with the new RAG audit layer: WHAT RagAuditEvent gets
 * built, WHEN it is emitted, and that it never carries the user's query, the LLM's answer, or full chunk
 * content (RagAuditClient's own wire-format/fail-open behavior is covered separately in
 * RagAuditClientTest - this class only verifies RagServiceImpl's side of the integration, matching this
 * platform's established unit-vs-wire test split). RagAuditClient is mocked here on purpose - a real one
 * would only prove RagAuditClient's own fail-open behavior again, not RagServiceImpl's event-construction
 * logic this class is actually responsible for.
 */
@ExtendWith(MockitoExtension.class)
class RagServiceAuditTest {

    private static final String SECRET_SENTINEL = "TEST_SECRET_SHOULD_NEVER_APPEAR_12345";

    @Mock
    private EmbeddingServiceClient embeddingServiceClient;
    @Mock
    private VectorServiceClient vectorServiceClient;
    @Mock
    private PromptServiceClient promptServiceClient;
    @Mock
    private LlmServiceClient llmServiceClient;
    @Mock
    private RagAuditClient auditClient;

    private RagServiceImpl ragService;

    @BeforeEach
    void setUp() {
        RagProperties properties = new RagProperties();
        properties.setMinScore(0.5);
        ContextBuilder contextBuilder = new ContextBuilder(properties);
        RagMetrics metrics = new RagMetrics(new SimpleMeterRegistry());
        ragService = new RagServiceImpl(embeddingServiceClient, vectorServiceClient, promptServiceClient,
                llmServiceClient, auditClient, contextBuilder, properties, metrics, new RestTemplateBuilder());
    }

    private static RetrievedChunk chunk(String chunkId, String content, double score) {
        return new RetrievedChunk("doc-1", chunkId, "doc-key-1", content, score, 1.0 - score);
    }

    private ArgumentCaptor<RagAuditEvent> captureAuditEvent() {
        ArgumentCaptor<RagAuditEvent> captor = ArgumentCaptor.forClass(RagAuditEvent.class);
        verify(auditClient).recordRetrieval(captor.capture());
        return captor;
    }

    @Test
    void query_successfulRetrieval_emitsExactlyOneAuditEventWithCorrectMetadata() {
        when(embeddingServiceClient.embed(anyString(), anyString(), anyString(), any())).thenReturn(List.of(0.1f, 0.2f));
        when(vectorServiceClient.search(anyString(), anyList(), anyString(), anyString(), anyInt(), any(), any()))
                .thenReturn(List.of(chunk("chunk-001", "Duplicate payment requests are rejected.", 0.91),
                        chunk("chunk-002", "Idempotency keys prevent double processing.", 0.6)));
        when(promptServiceClient.render(anyString(), anyString(), anyString(), anyString(), any())).thenReturn("rendered prompt text");
        when(llmServiceClient.generate(anyString(), anyString(), any()))
                .thenReturn(new LlmServiceClient.LlmAnswer("Duplicate payments are rejected by design.", false, "claude-opus-5", "anthropic"));

        ragService.query(new RagQueryRequest("Why was a duplicate payment rejected?", null, null));

        RagAuditEvent event = captureAuditEvent().getValue();
        assertThat(event.correlationId()).isNull(); // no MDC correlation id set outside a real request context
        assertThat(event.requestId()).isNotBlank();
        assertThat(UUID.fromString(event.requestId())).isNotNull(); // must be a real, well-formed UUID
        assertThat(event.retrievalCount()).isEqualTo(2);
        assertThat(event.chunkIds()).containsExactly("chunk-001", "chunk-002");
        assertThat(event.similarityScores()).containsExactly(0.91, 0.6);
        assertThat(event.latencyMs()).isGreaterThanOrEqualTo(0);
        assertThat(event.status()).isEqualTo(RagAuditClient.STATUS_SUCCESS);
    }

    @Test
    void query_noRelevantResults_emitsAuditWithNoResultsStatusAndRealCounts() {
        when(embeddingServiceClient.embed(anyString(), anyString(), anyString(), any())).thenReturn(List.of(0.1f, 0.2f));
        when(vectorServiceClient.search(anyString(), anyList(), anyString(), anyString(), anyInt(), any(), any()))
                .thenReturn(List.of(chunk("chunk-009", "unrelated content", 0.1)));

        ragService.query(new RagQueryRequest("an unrelated question", null, null));

        RagAuditEvent event = captureAuditEvent().getValue();
        assertThat(event.status()).isEqualTo(RagAuditClient.STATUS_NO_RESULTS);
        assertThat(event.retrievalCount()).isEqualTo(1);
        assertThat(event.chunkIds()).containsExactly("chunk-009");
        assertThat(event.similarityScores()).containsExactly(0.1);

        org.mockito.Mockito.verifyNoInteractions(promptServiceClient, llmServiceClient);
    }

    @Test
    void query_retrievalFailsBeforeAnyChunkIsFound_emitsFailureAuditWithNoChunkData() {
        when(embeddingServiceClient.embed(anyString(), anyString(), anyString(), any()))
                .thenThrow(RagException.embeddingServiceUnavailable("down", true));

        assertThatThrownBy(() -> ragService.query(new RagQueryRequest("a valid question", null, null)))
                .isInstanceOf(RagException.class);

        RagAuditEvent event = captureAuditEvent().getValue();
        assertThat(event.status()).isEqualTo(RagAuditClient.STATUS_FAILURE);
        assertThat(event.retrievalCount()).isZero();
        assertThat(event.chunkIds()).isEmpty();
        assertThat(event.similarityScores()).isEmpty();
    }

    /**
     * Phase 3.10.2 - a RagException thrown AFTER a successful retrieval (here: LLM Service itself
     * failing) must never be misreported as a retrieval failure - the retrieval-scoped audit event, with
     * its real SUCCESS status and real chunk data, was already sent before the LLM call ever started.
     * Exactly one audit event total, never two, never overwritten.
     */
    @Test
    void query_postRetrievalLlmFailure_stillReportsTheEarlierSuccessfulRetrievalOnlyOnce() {
        when(embeddingServiceClient.embed(anyString(), anyString(), anyString(), any())).thenReturn(List.of(0.1f, 0.2f));
        when(vectorServiceClient.search(anyString(), anyList(), anyString(), anyString(), anyInt(), any(), any()))
                .thenReturn(List.of(chunk("chunk-001", "relevant content", 0.9)));
        when(promptServiceClient.render(anyString(), anyString(), anyString(), anyString(), any())).thenReturn("rendered prompt text");
        when(llmServiceClient.generate(anyString(), anyString(), any())).thenThrow(RagException.llmTimeout("provider timed out"));

        assertThatThrownBy(() -> ragService.query(new RagQueryRequest("a valid question", null, null)))
                .isInstanceOf(RagException.class);

        // exactly ONE audit event total (captureAuditEvent's verify() itself asserts times(1))
        RagAuditEvent event = captureAuditEvent().getValue();
        assertThat(event.status()).isEqualTo(RagAuditClient.STATUS_SUCCESS);
        assertThat(event.retrievalCount()).isEqualTo(1);
    }

    @Test
    void query_invalidRequest_emitsFailureAuditWithoutEverCallingDownstreamClients() {
        assertThatThrownBy(() -> ragService.query(new RagQueryRequest("   ", null, null)))
                .isInstanceOf(RagException.class);

        // A request that fails validation never attempted retrieval at all - still an honest FAILURE
        // audit record (Step 14 item 10), never silently dropped, and never a fabricated SUCCESS.
        RagAuditEvent event = captureAuditEvent().getValue();
        assertThat(event.status()).isEqualTo(RagAuditClient.STATUS_FAILURE);
        assertThat(event.retrievalCount()).isZero();
        org.mockito.Mockito.verifyNoInteractions(embeddingServiceClient, vectorServiceClient);
    }

    /** Phase 3.10.2 Step 15 - the built RagAuditEvent must never carry the raw query text, the LLM's
     * answer, or full chunk content, even when both are deliberately poisoned with the sentinel. */
    @Test
    void query_sentinelInQueryAndChunkContentAndAnswer_neverReachesTheAuditEvent() {
        when(embeddingServiceClient.embed(anyString(), anyString(), anyString(), any())).thenReturn(List.of(0.1f, 0.2f));
        when(vectorServiceClient.search(anyString(), anyList(), anyString(), anyString(), anyInt(), any(), any()))
                .thenReturn(List.of(chunk("chunk-001", "content containing " + SECRET_SENTINEL, 0.9)));
        when(promptServiceClient.render(anyString(), anyString(), anyString(), anyString(), any())).thenReturn("rendered prompt text");
        when(llmServiceClient.generate(anyString(), anyString(), any()))
                .thenReturn(new LlmServiceClient.LlmAnswer("answer containing " + SECRET_SENTINEL, false, "claude-opus-5", "anthropic"));

        ragService.query(new RagQueryRequest("question containing " + SECRET_SENTINEL, null, null));

        RagAuditEvent event = captureAuditEvent().getValue();
        assertThat(event.toString()).doesNotContain(SECRET_SENTINEL);
        assertThat(event.chunkIds()).containsExactly("chunk-001");
    }
}
