package com.paymentx.llm.provider.groq;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 5 (Multi-Provider LLM Resilience Expansion) - the ONE and ONLY class in this service that
 * models a Groq-specific wire shape (GroqRequest/GroqResponse), mirroring
 * AnthropicLlmProvider's/GeminiLlmProvider's exact isolation boundary (LlmService/LlmServiceImpl/
 * LlmController never see a Groq-specific type, only LlmProviderRequest/LlmProviderResult).
 *
 * Selected as the third provider (see PAYMENTX_PHASE_5_MULTI_PROVIDER_LLM_RESILIENCE.md's own
 * Provider Evaluation section for the full comparison) specifically to eliminate Gemini's
 * 20-requests/day free-tier quota as a development/E2E blocker: Groq's free tier is ~14,400
 * requests/day (org-level) with no credit card required, and its API is a deliberate,
 * documented drop-in for OpenAI's Chat Completions wire format
 * (POST {base-url}/chat/completions, `Authorization: Bearer <key>` - verified against
 * console.groq.com/docs/api-reference before this was written, not invented), so - exactly like
 * Gemini's own rationale - a hand-rolled Spring RestClient is the smaller, equally
 * production-quality integration effort here, not a vendor SDK dependency.
 *
 * Same per-provider Resilience4j instance-naming pattern GeminiLlmProvider/AnthropicLlmProvider
 * already established (`llmProvider-groq`, independent circuit breaker/retry state from the
 * other two - see application.yml).
 */
@Component
@Slf4j
public class GroqLlmProvider implements LlmProvider {

    private static final String PROVIDER_NAME = "groq";
    private static final String DEFAULT_BASE_URL = "https://api.groq.com/openai/v1";

    private final LlmProperties properties;
    private RestClient restClient;

    public GroqLlmProvider(LlmProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void init() {
        LlmProperties.Groq config = properties.getGroq();
        String baseUrl = config.getBaseUrl() != null && !config.getBaseUrl().isBlank()
                ? config.getBaseUrl() : DEFAULT_BASE_URL;

        // Same JdkClientHttpRequestFactory + forced HTTP/1.1 rationale as GeminiLlmProvider.init -
        // see that method's own comment for why (reliable timeout surfacing, WireMock HTTP/1.1
        // compatibility in tests).
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
    @CircuitBreaker(name = "llmProvider-groq")
    @Retry(name = "llmProvider-groq")
    public LlmProviderResult generate(LlmProviderRequest request) {
        LlmProperties.Groq config = properties.getGroq();
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw LlmException.notConfigured(
                    "LLM Service has no Groq API key configured (GROQ_API_KEY is unset) - cannot call the provider.");
        }

        String model = request.model() != null && !request.model().isBlank() ? request.model() : config.getModel();
        long maxTokens = request.maxTokens() > 0 ? request.maxTokens() : config.getDefaultMaxTokens();

        List<GroqRequest.GroqMessage> messages = new ArrayList<>();
        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            messages.add(new GroqRequest.GroqMessage("system", request.systemPrompt()));
        }
        messages.add(new GroqRequest.GroqMessage("user", request.prompt()));

        // Phase 5.2 - map provider-neutral tool definitions to Groq's OpenAI-shaped
        // tools/tool_choice. Mirrors GeminiLlmProvider.generate's identical mapping loop. Groq
        // rejects a tool call the model attempts when tool_choice is absent (defaults to "none"),
        // so tool_choice is only ever sent (as "auto") when tools is non-empty - never sent
        // otherwise, preserving this provider's exact existing no-tools request shape.
        List<Map<String, Object>> groqTools = null;
        String toolChoice = null;
        if (request.tools() != null && !request.tools().isEmpty()) {
            groqTools = request.tools().stream()
                    .map(tool -> {
                        Map<String, Object> function = new LinkedHashMap<>();
                        function.put("name", tool.get("name"));
                        function.put("description", tool.get("description"));
                        Object inputSchema = tool.get("inputSchema");
                        function.put("parameters", inputSchema instanceof Map ? inputSchema : new LinkedHashMap<>());
                        Map<String, Object> declaration = new LinkedHashMap<>();
                        declaration.put("type", "function");
                        declaration.put("function", function);
                        return declaration;
                    })
                    .toList();
            toolChoice = "auto";
        }

        GroqRequest body = new GroqRequest(model, messages, maxTokens, request.temperature(), groqTools, toolChoice);

        long start = System.currentTimeMillis();
        try {
            GroqResponse response = restClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(GroqResponse.class);
            long latencyMs = System.currentTimeMillis() - start;
            return toResult(response, model, latencyMs);
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
            throw LlmException.credentialsRejected(
                    "Groq provider rejected the configured credentials: " + safeBody(e));
        } catch (HttpClientErrorException.TooManyRequests e) {
            throw LlmException.rateLimited("Groq provider rate limit exceeded: " + safeBody(e));
        } catch (HttpClientErrorException ex) {
            // Covers 400/404/422 - a malformed/invalid request, never retryable, matching
            // GeminiLlmProvider's/AnthropicLlmProvider's identical mapping.
            throw LlmException.invalidRequest("Groq provider rejected the request as invalid: " + safeBody(ex));
        } catch (HttpServerErrorException e) {
            throw LlmException.providerUnavailable("Groq provider returned a server error: " + safeBody(e));
        } catch (ResourceAccessException e) {
            throw LlmException.timeout("Groq provider call timed out or the connection failed: " + e.getMessage());
        } catch (RestClientException e) {
            throw LlmException.internalError("Unexpected Groq provider error: " + e.getMessage());
        }
    }

