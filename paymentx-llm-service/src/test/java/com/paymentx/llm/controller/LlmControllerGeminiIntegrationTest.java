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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Gemini-path twin of LlmControllerIntegrationTest - same real,
 * end-to-end Spring context (SecurityConfig, GlobalExceptionHandler,
 * Resilience4j), same WireMock-stands-in-for-the-real-provider approach,
 * but with `llm.provider=gemini` set via @DynamicPropertySource. This is
 * the one test in this module that actually proves the
 * @ConditionalOnProperty bean-selection mechanism (Phase 4.8.4) works at
 * the real Spring-context level, not just via direct construction in a
 * plain unit test - a wiring bug here (e.g. both providers registering, or
 * neither) would only surface in a test that boots the real context.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LlmControllerGeminiIntegrationTest {

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
        // Phase 4.8.6 - this class tests GEMINI's own error mapping/response shape in isolation
        // (provider.LlmProviderRouter's own automatic-failover behavior is separately, thoroughly
        // covered by provider.LlmProviderRouterTest) - fallback is explicitly disabled here so a
        // Gemini failure asserts as the real Gemini error, never masked by a fallback attempt
        // against whatever real/unconfigured Anthropic credentials this environment happens to
        // have (this class deliberately never configures llm.anthropic.* at all).
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
    void generate_successfulProviderCall_returns200WithNormalizedContent() {
        wireMockServer.stubFor(post(urlPathEqualTo("/v1beta/models/gemini-3.7-flash:generateContent")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {
                          "candidates": [
                            {"content": {"role": "model", "parts": [{"text": "Integration test answer."}]}, "finishReason": "STOP"}
                          ],
                          "usageMetadata": {"promptTokenCount": 7, "candidatesTokenCount": 3, "totalTokenCount": 10},
                          "modelVersion": "gemini-3.7-flash"
                        }
                        """)));

        ResponseEntity<ApiResponse<GenerateResponse>> response = restTemplate.exchange(
                url("/api/v1/llm/generate"), HttpMethod.POST,
                new org.springframework.http.HttpEntity<>(new GenerateRequest("Say hello.", null, null, null, null, null)),
                new ParameterizedTypeReference<>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().data().provider()).isEqualTo("gemini");
        assertThat(response.getBody().data().content()).isEqualTo("Integration test answer.");
        assertThat(response.getBody().data().refused()).isFalse();
    }

    @Test
    void generate_providerRateLimited_returns429WithErrorCode() {
        wireMockServer.stubFor(post(urlPathEqualTo("/v1beta/models/gemini-3.7-flash:generateContent")).willReturn(aResponse()
                .withStatus(429)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"error": {"code": 429, "message": "Resource exhausted", "status": "RESOURCE_EXHAUSTED"}}
                        """)));

        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                url("/api/v1/llm/generate"), HttpMethod.POST,
                new org.springframework.http.HttpEntity<>(new GenerateRequest("Trigger a rate limit.", null, null, null, null, null)),
                new ParameterizedTypeReference<>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().errorCode()).isEqualTo("LLM_RATE_LIMITED");
    }

    @Test
    void health_geminiConfigured_returnsConfiguredStatusForGemini_notAnthropic() {
        ResponseEntity<ApiResponse<LlmHealthResponse>> response = restTemplate.exchange(
                url("/api/v1/llm/health"), HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().status()).isEqualTo("CONFIGURED");
        assertThat(response.getBody().data().provider()).isEqualTo("gemini");
        assertThat(response.getBody().data().configuredModel()).isEqualTo("gemini-3.7-flash");
        assertThat(response.getBody().data().apiKeyPresent()).isTrue();
    }
}
