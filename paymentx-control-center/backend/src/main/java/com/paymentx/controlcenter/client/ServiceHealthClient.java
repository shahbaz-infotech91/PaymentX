package com.paymentx.controlcenter.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.paymentx.controlcenter.config.ControlCenterProperties;
import com.paymentx.controlcenter.config.CorrelationIdFilter;
import com.paymentx.controlcenter.dto.ServiceHealthStatus;
import com.paymentx.controlcenter.dto.ServiceIdentifier;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;

/**
 * ENGLISH: The real HTTP client that calls each of the 9 PaymentX
 * services' actual Spring Boot Actuator endpoints. What it does: GETs
 * /actuator/health, /actuator/health/liveness, /actuator/health/
 * readiness, or /actuator/info on the real, allowlisted base URL for a
 * given ServiceIdentifier, forwards this request's correlation ID as a
 * header, and turns the real HTTP outcome (success, real error status
 * from that service, or a genuine connection failure) into a
 * ServiceHealthStatus - never fabricating a status when a call fails.
 * Why it exists: this is the actual Phase 2 "integrate with actual
 * Spring Boot Actuator endpoints" requirement - only ServiceIdentifier
 * enum values (never a browser-supplied URL) can reach this client,
 * which is the SSRF boundary. How it will communicate with the
 * backend: this client itself IS backend-to-PaymentX-service
 * communication; ServiceHealthController calls it.
 *
 * HINGLISH: Real HTTP client jo 9 PaymentX services me se har ek ke
 * actual Spring Boot Actuator endpoints ko call karta hai. Ye kya
 * karti hai: diye gaye ServiceIdentifier ke real, allowlisted base URL
 * par /actuator/health, /actuator/health/liveness, /actuator/health/
 * readiness, ya /actuator/info GET karta hai, is request ka
 * correlation ID header ke roop me forward karta hai, aur real HTTP
 * outcome (success, us service se real error status, ya ek genuine
 * connection failure) ko ek ServiceHealthStatus me badalta hai - jab
 * call fail ho tab kabhi status fabricate nahi karta. Ye dashboard me
 * kyu hai: yehi actual Phase 2 "integrate with actual Spring Boot
 * Actuator endpoints" requirement hai - sirf ServiceIdentifier enum
 * values (kabhi browser-supplied URL nahi) is client tak pahunch
 * sakte hain, yehi SSRF boundary hai. Backend se kaise connect hogi:
 * ye client khud backend-se-PaymentX-service communication HAI;
 * ServiceHealthController ise call karta hai.
 */
@Component
@Slf4j
public class ServiceHealthClient {

    public enum Probe {
        HEALTH("/actuator/health"),
        LIVENESS("/actuator/health/liveness"),
        READINESS("/actuator/health/readiness"),
        INFO("/actuator/info");

        final String path;

        Probe(String path) {
            this.path = path;
        }
    }

    private final RestTemplate restTemplate;
    private final ControlCenterProperties properties;

    public ServiceHealthClient(RestTemplate paymentXServiceRestTemplate, ControlCenterProperties properties) {
        this.restTemplate = paymentXServiceRestTemplate;
        this.properties = properties;
    }

    public ServiceHealthStatus probe(ServiceIdentifier service, Probe probe) {
        String baseUrl = service.resolveBaseUrl(properties.getServices());
        String url = baseUrl + probe.path;
        long start = System.currentTimeMillis();

        HttpHeaders headers = new HttpHeaders();
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId != null) {
            headers.add(CorrelationIdFilter.HEADER_NAME, correlationId);
        }

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
            long elapsed = System.currentTimeMillis() - start;
            String status = extractStatus(response.getBody());
            return new ServiceHealthStatus(
                    service.slug(), service.displayName(), baseUrl, status,
                    response.getStatusCode().value(), elapsed, null, OffsetDateTime.now());
        } catch (RestClientResponseException httpError) {
            long elapsed = System.currentTimeMillis() - start;
            log.warn("Service health probe returned an error status service={} probe={} httpStatus={}",
                    service.slug(), probe, httpError.getStatusCode().value());
            String status = extractStatus(parseBodySafely(httpError.getResponseBodyAsString()));
            return new ServiceHealthStatus(
                    service.slug(), service.displayName(), baseUrl, status != null ? status : "DOWN",
                    httpError.getStatusCode().value(), elapsed, httpError.getMessage(), OffsetDateTime.now());
        } catch (ResourceAccessException connectionFailure) {
            long elapsed = System.currentTimeMillis() - start;
            log.warn("Service health probe could not connect service={} probe={} reason={}",
                    service.slug(), probe, connectionFailure.getMessage());
            return new ServiceHealthStatus(
                    service.slug(), service.displayName(), baseUrl, "UNREACHABLE",
                    null, elapsed, connectionFailure.getMostSpecificCause().getMessage(), OffsetDateTime.now());
        }
    }

    private String extractStatus(JsonNode body) {
        if (body == null) return null;
        JsonNode statusNode = body.path("status");
        return statusNode.isMissingNode() || statusNode.isNull() ? null : statusNode.asText();
    }

    private JsonNode parseBodySafely(String rawBody) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readTree(rawBody);
        } catch (Exception e) {
            return null;
        }
    }
}
