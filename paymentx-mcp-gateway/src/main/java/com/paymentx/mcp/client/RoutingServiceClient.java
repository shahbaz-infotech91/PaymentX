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
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.Optional;

/**
 * English:
 * The ONE class in this module that talks routing-service's real wire
 * format - backs tool/RoutingLookupTool (Step 9/11). Called on routing-
 * service's own direct port (8084, no API Gateway route exists for it
 * today - see application.yml's comment, and Control Center's
 * ApiTesterAllowlist which already established this exact routing
 * split). resolveForParticipant() calls GET
 * /api/v1/routes/participant/{participantId}?scheme=X;
 * resolveDefault() calls GET /api/v1/routes/default?scheme=X - both
 * real, already-existing RoutingController endpoints, never a
 * fabricated one. A real HTTP 404 (no active rule and no default
 * configured for that scheme) is returned as Optional.empty(), a
 * legitimate business result, not thrown as an exception (Step 33).
 * Why it exists: Step 9/11.
 * How it communicates with other components: injected into
 * tool/RoutingLookupTool; the ONLY class in this module that ever calls
 * routing-service.
 *
 * Hinglish:
 * Ye is module ki ek hi class hai jo routing-service ka real wire
 * format bolti hai - tool/RoutingLookupTool ko backing deti hai (Step
 * 9/11). routing-service ke apne direct port (8084) par call hoti hai,
 * aaj iske liye koi API Gateway route exist nahi karta (application.yml
 * ka comment dekho, aur Control Center ka ApiTesterAllowlist jo ye
 * exact routing split already establish kar chuka hai).
 * resolveForParticipant() GET /api/v1/routes/participant/{participantId}?scheme=X
 * call karta hai; resolveDefault() GET /api/v1/routes/default?scheme=X
 * call karta hai - dono real, already-existing RoutingController
 * endpoints, kabhi ek fabricated wala nahi. Ek real HTTP 404 (us scheme
 * ke liye koi active rule aur koi default configured nahi hai)
 * Optional.empty() ke roop me return hota hai, ek legitimate business
 * result, ek exception ke roop me throw nahi hota (Step 33).
 * Ye kyu hai: Step 9/11.
 * Dusre components se kaise communicate karta hai: tool/RoutingLookupTool
 * me inject hota hai; is module ki ek hi class jo kabhi routing-service
 * ko call karti hai.
 */
@Component
@Slf4j
public class RoutingServiceClient {

    private final RestTemplate restTemplate;
    private final McpGatewayProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RoutingServiceClient(RestTemplateBuilder builder, McpGatewayProperties properties) {
        this.properties = properties;
        this.restTemplate = builder
                .requestFactory(SimpleClientHttpRequestFactory::new)
                .connectTimeout(Duration.ofMillis(properties.getRoutingConnectTimeoutMs()))
                .readTimeout(Duration.ofMillis(properties.getRoutingReadTimeoutMs()))
                .build();
    }

    @CircuitBreaker(name = "routingService")
    @Retry(name = "routingService")
    public Optional<JsonNode> resolveForParticipant(String participantId, String scheme, String correlationId) {
        String path = UriComponentsBuilder.fromPath("/api/v1/routes/participant/{participantId}")
                .queryParam("scheme", scheme)
                .buildAndExpand(participantId)
                .toUriString();
        return get(path, correlationId);
    }

    @CircuitBreaker(name = "routingService")
    @Retry(name = "routingService")
    public Optional<JsonNode> resolveDefault(String scheme, String correlationId) {
        String path = UriComponentsBuilder.fromPath("/api/v1/routes/default")
                .queryParam("scheme", scheme)
                .toUriString();
        return get(path, correlationId);
    }

    private Optional<JsonNode> get(String path, String correlationId) {
        HttpHeaders headers = new HttpHeaders();
        if (correlationId != null) {
            headers.add(HeaderConstants.CORRELATION_ID, correlationId);
        }
        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    properties.getRoutingServiceUrl() + path, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
            JsonNode data = response.getBody() != null ? response.getBody().path("data") : null;
            if (data == null || data.isMissingNode() || data.isNull()) {
                throw McpException.targetServiceUnavailable("routing-service returned no data.", false);
            }
            return Optional.of(data);
        } catch (HttpClientErrorException.NotFound notFound) {
            return Optional.empty();
        } catch (HttpStatusCodeException httpError) {
            int status = httpError.getStatusCode().value();
            boolean retryable = status == 429 || status >= 500;
            String message = extractErrorMessage(httpError.getResponseBodyAsString(), httpError.getMessage());
            log.warn("routing-service call failed path={} httpStatus={} message={}", path, status, message);
            throw McpException.targetServiceUnavailable("routing-service call failed: " + message, retryable);
        } catch (ResourceAccessException connectionFailure) {
            log.warn("routing-service unreachable path={} reason={}", path, connectionFailure.getMessage());
            throw McpException.targetServiceUnavailable("routing-service is unreachable: " + connectionFailure.getMessage(), true);
        } catch (RestClientException unparseable) {
            throw McpException.targetServiceUnavailable("routing-service response could not be parsed: " + unparseable.getMessage(), false);
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
