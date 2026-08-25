package com.paymentx.llm.controller;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.paymentx.common.dto.ApiResponse;
import com.paymentx.llm.dto.GenerateRequest;
import com.paymentx.llm.dto.GenerateResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 5 - proves LlmServiceImpl.generate's Resilience4j RateLimiter (instance "llmGenerate",
 * see that class's own javadoc for why it exists: protecting Gemini's 20-requests/day free-tier
 * quota and Anthropic's billing from a duplicate click/tab/runaway-loop flood) actually rejects
 * calls once its permit budget is exhausted, and that the rejection surfaces as a real, honest
 * HTTP 429 with errorCode LLM_LOCAL_RATE_LIMITED via GlobalExceptionHandler - never a fabricated
 * success and never confused with LLM_RATE_LIMITED (the real upstream provider's own 429,
 * separately covered by LlmControllerGeminiIntegrationTest/LlmControllerIntegrationTest).
 * Deliberately overrides the production default (8 permits/10s, generous enough not to throttle
 * one legitimate agent execution's own multi-iteration planning loop) down to a much tighter
 * budget via @DynamicPropertySource, so this test is fast and deterministic rather than needing
 * to fire 9+ real requests or sleep out a 10s window. Every provider call here is a WireMock stub
 * - zero real Gemini/Anthropic quota or billing is ever spent by this test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LlmControllerRateLimiterIntegrationTest {

    private static final WireMockServer wireMockServer = new WireMockServer(0);

    static {
        wireMockServer.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("llm.provider", () -> "gemini");
        registry.add("llm.gemini.api-key", () -> "integration-test-gemini-key");
        registry.add("llm.gemini.model", () -> "gemini-3.7-flash");
        registry.add("llm.gemini.base-url", () -> "http://localhost:" + wireMockServer.port());
        registry.add("llm.routing.fallback-enabled", () -> "false");
        // Tight, deterministic override: exactly 1 permit per window, no wait - the second call
        // in the same window is rejected immediately rather than queued.
        registry.add("resilience4j.ratelimiter.instances.llmGenerate.limit-for-period", () -> "1");
        registry.add("resilience4j.ratelimiter.instances.llmGenerate.limit-refresh-period", () -> "10s");
        registry.add("resilience4j.ratelimiter.instances.llmGenerate.timeout-duration", () -> "0s");
    }

    @AfterAll
    static void tearDown() {
        wireMockServer.stop();
    }

    @LocalServerPort
    private int port;

    // Deliberately NOT the auto-injected TestRestTemplate: Spring Boot's RestTemplateBuilder
    // auto-configuration detects Apache HttpClient5 on the test classpath (pulled in transitively
    // by WireMock's own bundled dependencies) and wires TestRestTemplate to use it - and
    // HttpClient5's default behavior transparently RETRIES a 429 response after a short backoff,
    // which raced against this test's own 10s rate-limiter refresh window and produced a real,
    // observed flake (second call correctly got 429 from the server, but the client's own silent
    // retry got a 200 on a later attempt once system load pushed the retry past the window,
    // masking the very behavior this test exists to prove). Same fix this codebase's own
    // production code already applies for the same reason - see LlmServiceClient/AiPlatformClient,
    // both of which explicitly build their RestTemplate via .requestFactory(SimpleClientHttpRequestFactory::new)
    // to avoid exactly this kind of implicit, retrying HTTP client auto-detection.
    private final RestTemplate restTemplate = new RestTemplate(new SimpleClientHttpRequestFactory());

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private void stubSuccessfulGeminiCall() {
        wireMockServer.stubFor(post(urlPathEqualTo("/v1beta/models/gemini-3.7-flash:generateContent")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {
                          "candidates": [
                            {"content": {"role": "model", "parts": [{"text": "Rate limiter test answer."}]}, "finishReason": "STOP"}
                          ],
                          "usageMetadata": {"promptTokenCount": 5, "candidatesTokenCount": 3, "totalTokenCount": 8},
                          "modelVersion": "gemini-3.7-flash"
                        }
                        """)));
    }

    @Test
    void secondCallWithinTheSameWindow_isRejectedWithHonest429_neverAFabricatedSuccess() {
        stubSuccessfulGeminiCall();
        GenerateRequest request = new GenerateRequest("Say hello.", null, null, null, null);

        ResponseEntity<ApiResponse<GenerateResponse>> first = restTemplate.exchange(
                url("/api/v1/llm/generate"), HttpMethod.POST,
                new org.springframework.http.HttpEntity<>(request),
                new ParameterizedTypeReference<>() {
                });
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody()).isNotNull();
        assertThat(first.getBody().success()).isTrue();

        // Plain RestTemplate (unlike TestRestTemplate) throws on a non-2xx response rather than
        // returning it in the ResponseEntity - the real, correct 429 is asserted from the
        // exception itself.
        assertThatThrownBy(() -> restTemplate.exchange(
                url("/api/v1/llm/generate"), HttpMethod.POST,
                new org.springframework.http.HttpEntity<>(request),
                new ParameterizedTypeReference<ApiResponse<Void>>() {
                }))
                .isInstanceOfSatisfying(HttpClientErrorException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(ex.getResponseBodyAsString()).contains("LLM_LOCAL_RATE_LIMITED");
                });
    }
}
