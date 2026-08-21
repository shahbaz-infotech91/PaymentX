package com.paymentx.payment.client;

import com.paymentx.common.constant.HeaderConstants;
import com.paymentx.common.dto.ApiResponse;
import com.paymentx.payment.config.RoutingClientProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
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
 * The ONE class in this module that talks Routing Service's real wire
 * format. Mirrors paymentx-mcp-gateway's RoutingServiceClient (the
 * existing, real, already-working reference implementation for calling
 * this exact same service) - same RestTemplate/RestTemplateBuilder
 * convention, same @CircuitBreaker/@Retry instance name ("routingService"
 * - already pre-configured in application.yml's resilience4j block,
 * unused until this class), same direct-port call (no API Gateway route
 * exists for Routing Service), same "a real 404 is Optional.empty(), not
 * an exception" business-result handling.
 *
 * resolveForParticipant() calls GET
 * /api/v1/routes/participant/{participantId}?scheme=X;
 * resolveDefault() calls GET /api/v1/routes/default?scheme=X - both real,
 * already-existing, already-tested RoutingController endpoints. "scheme"
 * here is RoutingController's own existing query parameter name; its
 * value is a Payment Type (INSTANT_PAYMENT/REAL_TIME_PAYMENT/
 * CARD_PAYMENT), not renamed as part of this change.
 */
@Component
@Slf4j
public class RoutingClient {

    private final RestTemplate restTemplate;
    private final RoutingClientProperties properties;

    public RoutingClient(RestTemplateBuilder builder, RoutingClientProperties properties) {
        this.properties = properties;
        this.restTemplate = builder
                .requestFactory(SimpleClientHttpRequestFactory::new)
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .readTimeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                .build();
    }

    @CircuitBreaker(name = "routingService")
    @Retry(name = "routingService")
    public Optional<String> resolveForParticipant(String participantId, String paymentType, String correlationId) {
        String path = UriComponentsBuilder.fromPath("/api/v1/routes/participant/{participantId}")
                .queryParam("scheme", paymentType)
                .buildAndExpand(participantId)
                .toUriString();
        return get(path, correlationId);
    }

    @CircuitBreaker(name = "routingService")
    @Retry(name = "routingService")
    public Optional<String> resolveDefault(String paymentType, String correlationId) {
        String path = UriComponentsBuilder.fromPath("/api/v1/routes/default")
                .queryParam("scheme", paymentType)
                .toUriString();
        return get(path, correlationId);
    }

    private Optional<String> get(String path, String correlationId) {
        HttpHeaders headers = new HttpHeaders();
        if (correlationId != null) {
            headers.add(HeaderConstants.CORRELATION_ID, correlationId);
        }
        try {
            ResponseEntity<ApiResponse<RouteResolutionResponse>> response = restTemplate.exchange(
                    properties.getBaseUrl() + path,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {});

            ApiResponse<RouteResolutionResponse> body = response.getBody();
            if (body == null || body.data() == null || body.data().targetRoute() == null
                    || body.data().targetRoute().isBlank()) {
                throw new RoutingResolutionException(RoutingResolutionException.Reason.INVALID_ROUTING_RESPONSE,
                        false, "routing-service returned an empty targetRoute for path=" + path);
            }
            return Optional.of(body.data().targetRoute());

        } catch (HttpClientErrorException.NotFound notFound) {
            // A real, legitimate business result (no active rule configured for
            // this participant/default) - not a failure of the call itself.
            return Optional.empty();
        } catch (HttpStatusCodeException httpError) {
            int status = httpError.getStatusCode().value();
            boolean retryable = status == 429 || status >= 500;
            log.warn("routing-service call failed path={} httpStatus={}", path, status);
            throw new RoutingResolutionException(RoutingResolutionException.Reason.ROUTING_SERVICE_UNAVAILABLE,
                    retryable, "routing-service call failed with HTTP " + status);
        } catch (ResourceAccessException connectionFailure) {
            String cause = connectionFailure.getCause() != null
                    ? connectionFailure.getCause().getClass().getSimpleName() : "unknown";
            boolean isTimeout = cause.toLowerCase().contains("timeout");
            log.warn("routing-service unreachable path={} cause={}", path, cause);
            throw new RoutingResolutionException(
                    isTimeout ? RoutingResolutionException.Reason.ROUTING_TIMEOUT
                            : RoutingResolutionException.Reason.ROUTING_SERVICE_UNAVAILABLE,
                    true, "routing-service is unreachable: " + connectionFailure.getMessage());
        } catch (RestClientException unparseable) {
            throw new RoutingResolutionException(RoutingResolutionException.Reason.INVALID_ROUTING_RESPONSE,
                    false, "routing-service response could not be parsed: " + unparseable.getMessage());
        }
    }
}
