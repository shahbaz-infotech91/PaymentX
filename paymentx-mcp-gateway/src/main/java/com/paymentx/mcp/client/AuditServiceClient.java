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
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;

/**
 * English:
 * The ONE class in this module that talks audit-service's real wire
 * format on the READ side - backs tool/AuditSearchTool (Step 9/11).
 * Called on audit-service's own direct port (8085, no API Gateway route
 * exists for it today - matching Control Center's ApiTesterAllowlist's
 * already-verified split). search() calls the real, already-existing
 * GET /api/v1/audit-events with only the query parameters
 * AuditController actually declares (correlationId, paymentId,
 * participantId, reference, status, eventType, fromDate, toDate, page,
 * size) - unknown parameters are never forwarded (Step 16). Returns the
 * real PageResponse envelope's `data` node verbatim as JsonNode -
 * tool/AuditSearchTool is responsible for reshaping it into the
 * minimized, payload-excluded output (Step 17/18 - never returns the
 * raw event `payload` field to the AI). This is a distinct class from
 * audit/McpAuditClient (which only WRITES this gateway's own operational
 * audit trail) - a read tool must never share code with the write path
 * that records its own invocation, to keep the two concerns (business
 * data retrieval vs. this gateway's own operational logging) cleanly
 * separated.
 * Why it exists: Step 9/11.
 * How it communicates with other components: injected into
 * tool/AuditSearchTool; the ONLY class in this module that ever reads
 * from audit-service.
 *
 * Hinglish:
 * Ye is module ki ek hi class hai jo audit-service ka real wire format
 * READ side par bolti hai - tool/AuditSearchTool ko backing deti hai
 * (Step 9/11). audit-service ke apne direct port (8085) par call hoti
 * hai, aaj iske liye koi API Gateway route exist nahi karta (Control
 * Center ke ApiTesterAllowlist ke already-verified split se match karte
 * hue). search() real, already-existing GET /api/v1/audit-events ko
 * sirf un query parameters ke saath call karta hai jo AuditController
 * actually declare karta hai (correlationId, paymentId, participantId,
 * reference, status, eventType, fromDate, toDate, page, size) - unknown
 * parameters kabhi forward nahi hote (Step 16). Real PageResponse
 * envelope ke `data` node ko verbatim JsonNode ke roop me return karta
 * hai - tool/AuditSearchTool responsible hai ise minimized,
 * payload-excluded output me reshape karne ke liye (Step 17/18 - AI ko
 * kabhi raw event `payload` field return nahi hota). Ye
 * audit/McpAuditClient se ek distinct class hai (jo sirf is gateway ka
 * apna operational audit trail LIKHTA hai) - ek read tool ko us write
 * path ke saath code share nahi karna chahiye jo uski apni invocation
 * record karta hai, in do concerns (business data retrieval vs is
 * gateway ka apna operational logging) ko cleanly separate rakhne ke
 * liye.
 * Ye kyu hai: Step 9/11.
 * Dusre components se kaise communicate karta hai: tool/AuditSearchTool
 * me inject hota hai; is module ki ek hi class jo kabhi audit-service se
 * padhti hai.
 */
@Component
@Slf4j
public class AuditServiceClient {

    private final RestTemplate restTemplate;
    private final McpGatewayProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AuditServiceClient(RestTemplateBuilder builder, McpGatewayProperties properties) {
        this.properties = properties;
        this.restTemplate = builder
                .requestFactory(SimpleClientHttpRequestFactory::new)
                .connectTimeout(Duration.ofMillis(properties.getAuditSearchConnectTimeoutMs()))
                .readTimeout(Duration.ofMillis(properties.getAuditSearchReadTimeoutMs()))
                .build();
    }

    @CircuitBreaker(name = "auditService")
    @Retry(name = "auditService")
    public JsonNode search(String correlationIdFilter, String paymentId, String participantId, String reference,
                            String status, String eventType, String fromDate, String toDate,
                            int page, int size, String requestCorrelationId) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromPath("/api/v1/audit-events")
                .queryParamIfPresent("correlationId", java.util.Optional.ofNullable(correlationIdFilter))
                .queryParamIfPresent("paymentId", java.util.Optional.ofNullable(paymentId))
                .queryParamIfPresent("participantId", java.util.Optional.ofNullable(participantId))
                .queryParamIfPresent("reference", java.util.Optional.ofNullable(reference))
                .queryParamIfPresent("status", java.util.Optional.ofNullable(status))
                .queryParamIfPresent("eventType", java.util.Optional.ofNullable(eventType))
                .queryParamIfPresent("fromDate", java.util.Optional.ofNullable(fromDate))
                .queryParamIfPresent("toDate", java.util.Optional.ofNullable(toDate))
                .queryParam("page", page)
                .queryParam("size", size);

        HttpHeaders headers = new HttpHeaders();
        if (requestCorrelationId != null) {
            headers.add(HeaderConstants.CORRELATION_ID, requestCorrelationId);
        }

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    properties.getAuditServiceUrl() + uri.toUriString(), HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
            JsonNode data = response.getBody() != null ? response.getBody().path("data") : null;
            if (data == null || data.isMissingNode() || data.isNull()) {
                throw McpException.targetServiceUnavailable("audit-service returned no data.", false);
            }
            return data;
        } catch (HttpStatusCodeException httpError) {
            int status2 = httpError.getStatusCode().value();
            boolean retryable = status2 == 429 || status2 >= 500;
            String message = extractErrorMessage(httpError.getResponseBodyAsString(), httpError.getMessage());
            log.warn("audit-service search failed httpStatus={} message={}", status2, message);
            throw McpException.targetServiceUnavailable("audit-service call failed: " + message, retryable);
        } catch (ResourceAccessException connectionFailure) {
            log.warn("audit-service unreachable reason={}", connectionFailure.getMessage());
            throw McpException.targetServiceUnavailable("audit-service is unreachable: " + connectionFailure.getMessage(), true);
        } catch (RestClientException unparseable) {
            throw McpException.targetServiceUnavailable("audit-service response could not be parsed: " + unparseable.getMessage(), false);
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
