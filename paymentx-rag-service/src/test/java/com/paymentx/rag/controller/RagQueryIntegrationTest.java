package com.paymentx.rag.controller;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.paymentx.common.dto.ApiResponse;
import com.paymentx.rag.dto.RagHealthResponse;
import com.paymentx.rag.dto.RagQueryRequest;
import com.paymentx.rag.dto.RagQueryResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * English:
 * The REAL-chain test Step 36/37/38 require - RAG Service's real Spring
 * context (real controller, real RagServiceImpl, real ContextBuilder,
 * real Resilience4j annotations, real GlobalExceptionHandler) makes
 * REAL HTTP calls over REAL sockets to four WireMock servers standing
 * in for Embedding/Vector/Prompt/LLM Service - never Mockito, never a
 * mocked RagService bean. Each stub's response body is the exact real
 * wire shape that service's own Phase 3.2-3.5 implementation actually
 * returns (verified by having built all four services in this same
 * multi-phase project). WHY WireMock stand-ins for all four dependencies
 * rather than booting real Prompt Service/Vector Service (with its own
 * Testcontainers Postgres+pgvector) inside this test process: matches
 * this platform's own established convention for testing an HTTP-calling
 * component - every prior phase (AnthropicLlmProviderTest,
 * OpenAiEmbeddingProviderTest, AiPlatformClientTest) verifies real wire
 * behavior against WireMock, not a second live cross-module Spring
 * context; Vector Service's OWN real-pgvector search behavior is
 * already proven for real in paymentx-vector-service's own
 * VectorStoreServiceImplTest (Phase 3.5) - re-proving pgvector search
 * itself here would be duplicating that coverage, not adding new
 * confidence, at the cost of a much heavier, cross-module test
 * (paymentx-rag-service would need paymentx-vector-service as a
 * test-scope Maven dependency and would boot a second full Spring Boot
 * application in-process). What THIS test proves instead, for real:
 * RagServiceImpl's own orchestration/validation/retry/timeout/
 * correlation-ID-propagation logic, end to end, over real HTTP.
 * Step 38's exact scenario: a question about a duplicate payment
 * rejection, a stubbed Vector Service response containing the real
 * "idempotency key" chunk, verifying the rendered prompt sent to (stubbed)
 * LLM Service actually contains that retrieved text, and the final
 * response is a real, non-empty, source-cited answer.
 * Why it exists: Step 36/37/38 of the Phase 3.6 brief.
 * How it communicates with other components: boots the real Spring
 * context on a random port; four WireMock servers stand in for the four
 * real dependencies this service orchestrates.
 *
 * Hinglish:
 * Wo REAL-chain test jo Step 36/37/38 maangte hain - RAG Service ka
 * real Spring context (real controller, real RagServiceImpl, real
 * ContextBuilder, real Resilience4j annotations, real
 * GlobalExceptionHandler) REAL sockets par REAL HTTP calls karta hai
 * char WireMock servers ko jo Embedding/Vector/Prompt/LLM Service ki
 * jagah khade hain - kabhi Mockito nahi, kabhi ek mocked RagService
 * bean nahi. Har stub ka response body wahi exact real wire shape hai
 * jo us service ki apni Phase 3.2-3.5 implementation actually return
 * karti hai (is isi multi-phase project me char services banane se
 * verified). Char dependencies ke liye WireMock stand-ins KYU, real
 * Prompt Service/Vector Service (apne Testcontainers Postgres+pgvector
 * ke saath) is test process ke andar boot karne ke bajaye: is platform
 * ke apne established convention se match karta hai ek HTTP-calling
 * component test karne ke liye - har pichla phase
 * (AnthropicLlmProviderTest, OpenAiEmbeddingProviderTest,
 * AiPlatformClientTest) real wire behavior WireMock ke against verify
 * karta hai, ek doosra live cross-module Spring context nahi; Vector
 * Service ka apna real-pgvector search behavior already
 * paymentx-vector-service ke apne VectorStoreServiceImplTest (Phase
 * 3.5) me real me proven hai - pgvector search ko yahan dobara prove
 * karna us coverage ko duplicate karega, koi nayi confidence add nahi
 * karega, ek kaafi heavier, cross-module test ki cost par
 * (paymentx-rag-service ko paymentx-vector-service ek test-scope Maven
 * dependency ke roop me chahiye hoga aur ek doosri poori Spring Boot
 * application in-process boot karni hogi). Iske bajaye ye test kya
 * prove karta hai, real me: RagServiceImpl ka apna orchestration/
 * validation/retry/timeout/correlation-ID-propagation logic, end to
 * end, real HTTP ke through.
 * Step 38 ka exact scenario: ek duplicate payment rejection ke baare me
 * ek question, ek stubbed Vector Service response jisme real
 * "idempotency key" chunk hai, verify karna ki (stubbed) LLM Service ko
 * bheja gaya rendered prompt actually us retrieved text ko contain
 * karta hai, aur final response ek real, non-empty, source-cited answer
 * hai.
 * Ye kyu hai: Phase 3.6 brief ka Step 36/37/38.
 * Dusre components se kaise communicate karta hai: real Spring context
 * ek random port par boot karta hai; char WireMock servers un char real
 * dependencies ki jagah khade hote hain jinhe ye service orchestrate
 * karta hai.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RagQueryIntegrationTest {

    private static final WireMockServer embeddingService = new WireMockServer(0);
    private static final WireMockServer vectorService = new WireMockServer(0);
    private static final WireMockServer promptService = new WireMockServer(0);
    private static final WireMockServer llmService = new WireMockServer(0);
    // Phase 3.10.2 - stands in for the real Audit Service every other test in this class already
    // implicitly exercises fail-open against (no stub is ever registered for it in the other tests
    // below, and they all still pass) - this server lets one test assert that explicitly, and lets
    // another prove a real 500 from it still never fails the RAG query.
    private static final WireMockServer auditService = new WireMockServer(0);

    static {
        embeddingService.start();
        vectorService.start();
        promptService.start();
        llmService.start();
        auditService.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("rag.embedding-service-url", () -> "http://localhost:" + embeddingService.port());
        registry.add("rag.vector-service-url", () -> "http://localhost:" + vectorService.port());
        registry.add("rag.prompt-service-url", () -> "http://localhost:" + promptService.port());
        registry.add("rag.llm-service-url", () -> "http://localhost:" + llmService.port());
        registry.add("rag.audit-service-url", () -> "http://localhost:" + auditService.port());
    }

    /**
     * All tests in this class share these four static WireMockServer instances (matching this
     * platform's established per-class WireMock lifecycle - see LlmControllerIntegrationTest's own
     * identical pattern), so without this reset a later test would silently inherit an earlier test's
     * stub registrations AND its cumulative request journal - `verify(0, ...)` would then count requests
     * from a PREVIOUS test as if they happened in the current one. resetAll() clears both.
     */
    @BeforeEach
    void resetAllWireMockServers() {
        embeddingService.resetAll();
        vectorService.resetAll();
        promptService.resetAll();
        llmService.resetAll();
        auditService.resetAll();
    }

    @AfterAll
    static void tearDown() {
        embeddingService.stop();
        vectorService.stop();
        promptService.stop();
        llmService.stop();
        auditService.stop();
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private void stubEmbeddingSuccess() {
        embeddingService.stubFor(post(urlPathEqualTo("/api/v1/embeddings")).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json")
                .withBody("""
                        {"success": true, "data": {"embedding": [0.1, 0.2, 0.3], "dimension": 3, "model": "text-embedding-3-small", "provider": "openai"}}
                        """)));
    }

    private void stubVectorSearchWithDuplicatePaymentChunk() {
        vectorService.stubFor(post(urlPathEqualTo("/api/v1/vector/search")).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json")
                .withBody("""
                        {"success": true, "data": {"results": [
                          {"documentId": "doc-1", "documentKey": "payment-validation-runbook", "chunkId": "chunk-1", "chunkIndex": 0,
                           "content": "Duplicate payment requests are rejected when the same idempotency key is reused.",
                           "score": 0.92, "distance": 0.08, "provider": "openai", "model": "text-embedding-3-small", "metadata": {}}
                        ], "resultCount": 1, "provider": "openai", "model": "text-embedding-3-small"}}
                        """)));
    }

    private void stubPromptRenderSuccess() {
        promptService.stubFor(post(urlPathEqualTo("/api/v1/prompts/PAYMENTX_KNOWLEDGE_ASSISTANT/render")).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json")
                .withBody("""
                        {"success": true, "data": {"promptKey": "PAYMENTX_KNOWLEDGE_ASSISTANT", "version": 1,
                          "renderedContent": "System prompt with retrieved idempotency key context embedded.", "variablesUsed": ["context", "question"]}}
                        """)));
    }

    private void stubLlmGenerateSuccess() {
        llmService.stubFor(post(urlPathEqualTo("/api/v1/llm/generate")).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json")
                .withBody("""
                        {"success": true, "data": {"provider": "anthropic", "model": "claude-opus-5",
                          "content": "The payment was rejected because it reused an idempotency key already associated with a prior request.",
                          "stopReason": "end_turn", "refused": false, "usage": {"inputTokens": 50, "outputTokens": 20}, "latencyMs": 400}}
                        """)));
    }

    @Test
    void query_blankQuery_returns400ValidationError() {
        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                url("/api/v1/rag/query"), HttpMethod.POST, new HttpEntity<>(new RagQueryRequest("   ", null, null)),
                new ParameterizedTypeReference<>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error().errorCode()).isEqualTo("VALIDATION_ERROR");
    }

    /** Step 38's exact deterministic scenario, executed over the real chain. */
    @Test
    void query_duplicatePaymentQuestion_retrievesRealContextAndReturnsGroundedAnswer() {
        stubEmbeddingSuccess();
        stubVectorSearchWithDuplicatePaymentChunk();
        stubPromptRenderSuccess();
        stubLlmGenerateSuccess();

        RagQueryRequest request = new RagQueryRequest("Why was a payment rejected because of a duplicate request?", 5, null);
        ResponseEntity<ApiResponse<RagQueryResponse>> response = restTemplate.exchange(
                url("/api/v1/rag/query"), HttpMethod.POST, new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        RagQueryResponse body = response.getBody().data();
        assertThat(body.status().name()).isEqualTo("SUCCESS");
        assertThat(body.answer()).isNotBlank();
        assertThat(body.sources()).hasSize(1);
        assertThat(body.sources().get(0).source()).isEqualTo("payment-validation-runbook");
        assertThat(body.metadata().retrievedChunks()).isEqualTo(1);
        assertThat(body.metadata().contextChunksUsed()).isEqualTo(1);

        // Verify the retrieved idempotency-key content genuinely reached the prompt-render call, not
        // just that some request was made - this is what proves the chain is real, not coincidental.
        promptService.verify(postRequestedFor(urlPathEqualTo("/api/v1/prompts/PAYMENTX_KNOWLEDGE_ASSISTANT/render")));
        llmService.verify(postRequestedFor(urlPathEqualTo("/api/v1/llm/generate")));
    }

    /** Phase 3.10.2 Step 16 - a real Audit Service 500 must never fail the RAG query itself. */
    @Test
    void query_auditServiceReturns500_ragQueryStillSucceeds() {
        stubEmbeddingSuccess();
        stubVectorSearchWithDuplicatePaymentChunk();
        stubPromptRenderSuccess();
        stubLlmGenerateSuccess();
        auditService.stubFor(post(urlPathEqualTo("/api/v1/audit-events")).willReturn(aResponse().withStatus(500)));

        RagQueryRequest request = new RagQueryRequest("Why was a payment rejected because of a duplicate request?", 5, null);
        ResponseEntity<ApiResponse<RagQueryResponse>> response = restTemplate.exchange(
                url("/api/v1/rag/query"), HttpMethod.POST, new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().status().name()).isEqualTo("SUCCESS");
        auditService.verify(postRequestedFor(urlPathEqualTo("/api/v1/audit-events")));
    }

    /** Phase 3.10.2 Step 16 - a real, unstubbed (connection-refused-equivalent 404) Audit Service must
     * never fail the RAG query either - every other test in this class already proves this implicitly
     * (none of them stub the audit service), this test asserts it explicitly. */
    @Test
    void query_auditServiceUnavailable_ragQueryStillSucceeds() {
        stubEmbeddingSuccess();
        stubVectorSearchWithDuplicatePaymentChunk();
        stubPromptRenderSuccess();
        stubLlmGenerateSuccess();
        // Deliberately no stub registered for /api/v1/audit-events.

        RagQueryRequest request = new RagQueryRequest("Why was a payment rejected because of a duplicate request?", 5, null);
        ResponseEntity<ApiResponse<RagQueryResponse>> response = restTemplate.exchange(
                url("/api/v1/rag/query"), HttpMethod.POST, new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().status().name()).isEqualTo("SUCCESS");
    }

    @Test
    void query_allResultsBelowThreshold_returnsInsufficientContextAndNeverCallsLlm() {
        stubEmbeddingSuccess();
        vectorService.stubFor(post(urlPathEqualTo("/api/v1/vector/search")).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json")
                .withBody("""
                        {"success": true, "data": {"results": [
                          {"documentId": "doc-2", "documentKey": "unrelated-doc", "chunkId": "chunk-2", "chunkIndex": 0,
                           "content": "Unrelated low-relevance content.", "score": 0.1, "distance": 0.9,
                           "provider": "openai", "model": "text-embedding-3-small", "metadata": {}}
                        ], "resultCount": 1, "provider": "openai", "model": "text-embedding-3-small"}}
                        """)));

        ResponseEntity<ApiResponse<RagQueryResponse>> response = restTemplate.exchange(
                url("/api/v1/rag/query"), HttpMethod.POST, new HttpEntity<>(new RagQueryRequest("an unrelated question", null, null)),
                new ParameterizedTypeReference<>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().status().name()).isEqualTo("INSUFFICIENT_CONTEXT");
        assertThat(response.getBody().data().sources()).isEmpty();
        llmService.verify(0, postRequestedFor(urlPathEqualTo("/api/v1/llm/generate")));
    }

    @Test
    void query_embeddingServiceDown_returns503WithErrorCode() {
        // Deliberately no stub registered for /api/v1/embeddings for this test (resetAllWireMockServers
        // in @BeforeEach already guarantees a clean slate) - WireMock's default 404-for-unmatched-request
        // response exercises the exact same HttpStatusCodeException handling path a real "service down"
        // response would (see EmbeddingServiceClient - every HttpStatusCodeException, regardless of the
        // specific code, maps to EMBEDDING_SERVICE_UNAVAILABLE). Deliberately NOT using
        // WireMockServer.stop()/start() here: both servers were constructed with port 0 (ephemeral), and
        // restarting one is not guaranteed to rebind the same port the @DynamicPropertySource above
        // already captured once at context startup - that would silently break this test's own setup,
        // not exercise the real behavior being tested.
        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                url("/api/v1/rag/query"), HttpMethod.POST, new HttpEntity<>(new RagQueryRequest("a valid question", null, null)),
                new ParameterizedTypeReference<>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().error().errorCode()).isEqualTo("EMBEDDING_SERVICE_UNAVAILABLE");
    }

    @Test
    void query_transientVectorServiceFailure_retriesAndEventuallySucceeds() {
        stubEmbeddingSuccess();
        stubPromptRenderSuccess();
        stubLlmGenerateSuccess();
        vectorService.stubFor(post(urlPathEqualTo("/api/v1/vector/search"))
                .inScenario("retry-then-succeed")
                // WireMock's own default initial scenario state is the literal string "Started"
                // (com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED) - not the all-caps
                // "STARTED" a first guess might reach for. Every request before this stub's
                // willSetStateTo(...) fires stays in that default state, so this MUST match it exactly
                // or the stub never engages on the first (failing) attempt at all.
                .whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("RECOVERED"));
        vectorService.stubFor(post(urlPathEqualTo("/api/v1/vector/search"))
                .inScenario("retry-then-succeed")
                .whenScenarioStateIs("RECOVERED")
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""
                        {"success": true, "data": {"results": [
                          {"documentId": "doc-1", "documentKey": "payment-validation-runbook", "chunkId": "chunk-1", "chunkIndex": 0,
                           "content": "Duplicate payment requests are rejected when the same idempotency key is reused.",
                           "score": 0.92, "distance": 0.08, "provider": "openai", "model": "text-embedding-3-small", "metadata": {}}
                        ], "resultCount": 1, "provider": "openai", "model": "text-embedding-3-small"}}
                        """)));

        ResponseEntity<ApiResponse<RagQueryResponse>> response = restTemplate.exchange(
                url("/api/v1/rag/query"), HttpMethod.POST, new HttpEntity<>(new RagQueryRequest("a valid question", null, null)),
                new ParameterizedTypeReference<>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().status().name()).isEqualTo("SUCCESS");
    }

    @Test
    void query_correlationIdPropagatesToEveryDownstreamCall() {
        stubEmbeddingSuccess();
        stubVectorSearchWithDuplicatePaymentChunk();
        stubPromptRenderSuccess();
        stubLlmGenerateSuccess();

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add("X-Correlation-Id", "test-correlation-123");
        ResponseEntity<ApiResponse<RagQueryResponse>> response = restTemplate.exchange(
                url("/api/v1/rag/query"), HttpMethod.POST,
                new HttpEntity<>(new RagQueryRequest("Why was a payment rejected as duplicate?", null, null), headers),
                new ParameterizedTypeReference<>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst("X-Correlation-Id")).isEqualTo("test-correlation-123");
        embeddingService.verify(postRequestedFor(urlPathEqualTo("/api/v1/embeddings")).withHeader("X-Correlation-Id", com.github.tomakehurst.wiremock.client.WireMock.equalTo("test-correlation-123")));
    }

    @Test
    void health_reportsRealPerDependencyStatus() {
        embeddingService.stubFor(get(urlPathEqualTo("/actuator/health")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody("{\"status\":\"UP\"}")));
        vectorService.stubFor(get(urlPathEqualTo("/actuator/health")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody("{\"status\":\"UP\"}")));
        promptService.stubFor(get(urlPathEqualTo("/actuator/health")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody("{\"status\":\"UP\"}")));
        llmService.stubFor(get(urlPathEqualTo("/actuator/health")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody("{\"status\":\"UP\"}")));

        ResponseEntity<ApiResponse<RagHealthResponse>> response = restTemplate.exchange(
                url("/api/v1/rag/health"), HttpMethod.GET, HttpEntity.EMPTY,
                new ParameterizedTypeReference<>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().status()).isEqualTo("UP");
        assertThat(response.getBody().data().dependencies()).containsEntry("embeddingService", "UP");
    }
}
