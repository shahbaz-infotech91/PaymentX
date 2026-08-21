package com.paymentx.agent.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentx.agent.config.AgentOrchestratorProperties;
import com.paymentx.agent.exception.AgentException;
import com.paymentx.common.constant.HeaderConstants;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * English:
 * The ONE class in this module that talks LLM Service's real wire
 * format - calls the real, already-existing POST /api/v1/llm/generate
 * with an already-rendered prompt (never a provider SDK directly, Step
 * 6/27). Used twice per agent run in different roles: (1) by
 * planning/AgentPlanner to get the LLM's structured JSON planning
 * decision (Step 22), and (2) by orchestrator/AgentOrchestratorService
 * for the final answer synthesis when combined RAG+tool evidence needs
 * a real LLM call to produce prose (Step 13) - both calls go through
 * this same class, since LLM Service's own contract is identical either
 * way (prompt in, content out). Special-cases LLM Service's real
 * `LLM_PROVIDER_TIMEOUT` errorCode into AgentException.llmTimeout(...)
 * (a distinct code from a generic unavailable), matching RAG Service's
 * own LlmServiceClient precedent exactly.
 * Why it exists: Step 6/22/24/27.
 * How it communicates with other components: injected into
 * planning/AgentPlanner and orchestrator/AgentOrchestratorService; the
 * ONLY class in this module that ever calls LLM Service.
 *
 * Hinglish:
 * Ye is module ki ek hi class hai jo LLM Service ka real wire format
 * bolti hai - real, already-existing POST /api/v1/llm/generate ko ek
 * already-rendered prompt ke saath call karta hai (kabhi ek provider
 * SDK seedhe nahi, Step 6/27). Ek agent run me do alag roles me use
 * hota hai: (1) planning/AgentPlanner dwara LLM ka structured JSON
 * planning decision lene ke liye (Step 22), aur (2)
 * orchestrator/AgentOrchestratorService dwara final answer synthesis
 * ke liye jab combined RAG+tool evidence ko prose banane ke liye ek
 * real LLM call chahiye (Step 13) - dono calls isi class se guzarte
 * hain, kyunki LLM Service ka apna contract dono roop me identical hai
 * (prompt andar, content bahar). LLM Service ke real `LLM_PROVIDER_TIMEOUT`
 * errorCode ko AgentException.llmTimeout(...) me special-case karta hai
 * (ek generic unavailable se alag code), exactly RAG Service ke apne
 * LlmServiceClient precedent se match karte hue.
 * Ye kyu hai: Step 6/22/24/27.
 * Dusre components se kaise communicate karta hai: planning/AgentPlanner
 * aur orchestrator/AgentOrchestratorService me inject hota hai; is
 * module ki ek hi class jo kabhi LLM Service ko call karti hai.
 */
@Component
@Slf4j
public class LlmServiceClient {

    public record LlmAnswer(String content, boolean refused) {
    }

    private final RestTemplate restTemplate;
    private final AgentOrchestratorProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LlmServiceClient(RestTemplateBuilder builder, AgentOrchestratorProperties properties) {
        this.properties = properties;
        this.restTemplate = builder
                .requestFactory(SimpleClientHttpRequestFactory::new)
                .connectTimeout(Duration.ofMillis(properties.getLlmConnectTimeoutMs()))
                .readTimeout(Duration.ofMillis(properties.getLlmReadTimeoutMs()))
                .build();
    }

    @CircuitBreaker(name = "llmService")
    @Retry(name = "llmService")
    public LlmAnswer generate(String prompt, String correlationId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (correlationId != null) {
            headers.add(HeaderConstants.CORRELATION_ID, correlationId);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("prompt", prompt);

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    properties.getLlmServiceUrl() + "/api/v1/llm/generate", HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
            JsonNode data = response.getBody() != null ? response.getBody().path("data") : null;
            if (data == null || data.isMissingNode()) {
                throw AgentException.llmServiceUnavailable("LLM Service returned no data.", false);
            }
            return new LlmAnswer(data.path("content").asText(""), data.path("refused").asBoolean(false));
        } catch (HttpStatusCodeException httpError) {
            int status = httpError.getStatusCode().value();
            JsonNode errorBody = parseBodySafely(httpError.getResponseBodyAsString());
            String errorCode = errorBody != null ? errorBody.path("error").path("errorCode").asText(null) : null;
            String message = extractErrorMessage(errorBody, httpError.getMessage());
            log.warn("LLM Service generate call failed httpStatus={} errorCode={} message={}", status, errorCode, message);

            if ("LLM_PROVIDER_TIMEOUT".equals(errorCode)) {
                throw AgentException.llmTimeout("LLM provider timed out: " + message);
            }
            boolean retryable = status == 429 || status >= 500;
            throw AgentException.llmServiceUnavailable("LLM Service call failed: " + message, retryable);
        } catch (ResourceAccessException connectionFailure) {
            log.warn("LLM Service unreachable reason={}", connectionFailure.getMessage());
            throw AgentException.llmServiceUnavailable("LLM Service is unreachable: " + connectionFailure.getMessage(), true);
        } catch (RestClientException unparseable) {
            throw AgentException.llmServiceUnavailable("LLM Service response could not be parsed: " + unparseable.getMessage(), false);
        }
    }

    private JsonNode parseBodySafely(String rawBody) {
        try {
            return objectMapper.readTree(rawBody);
        } catch (Exception e) {
            return null;
        }
    }

    private String extractErrorMessage(JsonNode errorBody, String fallback) {
        if (errorBody == null) {
            return fallback;
        }
        JsonNode errorMessage = errorBody.path("error").path("message");
        return errorMessage.isMissingNode() || errorMessage.isNull() ? fallback : errorMessage.asText();
    }
}
