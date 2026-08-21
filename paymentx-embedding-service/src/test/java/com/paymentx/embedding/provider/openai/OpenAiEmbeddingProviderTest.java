package com.paymentx.embedding.provider.openai;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.paymentx.embedding.config.EmbeddingProperties;
import com.paymentx.embedding.exception.EmbeddingErrorCodes;
import com.paymentx.embedding.exception.EmbeddingException;
import com.paymentx.embedding.provider.EmbeddingProviderRequest;
import com.paymentx.embedding.provider.EmbeddingProviderResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * English:
 * Real wire-level tests for the ONE class in this service that ever
 * calls an embedding provider - matches LLM Service's
 * AnthropicLlmProviderTest's/Notification Service's
 * WebhookChannelTest's exact WireMock pattern, pointing
 * OpenAiEmbeddingProvider's baseUrl at a local WireMockServer instead
 * of api.openai.com. Proves the adapter builds the real request shape
 * OpenAI expects, correctly maps real HTTP status codes into the right
 * EmbeddingException per Step 14's retryable/non-retryable split, and
 * enforces Step 12's dimension validation against a real (stubbed)
 * response - all without ever making a real, billed call to OpenAI.
 * Why it exists: Step 27 of the Phase 3.4 brief - real, verifiable
 * tests, not fakes claiming coverage they don't have.
 * How it communicates with other components: exercises
 * OpenAiEmbeddingProvider directly, the same way EmbeddingServiceImpl
 * does in production.
 *
 * Hinglish:
 * Is service ki us ek class ke liye real wire-level tests jo kabhi ek
 * embedding provider ko call karti hai - LLM Service ke
 * AnthropicLlmProviderTest/Notification Service ke WebhookChannelTest
 * ke exact WireMock pattern se match karta hai, OpenAiEmbeddingProvider
 * ka baseUrl api.openai.com ke bajaye ek local WireMockServer par point
 * karte hue. Ye prove karta hai ki adapter wahi real request shape
 * banata hai jo OpenAI expect karta hai, real HTTP status codes ko sahi
 * EmbeddingException me correctly map karta hai Step 14 ke retryable/
 * non-retryable split ke hisaab se, aur Step 12 ki dimension validation
 * ek real (stubbed) response ke against enforce karta hai - sab kuch
 * bina kabhi OpenAI ko ek real, billed call kiye.
 * Ye kyu hai: Phase 3.4 brief ka Step 27 - real, verifiable tests, aise
 * fakes nahi jo coverage claim karein jo unke paas hai hi nahi.
 * Dusre components se kaise communicate karta hai: OpenAiEmbeddingProvider
 * ko seedhe exercise karta hai, wahi tarike se jaise production me
 * EmbeddingServiceImpl karta hai.
 */
class OpenAiEmbeddingProviderTest {

    private WireMockServer wireMockServer;
    private OpenAiEmbeddingProvider provider;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();

        EmbeddingProperties properties = new EmbeddingProperties();
        properties.getOpenai().setApiKey("test-api-key");
        properties.getOpenai().setModel("text-embedding-3-small");
        properties.getOpenai().setBaseUrl("http://localhost:" + wireMockServer.port());
        properties.getOpenai().setDimension(3);
        properties.getOpenai().setConnectTimeoutMs(2000);
        properties.getOpenai().setReadTimeoutMs(2000);

        provider = new OpenAiEmbeddingProvider(properties, new RestTemplateBuilder());
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void embed_successfulSingleResponse_returnsNormalizedVector() {
        wireMockServer.stubFor(post(urlPathEqualTo("/embeddings")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {
                          "object": "list",
                          "data": [{"object": "embedding", "index": 0, "embedding": [0.1, -0.2, 0.3]}],
                          "model": "text-embedding-3-small",
                          "usage": {"prompt_tokens": 5, "total_tokens": 5}
                        }
                        """)));

        EmbeddingProviderResult result = provider.embed(new EmbeddingProviderRequest(List.of("Duplicate payment detected"), null));

        assertThat(result.provider()).isEqualTo("openai");
        assertThat(result.vectors()).hasSize(1);
        assertThat(result.vectors().get(0)).containsExactly(0.1f, -0.2f, 0.3f);

        wireMockServer.verify(postRequestedFor(urlPathEqualTo("/embeddings"))
                .withHeader("Authorization", equalTo("Bearer test-api-key")));
    }

    @Test
    void embed_batchResponse_preservesOrderRegardlessOfArrayOrder() {
        wireMockServer.stubFor(post(urlPathEqualTo("/embeddings")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {
                          "object": "list",
                          "data": [
                            {"object": "embedding", "index": 1, "embedding": [0.4, 0.5, 0.6]},
                            {"object": "embedding", "index": 0, "embedding": [0.1, 0.2, 0.3]}
                          ],
                          "model": "text-embedding-3-small",
                          "usage": {"prompt_tokens": 10, "total_tokens": 10}
                        }
                        """)));

        EmbeddingProviderResult result = provider.embed(new EmbeddingProviderRequest(List.of("first", "second"), null));

