package com.paymentx.llm.controller;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.paymentx.common.dto.ApiResponse;
import com.paymentx.llm.dto.GenerateRequest;
import com.paymentx.llm.dto.GenerateResponse;
import com.paymentx.llm.dto.LlmHealthResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * English:
 * A real, end-to-end HTTP test of LlmController against the real Spring
 * context (SecurityConfig, GlobalExceptionHandler, Resilience4j
 * annotations, the works) - matching Prompt Service's
 * PromptControllerIntegrationTest's exact pattern, with one difference:
 * this service has no database, so there is no Testcontainers Postgres
 * here - a WireMockServer standing in for api.anthropic.com is the only
 * external dependency, started before the Spring context via
 * @DynamicPropertySource so llm.anthropic.base-url points at it from
 * the first request. What it verifies: a blank prompt is rejected with
 * a real 400 VALIDATION_ERROR before ever reaching LlmServiceImpl; a
 * real successful provider call round-trips through
 * AnthropicLlmProvider -> LlmServiceImpl -> LlmController into a real
 * HTTP 200 ApiResponse; a real provider rate-limit response maps to a
 * real HTTP 429 with errorCode LLM_RATE_LIMITED via
 * GlobalExceptionHandler; GET /health reports CONFIGURED for the
 * test's real (fake but present) api-key.
 * Why it exists: Step 34 of the Phase 3.3 brief - "@Valid/
 * @RestControllerAdvice are Spring-managed behavior mocked unit tests
 * never actually exercise" (PromptControllerIntegrationTest's own
 * javadoc reasoning, equally true here).
 * How it communicates with other components: boots the real Spring
 * context on a random port; WireMock stands in for the one real
 * external dependency this service has.
 *
 * Hinglish:
 * LlmController ka ek real, end-to-end HTTP test, real Spring context
 * ke against (SecurityConfig, GlobalExceptionHandler, Resilience4j
 * annotations, sab kuch) - Prompt Service ke
 * PromptControllerIntegrationTest ke exact pattern se match karte hue,
 * ek fark ke saath: is service ka koi database nahi hai, isliye yahan
 * koi Testcontainers Postgres nahi hai - ek WireMockServer jo
 * api.anthropic.com ki jagah khada hai hi ek hi external dependency
 * hai, Spring context se pehle @DynamicPropertySource ke through start
 * hota hai taaki llm.anthropic.base-url pehli request se hi usi par
 * point kare. Ye kya verify karta hai: ek blank prompt ek real 400
 * VALIDATION_ERROR se reject hota hai, LlmServiceImpl tak pahunchne se
 * pehle hi; ek real successful provider call
 * AnthropicLlmProvider -> LlmServiceImpl -> LlmController se hote hue
 * ek real HTTP 200 ApiResponse me round-trip karta hai; ek real
 * provider rate-limit response GlobalExceptionHandler ke through ek
 * real HTTP 429, errorCode LLM_RATE_LIMITED ke saath, map hota hai; GET
 * /health test ke real (fake lekin present) api-key ke liye CONFIGURED
 * report karta hai.
 * Ye kyu hai: Phase 3.3 brief ka Step 34 - "@Valid/
 * @RestControllerAdvice Spring-managed behavior hai jise mocked unit
 * tests actually kabhi exercise nahi karte"
 * (PromptControllerIntegrationTest ka apna javadoc reasoning, yahan
 * bhi utna hi sahi).
 * Dusre components se kaise communicate karta hai: real Spring context
 * ek random port par boot karta hai; WireMock is service ki ek hi real
 * external dependency ki jagah khada hota hai.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LlmControllerIntegrationTest {

    private static final WireMockServer wireMockServer = new WireMockServer(0);

    static {
        wireMockServer.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        // Phase 4.8.4 - explicit now that application-dev.yml's own default
        // changed to "gemini" for local/dev testing: this test exercises
        // AnthropicLlmProvider specifically (WireMock stubs Anthropic's own
        // /v1/messages path below), so it must pin its own provider rather
        // than inherit whatever the active profile currently defaults to.
        registry.add("llm.provider", () -> "anthropic");
        registry.add("llm.anthropic.api-key", () -> "integration-test-key");
        registry.add("llm.anthropic.base-url", () -> "http://localhost:" + wireMockServer.port());
        // Phase 5 (Multi-Provider LLM Resilience Expansion) - explicit for the same reason
        // `llm.provider` above already is: application-dev.yml's own default
        // `fallback-providers` now includes "groq" for real local-dev continuity, which would
        // otherwise make a 429 from Anthropic (this test's own primary) fail over to Groq -
        // unconfigured in this test's context (no GROQ_API_KEY, no WireMock stub for it) - and
        // surface as a misleading LLM_NOT_CONFIGURED instead of the real Anthropic 429 this test
        // exists to verify. Same isolation precedent LlmControllerGeminiIntegrationTest's own
        // `fallback-enabled: false` already establishes for the identical reason.
        registry.add("llm.routing.fallback-enabled", () -> "false");
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
    void generate_blankPrompt_returns400ValidationError() {
        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                url("/api/v1/llm/generate"), org.springframework.http.HttpMethod.POST,
                new org.springframework.http.HttpEntity<>(new GenerateRequest("   ", null, null, null, null)),
                new ParameterizedTypeReference<>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().error().errorCode()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void generate_successfulProviderCall_returns200WithNormalizedContent() {
        wireMockServer.stubFor(post(urlPathEqualTo("/v1/messages")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {
                          "id": "msg_it_01",
                          "type": "message",
                          "role": "assistant",
                          "model": "claude-opus-5",
                          "content": [{"type": "text", "text": "Integration test answer."}],
                          "stop_reason": "end_turn",
                          "stop_sequence": null,
                          "usage": {"input_tokens": 7, "output_tokens": 3}
                        }
                        """)));

        ResponseEntity<ApiResponse<GenerateResponse>> response = restTemplate.exchange(
                url("/api/v1/llm/generate"), org.springframework.http.HttpMethod.POST,
                new org.springframework.http.HttpEntity<>(new GenerateRequest("Say hello.", null, null, null, null)),
                new ParameterizedTypeReference<>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().data().content()).isEqualTo("Integration test answer.");
        assertThat(response.getBody().data().refused()).isFalse();
    }

    @Test
    void generate_providerRateLimited_returns429WithErrorCode() {
        wireMockServer.stubFor(post(urlPathEqualTo("/v1/messages")).willReturn(aResponse()
                .withStatus(429)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"type": "error", "error": {"type": "rate_limit_error", "message": "rate limited"}}
                        """)));

        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                url("/api/v1/llm/generate"), org.springframework.http.HttpMethod.POST,
                new org.springframework.http.HttpEntity<>(new GenerateRequest("Trigger a rate limit.", null, null, null, null)),
                new ParameterizedTypeReference<>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().errorCode()).isEqualTo("LLM_RATE_LIMITED");
    }

    @Test
    void health_apiKeyConfigured_returnsConfiguredStatus() {
        ResponseEntity<ApiResponse<LlmHealthResponse>> response = restTemplate.exchange(
                url("/api/v1/llm/health"), org.springframework.http.HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().status()).isEqualTo("CONFIGURED");
        assertThat(response.getBody().data().apiKeyPresent()).isTrue();
    }
}
