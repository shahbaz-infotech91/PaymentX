package com.paymentx.rag.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentx.common.constant.HeaderConstants;
import com.paymentx.rag.config.RagProperties;
import com.paymentx.rag.dto.RetrievedChunk;
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
 * The ONE class in this service that talks Vector Service's real wire
 * format (Step 9 - "Do NOT directly access pgvector tables from RAG
 * Service" - this class never touches Postgres/pgvector at all, only
 * Vector Service's own already-built POST /api/v1/vector/search, Phase
 * 3.5). Deliberately has NO compile-time dependency on
 * paymentx-vector-service's own DTO/entity classes - matches every
 * other client in this package. WHY `minScore` is never passed to
 * Vector Service's own search request here (even though Vector Service
 * supports it): RagServiceImpl needs to see the FULL raw top-K
 * (including candidates below its own configured threshold) so it can
 * count real rejections for the rag_relevance_threshold_rejections
 * metric and RagQueryMetadata.rejectedByThreshold - delegating
 * filtering to Vector Service would make those numbers always read
 * zero, since the rejected candidates would never come back at all
 * (see RagProperties' javadoc for the same reasoning from the
 * configuration side).
 * Why it exists: Step 9/10 - use the existing Vector Database API,
 * respecting topK/filters/dimension/model compatibility, never
 * reimplementing search.
 * How it communicates with other components: injected into
 * RagServiceImpl; the ONLY class in this module that ever calls Vector
 * Service.
 *
 * Hinglish:
 * Is service ki ek hi class jo Vector Service ka real wire format
 * bolti hai (Step 9 - "RAG Service se seedhe pgvector tables access MAT
 * karo" - ye class kabhi Postgres/pgvector ko seedhe nahi chhuti, sirf
 * Vector Service ka apna already-built POST /api/v1/vector/search,
 * Phase 3.5). Jaan-boojh kar paymentx-vector-service ke apne DTO/entity
 * classes par koi compile-time dependency nahi hai - is package ke har
 * doosre client se match karta hai. `minScore` yahan Vector Service ke
 * apne search request ko kabhi pass NAHI hota (chahe Vector Service ise
 * support karta ho) KYU: RagServiceImpl ko poora raw top-K dekhna hota
 * hai (uske apne configured threshold se neeche wale candidates sameत)
 * taaki wo rag_relevance_threshold_rejections metric aur
 * RagQueryMetadata.rejectedByThreshold ke liye real rejections count
 * kar sake - filtering ko Vector Service ko delegate karna un numbers
 * ko hamesha zero dikha deta, kyunki rejected candidates wapas aate hi
 * nahi (RagProperties ka javadoc dekho configuration side se wahi
 * reasoning ke liye).
 * Ye kyu hai: Step 9/10 - existing Vector Database API use karo,
 * topK/filters/dimension/model compatibility respect karte hue, kabhi
 * search reimplement na karo.
 * Dusre components se kaise communicate karta hai: RagServiceImpl me
 * inject hota hai; is module ki ek hi class jo kabhi Vector Service ko
 * call karti hai.
 */
@Component
@Slf4j
public class VectorServiceClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public VectorServiceClient(RestTemplateBuilder builder, RagProperties properties) {
        this.restTemplate = builder
                .requestFactory(SimpleClientHttpRequestFactory::new)
                .connectTimeout(Duration.ofMillis(properties.getVectorConnectTimeoutMs()))
                .readTimeout(Duration.ofMillis(properties.getVectorReadTimeoutMs()))
                .build();
    }

    @CircuitBreaker(name = "vectorService")
    @Retry(name = "vectorService")
    public List<RetrievedChunk> search(String baseUrl, List<Float> queryEmbedding, String provider, String model,
                                        int topK, Map<String, Object> filters, String correlationId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (correlationId != null) {
            headers.add(HeaderConstants.CORRELATION_ID, correlationId);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("queryEmbedding", queryEmbedding);
        body.put("provider", provider);
        body.put("model", model);
        body.put("topK", topK);
        if (filters != null && !filters.isEmpty()) {
            body.put("filters", filters);
        }

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    baseUrl + "/api/v1/vector/search", HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
            JsonNode resultsNode = response.getBody() != null ? response.getBody().path("data").path("results") : null;
            if (resultsNode == null || !resultsNode.isArray()) {
                throw RagException.vectorServiceUnavailable("Vector Service returned no results array.", false);
            }

            List<RetrievedChunk> chunks = new ArrayList<>(resultsNode.size());
            for (JsonNode item : resultsNode) {
                chunks.add(new RetrievedChunk(
                        item.path("documentId").asText(null),
                        item.path("chunkId").asText(null),
                        item.path("documentKey").asText(null),
                        item.path("content").asText(""),
                        item.path("score").asDouble(),
                        item.path("distance").asDouble()));
            }
            return chunks;
        } catch (HttpStatusCodeException httpError) {
            int status = httpError.getStatusCode().value();
            boolean retryable = status == 429 || status >= 500;
            String message = extractErrorMessage(httpError.getResponseBodyAsString(), httpError.getMessage());
            log.warn("Vector Service call failed httpStatus={} message={}", status, message);
            throw RagException.vectorServiceUnavailable("Vector Service call failed: " + message, retryable);
        } catch (ResourceAccessException connectionFailure) {
            log.warn("Vector Service unreachable reason={}", connectionFailure.getMessage());
            throw RagException.vectorServiceUnavailable("Vector Service is unreachable: " + connectionFailure.getMessage(), true);
        } catch (RestClientException unparseable) {
            throw RagException.vectorServiceUnavailable("Vector Service response could not be parsed: " + unparseable.getMessage(), false);
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
