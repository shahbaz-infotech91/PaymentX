package com.paymentx.llm.provider.groq;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.paymentx.llm.config.LlmProperties;
import com.paymentx.llm.exception.LlmErrorCodes;
import com.paymentx.llm.exception.LlmException;
import com.paymentx.llm.provider.LlmProviderRequest;
import com.paymentx.llm.provider.LlmProviderResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 5 (Multi-Provider LLM Resilience Expansion) - real wire-level tests for GroqLlmProvider,
 * exactly mirroring GeminiLlmProviderTest's/AnthropicLlmProviderTest's pattern (same WireMock
 * dependency, same "point the real client's baseUrl at a local WireMockServer" approach, same
 * real HTTP-level verification, never a mocked-RestClient test) so all three providers are held
 * to the identical verification bar. Verifies GroqLlmProvider builds the exact OpenAI-Chat-
 * Completions-compatible request Groq's real API expects and correctly maps real HTTP status
 * codes into the right LlmException, without ever making a real, billed/quota-consuming call to
 * Groq.
 */
class GroqLlmProviderTest {

    private WireMockServer wireMockServer;
    private GroqLlmProvider provider;
    private LlmProperties properties;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();

        properties = new LlmProperties();
        properties.getGroq().setApiKey("test-groq-key");
        properties.getGroq().setModel("llama-3.3-70b-versatile");
        properties.getGroq().setBaseUrl("http://localhost:" + wireMockServer.port());
        properties.getGroq().setDefaultMaxTokens(1024);
        properties.getGroq().setTimeoutSeconds(5);

        provider = new GroqLlmProvider(properties);
        provider.init();
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    private LlmProviderRequest defaultRequest() {
        return new LlmProviderRequest("Summarize this payment dispute.", null, null, 0, null);
    }

    @Test
    void providerName_returnsGroq() {
        assertThat(provider.providerName()).isEqualTo("groq");
    }

    // ---- 15. API key missing ----

    @Test
    void generate_missingApiKey_throwsNotConfiguredWithoutCallingProvider() {
        properties.getGroq().setApiKey("");
        GroqLlmProvider unconfigured = new GroqLlmProvider(properties);
        unconfigured.init();

        assertThatThrownBy(() -> unconfigured.generate(defaultRequest()))
                .isInstanceOf(LlmException.class)
                .satisfies(ex -> assertThat(((LlmException) ex).getErrorCode()).isEqualTo(LlmErrorCodes.LLM_NOT_CONFIGURED));

        wireMockServer.verify(0, postRequestedFor(urlPathEqualTo("/chat/completions")));
    }

    // ---- 14. New provider model/configuration - real successful call, real request shape ----

    @Test
    void generate_successfulResponse_returnsNormalizedResult_andSendsExpectedRequestShape() {
        wireMockServer.stubFor(post(urlPathEqualTo("/chat/completions")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {
                          "id": "chatcmpl-test",
                          "object": "chat.completion",
                          "model": "llama-3.3-70b-versatile",
                          "choices": [
                            {"index": 0, "message": {"role": "assistant", "content": "The dispute concerns a duplicate charge."}, "finish_reason": "stop"}
                          ],
                          "usage": {"prompt_tokens": 42, "completion_tokens": 9, "total_tokens": 51}
                        }
                        """)));

        LlmProviderResult result = provider.generate(defaultRequest());

        assertThat(result.provider()).isEqualTo("groq");
        assertThat(result.model()).isEqualTo("llama-3.3-70b-versatile");
        assertThat(result.content()).isEqualTo("The dispute concerns a duplicate charge.");
        assertThat(result.stopReason()).isEqualTo("stop");
        assertThat(result.refused()).isFalse();
        assertThat(result.inputTokens()).isEqualTo(42);
        assertThat(result.outputTokens()).isEqualTo(9);

        // Never appears in a URI or query string - only as a header, and never logged.
        wireMockServer.verify(postRequestedFor(urlPathEqualTo("/chat/completions"))
                .withHeader("Authorization", equalTo("Bearer test-groq-key"))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath("$.messages[0].content")));
    }

    @Test
    void generate_contentFilterFinishReason_returnsRefusedTrueNotAnException() {
        wireMockServer.stubFor(post(urlPathEqualTo("/chat/completions")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {
                          "model": "llama-3.3-70b-versatile",
                          "choices": [
                            {"index": 0, "message": {"role": "assistant", "content": ""}, "finish_reason": "content_filter"}
                          ],
                          "usage": {"prompt_tokens": 12, "completion_tokens": 0, "total_tokens": 12}
                        }
                        """)));

        LlmProviderResult result = provider.generate(defaultRequest());

