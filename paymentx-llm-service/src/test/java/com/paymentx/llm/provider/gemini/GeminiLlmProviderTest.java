package com.paymentx.llm.provider.gemini;

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
 * Real wire-level tests for GeminiLlmProvider - exactly mirrors
 * AnthropicLlmProviderTest's pattern (same WireMock dependency, same
 * "point the real client's baseUrl at a local WireMockServer" approach,
 * same real HTTP-level verification, never a mocked-RestClient test) so
 * both providers are held to the identical verification bar. Verifies
 * GeminiLlmProvider builds the exact request Gemini's real
 * generateContent endpoint expects and correctly maps real HTTP status
 * codes into the right LlmException, without ever making a real, billed
 * call to Gemini.
 */
class GeminiLlmProviderTest {

    private WireMockServer wireMockServer;
    private GeminiLlmProvider provider;
    private LlmProperties properties;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();

        properties = new LlmProperties();
        properties.getGemini().setApiKey("test-gemini-key");
        properties.getGemini().setModel("gemini-3.7-flash");
        properties.getGemini().setBaseUrl("http://localhost:" + wireMockServer.port());
        properties.getGemini().setDefaultMaxTokens(1024);
        properties.getGemini().setTimeoutSeconds(5);

        provider = new GeminiLlmProvider(properties);
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
    void providerName_returnsGemini() {
        assertThat(provider.providerName()).isEqualTo("gemini");
    }

    @Test
    void generate_missingApiKey_throwsNotConfiguredWithoutCallingProvider() {
        properties.getGemini().setApiKey("");
        GeminiLlmProvider unconfigured = new GeminiLlmProvider(properties);
        unconfigured.init();

        assertThatThrownBy(() -> unconfigured.generate(defaultRequest()))
                .isInstanceOf(LlmException.class)
                .satisfies(ex -> assertThat(((LlmException) ex).getErrorCode()).isEqualTo(LlmErrorCodes.LLM_NOT_CONFIGURED));

        wireMockServer.verify(0, postRequestedFor(urlPathEqualTo("/v1beta/models/gemini-3.7-flash:generateContent")));
    }

    @Test
    void generate_successfulResponse_returnsNormalizedResult_andSendsExpectedRequestShape() {
        wireMockServer.stubFor(post(urlPathEqualTo("/v1beta/models/gemini-3.7-flash:generateContent")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {
                          "candidates": [
                            {
                              "content": {"role": "model", "parts": [{"text": "The dispute concerns a duplicate charge."}]},
                              "finishReason": "STOP"
                            }
                          ],
                          "usageMetadata": {"promptTokenCount": 42, "candidatesTokenCount": 9, "totalTokenCount": 51},
                          "modelVersion": "gemini-3.7-flash"
                        }
                        """)));

        LlmProviderResult result = provider.generate(defaultRequest());

        assertThat(result.provider()).isEqualTo("gemini");
        assertThat(result.model()).isEqualTo("gemini-3.7-flash");
        assertThat(result.content()).isEqualTo("The dispute concerns a duplicate charge.");
        assertThat(result.stopReason()).isEqualTo("STOP");
        assertThat(result.refused()).isFalse();
        assertThat(result.inputTokens()).isEqualTo(42);
        assertThat(result.outputTokens()).isEqualTo(9);

        // Never appears in a URI or query string - only as a header, and never logged.
        wireMockServer.verify(postRequestedFor(urlPathEqualTo("/v1beta/models/gemini-3.7-flash:generateContent"))
                .withHeader("x-goog-api-key", equalTo("test-gemini-key"))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath("$.contents[0].parts[0].text")));
    }

    @Test
    void generate_promptBlockedBySafety_returnsRefusedTrueNotAnException() {
        wireMockServer.stubFor(post(urlPathEqualTo("/v1beta/models/gemini-3.7-flash:generateContent")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {
                          "candidates": [],
                          "promptFeedback": {"blockReason": "SAFETY"},
                          "usageMetadata": {"promptTokenCount": 12, "totalTokenCount": 12}
                        }
                        """)));

        LlmProviderResult result = provider.generate(defaultRequest());

