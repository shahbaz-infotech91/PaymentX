package com.paymentx.llm.provider.gemini;

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
import java.util.List;

/**
 * The ONE and ONLY class in this service that models a Gemini-specific wire
 * shape (GeminiRequest/GeminiResponse) - mirrors AnthropicLlmProvider's
 * exact isolation boundary (LlmService/LlmServiceImpl/LlmController never
 * see a Gemini-specific type, only LlmProviderRequest/LlmProviderResult).
 *
 * Uses a plain Spring RestClient (already on the classpath via
 * spring-boot-starter-web) rather than a Google SDK dependency: unlike
 * Anthropic, this platform has no prior/mandated Google Gen AI SDK
 * convention to follow, and the REST contract
 * (POST /v1beta/models/{model}:generateContent, header-based
 * `x-goog-api-key` auth - verified against ai.google.dev/api/generate-content
 * before this was written, not invented) is small and stable enough that a
 * hand-rolled client is the smaller, equally production-quality change here
 * - the opposite tradeoff from Anthropic's SDK requirement, not a
 * contradiction of it.
 *
 * Same Resilience4j instance (`llmProvider`) as AnthropicLlmProvider - one
 * provider is active at a time (see the @ConditionalOnProperty on both
 * classes), so sharing the instance name is correct, not a collision: the
 * circuit breaker/retry policy describes "the currently configured LLM
 * provider," not a specific vendor.
 */
// Phase 4.8.6 - the @ConditionalOnProperty mutual exclusion this class previously had (vs.
// AnthropicLlmProvider) was removed: provider.LlmProviderRouter (the sole bean actually injected
// into LlmServiceImpl, @Primary) needs both concrete providers present simultaneously so it can
// call whichever one (primary or fallback) a request needs, resolved by matching providerName()
// against configuration. This class's own generate() behavior is unchanged.
@Component
@Slf4j
public class GeminiLlmProvider implements LlmProvider {

    private static final String PROVIDER_NAME = "gemini";
    private static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com";

    private final LlmProperties properties;
    private RestClient restClient;

