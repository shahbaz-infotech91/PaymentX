package com.paymentx.llm.provider.openai;

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
 * Phase 5 (OpenAI last-resort paid fallback) - real wire-level tests for OpenAiLlmProvider,
 * exactly mirroring GroqLlmProviderTest's pattern (same WireMock dependency, same "point the real
 * client's baseUrl at a local WireMockServer" approach, same real HTTP-level verification, never
 * a mocked-RestClient test) so this fourth provider is held to the identical verification bar as
 * the other three.
 *
 * CRITICAL: every test in this file points OpenAiLlmProvider at a local WireMockServer, never
 * https://api.openai.com - this file makes ZERO real, billed OpenAI API calls, in line with this
 * phase's explicit cost-protection requirement (a real OPENAI_API_KEY exists in this
 * environment's Windows user environment variables, but it is never read or exercised by
 * anything in this file).
 */
class OpenAiLlmProviderTest {

    private WireMockServer wireMockServer;
    private OpenAiLlmProvider provider;
    private LlmProperties properties;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();

        properties = new LlmProperties();
        properties.getOpenai().setApiKey("test-openai-key");
        properties.getOpenai().setModel("gpt-5.4-nano");
        properties.getOpenai().setBaseUrl("http://localhost:" + wireMockServer.port());
        properties.getOpenai().setDefaultMaxTokens(1024);
        properties.getOpenai().setTimeoutSeconds(5);

        provider = new OpenAiLlmProvider(properties);
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
    void providerName_returnsOpenAi() {
        assertThat(provider.providerName()).isEqualTo("openai");
    }

    // ---- 16. Missing OPENAI_API_KEY configuration ----

    @Test
    void generate_missingApiKey_throwsNotConfiguredWithoutCallingProvider() {
        properties.getOpenai().setApiKey("");
        OpenAiLlmProvider unconfigured = new OpenAiLlmProvider(properties);
        unconfigured.init();

        assertThatThrownBy(() -> unconfigured.generate(defaultRequest()))
                .isInstanceOf(LlmException.class)
                .satisfies(ex -> assertThat(((LlmException) ex).getErrorCode()).isEqualTo(LlmErrorCodes.LLM_NOT_CONFIGURED));

        wireMockServer.verify(0, postRequestedFor(urlPathEqualTo("/chat/completions")));
    }

    // ---- 1. OpenAI provider success using mock response ----

    @Test
    void generate_successfulResponse_returnsNormalizedResult_andSendsExpectedRequestShape() {
        wireMockServer.stubFor(post(urlPathEqualTo("/chat/completions")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {
                          "id": "chatcmpl-test",
                          "object": "chat.completion",
                          "model": "gpt-5.4-nano",
                          "choices": [
                            {"index": 0, "message": {"role": "assistant", "content": "The dispute concerns a duplicate charge."}, "finish_reason": "stop"}
                          ],
                          "usage": {"prompt_tokens": 42, "completion_tokens": 9, "total_tokens": 51}
                        }
                        """)));

        LlmProviderResult result = provider.generate(defaultRequest());

        assertThat(result.provider()).isEqualTo("openai");
        assertThat(result.model()).isEqualTo("gpt-5.4-nano");
        assertThat(result.content()).isEqualTo("The dispute concerns a duplicate charge.");
        assertThat(result.stopReason()).isEqualTo("stop");
        assertThat(result.refused()).isFalse();
        assertThat(result.inputTokens()).isEqualTo(42);
        assertThat(result.outputTokens()).isEqualTo(9);

        // Never appears in a URI or query string - only as a header, and never logged.
        wireMockServer.verify(postRequestedFor(urlPathEqualTo("/chat/completions"))
                .withHeader("Authorization", equalTo("Bearer test-openai-key"))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath("$.messages[0].content")));
    }

    @Test
    void generate_contentFilterFinishReason_returnsRefusedTrueNotAnException() {
        wireMockServer.stubFor(post(urlPathEqualTo("/chat/completions")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {
                          "model": "gpt-5.4-nano",
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

    // ---- 2. OpenAI 429 ----

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
                    assertThat(llmEx.getMessage()).doesNotContain("test-openai-key");
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
                    assertThat(llmEx.getMessage()).doesNotContain("test-openai-key");
                });
    }

    // ---- 6. OpenAI non-retryable error ----

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

    // ---- 4. OpenAI 5xx ----

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

    // ---- 3. OpenAI timeout ----

    @Test
    void generate_connectionTimesOut_throwsRetryableTimeoutException() {
        properties.getOpenai().setTimeoutSeconds(1);
        OpenAiLlmProvider shortTimeoutProvider = new OpenAiLlmProvider(properties);
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
                        {"model": "gpt-5.4-nano", "choices": [], "usage": {"prompt_tokens": 5, "completion_tokens": 0, "total_tokens": 5}}
                        """)));

