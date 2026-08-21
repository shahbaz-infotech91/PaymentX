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
 * The ONE class in this module that talks Prompt Service's real wire
 * format - renders the real, managed PAYMENTX_AGENT_ORCHESTRATOR prompt
 * (Step 24 - "Do NOT hardcode the complete Agent system prompt inside
 * Java code") via the real, already-existing
 * POST /api/v1/prompts/{promptKey}/render. Deliberately has NO compile-
 * time dependency on paymentx-prompt-service's own DTO classes,
 * matching every other client in this platform's AI Platform services.
 * Why it exists: Step 24.
 * How it communicates with other components: injected into
 * planning/AgentPlanner; the ONLY class in this module that ever calls
 * Prompt Service.
 *
 * Hinglish:
 * Ye is module ki ek hi class hai jo Prompt Service ka real wire format
 * bolti hai - real, managed PAYMENTX_AGENT_ORCHESTRATOR prompt ko
 * render karta hai (Step 24 - "Poora Agent system prompt Java code ke
 * andar hardcode MAT karo") real, already-existing
 * POST /api/v1/prompts/{promptKey}/render ke through. Jaan-boojh kar
 * paymentx-prompt-service ke apne DTO classes par koi compile-time
 * dependency nahi hai, is platform ki AI Platform services ke har
 * doosre client se match karte hue.
 * Ye kyu hai: Step 24.
 * Dusre components se kaise communicate karta hai: planning/
 * AgentPlanner me inject hota hai; is module ki ek hi class jo kabhi
 * Prompt Service ko call karti hai.
 */
@Component
@Slf4j
public class PromptServiceClient {

    private final RestTemplate restTemplate;
    private final AgentOrchestratorProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PromptServiceClient(RestTemplateBuilder builder, AgentOrchestratorProperties properties) {
        this.properties = properties;
        this.restTemplate = builder
                .requestFactory(SimpleClientHttpRequestFactory::new)
                .connectTimeout(Duration.ofMillis(properties.getPromptConnectTimeoutMs()))
                .readTimeout(Duration.ofMillis(properties.getPromptReadTimeoutMs()))
                .build();
    }

    @CircuitBreaker(name = "promptService")
    @Retry(name = "promptService")
    public String render(String promptKey, Map<String, String> variables, String correlationId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (correlationId != null) {
            headers.add(HeaderConstants.CORRELATION_ID, correlationId);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("variables", variables);

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    properties.getPromptServiceUrl() + "/api/v1/prompts/" + promptKey + "/render",
                    HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
            JsonNode data = response.getBody() != null ? response.getBody().path("data") : null;
            String renderedContent = data != null ? data.path("renderedContent").asText(null) : null;
            if (renderedContent == null || renderedContent.isBlank()) {
                throw AgentException.promptServiceUnavailable("Prompt Service returned no rendered content.", false);
            }
            return renderedContent;
        } catch (HttpStatusCodeException httpError) {
            int status = httpError.getStatusCode().value();
            boolean retryable = status == 429 || status >= 500;
            String message = extractErrorMessage(httpError.getResponseBodyAsString(), httpError.getMessage());
            log.warn("Prompt Service render call failed httpStatus={} message={}", status, message);
            throw AgentException.promptServiceUnavailable("Prompt Service call failed: " + message, retryable);
        } catch (ResourceAccessException connectionFailure) {
            log.warn("Prompt Service unreachable reason={}", connectionFailure.getMessage());
            throw AgentException.promptServiceUnavailable("Prompt Service is unreachable: " + connectionFailure.getMessage(), true);
        } catch (RestClientException unparseable) {
            throw AgentException.promptServiceUnavailable("Prompt Service response could not be parsed: " + unparseable.getMessage(), false);
        }
    }

    private String extractErrorMessage(String rawBody, String fallback) {
        try {
            JsonNode errorMessage = objectMapper.readTree(rawBody).path("error").path("message");
            return errorMessage.isMissingNode() || errorMessage.isNull() ? fallback : errorMessage.asText();
        } catch (Exception parseFailure) {
            return fallback;
        }
    }
}