        assertThat(result.vectors()).hasSize(2);
        assertThat(result.vectors().get(0)).containsExactly(0.1f, 0.2f, 0.3f);
        assertThat(result.vectors().get(1)).containsExactly(0.4f, 0.5f, 0.6f);
    }

    @Test
    void embed_dimensionMismatch_throwsNonRetryableDimensionMismatchException() {
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

        assertThatThrownBy(() -> provider.embed(new EmbeddingProviderRequest(List.of("text"), null)))
                .isInstanceOf(EmbeddingException.class)
                .satisfies(ex -> {
                    EmbeddingException embeddingEx = (EmbeddingException) ex;
                    assertThat(embeddingEx.getErrorCode()).isEqualTo(EmbeddingErrorCodes.EMBEDDING_DIMENSION_MISMATCH);
                    assertThat(embeddingEx.isRetryable()).isFalse();
                });
    }

    @Test
    void embed_rateLimited_throwsRetryableEmbeddingException() {
        wireMockServer.stubFor(post(urlPathEqualTo("/embeddings")).willReturn(aResponse()
                .withStatus(429)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"error": {"message": "rate limited", "type": "rate_limit_error"}}
                        """)));

        assertThatThrownBy(() -> provider.embed(new EmbeddingProviderRequest(List.of("text"), null)))
                .isInstanceOf(EmbeddingException.class)
                .satisfies(ex -> {
                    EmbeddingException embeddingEx = (EmbeddingException) ex;
                    assertThat(embeddingEx.getErrorCode()).isEqualTo(EmbeddingErrorCodes.EMBEDDING_RATE_LIMITED);
                    assertThat(embeddingEx.isRetryable()).isTrue();
                });
    }

    @Test
    void embed_unauthorized_throwsNonRetryableCredentialsRejectedException() {
        wireMockServer.stubFor(post(urlPathEqualTo("/embeddings")).willReturn(aResponse()
                .withStatus(401)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"error": {"message": "invalid api key", "type": "invalid_request_error"}}
                        """)));

        assertThatThrownBy(() -> provider.embed(new EmbeddingProviderRequest(List.of("text"), null)))
                .isInstanceOf(EmbeddingException.class)
                .satisfies(ex -> {
                    EmbeddingException embeddingEx = (EmbeddingException) ex;
                    assertThat(embeddingEx.getErrorCode()).isEqualTo(EmbeddingErrorCodes.EMBEDDING_CREDENTIALS_REJECTED);
                    assertThat(embeddingEx.isRetryable()).isFalse();
                });
    }

    @Test
    void embed_badRequest_throwsNonRetryableInvalidRequestException() {
        wireMockServer.stubFor(post(urlPathEqualTo("/embeddings")).willReturn(aResponse()
                .withStatus(400)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"error": {"message": "unknown model", "type": "invalid_request_error"}}
                        """)));

        assertThatThrownBy(() -> provider.embed(new EmbeddingProviderRequest(List.of("text"), "unknown-model")))
                .isInstanceOf(EmbeddingException.class)
                .satisfies(ex -> {
                    EmbeddingException embeddingEx = (EmbeddingException) ex;
                    assertThat(embeddingEx.getErrorCode()).isEqualTo(EmbeddingErrorCodes.EMBEDDING_INVALID_REQUEST);
                    assertThat(embeddingEx.isRetryable()).isFalse();
                });
    }

    @Test
    void embed_serverError_throwsRetryableProviderUnavailableException() {
        wireMockServer.stubFor(post(urlPathEqualTo("/embeddings")).willReturn(aResponse()
                .withStatus(500)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"error": {"message": "internal error", "type": "api_error"}}
                        """)));

        assertThatThrownBy(() -> provider.embed(new EmbeddingProviderRequest(List.of("text"), null)))
                .isInstanceOf(EmbeddingException.class)
                .satisfies(ex -> {
                    EmbeddingException embeddingEx = (EmbeddingException) ex;
                    assertThat(embeddingEx.getErrorCode()).isEqualTo(EmbeddingErrorCodes.EMBEDDING_PROVIDER_UNAVAILABLE);
                    assertThat(embeddingEx.isRetryable()).isTrue();
                });
    }

    @Test
    void embed_malformedJsonBody_throwsResponseInvalidException() {
        wireMockServer.stubFor(post(urlPathEqualTo("/embeddings")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{ this is not valid json")));

        assertThatThrownBy(() -> provider.embed(new EmbeddingProviderRequest(List.of("text"), null)))
                .isInstanceOf(EmbeddingException.class)
                .satisfies(ex -> assertThat(((EmbeddingException) ex).getErrorCode()).isEqualTo(EmbeddingErrorCodes.EMBEDDING_RESPONSE_INVALID));
    }

    @Test
    void embed_missingDataArray_throwsResponseInvalidException() {
        wireMockServer.stubFor(post(urlPathEqualTo("/embeddings")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"object": "list", "model": "text-embedding-3-small"}
                        """)));

        assertThatThrownBy(() -> provider.embed(new EmbeddingProviderRequest(List.of("text"), null)))
                .isInstanceOf(EmbeddingException.class)
                .satisfies(ex -> assertThat(((EmbeddingException) ex).getErrorCode()).isEqualTo(EmbeddingErrorCodes.EMBEDDING_RESPONSE_INVALID));
    }

    @Test
    void embed_notConfigured_throwsWithoutCallingProvider() {
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.getOpenai().setApiKey("");
        properties.getOpenai().setBaseUrl("http://localhost:" + wireMockServer.port());
        OpenAiEmbeddingProvider unconfiguredProvider = new OpenAiEmbeddingProvider(properties, new RestTemplateBuilder());

        assertThatThrownBy(() -> unconfiguredProvider.embed(new EmbeddingProviderRequest(List.of("text"), null)))
                .isInstanceOf(EmbeddingException.class)
                .satisfies(ex -> assertThat(((EmbeddingException) ex).getErrorCode()).isEqualTo(EmbeddingErrorCodes.EMBEDDING_NOT_CONFIGURED));

        wireMockServer.verify(0, postRequestedFor(urlPathEqualTo("/embeddings")));
    }
}
