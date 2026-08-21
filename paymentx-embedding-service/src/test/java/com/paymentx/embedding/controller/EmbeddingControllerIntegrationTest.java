package com.paymentx.embedding.controller;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.paymentx.common.dto.ApiResponse;
import com.paymentx.embedding.dto.BatchEmbeddingRequest;
import com.paymentx.embedding.dto.BatchEmbeddingResponse;
import com.paymentx.embedding.dto.EmbeddingHealthResponse;
import com.paymentx.embedding.dto.EmbeddingRequest;
import com.paymentx.embedding.dto.EmbeddingResponse;
import org.junit.jupiter.api.AfterAll;
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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * English:
 * A real, end-to-end HTTP test of EmbeddingController against the real
 * Spring context (SecurityConfig, GlobalExceptionHandler, Resilience4j
 * annotations, the works) - matches LLM Service's
 * LlmControllerIntegrationTest's exact pattern: no database, a
 * WireMockServer standing in for api.openai.com is the only external
 * dependency, started before the Spring context via
 * @DynamicPropertySource. What it verifies: blank text and an
 * oversized batch are both rejected with a real 400 before ever
 * reaching EmbeddingServiceImpl; a real successful single-embed call
 * round-trips through OpenAiEmbeddingProvider -> EmbeddingServiceImpl
 * -> EmbeddingController into a real HTTP 200 ApiResponse; a real batch
 * call preserves order; a real provider dimension mismatch maps to a
 * real HTTP 502 with errorCode EMBEDDING_DIMENSION_MISMATCH via
 * GlobalExceptionHandler; GET /health reports CONFIGURED for the
 * test's real (fake but present) api-key.
 * Why it exists: Step 27 of the Phase 3.4 brief - @Valid/
 * @RestControllerAdvice are Spring-managed behavior mocked unit tests
 * never actually exercise.
 * How it communicates with other components: boots the real Spring
 * context on a random port; WireMock stands in for the one real
 * external dependency this service has.
 *
 * Hinglish:
 * EmbeddingController ka ek real, end-to-end HTTP test, real Spring
 * context ke against (SecurityConfig, GlobalExceptionHandler,
 * Resilience4j annotations, sab kuch) - LLM Service ke
 * LlmControllerIntegrationTest ke exact pattern se match karta hai: koi
 * database nahi, ek WireMockServer jo api.openai.com ki jagah khada hai
 * hi ek hi external dependency hai, Spring context se pehle
 * @DynamicPropertySource ke through start hota hai. Ye kya verify karta
 * hai: blank text aur ek oversized batch dono ek real 400 se reject
 * hote hain, EmbeddingServiceImpl tak pahunchne se pehle hi; ek real
 * successful single-embed call OpenAiEmbeddingProvider ->
 * EmbeddingServiceImpl -> EmbeddingController se hote hue ek real HTTP
 * 200 ApiResponse me round-trip karta hai; ek real batch call order
 * preserve karta hai; ek real provider dimension mismatch
 * GlobalExceptionHandler ke through ek real HTTP 502, errorCode
 * EMBEDDING_DIMENSION_MISMATCH ke saath, map hota hai; GET /health
 * test ke real (fake lekin present) api-key ke liye CONFIGURED report
 * karta hai.
 * Ye kyu hai: Phase 3.4 brief ka Step 27 - @Valid/@RestControllerAdvice
 * Spring-managed behavior hai jise mocked unit tests actually kabhi
 * exercise nahi karte.
 * Dusre components se kaise communicate karta hai: real Spring context
 * ek random port par boot karta hai; WireMock is service ki ek hi real
 * external dependency ki jagah khada hota hai.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EmbeddingControllerIntegrationTest {

    private static final WireMockServer wireMockServer = new WireMockServer(0);

    static {
        wireMockServer.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        // Phase 3.4 local-embedding migration: "local" is now the real default (see
        // EmbeddingProperties.provider's javadoc) - this whole test class is specifically about the OpenAI
        // provider's real HTTP behavior against WireMock, so it must opt back into "openai" explicitly
        // rather than silently getting LocalEmbeddingProvider (which would also try a real ONNX model
        // download at context startup - unnecessary and unwanted for this test's actual purpose).
        registry.add("embedding.provider", () -> "openai");
        registry.add("embedding.openai.api-key", () -> "integration-test-key");
        registry.add("embedding.openai.base-url", () -> "http://localhost:" + wireMockServer.port());
        registry.add("embedding.openai.dimension", () -> "3");
    }

    @AfterAll
    static void tearDown() {
        wireMockServer.stop();
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void embed_blankText_returns400ValidationError() {
        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                url("/api/v1/embeddings"), HttpMethod.POST,
                new HttpEntity<>(new EmbeddingRequest("   ", null)),
                new ParameterizedTypeReference<>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().errorCode()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void embed_successfulProviderCall_returns200WithNormalizedVector() {
        wireMockServer.stubFor(post(urlPathEqualTo("/embeddings")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {
                          "object": "list",
                          "data": [{"object": "embedding", "index": 0, "embedding": [0.1, 0.2, 0.3]}],
                          "model": "text-embedding-3-small",
                          "usage": {"prompt_tokens": 5, "total_tokens": 5}
                        }
                        """)));

        ResponseEntity<ApiResponse<EmbeddingResponse>> response = restTemplate.exchange(
                url("/api/v1/embeddings"), HttpMethod.POST,
                new HttpEntity<>(new EmbeddingRequest("Duplicate payment detected", null)),
                new ParameterizedTypeReference<>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().embedding()).containsExactly(0.1f, 0.2f, 0.3f);
        assertThat(response.getBody().data().dimension()).isEqualTo(3);
    }

    @Test
    void embedBatch_successfulProviderCall_returns200WithOrderedItems() {
        wireMockServer.stubFor(post(urlPathEqualTo("/embeddings")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {
                          "object": "list",
                          "data": [
                            {"object": "embedding", "index": 0, "embedding": [0.1, 0.1, 0.1]},
                            {"object": "embedding", "index": 1, "embedding": [0.2, 0.2, 0.2]}
                          ],
                          "model": "text-embedding-3-small",
                          "usage": {"prompt_tokens": 8, "total_tokens": 8}
                        }
                        """)));

        ResponseEntity<ApiResponse<BatchEmbeddingResponse>> response = restTemplate.exchange(
                url("/api/v1/embeddings/batch"), HttpMethod.POST,
                new HttpEntity<>(new BatchEmbeddingRequest(List.of("Payment failed", "Duplicate payment detected"), null)),
                new ParameterizedTypeReference<>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().embeddings()).hasSize(2);
        assertThat(response.getBody().data().embeddings().get(0).embedding()).containsExactly(0.1f, 0.1f, 0.1f);
        assertThat(response.getBody().data().embeddings().get(1).embedding()).containsExactly(0.2f, 0.2f, 0.2f);
    }

    @Test
    void embedBatch_oversizedBatch_returns400ValidationError() {
        List<String> tooMany = java.util.stream.IntStream.range(0, 60).mapToObj(i -> "text " + i).toList();

        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                url("/api/v1/embeddings/batch"), HttpMethod.POST,
                new HttpEntity<>(new BatchEmbeddingRequest(tooMany, null)),
                new ParameterizedTypeReference<>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void embed_providerDimensionMismatch_returns502WithErrorCode() {
        wireMockServer.stubFor(post(urlPathEqualTo("/embeddings")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {
                          "object": "list",
                          "data": [{"object": "embedding", "index": 0, "embedding": [0.1, 0.2]}],
                          "model": "text-embedding-3-small",
                          "usage": {"prompt_tokens": 5, "total_tokens": 5}
                        }
                        """)));

        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                url("/api/v1/embeddings"), HttpMethod.POST,
                new HttpEntity<>(new EmbeddingRequest("some text triggering a dimension mismatch stub", null)),
                new ParameterizedTypeReference<>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().errorCode()).isEqualTo("EMBEDDING_DIMENSION_MISMATCH");
    }

    @Test
    void health_apiKeyConfigured_returnsConfiguredStatus() {
        ResponseEntity<ApiResponse<EmbeddingHealthResponse>> response = restTemplate.exchange(
                url("/api/v1/embeddings/health"), HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().status()).isEqualTo("CONFIGURED");
        assertThat(response.getBody().data().apiKeyPresent()).isTrue();
    }
}
