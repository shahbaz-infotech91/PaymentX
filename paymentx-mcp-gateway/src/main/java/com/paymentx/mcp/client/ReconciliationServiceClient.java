package com.paymentx.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentx.common.constant.HeaderConstants;
import com.paymentx.mcp.config.McpGatewayProperties;
import com.paymentx.mcp.exception.McpException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Optional;

/**
 * English:
 * The ONE class in this module that talks reconciliation-service's real
 * wire format - backs tool/ReconciliationStatusTool (Step 9/11). Called
 * on reconciliation-service's own direct port (8087, no API Gateway
 * route exists for it today - matching Control Center's
 * ApiTesterAllowlist's already-verified split). getBatchStatus() calls
 * GET /api/v1/reconciliation/batches/{batchId}; getSummary() calls GET
 * /api/v1/reconciliation/batches/{batchId}/summary - both real,
 * already-existing ReconciliationController endpoints. A real HTTP 404
 * (no such batch id) is returned as Optional.empty(), a legitimate
 * business result, not thrown as an exception (Step 33).
 * Why it exists: Step 9/11.
 * How it communicates with other components: injected into
 * tool/ReconciliationStatusTool; the ONLY class in this module that
 * ever calls reconciliation-service.
 *
 * Hinglish:
 * Ye is module ki ek hi class hai jo reconciliation-service ka real
 * wire format bolti hai - tool/ReconciliationStatusTool ko backing deti
 * hai (Step 9/11). reconciliation-service ke apne direct port (8087)
 * par call hoti hai, aaj iske liye koi API Gateway route exist nahi
 * karta (Control Center ke ApiTesterAllowlist ke already-verified split
 * se match karte hue). getBatchStatus() GET
 * /api/v1/reconciliation/batches/{batchId} call karta hai; getSummary()
 * GET /api/v1/reconciliation/batches/{batchId}/summary call karta hai -
 * dono real, already-existing ReconciliationController endpoints. Ek
 * real HTTP 404 (aisa koi batch id nahi hai) Optional.empty() ke roop
 * me return hota hai, ek legitimate business result, ek exception ke
 * roop me throw nahi hota (Step 33).
 * Ye kyu hai: Step 9/11.
 * Dusre components se kaise communicate karta hai: tool/
 * ReconciliationStatusTool me inject hota hai; is module ki ek hi class
 * jo kabhi reconciliation-service ko call karti hai.
 */
@Component
@Slf4j
public class ReconciliationServiceClient {

    private final RestTemplate restTemplate;
    private final McpGatewayProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ReconciliationServiceClient(RestTemplateBuilder builder, McpGatewayProperties properties) {
        this.properties = properties;
        this.restTemplate = builder
                .requestFactory(SimpleClientHttpRequestFactory::new)
                .connectTimeout(Duration.ofMillis(properties.getReconciliationConnectTimeoutMs()))
                .readTimeout(Duration.ofMillis(properties.getReconciliationReadTimeoutMs()))
                .build();
    }

    @CircuitBreaker(name = "reconciliationService")
    @Retry(name = "reconciliationService")
    public Optional<JsonNode> getBatchStatus(String batchId, String correlationId) {
        return get("/api/v1/reconciliation/batches/" + batchId, correlationId);
    }

    @CircuitBreaker(name = "reconciliationService")
    @Retry(name = "reconciliationService")
    public Optional<JsonNode> getSummary(String batchId, String correlationId) {
        return get("/api/v1/reconciliation/batches/" + batchId + "/summary", correlationId);
    }

    private Optional<JsonNode> get(String path, String correlationId) {
        HttpHeaders headers = new HttpHeaders();
        if (correlationId != null) {
            headers.add(HeaderConstants.CORRELATION_ID, correlationId);
        }
        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    properties.getReconciliationServiceUrl() + path, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
            JsonNode data = response.getBody() != null ? response.getBody().path("data") : null;
            if (data == null || data.isMissingNode() || data.isNull()) {
                throw McpException.targetServiceUnavailable("reconciliation-service returned no data.", false);
            }
            return Optional.of(data);
        } catch (HttpClientErrorException.NotFound notFound) {
            return Optional.empty();
        } catch (HttpStatusCodeException httpError) {
            int status = httpError.getStatusCode().value();
            boolean retryable = status == 429 || status >= 500;
            String message = extractErrorMessage(httpError.getResponseBodyAsString(), httpError.getMessage());
            log.warn("reconciliation-service call failed path={} httpStatus={} message={}", path, status, message);
            throw McpException.targetServiceUnavailable("reconciliation-service call failed: " + message, retryable);
        } catch (ResourceAccessException connectionFailure) {
            log.warn("reconciliation-service unreachable path={} reason={}", path, connectionFailure.getMessage());
            throw McpException.targetServiceUnavailable("reconciliation-service is unreachable: " + connectionFailure.getMessage(), true);
        } catch (RestClientException unparseable) {
            throw McpException.targetServiceUnavailable("reconciliation-service response could not be parsed: " + unparseable.getMessage(), false);
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