        assertThat(result.refused()).isTrue();
        assertThat(result.stopReason()).isEqualTo("content_filter");
    }

    // ---- 16. New provider rate-limit response ----

    @Test
    void generate_rateLimited_throwsRetryableLlmException() {
        wireMockServer.stubFor(post(urlPathEqualTo("/chat/completions")).willReturn(aResponse()
                .withStatus(429)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"error": {"message": "Rate limit reached", "type": "rate_limit_error", "code": "rate_limit_exceeded"}}
                        """)));

        assertThatThrownBy(() -> provider.generate(defaultRequest()))
                .isInstanceOf(LlmException.class)
                .satisfies(ex -> {
                    LlmException llmEx = (LlmException) ex;
                    assertThat(llmEx.getErrorCode()).isEqualTo(LlmErrorCodes.LLM_RATE_LIMITED);
                    assertThat(llmEx.isRetryable()).isTrue();
                    assertThat(llmEx.getMessage()).doesNotContain("test-groq-key");
                });
    }

    @Test
    void generate_unauthorized_throwsNonRetryableLlmException_andNeverLeaksApiKey() {
        wireMockServer.stubFor(post(urlPathEqualTo("/chat/completions")).willReturn(aResponse()
                .withStatus(401)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"error": {"message": "Invalid API Key", "type": "invalid_request_error", "code": "invalid_api_key"}}
                        """)));

        assertThatThrownBy(() -> provider.generate(defaultRequest()))
                .isInstanceOf(LlmException.class)
                .satisfies(ex -> {
                    LlmException llmEx = (LlmException) ex;
                    assertThat(llmEx.getErrorCode()).isEqualTo(LlmErrorCodes.LLM_CREDENTIALS_REJECTED);
                    assertThat(llmEx.isRetryable()).isFalse();
                    assertThat(llmEx.getMessage()).doesNotContain("test-groq-key");
                });
    }

    @Test
    void generate_badRequest_throwsNonRetryableInvalidRequestException() {
        wireMockServer.stubFor(post(urlPathEqualTo("/chat/completions")).willReturn(aResponse()
                .withStatus(400)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"error": {"message": "Invalid request", "type": "invalid_request_error"}}
                        """)));

        assertThatThrownBy(() -> provider.generate(defaultRequest()))
                .isInstanceOf(LlmException.class)
                .satisfies(ex -> {
                    LlmException llmEx = (LlmException) ex;
                    assertThat(llmEx.getErrorCode()).isEqualTo(LlmErrorCodes.LLM_INVALID_REQUEST);
                    assertThat(llmEx.isRetryable()).isFalse();
                });
    }

    @Test
    void generate_serverError_throwsRetryableProviderUnavailableException() {
        wireMockServer.stubFor(post(urlPathEqualTo("/chat/completions")).willReturn(aResponse()
                .withStatus(503)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"error": {"message": "Service unavailable", "type": "server_error"}}
                        """)));

        assertThatThrownBy(() -> provider.generate(defaultRequest()))
                .isInstanceOf(LlmException.class)
                .satisfies(ex -> {
                    LlmException llmEx = (LlmException) ex;
                    assertThat(llmEx.getErrorCode()).isEqualTo(LlmErrorCodes.LLM_PROVIDER_UNAVAILABLE);
                    assertThat(llmEx.isRetryable()).isTrue();
                });
    }

    // ---- 17. New provider timeout ----

    @Test
    void generate_connectionTimesOut_throwsRetryableTimeoutException() {
        properties.getGroq().setTimeoutSeconds(1);
        GroqLlmProvider shortTimeoutProvider = new GroqLlmProvider(properties);
        shortTimeoutProvider.init();

        wireMockServer.stubFor(post(urlPathEqualTo("/chat/completions")).willReturn(aResponse()
                .withStatus(200)
                .withFixedDelay(3000)
                .withBody("{}")));

        assertThatThrownBy(() -> shortTimeoutProvider.generate(defaultRequest()))
                .isInstanceOf(LlmException.class)
                .satisfies(ex -> {
                    LlmException llmEx = (LlmException) ex;
                    assertThat(llmEx.getErrorCode()).isEqualTo(LlmErrorCodes.LLM_PROVIDER_TIMEOUT);
                    assertThat(llmEx.isRetryable()).isTrue();
                });
    }

    @Test
    void generate_malformedResponseBody_throwsLlmException() {
        wireMockServer.stubFor(post(urlPathEqualTo("/chat/completions")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{ this is not valid json")));

        assertThatThrownBy(() -> provider.generate(defaultRequest()))
                .isInstanceOf(LlmException.class);
    }

    @Test
    void generate_emptyChoicesArray_throwsResponseInvalidException() {
        wireMockServer.stubFor(post(urlPathEqualTo("/chat/completions")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"model": "llama-3.3-70b-versatile", "choices": [], "usage": {"prompt_tokens": 5, "completion_tokens": 0, "total_tokens": 5}}
                        """)));

        assertThatThrownBy(() -> provider.generate(defaultRequest()))
                .isInstanceOf(LlmException.class)
                .satisfies(ex -> assertThat(((LlmException) ex).getErrorCode()).isEqualTo(LlmErrorCodes.LLM_RESPONSE_INVALID));
    }
}
