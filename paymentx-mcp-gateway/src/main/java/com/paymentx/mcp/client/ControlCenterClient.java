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
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Phase 4.4 - the ONE class in this module that talks Control Center's real wire format, backing
 * tool/DatabaseStatisticsTool. Calls Control Center's own, ALREADY-EXISTING, read-only
 * PostgresController endpoints directly (not through the API Gateway - Control Center is not one of
 * the two services the Gateway currently routes to, same "called directly" treatment
 * routing/audit/reconciliation-service already get - see McpGatewayProperties.controlCenterUrl
 * javadoc) - never a new SQL query, never a new database connection anywhere in this module.
 * PostgresController itself guarantees no PUT/POST/DELETE exists and no endpoint accepts raw SQL
 * (see that controller's own javadoc, verified by direct source read before this class was written) -
 * this client only ever issues GET requests to two of its endpoints.
 * Same envelope-parsing/error-mapping discipline as PaymentServiceClient: reads the real
 * {@code ApiResponse<T>} envelope as a raw JsonNode (no compile-time dependency on Control Center's
 * own DTOs), any non-2xx/connection/parse failure becomes a real McpException.targetServiceUnavailable,
 * never a fabricated result.
 */
@Component
@Slf4j
public class ControlCenterClient {

    private final RestTemplate restTemplate;
    private final McpGatewayProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ControlCenterClient(RestTemplateBuilder builder, McpGatewayProperties properties) {
        this.properties = properties;
        this.restTemplate = builder
                .requestFactory(SimpleClientHttpRequestFactory::new)
                .connectTimeout(Duration.ofMillis(properties.getControlCenterConnectTimeoutMs()))
                .readTimeout(Duration.ofMillis(properties.getControlCenterReadTimeoutMs()))
                .build();
    }

    @CircuitBreaker(name = "controlCenter")
    @Retry(name = "controlCenter")
    public JsonNode getPaymentStats(String correlationId) {
        return get("/api/v1/postgres/payments/stats", correlationId);
    }

    @CircuitBreaker(name = "controlCenter")
    @Retry(name = "controlCenter")
    public JsonNode getTableInfo(String databaseSlug, String correlationId) {
        return get("/api/v1/postgres/databases/" + databaseSlug + "/tables", correlationId);
    }

    private JsonNode get(String path, String correlationId) {
        HttpHeaders headers = new HttpHeaders();
        if (correlationId != null) {
            headers.add(HeaderConstants.CORRELATION_ID, correlationId);
        }
        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    properties.getControlCenterUrl() + path, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
            JsonNode data = response.getBody() != null ? response.getBody().path("data") : null;
            if (data == null || data.isMissingNode() || data.isNull()) {
                throw McpException.targetServiceUnavailable("control-center returned no data.", false);
            }
            return data;
        } catch (HttpStatusCodeException httpError) {
            int status = httpError.getStatusCode().value();
            boolean retryable = status == 429 || status >= 500;
            String message = extractErrorMessage(httpError.getResponseBodyAsString(), httpError.getMessage());
            log.warn("control-center call failed path={} httpStatus={} message={}", path, status, message);
            throw McpException.targetServiceUnavailable("control-center call failed: " + message, retryable);
        } catch (ResourceAccessException connectionFailure) {
            log.warn("control-center unreachable path={} reason={}", path, connectionFailure.getMessage());
            throw McpException.targetServiceUnavailable("control-center is unreachable: " + connectionFailure.getMessage(), true);
        } catch (RestClientException unparseable) {
            throw McpException.targetServiceUnavailable("control-center response could not be parsed: " + unparseable.getMessage(), false);
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
