package com.paymentx.rag.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentx.common.constant.HeaderConstants;
import com.paymentx.rag.config.RagProperties;
import com.paymentx.rag.exception.RagException;
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
 * The ONE class in this service that talks Prompt Service's real wire
 * format (Step 17/18 - "Do NOT hardcode the complete final system
 * prompt inside RAG Service" - the actual RAG system prompt text lives
 * in Prompt Service's own database, seeded under the promptKey
 * `rag.rag-prompt-key` = "PAYMENTX_KNOWLEDGE_ASSISTANT" by a new
 * paymentx-prompt-service Liquibase changeset this phase adds - see
 * that migration's own javadoc for why, unlike Phase 3.2's DRAFT-only
 * PAYMENT_ERROR_ANALYSIS seed, this one is seeded ACTIVE: RAG Service's
 * core function depends on a real, renderable prompt existing from the
 * moment this phase ships, not a future consumer's speculative need).
 * Calls Prompt Service's own already-built
 * POST /api/v1/prompts/{key}/render, Phase 3.2 - never constructs
 * prompt text itself beyond supplying the `context`/`question`
 * variables Prompt Service's template substitutes.
 * Why it exists: Step 17 - RAG Service must use Prompt Service, never
 * hardcode or bypass it.
 * How it communicates with other components: injected into
 * RagServiceImpl; the ONLY class in this module that ever calls Prompt
 * Service.
 *
 * Hinglish:
 * Is service ki ek hi class jo Prompt Service ka real wire format
 * bolti hai (Step 17/18 - "RAG Service ke andar poora final system
 * prompt hardcode MAT karo" - actual RAG system prompt text Prompt
 * Service ke apne database me rehta hai, promptKey `rag.rag-prompt-key`
 * = "PAYMENTX_KNOWLEDGE_ASSISTANT" ke under seeded, ek naye
 * paymentx-prompt-service Liquibase changeset se jo ye phase add karta
 * hai - us migration ka apna javadoc dekho ki kyun, Phase 3.2 ke
 * DRAFT-only PAYMENT_ERROR_ANALYSIS seed ke ulat, ye ACTIVE seeded hai:
 * RAG Service ka core function ek real, renderable prompt ke exist
 * karne par depend karta hai jis pal ye phase ship hoti hai, ek future
 * consumer ki speculative need nahi). Prompt Service ka apna already-
 * built POST /api/v1/prompts/{key}/render, Phase 3.2, call karta hai -
 * khud kabhi prompt text construct nahi karta, sirf `context`/`question`
 * variables supply karta hai jinhe Prompt Service ka template
 * substitute karta hai.
 * Ye kyu hai: Step 17 - RAG Service ko Prompt Service use karni chahiye,
 * kabhi hardcode ya bypass nahi karna chahiye.
 * Dusre components se kaise communicate karta hai: RagServiceImpl me
 * inject hota hai; is module ki ek hi class jo kabhi Prompt Service ko
 * call karti hai.
 */
@Component
@Slf4j
public class PromptServiceClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PromptServiceClient(RestTemplateBuilder builder, RagProperties properties) {
        this.restTemplate = builder
                .requestFactory(SimpleClientHttpRequestFactory::new)
                .connectTimeout(Duration.ofMillis(properties.getPromptConnectTimeoutMs()))
                .readTimeout(Duration.ofMillis(properties.getPromptReadTimeoutMs()))
                .build();
    }

    @CircuitBreaker(name = "promptService")
    @Retry(name = "promptService")
    public String render(String baseUrl, String promptKey, String context, String question, String correlationId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (correlationId != null) {
            headers.add(HeaderConstants.CORRELATION_ID, correlationId);
        }

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("context", context);
        variables.put("question", question);
        Map<String, Object> body = Map.of("variables", variables);

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    baseUrl + "/api/v1/prompts/" + promptKey + "/render", HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
            JsonNode rendered = response.getBody() != null ? response.getBody().path("data").path("renderedContent") : null;
            if (rendered == null || rendered.isMissingNode() || rendered.asText().isBlank()) {
                throw RagException.promptServiceUnavailable("Prompt Service returned an empty rendered prompt.", false);
            }
            return rendered.asText();
        } catch (HttpStatusCodeException httpError) {
            int status = httpError.getStatusCode().value();
            boolean retryable = status == 429 || status >= 500;
            String message = extractErrorMessage(httpError.getResponseBodyAsString(), httpError.getMessage());
            log.warn("Prompt Service call failed httpStatus={} message={}", status, message);
            throw RagException.promptServiceUnavailable("Prompt Service call failed: " + message, retryable);
        } catch (ResourceAccessException connectionFailure) {
            log.warn("Prompt Service unreachable reason={}", connectionFailure.getMessage());
            throw RagException.promptServiceUnavailable("Prompt Service is unreachable: " + connectionFailure.getMessage(), true);
        } catch (RestClientException unparseable) {
            throw RagException.promptServiceUnavailable("Prompt Service response could not be parsed: " + unparseable.getMessage(), false);
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