        assertThatThrownBy(() -> provider.generate(defaultRequest()))
                .isInstanceOf(LlmException.class)
                .satisfies(ex -> assertThat(((LlmException) ex).getErrorCode()).isEqualTo(LlmErrorCodes.LLM_RESPONSE_INVALID));
    }

    // ---- Phase 5.2 - OpenAI tool-calling support (mirrors GroqLlmProviderTest's identical cases) ----

    @Test
    void generate_withTools_sendsToolsAndAutoToolChoice() {
        wireMockServer.stubFor(post(urlPathEqualTo("/chat/completions")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {
                          "model": "gpt-5.4-nano",
                          "choices": [
                            {"index": 0, "message": {"role": "assistant", "content": "ok"}, "finish_reason": "stop"}
                          ],
                          "usage": {"prompt_tokens": 10, "completion_tokens": 2, "total_tokens": 12}
                        }
                        """)));

        LlmProviderRequest request = new LlmProviderRequest("What is the payment status distribution?", null, null, 0, null,
                java.util.List.of(java.util.Map.of(
                        "name", "database_statistics",
                        "description", "Retrieve read-only aggregate PaymentX database evidence.",
                        "inputSchema", java.util.Map.of(
                                "type", "object",
                                "properties", java.util.Map.of("queryType", java.util.Map.of("type", "string")),
                                "required", java.util.List.of("queryType")))));

        provider.generate(request);

        wireMockServer.verify(postRequestedFor(urlPathEqualTo("/chat/completions"))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath("$.tool_choice", equalTo("auto")))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath("$.tools[0].type", equalTo("function")))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath("$.tools[0].function.name", equalTo("database_statistics"))));
    }

    @Test
    void generate_withoutTools_omitsToolChoiceAndTools() {
        wireMockServer.stubFor(post(urlPathEqualTo("/chat/completions")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {
                          "model": "gpt-5.4-nano",
                          "choices": [
                            {"index": 0, "message": {"role": "assistant", "content": "ok"}, "finish_reason": "stop"}
                          ],
                          "usage": {"prompt_tokens": 10, "completion_tokens": 2, "total_tokens": 12}
                        }
                        """)));

        provider.generate(defaultRequest());

        String requestBody = wireMockServer.findAll(postRequestedFor(urlPathEqualTo("/chat/completions"))).get(0).getBodyAsString();
        assertThat(requestBody).doesNotContain("tool_choice");
        assertThat(requestBody).doesNotContain("\"tools\"");
    }

    @Test
    void generate_toolCallResponse_returnsCallToolJsonContentForAgentPlanner() {
        wireMockServer.stubFor(post(urlPathEqualTo("/chat/completions")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {
                          "model": "gpt-5.4-nano",
                          "choices": [
                            {
                              "index": 0,
                              "message": {
                                "role": "assistant",
                                "content": null,
                                "tool_calls": [
                                  {"id": "call_1", "type": "function", "function": {"name": "database_statistics", "arguments": "{\\"queryType\\":\\"PAYMENT_STATUS_DISTRIBUTION\\"}"}}
                                ]
                              },
                              "finish_reason": "tool_calls"
                            }
                          ],
                          "usage": {"prompt_tokens": 10, "completion_tokens": 5, "total_tokens": 15}
                        }
                        """)));

        LlmProviderRequest request = new LlmProviderRequest("What is the payment status distribution?", null, null, 0, null,
                java.util.List.of(java.util.Map.of("name", "database_statistics", "description", "d",
                        "inputSchema", java.util.Map.of("type", "object"))));

        LlmProviderResult result = provider.generate(request);

        assertThat(result.content()).isEqualTo(
                "{\"action\": \"CALL_TOOL\", \"reasoning\": \"\", \"tool\": \"database_statistics\", "
                        + "\"arguments\": {\"queryType\":\"PAYMENT_STATUS_DISTRIBUTION\"}}");
        assertThat(result.stopReason()).isEqualTo("tool_calls");
    }
}
