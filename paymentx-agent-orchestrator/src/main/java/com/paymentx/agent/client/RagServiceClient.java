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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * English:
 * The ONE class in this module that talks RAG Service's real wire
 * format (Step 5 - "Use the existing RAG Service. Do NOT duplicate
 * embedding generation, vector search, context building, prompt
 * construction, RAG logic"). Calls the real, already-existing
 * POST /api/v1/rag/query - never re-implements retrieval. Deliberately
 * has NO compile-time dependency on paymentx-rag-service's own DTO
 * classes - matches every other client in this platform's AI Platform
 * services. Never treats a real INSUFFICIENT_CONTEXT/REFUSED status as
 * an HTTP error - those are RAG Service's own honest, non-error
 * outcomes (RagQueryStatus), returned here as-is inside a normal
 * RagQueryResult.
 * Why it exists: Step 5.
 * How it communicates with other components: injected into
 * orchestrator/AgentOrchestratorService; the ONLY class in this module
 * that ever calls RAG Service.
 *
 * Hinglish:
 * Ye is module ki ek hi class hai jo RAG Service ka real wire format
 * bolti hai (Step 5 - "Existing RAG Service use karo. Embedding
 * generation, vector search, context building, prompt construction, RAG
 * logic duplicate MAT karo"). Real, already-existing POST
 * /api/v1/rag/query call karta hai - retrieval kabhi re-implement nahi
 * karta. Jaan-boojh kar paymentx-rag-service ke apne DTO classes par
 * koi compile-time dependency nahi hai - is platform ki AI Platform
 * services ke har doosre client se match karta hai. Ek real
 * INSUFFICIENT_CONTEXT/REFUSED status ko kabhi ek HTTP error treat nahi
 * karta - wo RAG Service ke apne honest, non-error outcomes hain
 * (RagQueryStatus), yahan as-is ek normal RagQueryResult ke andar
 * return hote hain.
 * Ye kyu hai: Step 5.
 * Dusre components se kaise communicate karta hai: orchestrator/
 * AgentOrchestratorService me inject hota hai; is module ki ek hi class
 * jo kabhi RAG Service ko call karti hai.
 */
@Component
@Slf4j
public class RagServiceClient {

    public record RagQueryResult(String answer, String status, List<SourceRecord> sources) {
    }

    public record SourceRecord(String documentId, String chunkId, String source, double score) {
    }

    private final RestTemplate restTemplate;
    private final AgentOrchestratorProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RagServiceClient(RestTemplateBuilder builder, AgentOrchestratorProperties properties) {
        this.properties = properties;
        this.restTemplate = builder
                .requestFactory(SimpleClientHttpRequestFactory::new)
                .connectTimeout(Duration.ofMillis(properties.getRagConnectTimeoutMs()))
                .readTimeout(Duration.ofMillis(properties.getRagReadTimeoutMs()))
                .build();
    }

    @CircuitBreaker(name = "ragService")
    @Retry(name = "ragService")
    public RagQueryResult query(String query, String correlationId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (correlationId != null) {
            headers.add(HeaderConstants.CORRELATION_ID, correlationId);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", query);

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    properties.getRagServiceUrl() + "/api/v1/rag/query", HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
            JsonNode data = response.getBody() != null ? response.getBody().path("data") : null;
            if (data == null || data.isMissingNode()) {
                throw AgentException.ragServiceUnavailable("RAG Service returned no data.", false);
            }

            List<SourceRecord> sources = new ArrayList<>();
            for (JsonNode source : data.path("sources")) {
                sources.add(new SourceRecord(
                        source.path("documentId").asText(null),
                        source.path("chunkId").asText(null),
                        source.path("source").asText(null),
                        source.path("score").asDouble()));
            }

            return new RagQueryResult(data.path("answer").asText(""), data.path("status").asText("SUCCESS"), sources);
        } catch (HttpStatusCodeException httpError) {
            int status = httpError.getStatusCode().value();
            boolean retryable = status == 429 || status >= 500;
            String message = extractErrorMessage(httpError.getResponseBodyAsString(), httpError.getMessage());
            log.warn("RAG Service call failed httpStatus={} message={}", status, message);
            throw AgentException.ragServiceUnavailable("RAG Service call failed: " + message, retryable);
        } catch (ResourceAccessException connectionFailure) {
            log.warn("RAG Service unreachable reason={}", connectionFailure.getMessage());
            throw AgentException.ragServiceUnavailable("RAG Service is unreachable: " + connectionFailure.getMessage(), true);
        } catch (RestClientException unparseable) {
            throw AgentException.ragServiceUnavailable("RAG Service response could not be parsed: " + unparseable.getMessage(), false);
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