        assertThat(result.refused()).isTrue();
        assertThat(result.stopReason()).isEqualTo("SAFETY");
        assertThat(result.content()).isEmpty();
    }

    @Test
    void generate_finishReasonSafety_returnsRefusedTrueNotAnException() {
        wireMockServer.stubFor(post(urlPathEqualTo("/v1beta/models/gemini-3.7-flash:generateContent")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {
                          "candidates": [{"content": {"role": "model", "parts": []}, "finishReason": "SAFETY"}],
                          "usageMetadata": {"promptTokenCount": 12, "candidatesTokenCount": 0, "totalTokenCount": 12}
                        }
                        """)));

        LlmProviderResult result = provider.generate(defaultRequest());

        assertThat(result.refused()).isTrue();
        assertThat(result.stopReason()).isEqualTo("SAFETY");
    }

    @Test
    void generate_rateLimited_throwsRetryableLlmException() {
        wireMockServer.stubFor(post(urlPathEqualTo("/v1beta/models/gemini-3.7-flash:generateContent")).willReturn(aResponse()
                .withStatus(429)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"error": {"code": 429, "message": "Resource exhausted", "status": "RESOURCE_EXHAUSTED"}}
                        """)));

        assertThatThrownBy(() -> provider.generate(defaultRequest()))
                .isInstanceOf(LlmException.class)
                .satisfies(ex -> {
                    LlmException llmEx = (LlmException) ex;
                    assertThat(llmEx.getErrorCode()).isEqualTo(LlmErrorCodes.LLM_RATE_LIMITED);
                    assertThat(llmEx.isRetryable()).isTrue();
                    assertThat(llmEx.getMessage()).doesNotContain("test-gemini-key");
                });
    }

    @Test
    void generate_unauthorized_throwsNonRetryableLlmException_andNeverLeaksApiKey() {
        wireMockServer.stubFor(post(urlPathEqualTo("/v1beta/models/gemini-3.7-flash:generateContent")).willReturn(aResponse()
                .withStatus(401)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"error": {"code": 401, "message": "API key missing or invalid", "status": "UNAUTHENTICATED"}}
                        """)));

        assertThatThrownBy(() -> provider.generate(defaultRequest()))
                .isInstanceOf(LlmException.class)
                .satisfies(ex -> {
                    LlmException llmEx = (LlmException) ex;
                    assertThat(llmEx.getErrorCode()).isEqualTo(LlmErrorCodes.LLM_CREDENTIALS_REJECTED);
                    assertThat(llmEx.isRetryable()).isFalse();
                    assertThat(llmEx.getMessage()).doesNotContain("test-gemini-key");
                });
    }

    @Test
    void generate_forbidden_throwsNonRetryableCredentialsRejectedException() {
        wireMockServer.stubFor(post(urlPathEqualTo("/v1beta/models/gemini-3.7-flash:generateContent")).willReturn(aResponse()
                .withStatus(403)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"error": {"code": 403, "message": "API key does not have permission", "status": "PERMISSION_DENIED"}}
                        """)));

        assertThatThrownBy(() -> provider.generate(defaultRequest()))
                .isInstanceOf(LlmException.class)
                .satisfies(ex -> {
                    LlmException llmEx = (LlmException) ex;
                    assertThat(llmEx.getErrorCode()).isEqualTo(LlmErrorCodes.LLM_CREDENTIALS_REJECTED);
                    assertThat(llmEx.isRetryable()).isFalse();
                });
    }

    @Test
    void generate_badRequest_throwsNonRetryableInvalidRequestException() {
        wireMockServer.stubFor(post(urlPathEqualTo("/v1beta/models/gemini-3.7-flash:generateContent")).willReturn(aResponse()
                .withStatus(400)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"error": {"code": 400, "message": "Invalid request body", "status": "INVALID_ARGUMENT"}}
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
        wireMockServer.stubFor(post(urlPathEqualTo("/v1beta/models/gemini-3.7-flash:generateContent")).willReturn(aResponse()
                .withStatus(500)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"error": {"code": 500, "message": "Internal server error", "status": "INTERNAL"}}
                        """)));

        assertThatThrownBy(() -> provider.generate(defaultRequest()))
                .isInstanceOf(LlmException.class)
                .satisfies(ex -> {
                    LlmException llmEx = (LlmException) ex;
                    assertThat(llmEx.getErrorCode()).isEqualTo(LlmErrorCodes.LLM_PROVIDER_UNAVAILABLE);
                    assertThat(llmEx.isRetryable()).isTrue();
                });
    }

    @Test
    void generate_connectionTimesOut_throwsRetryableTimeoutException() {
        properties.getGemini().setTimeoutSeconds(1);
        GeminiLlmProvider shortTimeoutProvider = new GeminiLlmProvider(properties);
        shortTimeoutProvider.init();

        wireMockServer.stubFor(post(urlPathEqualTo("/v1beta/models/gemini-3.7-flash:generateContent")).willReturn(aResponse()
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
        wireMockServer.stubFor(post(urlPathEqualTo("/v1beta/models/gemini-3.7-flash:generateContent")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{ this is not valid json")));

        assertThatThrownBy(() -> provider.generate(defaultRequest()))
                .isInstanceOf(LlmException.class);
    }
}
