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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * English:
 * The ONE class in this service that talks Embedding Service's real
 * wire format (Step 8's "Do NOT duplicate embedding provider logic" -
 * this client duplicates nothing about HOW an embedding is computed, it
 * only calls Embedding Service's own already-built
 * POST /api/v1/embeddings, Phase 3.4). Deliberately has NO
 * compile-time dependency on paymentx-embedding-service's own DTO
 * classes - matches Control Center's AiPlatformClient/ServiceHealthClient
 * exact JsonNode-parsing pattern, so this module never needs to be
 * rebuilt just because Embedding Service's internal DTO shape changes.
 * `requestFactory(SimpleClientHttpRequestFactory::new)` pins plain
 * HTTP/1.1 - see EmbeddingServiceImpl's/OpenAiEmbeddingProvider's own
 * identical fix in Phase 3.4 for why (Spring Boot's auto-detected
 * java.net.http.HttpClient factory negotiates HTTP/2 cleartext against
 * WireMock's embedded Jetty in tests and intermittently fails there;
 * harmless against a real Tomcat target in production, which does not
 * offer HTTP/2 cleartext).
 * Why it exists: Step 8 - RAG Service must use the existing Embedding
 * Service, never call an embedding provider directly.
 * How it communicates with other components: injected into
 * RagServiceImpl; the ONLY class in this module that ever calls
 * Embedding Service.
 *
 * Hinglish:
 * Is service ki ek hi class jo Embedding Service ka real wire format
 * bolti hai (Step 8 ka "embedding provider logic duplicate MAT karo" -
 * ye client ISKE baare me kuch bhi duplicate nahi karta ki ek embedding
 * kaise compute hota hai, ye sirf Embedding Service ka apna already-
 * built POST /api/v1/embeddings, Phase 3.4, call karta hai). Jaan-
 * boojh kar paymentx-embedding-service ke apne DTO classes par koi
 * compile-time dependency nahi hai - Control Center ke AiPlatformClient/
 * ServiceHealthClient ke exact JsonNode-parsing pattern se match karta
 * hai, taaki is module ko kabhi sirf isliye rebuild na karna pade
 * kyunki Embedding Service ka internal DTO shape badal gaya.
 * `requestFactory(SimpleClientHttpRequestFactory::new)` plain HTTP/1.1
 * pin karta hai - Phase 3.4 me EmbeddingServiceImpl/OpenAiEmbeddingProvider
 * ka apna identical fix dekho ki kyun (Spring Boot ka auto-detected
 * java.net.http.HttpClient factory tests me WireMock ke embedded Jetty
 * ke against HTTP/2 cleartext negotiate karta hai aur wahan intermittently
 * fail hota hai; production me ek real Tomcat target ke against
 * harmless hai, jo HTTP/2 cleartext offer nahi karta).
 * Ye kyu hai: Step 8 - RAG Service ko existing Embedding Service use
 * karni chahiye, kabhi ek embedding provider ko seedhe call nahi karna
 * chahiye.
 * Dusre components se kaise communicate karta hai: RagServiceImpl me
 * inject hota hai; is module ki ek hi class jo kabhi Embedding Service
 * ko call karti hai.
 */
@Component
@Slf4j
public class EmbeddingServiceClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EmbeddingServiceClient(RestTemplateBuilder builder, RagProperties properties) {
        this.restTemplate = builder
                .requestFactory(SimpleClientHttpRequestFactory::new)
                .connectTimeout(Duration.ofMillis(properties.getEmbeddingConnectTimeoutMs()))
                .readTimeout(Duration.ofMillis(properties.getEmbeddingReadTimeoutMs()))
                .build();
    }

    @CircuitBreaker(name = "embeddingService")
    @Retry(name = "embeddingService")
    public List<Float> embed(String baseUrl, String query, String model, String correlationId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (correlationId != null) {
            headers.add(HeaderConstants.CORRELATION_ID, correlationId);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("text", query);
        body.put("model", model);

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    baseUrl + "/api/v1/embeddings", HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
            JsonNode embeddingNode = response.getBody() != null ? response.getBody().path("data").path("embedding") : null;
            if (embeddingNode == null || !embeddingNode.isArray() || embeddingNode.isEmpty()) {
                throw RagException.embeddingServiceUnavailable("Embedding Service returned an empty embedding.", false);
            }
            List<Float> vector = new ArrayList<>(embeddingNode.size());
            for (JsonNode value : embeddingNode) {
                vector.add((float) value.asDouble());
            }
            return vector;
        } catch (HttpStatusCodeException httpError) {
            int status = httpError.getStatusCode().value();
            boolean retryable = status == 429 || status >= 500;
            String message = extractErrorMessage(httpError.getResponseBodyAsString(), httpError.getMessage());
            log.warn("Embedding Service call failed httpStatus={} message={}", status, message);
            throw RagException.embeddingServiceUnavailable("Embedding Service call failed: " + message, retryable);
        } catch (ResourceAccessException connectionFailure) {
            log.warn("Embedding Service unreachable reason={}", connectionFailure.getMessage());
            throw RagException.embeddingServiceUnavailable("Embedding Service is unreachable: " + connectionFailure.getMessage(), true);
        } catch (RestClientException unparseable) {
            throw RagException.embeddingServiceUnavailable("Embedding Service response could not be parsed: " + unparseable.getMessage(), false);
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
