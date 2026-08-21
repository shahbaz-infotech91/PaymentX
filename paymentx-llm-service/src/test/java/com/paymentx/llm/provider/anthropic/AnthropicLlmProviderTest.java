package com.paymentx.llm.provider.anthropic;

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
 * English:
 * Real wire-level tests for the ONE class in this service that ever
 * calls an LLM - reuses WireMock exactly like Notification Service's
 * WebhookChannelTest (an already-established, real test dependency in
 * this platform - org.wiremock:wiremock-standalone, confirmed present
 * in notification-service's/api-gateway's pom.xml before being added
 * here), pointing the real Anthropic SDK client's baseUrl at a local
 * WireMockServer instead of api.anthropic.com. This is deliberately a
 * real HTTP-level test, not a mocked-SDK test - it proves
 * AnthropicLlmProvider builds the request the SDK actually sends and
 * correctly maps the SDK's real typed exceptions (thrown from real
 * HTTP status codes WireMock returns) into the right LlmException per
 * Step 13's retryable/non-retryable split, without ever making a real,
 * billed call to Anthropic.
 * Why it exists: Step 34 of the Phase 3.3 brief - real, verifiable
 * tests, not fakes claiming coverage they don't have.
 * How it communicates with other components: exercises
 * AnthropicLlmProvider directly, the same way LlmServiceImpl does in
 * production.
 *
 * Hinglish:
 * Is service ki us ek class ke liye real wire-level tests jo kabhi ek
 * LLM ko call karti hai - Notification Service ke WebhookChannelTest ko
 * exactly reuse karta hai (ek already-established, real test dependency
 * is platform me - org.wiremock:wiremock-standalone, yahan add hone se
 * pehle notification-service/api-gateway ke pom.xml me confirm present
 * paaya gaya), real Anthropic SDK client ka baseUrl api.anthropic.com
 * ke bajaye ek local WireMockServer par point karte hue. Ye jaan-boojh
 * kar ek real HTTP-level test hai, ek mocked-SDK test nahi - ye prove
 * karta hai ki AnthropicLlmProvider wahi request banata hai jo SDK
 * actually bhejta hai aur SDK ke real typed exceptions ko (WireMock ke
 * return kiye real HTTP status codes se throw hue) sahi LlmException me
 * correctly map karta hai, Step 13 ke retryable/non-retryable split ke
 * hisaab se, bina kabhi Anthropic ko ek real, billed call kiye.
 * Ye kyu hai: Phase 3.3 brief ka Step 34 - real, verifiable tests, aise
 * fakes nahi jo coverage claim karein jo unke paas hai hi nahi.
 * Dusre components se kaise communicate karta hai: AnthropicLlmProvider
 * ko seedhe exercise karta hai, wahi tarike se jaise production me
 * LlmServiceImpl karta hai.
 */
class AnthropicLlmProviderTest {

    private WireMockServer wireMockServer;
    private AnthropicLlmProvider provider;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();

        LlmProperties properties = new LlmProperties();
        properties.getAnthropic().setApiKey("test-api-key");
        properties.getAnthropic().setModel("claude-opus-5");
        properties.getAnthropic().setBaseUrl("http://localhost:" + wireMockServer.port());
        properties.getAnthropic().setDefaultMaxTokens(1024);
        properties.getAnthropic().setTimeoutSeconds(5);

        provider = new AnthropicLlmProvider(properties);
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
    void generate_successfulResponse_returnsNormalizedResult() {
        wireMockServer.stubFor(post(urlPathEqualTo("/v1/messages")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {
                          "id": "msg_01",
                          "type": "message",
                          "role": "assistant",
                          "model": "claude-opus-5",
                          "content": [{"type": "text", "text": "The dispute concerns a duplicate charge."}],
                          "stop_reason": "end_turn",
                          "stop_sequence": null,
                          "usage": {"input_tokens": 42, "output_tokens": 9}
                        }
                        """)));

        LlmProviderResult result = provider.generate(defaultRequest());

        assertThat(result.provider()).isEqualTo("anthropic");
        assertThat(result.content()).isEqualTo("The dispute concerns a duplicate charge.");
        assertThat(result.stopReason()).isEqualTo("end_turn");
        assertThat(result.refused()).isFalse();
        assertThat(result.inputTokens()).isEqualTo(42);
        assertThat(result.outputTokens()).isEqualTo(9);

        wireMockServer.verify(postRequestedFor(urlPathEqualTo("/v1/messages"))
                .withHeader("x-api-key", equalTo("test-api-key")));
    }

    @Test
    void generate_refusalStopReason_returnsRefusedTrueNotAnException() {
        wireMockServer.stubFor(post(urlPathEqualTo("/v1/messages")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {
                          "id": "msg_02",
                          "type": "message",
                          "role": "assistant",
                          "model": "claude-opus-5",
                          "content": [],
                          "stop_reason": "refusal",
                          "stop_sequence": null,
                          "usage": {"input_tokens": 12, "output_tokens": 0}
                        }
                        """)));

        LlmProviderResult result = provider.generate(defaultRequest());

        assertThat(result.refused()).isTrue();
        assertThat(result.stopReason()).isEqualTo("refusal");
        assertThat(result.content()).isEmpty();
    }

    @Test
    void generate_rateLimited_throwsRetryableLlmException() {
        wireMockServer.stubFor(post(urlPathEqualTo("/v1/messages")).willReturn(aResponse()
                .withStatus(429)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"type": "error", "error": {"type": "rate_limit_error", "message": "rate limited"}}
                        """)));

        assertThatThrownBy(() -> provider.generate(defaultRequest()))
                .isInstanceOf(LlmException.class)
                .satisfies(ex -> {
                    LlmException llmEx = (LlmException) ex;
                    assertThat(llmEx.getErrorCode()).isEqualTo(LlmErrorCodes.LLM_RATE_LIMITED);
                    assertThat(llmEx.isRetryable()).isTrue();
                });
    }

    @Test
    void generate_unauthorized_throwsNonRetryableLlmException() {
        wireMockServer.stubFor(post(urlPathEqualTo("/v1/messages")).willReturn(aResponse()
                .withStatus(401)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"type": "error", "error": {"type": "authentication_error", "message": "invalid key"}}
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
        wireMockServer.stubFor(post(urlPathEqualTo("/v1/messages")).willReturn(aResponse()
                .withStatus(400)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"type": "error", "error": {"type": "invalid_request_error", "message": "bad request"}}
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
        wireMockServer.stubFor(post(urlPathEqualTo("/v1/messages")).willReturn(aResponse()
                .withStatus(500)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"type": "error", "error": {"type": "api_error", "message": "internal error"}}
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
    void generate_malformedResponseBody_throwsInternalErrorException() {
        wireMockServer.stubFor(post(urlPathEqualTo("/v1/messages")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{ this is not valid json")));

        assertThatThrownBy(() -> provider.generate(defaultRequest()))
                .isInstanceOf(LlmException.class);
    }
}
