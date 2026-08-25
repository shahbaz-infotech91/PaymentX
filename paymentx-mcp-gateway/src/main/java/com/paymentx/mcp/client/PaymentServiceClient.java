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
 * The ONE class in this module that talks payment-service's real wire
 * format - backs tool/PaymentLookupTool and tool/PaymentStatusTool
 * (Step 11 - MCP tools call real service APIs through their normal
 * REST surface, never a database directly). Calls THROUGH the real API
 * Gateway (port 8080, see application.yml's comment for why - payment-
 * service is one of only two services the Gateway currently routes to)
 * rather than payment-service's own direct port, matching Control
 * Center's ApiTesterAllowlist's already-verified real routing split for
 * these exact endpoints. Deliberately has NO compile-time dependency on
 * paymentx-payment-service's own DTO/entity classes - matches every
 * client class in this platform's AI Platform services (RAG Service's
 * VectorServiceClient etc.) - reads the real ApiResponse envelope as a
 * raw JsonNode instead.
 * A real HTTP 404 with payment-service's real RESOURCE_NOT_FOUND
 * envelope is NOT thrown as an exception here - it is returned as
 * Optional.empty(), a legitimate business result (Step 33 - distinguish
 * an MCP infrastructure error from a real PaymentX business result).
 * Any other failure (5xx, connection failure, unparseable response) is
 * a real infrastructure failure and IS thrown as McpException.
 * Why it exists: Step 9/11.
 * How it communicates with other components: injected into
 * tool/PaymentLookupTool and tool/PaymentStatusTool; the ONLY class in
 * this module that ever calls payment-service.
 *
 * Hinglish:
 * Ye is module ki ek hi class hai jo payment-service ka real wire
 * format bolti hai - tool/PaymentLookupTool aur tool/PaymentStatusTool
 * ko backing deti hai (Step 11 - MCP tools real service APIs ko unke
 * normal REST surface ke through call karte hain, kabhi seedhe ek
 * database nahi). Real API Gateway (port 8080, application.yml ke
 * comment me kyun dekho - payment-service un do services me se ek hai
 * jinhe Gateway currently route karta hai) ke THROUGH call karta hai,
 * payment-service ke apne direct port ke bajaye, Control Center ke
 * ApiTesterAllowlist ke already-verified real routing split se match
 * karte hue in exact endpoints ke liye. Jaan-boojh kar
 * paymentx-payment-service ke apne DTO/entity classes par koi compile-
 * time dependency nahi hai - is platform ki AI Platform services ki har
 * client class se match karta hai (RAG Service ka VectorServiceClient
 * etc.) - real ApiResponse envelope ko ek raw JsonNode ke roop me
 * padhta hai.
 * Ek real HTTP 404 payment-service ke real RESOURCE_NOT_FOUND envelope
 * ke saath yahan ek exception ke roop me throw NAHI hota - ye
 * Optional.empty() ke roop me return hota hai, ek legitimate business
 * result (Step 33 - ek MCP infrastructure error ko ek real PaymentX
 * business result se distinguish karo). Koi bhi doosra failure (5xx,
 * connection failure, unparseable response) ek real infrastructure
 * failure hai aur McpException ke roop me THROW hota hai.
 * Ye kyu hai: Step 9/11.
 * Dusre components se kaise communicate karta hai: tool/PaymentLookupTool
 * aur tool/PaymentStatusTool me inject hota hai; is module ki ek hi
 * class jo kabhi payment-service ko call karti hai.
 */
@Component
@Slf4j
public class PaymentServiceClient {

    private final RestTemplate restTemplate;
    private final McpGatewayProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PaymentServiceClient(RestTemplateBuilder builder, McpGatewayProperties properties) {
        this.properties = properties;
        this.restTemplate = builder
                .requestFactory(SimpleClientHttpRequestFactory::new)
                .connectTimeout(Duration.ofMillis(properties.getPaymentConnectTimeoutMs()))
                .readTimeout(Duration.ofMillis(properties.getPaymentReadTimeoutMs()))
                .build();
    }

    @CircuitBreaker(name = "paymentService")
    @Retry(name = "paymentService")
    public Optional<JsonNode> getByReference(String paymentReference, String correlationId) {
        return get("/api/v1/payments/" + paymentReference, correlationId);
    }

    @CircuitBreaker(name = "paymentService")
    @Retry(name = "paymentService")
    public Optional<JsonNode> getStatus(String paymentReference, String correlationId) {
        return get("/api/v1/payments/" + paymentReference + "/status", correlationId);
    }

    // Phase 4.8.0 - backs the paymentReference-scoped timeline evidence Incident RCA Agent needs;
    // calls payment-service's own real GET /api/v1/payments/{reference}/history (also added this
    // phase, wiring up the pre-existing PaymentHistoryResponse DTO to a real endpoint for the
    // first time). Same "404 is a business result, not an error" convention as the two methods above.
    @CircuitBreaker(name = "paymentService")
    @Retry(name = "paymentService")
    public Optional<JsonNode> getHistory(String paymentReference, String correlationId) {
        return get("/api/v1/payments/" + paymentReference + "/history", correlationId);
    }

    private Optional<JsonNode> get(String path, String correlationId) {
        HttpHeaders headers = new HttpHeaders();
        if (correlationId != null) {
            headers.add(HeaderConstants.CORRELATION_ID, correlationId);
        }
        // Phase 3.9: API Gateway's ApiKeyAuthenticationGlobalFilter requires this header on every
        // request reaching payment-service through it - see McpGatewayProperties.paymentServiceApiKey javadoc.
        if (properties.getPaymentServiceApiKey() != null && !properties.getPaymentServiceApiKey().isBlank()) {
            headers.add(HeaderConstants.API_KEY, properties.getPaymentServiceApiKey());
        }
        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    properties.getPaymentServiceUrl() + path, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
            JsonNode data = response.getBody() != null ? response.getBody().path("data") : null;
            if (data == null || data.isMissingNode() || data.isNull()) {
                throw McpException.targetServiceUnavailable("payment-service returned no data.", false);
            }
            return Optional.of(data);
        } catch (HttpClientErrorException.NotFound notFound) {
            return Optional.empty();
        } catch (HttpStatusCodeException httpError) {
            int status = httpError.getStatusCode().value();
            boolean retryable = status == 429 || status >= 500;
            String message = extractErrorMessage(httpError.getResponseBodyAsString(), httpError.getMessage());
            log.warn("payment-service call failed path={} httpStatus={} message={}", path, status, message);
            throw McpException.targetServiceUnavailable("payment-service call failed: " + message, retryable);
        } catch (ResourceAccessException connectionFailure) {
            log.warn("payment-service unreachable path={} reason={}", path, connectionFailure.getMessage());
            throw McpException.targetServiceUnavailable("payment-service is unreachable: " + connectionFailure.getMessage(), true);
        } catch (RestClientException unparseable) {
            throw McpException.targetServiceUnavailable("payment-service response could not be parsed: " + unparseable.getMessage(), false);
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