    public GeminiLlmProvider(LlmProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void init() {
        LlmProperties.Gemini config = properties.getGemini();
        String baseUrl = config.getBaseUrl() != null && !config.getBaseUrl().isBlank()
                ? config.getBaseUrl() : DEFAULT_BASE_URL;

        // JdkClientHttpRequestFactory (java.net.http.HttpClient, JDK-native,
        // no extra dependency) rather than SimpleClientHttpRequestFactory:
        // verified during implementation that the latter does not reliably
        // surface a slow/delayed response as a clean timeout exception in
        // this environment (a slow response was misread as a malformed body
        // instead of timing out), where the JDK HttpClient-backed factory
        // throws a real java.net.http.HttpTimeoutException -> ResourceAccessException
        // exactly at the configured duration, every time.
        // HTTP_1_1 forced explicitly: java.net.http.HttpClient's default HTTP/2
        // upgrade attempt was observed causing intermittent connection aborts
        // against WireMock (HTTP/1.1-only) during testing - Gemini's real API
        // serves HTTP/1.1 fine, so this is a test-environment compatibility fix,
        // not a functional restriction.
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

    // Phase 4.8.6 - own, per-provider circuit-breaker/retry instance name; see
    // AnthropicLlmProvider.generate's identical comment for why sharing one instance across two
    // simultaneously-callable providers would be wrong now that LlmProviderRouter exists.
    @Override
    @CircuitBreaker(name = "llmProvider-gemini")
    @Retry(name = "llmProvider-gemini")
    public LlmProviderResult generate(LlmProviderRequest request) {
        LlmProperties.Gemini config = properties.getGemini();
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw LlmException.notConfigured(
                    "LLM Service has no Gemini API key configured (GEMINI_API_KEY is unset) - cannot call the provider.");
        }

        String model = request.model() != null && !request.model().isBlank() ? request.model() : config.getModel();
        long maxTokens = request.maxTokens() > 0 ? request.maxTokens() : config.getDefaultMaxTokens();

        GeminiRequest.GeminiContent userContent = new GeminiRequest.GeminiContent(
                "user", List.of(new GeminiRequest.GeminiPart(request.prompt())));
        GeminiRequest.GeminiContent systemInstruction = request.systemPrompt() != null && !request.systemPrompt().isBlank()
                ? new GeminiRequest.GeminiContent(null, List.of(new GeminiRequest.GeminiPart(request.systemPrompt())))
                : null;
        GeminiRequest.GeminiGenerationConfig generationConfig = new GeminiRequest.GeminiGenerationConfig(
                maxTokens, request.temperature());

        GeminiRequest body = new GeminiRequest(List.of(userContent), systemInstruction, generationConfig);

        long start = System.currentTimeMillis();
        try {
            GeminiResponse response = restClient.post()
                    .uri("/v1beta/models/{model}:generateContent", model)
                    .header("x-goog-api-key", config.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(GeminiResponse.class);
            long latencyMs = System.currentTimeMillis() - start;
            return toResult(response, model, latencyMs);
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
            throw LlmException.credentialsRejected(
                    "Gemini provider rejected the configured credentials: " + safeBody(e));
        } catch (HttpClientErrorException.TooManyRequests e) {
            throw LlmException.rateLimited("Gemini provider rate limit exceeded: " + safeBody(e));
        } catch (HttpClientErrorException ex) {
            // Covers 400/404/422 - a malformed/invalid request, never retryable,
            // matching AnthropicLlmProvider's identical BadRequest/NotFound/
            // UnprocessableEntity -> invalidRequest mapping.
            throw LlmException.invalidRequest("Gemini provider rejected the request as invalid: " + safeBody(ex));
        } catch (HttpServerErrorException e) {
            throw LlmException.providerUnavailable("Gemini provider returned a server error: " + safeBody(e));
        } catch (ResourceAccessException e) {
            throw LlmException.timeout("Gemini provider call timed out or the connection failed: " + e.getMessage());
        } catch (RestClientException e) {
            throw LlmException.internalError("Unexpected Gemini provider error: " + e.getMessage());
        }
    }

    // WHY a dedicated helper instead of e.getMessage(): HttpStatusCodeException's
    // own getMessage() already omits the request (no API key ever appears in the
    // URI or a header Spring logs at this level), but the response BODY is the
    // part an operator actually needs to diagnose a real failure (e.g. Gemini's
    // own RESOURCE_EXHAUSTED/quota message) - same information AnthropicLlmProvider
    // gets for free from the SDK's own typed exceptions.
    private String safeBody(HttpClientErrorException e) {
        String responseBody = e.getResponseBodyAsString();
        return (responseBody == null || responseBody.isBlank()) ? e.getMessage() : responseBody;
    }

    private String safeBody(HttpServerErrorException e) {
        String responseBody = e.getResponseBodyAsString();
        return (responseBody == null || responseBody.isBlank()) ? e.getMessage() : responseBody;
    }

    private LlmProviderResult toResult(GeminiResponse response, String requestedModel, long latencyMs) {
        if (response == null) {
            throw LlmException.responseInvalid("Gemini provider returned an empty response body.");
        }

        String actualModel = response.modelVersion() != null && !response.modelVersion().isBlank()
                ? response.modelVersion() : requestedModel;

        List<GeminiResponse.Candidate> candidates = response.candidates();
        if (candidates == null || candidates.isEmpty()) {
            // The prompt itself was blocked before any candidate was generated -
            // a real HTTP-200 answer (mirrors AnthropicLlmProvider's REFUSAL
            // handling: this is not a thrown exception, it's a legitimate result
            // the caller must be told about honestly, not silently swallowed).
            String blockReason = response.promptFeedback() != null && response.promptFeedback().blockReason() != null
                    ? response.promptFeedback().blockReason() : "unknown";
            return new LlmProviderResult(PROVIDER_NAME, actualModel, "", blockReason, true,
                    usageOrZero(response, GeminiResponse.UsageMetadata::promptTokenCount),
                    0L,
                    null,
                    usageOrNull(response, GeminiResponse.UsageMetadata::cachedContentTokenCount),
                    latencyMs);
        }

        GeminiResponse.Candidate candidate = candidates.get(0);
        String finishReason = candidate.finishReason() != null ? candidate.finishReason() : "unknown";
        boolean refused = "SAFETY".equals(finishReason) || "RECITATION".equals(finishReason)
                || "PROHIBITED_CONTENT".equals(finishReason);

        String content = candidate.content() != null && candidate.content().parts() != null
                ? candidate.content().parts().stream()
                        .map(GeminiResponse.Part::text)
                        .filter(t -> t != null)
                        .reduce("", String::concat)
                : "";

        return new LlmProviderResult(
                PROVIDER_NAME,
                actualModel,
                content,
                finishReason,
                refused,
                usageOrZero(response, GeminiResponse.UsageMetadata::promptTokenCount),
                usageOrZero(response, GeminiResponse.UsageMetadata::candidatesTokenCount),
                null,
                usageOrNull(response, GeminiResponse.UsageMetadata::cachedContentTokenCount),
                latencyMs
        );
    }

    private long usageOrZero(GeminiResponse response, java.util.function.Function<GeminiResponse.UsageMetadata, Long> extractor) {
        if (response.usageMetadata() == null) {
            return 0L;
        }
        Long value = extractor.apply(response.usageMetadata());
        return value != null ? value : 0L;
    }

    private Long usageOrNull(GeminiResponse response, java.util.function.Function<GeminiResponse.UsageMetadata, Long> extractor) {
        return response.usageMetadata() != null ? extractor.apply(response.usageMetadata()) : null;
    }
}