    // Same rationale as GeminiLlmProvider's own safeBody: the response BODY (not just
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

    private LlmProviderResult toResult(GroqResponse response, String requestedModel, long latencyMs) {
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw LlmException.responseInvalid("Groq provider returned an empty response body.");
        }

        String actualModel = response.model() != null && !response.model().isBlank() ? response.model() : requestedModel;

        GroqResponse.Choice choice = response.choices().get(0);
        String finishReason = choice.finishReason() != null ? choice.finishReason() : "unknown";
        boolean refused = "content_filter".equals(finishReason);

        // Phase 5.2 - mirrors GeminiLlmProvider.toResult's identical tool-call handling: when Groq
        // returns tool_calls, synthesize the same {"action":"CALL_TOOL",...} JSON content string
        // AgentPlanner.parsePlan() already parses regardless of which provider produced it, so no
        // downstream/agent-orchestrator change is needed for a second tool-calling provider.
        String content;
        List<GroqResponse.ToolCall> toolCalls = choice.message() != null ? choice.message().toolCalls() : null;
        if (toolCalls != null && !toolCalls.isEmpty() && toolCalls.get(0).function() != null) {
            GroqResponse.FunctionCall function = toolCalls.get(0).function();
            String toolName = function.name();
            String argumentsJson = function.arguments() != null && !function.arguments().isBlank()
                    ? function.arguments() : "{}";
            content = "{\"action\": \"CALL_TOOL\", \"reasoning\": \"\", \"tool\": \"" + toolName + "\", \"arguments\": " + argumentsJson + "}";
        } else {
            content = choice.message() != null && choice.message().content() != null ? choice.message().content() : "";
        }

        return new LlmProviderResult(
                PROVIDER_NAME,
                actualModel,
                content,
                finishReason,
                refused,
                usageOrZero(response, GroqResponse.Usage::promptTokens),
                usageOrZero(response, GroqResponse.Usage::completionTokens),
                null,
                null,
                latencyMs
        );
    }

    private long usageOrZero(GroqResponse response, java.util.function.Function<GroqResponse.Usage, Long> extractor) {
        if (response.usage() == null) {
            return 0L;
        }
        Long value = extractor.apply(response.usage());
        return value != null ? value : 0L;
    }
}
