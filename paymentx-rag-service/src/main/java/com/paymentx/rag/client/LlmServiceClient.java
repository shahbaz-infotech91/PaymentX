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
import java.util.Map;

/**
 * English:
 * The ONE class in this service that talks LLM Service's real wire
 * format (Step 19 - "Do NOT call the provider directly" - this class
 * never imports com.anthropic.* or knows any provider exists; it only
 * calls LLM Service's own already-built POST /api/v1/llm/generate,
 * Phase 3.3). `refused` is read straight from LLM Service's real
 * response and returned to RagServiceImpl as-is (never reinterpreted
 * here) - RagServiceImpl is the one place that decides what a refusal
 * means for RagQueryStatus (see that class's javadoc). LLM_PROVIDER_TIMEOUT
 * specifically maps to RagException.llmTimeout (Step 28's distinct
 * LLM_TIMEOUT code), not the generic llmServiceUnavailable - a caller
 * telling "the LLM itself timed out" apart from "LLM Service is down"
 * is real, useful information this client preserves rather than
 * collapsing into one generic failure.
 * Why it exists: Step 19/20 - RAG Service must use LLM Service, never
 * call an LLM provider directly, and the final answer must come from a
 * real LLM Service call with the grounded prompt.
 * How it communicates with other components: injected into
 * RagServiceImpl; the ONLY class in this module that ever calls LLM
 * Service.
 *
 * Hinglish:
 * Is service ki ek hi class jo LLM Service ka real wire format bolti
 * hai (Step 19 - "provider ko seedhe call MAT karo" - ye class kabhi
 * com.anthropic.* import nahi karti ya kisi provider ke exist karne ke
 * baare me jaanti hi nahi; ye sirf LLM Service ka apna already-built
 * POST /api/v1/llm/generate, Phase 3.3, call karti hai). `refused` LLM
 * Service ke real response se seedhe padha jaata hai aur RagServiceImpl
 * ko as-is return hota hai (yahan kabhi reinterpret nahi hota) -
 * RagServiceImpl hi wo ek jagah hai jo decide karti hai ki ek refusal
 * ka RagQueryStatus ke liye kya matlab hai (us class ka javadoc dekho).
 * LLM_PROVIDER_TIMEOUT specifically RagException.llmTimeout par map
 * hota hai (Step 28 ka distinct LLM_TIMEOUT code), generic
 * llmServiceUnavailable nahi - ek caller ko "LLM khud timeout ho gaya"
 * ko "LLM Service down hai" se alag bata sakna real, useful information
 * hai jo ye client preserve karta hai, ek generic failure me collapse
 * karne ke bajaye.
 * Ye kyu hai: Step 19/20 - RAG Service ko LLM Service use karni chahiye,
 * kabhi ek LLM provider ko seedhe call nahi karna chahiye, aur final
 * answer ek real LLM Service call se grounded prompt ke saath aani
 * chahiye.
 * Dusre components se kaise communicate karta hai: RagServiceImpl me
 * inject hota hai; is module ki ek hi class jo kabhi LLM Service ko
 * call karti hai.
 */
@Component
@Slf4j
public class LlmServiceClient {

    public record LlmAnswer(String content, boolean refused, String model, String provider) {
    }

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LlmServiceClient(RestTemplateBuilder builder, RagProperties properties) {
        this.restTemplate = builder
                .requestFactory(SimpleClientHttpRequestFactory::new)
                .connectTimeout(Duration.ofMillis(properties.getLlmConnectTimeoutMs()))
                .readTimeout(Duration.ofMillis(properties.getLlmReadTimeoutMs()))
                .build();
    }

    @CircuitBreaker(name = "llmService")
    @Retry(name = "llmService")
    public LlmAnswer generate(String baseUrl, String prompt, String correlationId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (correlationId != null) {
            headers.add(HeaderConstants.CORRELATION_ID, correlationId);
        }

        Map<String, Object> body = Map.of("prompt", prompt);

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    baseUrl + "/api/v1/llm/generate", HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
            JsonNode data = response.getBody() != null ? response.getBody().path("data") : null;
            if (data == null || data.isMissingNode()) {
                throw RagException.llmServiceUnavailable("LLM Service returned an empty response.", false);
            }
            return new LlmAnswer(
                    data.path("content").asText(""),
                    data.path("refused").asBoolean(false),
                    data.path("model").asText(null),
                    data.path("provider").asText(null));
        } catch (HttpStatusCodeException httpError) {
            int status = httpError.getStatusCode().value();
            String errorCode = extractErrorCode(httpError.getResponseBodyAsString());
            String message = extractErrorMessage(httpError.getResponseBodyAsString(), httpError.getMessage());
            log.warn("LLM Service call failed httpStatus={} errorCode={} message={}", status, errorCode, message);
            if ("LLM_PROVIDER_TIMEOUT".equals(errorCode)) {
                throw RagException.llmTimeout("LLM Service reported a provider timeout: " + message);
            }
            boolean retryable = status == 429 || status >= 500;
            throw RagException.llmServiceUnavailable("LLM Service call failed: " + message, retryable);
        } catch (ResourceAccessException connectionFailure) {
            log.warn("LLM Service unreachable reason={}", connectionFailure.getMessage());
            throw RagException.llmServiceUnavailable("LLM Service is unreachable: " + connectionFailure.getMessage(), true);
        } catch (RestClientException unparseable) {
            throw RagException.llmServiceUnavailable("LLM Service response could not be parsed: " + unparseable.getMessage(), false);
        }
    }

    private String extractErrorCode(String rawBody) {
        try {
            JsonNode errorCode = objectMapper.readTree(rawBody).path("error").path("errorCode");
            return errorCode.isMissingNode() ? null : errorCode.asText();
        } catch (Exception parseFailure) {
            return null;
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
