package com.paymentx.llm.provider.openai;

import com.paymentx.llm.config.LlmProperties;
import com.paymentx.llm.exception.LlmException;
import com.paymentx.llm.provider.LlmProvider;
import com.paymentx.llm.provider.LlmProviderRequest;
import com.paymentx.llm.provider.LlmProviderResult;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase 5 (OpenAI last-resort paid fallback) - the ONE and ONLY class in this service that models
 * an OpenAI-specific wire shape (OpenAiRequest/OpenAiResponse), mirroring
 * GroqLlmProvider's/AnthropicLlmProvider's/GeminiLlmProvider's exact isolation boundary
 * (LlmService/LlmServiceImpl/LlmController never see an OpenAI-specific type, only
 * LlmProviderRequest/LlmProviderResult).
 *
 * Deliberately the LAST provider in the desired failover chain (Gemini -> Groq -> Anthropic ->
 * OpenAI, see application-dev.yml) - unlike the other three providers, a real OpenAI call draws
 * down a funded, non-free credit balance, so it must only ever be reached once every
 * higher-priority provider has genuinely failed with a retryable error (LlmProviderRouter already
 * enforces this uniformly for every entry in the chain; nothing here is OpenAI-specific about
 * that). `model` defaults to a low-cost model (gpt-5.4-nano, not a flagship) for the same reason -
 * see LlmProperties.OpenAi's own javadoc.
 *
 * Uses the standard OpenAI Chat Completions wire format (POST {base-url}/chat/completions,
 * `Authorization: Bearer <key>`) - the exact same format GroqRequest/GroqResponse already model,
 * since Groq's own API is a documented drop-in for it. A hand-rolled Spring RestClient is used
 * here for the same reason as Gemini/Groq: this service's existing agent/tool-calling
 * requirements are met by the plain Chat Completions shape, so a vendor SDK dependency buys
 * nothing a REST call doesn't already provide.
 *
 * Same per-provider Resilience4j instance-naming pattern every other provider already establishes
 * (`llmProvider-openai`, independent circuit breaker/retry state from Gemini/Anthropic/Groq - see
 * application.yml).
 */
@Component
@Slf4j
public class OpenAiLlmProvider implements LlmProvider {

    private static final String PROVIDER_NAME = "openai";
    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";

    private final LlmProperties properties;
    private RestClient restClient;

    public OpenAiLlmProvider(LlmProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void init() {
        LlmProperties.OpenAi config = properties.getOpenai();
        String baseUrl = config.getBaseUrl() != null && !config.getBaseUrl().isBlank()
                ? config.getBaseUrl() : DEFAULT_BASE_URL;

        // Same JdkClientHttpRequestFactory + forced HTTP/1.1 rationale as GeminiLlmProvider.init/
        // GroqLlmProvider.init - see those methods' own comments for why (reliable timeout
        // surfacing, WireMock HTTP/1.1 compatibility in tests).
        Duration timeout = Duration.ofSeconds(config.getTimeoutSeconds());
        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                .version(java.net.http.HttpClient.Version.HTTP_1_1)
                .connectTimeout(timeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public String providerName() {
        return PROVIDER_NAME;
    }

    @Override
    @CircuitBreaker(name = "llmProvider-openai")
    @Retry(name = "llmProvider-openai")
    public LlmProviderResult generate(LlmProviderRequest request) {
        LlmProperties.OpenAi config = properties.getOpenai();
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw LlmException.notConfigured(
                    "LLM Service has no OpenAI API key configured (OPENAI_API_KEY is unset) - cannot call the provider.");
        }

        String model = request.model() != null && !request.model().isBlank() ? request.model() : config.getModel();
        long maxTokens = request.maxTokens() > 0 ? request.maxTokens() : config.getDefaultMaxTokens();

        List<OpenAiRequest.OpenAiMessage> messages = new ArrayList<>();
        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            messages.add(new OpenAiRequest.OpenAiMessage("system", request.systemPrompt()));
        }
        messages.add(new OpenAiRequest.OpenAiMessage("user", request.prompt()));

        OpenAiRequest body = new OpenAiRequest(model, messages, maxTokens, request.temperature());

        long start = System.currentTimeMillis();
        try {
            OpenAiResponse response = restClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(OpenAiResponse.class);
            long latencyMs = System.currentTimeMillis() - start;
            return toResult(response, model, latencyMs);
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
            throw LlmException.credentialsRejected(
                    "OpenAI provider rejected the configured credentials: " + safeBody(e));
        } catch (HttpClientErrorException.TooManyRequests e) {
            throw LlmException.rateLimited("OpenAI provider rate limit exceeded: " + safeBody(e));
        } catch (HttpClientErrorException ex) {
            // Covers 400/404/422 - a malformed/invalid request, never retryable, matching
            // GeminiLlmProvider's/AnthropicLlmProvider's/GroqLlmProvider's identical mapping.
            throw LlmException.invalidRequest("OpenAI provider rejected the request as invalid: " + safeBody(ex));
        } catch (HttpServerErrorException e) {
            throw LlmException.providerUnavailable("OpenAI provider returned a server error: " + safeBody(e));
        } catch (ResourceAccessException e) {
            throw LlmException.timeout("OpenAI provider call timed out or the connection failed: " + e.getMessage());
        } catch (RestClientException e) {
            throw LlmException.internalError("Unexpected OpenAI provider error: " + e.getMessage());
        }
    }

    // Same rationale as GroqLlmProvider's own safeBody: the response BODY (not just
    // e.getMessage()) is what an operator needs to diagnose a real failure - never contains the
    // API key (that lives only in the outbound Authorization header, never echoed back or logged
    // here).
    private String safeBody(HttpClientErrorException e) {
        String responseBody = e.getResponseBodyAsString();
        return (responseBody == null || responseBody.isBlank()) ? e.getMessage() : responseBody;
    }

    private String safeBody(HttpServerErrorException e) {
        String responseBody = e.getResponseBodyAsString();
        return (responseBody == null || responseBody.isBlank()) ? e.getMessage() : responseBody;
    }

    private LlmProviderResult toResult(OpenAiResponse response, String requestedModel, long latencyMs) {
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw LlmException.responseInvalid("OpenAI provider returned an empty response body.");
        }

        String actualModel = response.model() != null && !response.model().isBlank() ? response.model() : requestedModel;

        OpenAiResponse.Choice choice = response.choices().get(0);
        String finishReason = choice.finishReason() != null ? choice.finishReason() : "unknown";
        boolean refused = "content_filter".equals(finishReason);
        String content = choice.message() != null && choice.message().content() != null ? choice.message().content() : "";

        return new LlmProviderResult(
                PROVIDER_NAME,
                actualModel,
                content,
                finishReason,
                refused,
                usageOrZero(response, OpenAiResponse.Usage::promptTokens),
                usageOrZero(response, OpenAiResponse.Usage::completionTokens),
                null,
                null,
                latencyMs
        );
    }

    private long usageOrZero(OpenAiResponse response, java.util.function.Function<OpenAiResponse.Usage, Long> extractor) {
        if (response.usage() == null) {
            return 0L;
        }
        Long value = extractor.apply(response.usage());
        return value != null ? value : 0L;
    }
}
