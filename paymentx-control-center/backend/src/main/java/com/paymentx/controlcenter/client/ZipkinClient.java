package com.paymentx.controlcenter.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.paymentx.controlcenter.config.ControlCenterProperties;
import com.paymentx.controlcenter.dto.zipkin.ZipkinSpan;
import com.paymentx.controlcenter.dto.zipkin.ZipkinTraceDetail;
import com.paymentx.controlcenter.exception.ControlCenterException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * ENGLISH: The real HTTP client for Zipkin's v2 API - GET
 * /api/v2/services (real list of services that have ever reported a
 * span) and GET /api/v2/trace/{traceId} (the real, complete set of
 * spans for one trace, exactly as Zipkin stored them). What it does:
 * assembles the raw span array into a ZipkinTraceDetail by computing
 * duration/status/service-set directly from the real spans returned -
 * never fabricating a span or a status when the trace genuinely has
 * none of a given signal. A traceId with zero spans (never recorded,
 * or already expired from Zipkin's retention window) surfaces as a
 * real 404-shaped ControlCenterException, not an empty-but-"OK" trace.
 * Why it exists: the actual Phase 2 "trace lookup by Trace ID" +
 * "Zipkin real API integration" requirement.
 *
 * HINGLISH: Zipkin ke v2 API ke liye real HTTP client - GET
 * /api/v2/services (un services ki real list jinhone kabhi ek span
 * report kiya) aur GET /api/v2/trace/{traceId} (ek trace ke liye real,
 * complete spans ka set, bilkul waise jaise Zipkin ne unhe store kiya).
 * Ye kya karti hai: raw span array ko ek ZipkinTraceDetail me assemble
 * karta hai, duration/status/service-set ko seedha return hue real
 * spans se compute karke - kabhi ek span ya ek status fabricate nahi
 * karta jab trace me genuinely us signal ka koi record na ho. Ek
 * traceId jiske zero spans hain (kabhi record nahi hua, ya Zipkin ke
 * retention window se already expire ho chuka) ek real 404-shaped
 * ControlCenterException ke roop me surface hota hai, ek empty-but-
 * "OK" trace ke roop me nahi. Ye dashboard me kyu hai: yehi actual
 * Phase 2 "trace lookup by Trace ID" + "Zipkin real API integration"
 * requirement hai.
 */
@Component
public class ZipkinClient {

    private final RestTemplate restTemplate;
    private final ControlCenterProperties properties;

    public ZipkinClient(RestTemplate zipkinRestTemplate, ControlCenterProperties properties) {
        this.restTemplate = zipkinRestTemplate;
        this.properties = properties;
    }

    public List<String> services() {
        String url = properties.getZipkin().getBaseUrl() + "/api/v2/services";
        JsonNode body = get(url);
        List<String> result = new ArrayList<>();
        body.forEach(n -> result.add(n.asText()));
        return result;
    }

    public ZipkinTraceDetail trace(String traceId) {
        String url = properties.getZipkin().getBaseUrl() + "/api/v2/trace/" + traceId;
        JsonNode body = get(url);
        if (!body.isArray() || body.isEmpty()) {
            throw new ControlCenterException("ZIPKIN_TRACE_NOT_FOUND", "No spans found for trace ID: " + traceId);
        }

        List<ZipkinSpan> spans = new ArrayList<>();
        Set<String> services = new LinkedHashSet<>();
        long earliest = Long.MAX_VALUE;
        long latestEnd = Long.MIN_VALUE;
        boolean errorFound = false;

        for (JsonNode s : body) {
            String serviceName = s.path("localEndpoint").path("serviceName").asText(null);
            if (serviceName != null) services.add(serviceName);

            Map<String, String> tags = new LinkedHashMap<>();
            s.path("tags").fields().forEachRemaining(f -> tags.put(f.getKey(), f.getValue().asText()));

            Long timestamp = s.hasNonNull("timestamp") ? s.path("timestamp").asLong() : null;
            Long duration = s.hasNonNull("duration") ? s.path("duration").asLong() : null;
            if (timestamp != null) {
                earliest = Math.min(earliest, timestamp);
                latestEnd = Math.max(latestEnd, timestamp + (duration != null ? duration : 0));
            }

            if (tags.containsKey("error")) {
                errorFound = true;
            }
            String outcome = tags.get("outcome");
            if (outcome != null && !outcome.equalsIgnoreCase("SUCCESS")) {
                errorFound = true;
            }
            String status = tags.get("status");
            if (status != null) {
                try {
                    if (Integer.parseInt(status) >= 400) errorFound = true;
                } catch (NumberFormatException ignored) {
                }
            }

            spans.add(new ZipkinSpan(
                    s.path("traceId").asText(null),
                    s.path("id").asText(null),
                    s.hasNonNull("parentId") ? s.path("parentId").asText() : null,
                    s.path("name").asText(null),
                    serviceName,
                    s.hasNonNull("kind") ? s.path("kind").asText() : null,
                    timestamp,
                    duration,
                    tags));
        }

        long totalDuration = (earliest != Long.MAX_VALUE && latestEnd != Long.MIN_VALUE) ? (latestEnd - earliest) : 0;
        return new ZipkinTraceDetail(traceId, spans, services,
                earliest == Long.MAX_VALUE ? null : earliest, totalDuration, errorFound ? "ERROR" : "OK");
    }

    private JsonNode get(String url) {
        try {
            JsonNode body = restTemplate.getForObject(url, JsonNode.class);
            if (body == null) {
                throw new ControlCenterException("ZIPKIN_EMPTY_RESPONSE", "Empty response from Zipkin");
            }
            return body;
        } catch (ResourceAccessException connectionFailure) {
            throw new ControlCenterException("ZIPKIN_UNREACHABLE",
                    "Could not reach Zipkin: " + connectionFailure.getMostSpecificCause().getMessage(), connectionFailure);
        } catch (RestClientException httpError) {
            throw new ControlCenterException("ZIPKIN_ERROR", "Zipkin API call failed: " + httpError.getMessage(), httpError);
        }
    }
}
