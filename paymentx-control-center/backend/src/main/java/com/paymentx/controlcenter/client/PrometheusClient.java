package com.paymentx.controlcenter.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.paymentx.controlcenter.config.ControlCenterProperties;
import com.paymentx.controlcenter.dto.prometheus.PrometheusMetricQuery;
import com.paymentx.controlcenter.dto.prometheus.PrometheusQueryResult;
import com.paymentx.controlcenter.dto.prometheus.PrometheusRangePoint;
import com.paymentx.controlcenter.dto.prometheus.PrometheusRangeResult;
import com.paymentx.controlcenter.dto.prometheus.PrometheusRangeSeries;
import com.paymentx.controlcenter.dto.prometheus.PrometheusSample;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ENGLISH: The real HTTP client for Prometheus's instant-query API
 * (GET {baseUrl}/api/v1/query). What it does: takes only a
 * PrometheusMetricQuery enum value (never a raw query string), URL-
 * encodes its fixed PromQL text, calls the real, server-configured
 * Prometheus base URL, and parses the real vector/scalar result into
 * PrometheusSample records - turning a Prometheus-side error or a
 * connection failure into success=false with the real error message
 * rather than throwing past the controller layer. Why it exists: this
 * is the actual "integrate with the real Prometheus HTTP API" Phase 2
 * requirement.
 *
 * HINGLISH: Prometheus ke instant-query API (GET {baseUrl}/api/v1/
 * query) ke liye real HTTP client. Ye kya karti hai: sirf ek
 * PrometheusMetricQuery enum value leta hai (kabhi raw query string
 * nahi), uske fixed PromQL text ko URL-encode karta hai, real,
 * server-configured Prometheus base URL ko call karta hai, aur real
 * vector/scalar result ko PrometheusSample records me parse karta hai
 * - ek Prometheus-side error ya connection failure ko controller layer
 * ke aage throw karne ke bajaye success=false aur real error message
 * me badalta hai. Ye dashboard me kyu hai: yehi actual "integrate with
 * the real Prometheus HTTP API" Phase 2 requirement hai.
 */
@Component
@Slf4j
public class PrometheusClient {

    private final RestTemplate restTemplate;
    private final ControlCenterProperties properties;

    public PrometheusClient(RestTemplate prometheusRestTemplate, ControlCenterProperties properties) {
        this.restTemplate = prometheusRestTemplate;
        this.properties = properties;
    }

    public PrometheusQueryResult query(PrometheusMetricQuery metricQuery) {
        String baseUrl = properties.getPrometheus().getBaseUrl();
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl + "/api/v1/query")
                .queryParam("query", "{query}")
                .buildAndExpand(Map.of("query", metricQuery.promQl()))
                .toUri();

        try {
            JsonNode body = restTemplate.getForObject(uri, JsonNode.class);
            if (body == null) {
                return new PrometheusQueryResult(metricQuery.slug(), metricQuery.promQl(), false, "Empty response body from Prometheus", List.of());
            }
            String status = body.path("status").asText();
            if (!"success".equals(status)) {
                String error = body.path("error").asText("Prometheus reported a non-success status");
                return new PrometheusQueryResult(metricQuery.slug(), metricQuery.promQl(), false, error, List.of());
            }
            return new PrometheusQueryResult(metricQuery.slug(), metricQuery.promQl(), true, null, parseSamples(body));
        } catch (ResourceAccessException connectionFailure) {
            log.warn("Could not reach Prometheus for query slug={} reason={}", metricQuery.slug(), connectionFailure.getMessage());
            return new PrometheusQueryResult(metricQuery.slug(), metricQuery.promQl(), false,
                    "Could not reach Prometheus: " + connectionFailure.getMostSpecificCause().getMessage(), List.of());
        } catch (RestClientException httpError) {
            log.warn("Prometheus query failed slug={} reason={}", metricQuery.slug(), httpError.getMessage());
            return new PrometheusQueryResult(metricQuery.slug(), metricQuery.promQl(), false, httpError.getMessage(), List.of());
        }
    }

    /**
     * Real range query (GET /api/v1/query_range) for Phase 4's Metrics
     * time-range charts - same allowlisted-enum boundary as query()
     * above, plus a real start/end/step window computed by the caller
     * (PrometheusMetricsService), never accepted raw from the browser.
     */
    public PrometheusRangeResult queryRange(PrometheusMetricQuery metricQuery, long startEpochSeconds, long endEpochSeconds, int stepSeconds) {
        String baseUrl = properties.getPrometheus().getBaseUrl();
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl + "/api/v1/query_range")
                .queryParam("query", "{query}")
                .queryParam("start", "{start}")
                .queryParam("end", "{end}")
                .queryParam("step", "{step}")
                .buildAndExpand(Map.of(
                        "query", metricQuery.promQl(),
                        "start", String.valueOf(startEpochSeconds),
                        "end", String.valueOf(endEpochSeconds),
                        "step", stepSeconds + "s"))
                .toUri();

        try {
            JsonNode body = restTemplate.getForObject(uri, JsonNode.class);
            if (body == null) {
                return new PrometheusRangeResult(metricQuery.slug(), metricQuery.promQl(), false, "Empty response body from Prometheus", List.of());
            }
            String status = body.path("status").asText();
            if (!"success".equals(status)) {
                String error = body.path("error").asText("Prometheus reported a non-success status");
                return new PrometheusRangeResult(metricQuery.slug(), metricQuery.promQl(), false, error, List.of());
            }
            return new PrometheusRangeResult(metricQuery.slug(), metricQuery.promQl(), true, null, parseSeries(body));
        } catch (ResourceAccessException connectionFailure) {
            log.warn("Could not reach Prometheus for range query slug={} reason={}", metricQuery.slug(), connectionFailure.getMessage());
            return new PrometheusRangeResult(metricQuery.slug(), metricQuery.promQl(), false,
                    "Could not reach Prometheus: " + connectionFailure.getMostSpecificCause().getMessage(), List.of());
        } catch (RestClientException httpError) {
            log.warn("Prometheus range query failed slug={} reason={}", metricQuery.slug(), httpError.getMessage());
            return new PrometheusRangeResult(metricQuery.slug(), metricQuery.promQl(), false, httpError.getMessage(), List.of());
        }
    }

    private List<PrometheusRangeSeries> parseSeries(JsonNode body) {
        JsonNode resultNode = body.path("data").path("result");
        List<PrometheusRangeSeries> series = new ArrayList<>();
        if (!resultNode.isArray()) {
            return series;
        }
        for (JsonNode entry : resultNode) {
            Map<String, String> labels = new LinkedHashMap<>();
            entry.path("metric").fields().forEachRemaining(field -> labels.put(field.getKey(), field.getValue().asText()));
            List<PrometheusRangePoint> points = new ArrayList<>();
            for (JsonNode valuePair : entry.path("values")) {
                if (valuePair.isArray() && valuePair.size() == 2) {
                    long timestamp = (long) valuePair.get(0).asDouble();
                    Double value = normalizeValue(parsePrometheusValue(valuePair.get(1).asText()));
                    points.add(new PrometheusRangePoint(timestamp, value));
                }
            }
            series.add(new PrometheusRangeSeries(labels, points));
        }
        return series;
    }

    private List<PrometheusSample> parseSamples(JsonNode body) {
        JsonNode resultNode = body.path("data").path("result");
        List<PrometheusSample> samples = new ArrayList<>();
        if (!resultNode.isArray()) {
            return samples;
        }
        for (JsonNode entry : resultNode) {
            Map<String, String> labels = new LinkedHashMap<>();
            entry.path("metric").fields().forEachRemaining(field -> labels.put(field.getKey(), field.getValue().asText()));
            JsonNode valueNode = entry.path("value");
            if (valueNode.isArray() && valueNode.size() == 2) {
                long timestamp = (long) valueNode.get(0).asDouble();
                Double value = normalizeValue(parsePrometheusValue(valueNode.get(1).asText()));
                samples.add(new PrometheusSample(labels, value, timestamp));
            }
        }
        return samples;
    }

    /**
     * Phase 3.10.3 NaN-crash fix - Prometheus's real JSON API spells its three special sample values
     * "NaN", "+Inf", and "-Inf" (see https://prometheus.io/docs/prometheus/latest/querying/api/) -
     * NEITHER "+Inf" NOR "-Inf" is accepted by java.lang.Double.parseDouble (which only understands the
     * full words "Infinity"/"-Infinity"), so calling it directly on a raw Prometheus value string threw
     * an uncaught NumberFormatException for any +Inf/-Inf result - a real, more severe crash this fix
     * found and closes alongside the originally-reported NaN-to-JSON-string issue (a query that hit this
     * path failed entirely, with no PrometheusQueryResult ever returned, rather than degrading to one
     * sample's value being `null`). "NaN" alone happens to already parse via Double.parseDouble, but is
     * routed through here too for one single, obvious place to reason about "how does this class turn a
     * Prometheus value string into a Java double."
     */
    private double parsePrometheusValue(String rawValue) {
        return switch (rawValue) {
            case "+Inf" -> Double.POSITIVE_INFINITY;
            case "-Inf" -> Double.NEGATIVE_INFINITY;
            default -> Double.parseDouble(rawValue);
        };
    }

    /**
     * Phase 3.10.3 NaN-crash fix - the one place a raw Prometheus value string (which can legitimately
     * be "NaN", "+Inf", or "-Inf" - most commonly from histogram_quantile() when too few samples exist
     * in its rate window, e.g. a sparse-traffic AI metric) is normalized before it ever reaches a
     * PrometheusSample/PrometheusRangePoint record. Maps non-finite doubles to `null` (never to a
     * fabricated 0 - see PrometheusSample's javadoc for why 0 would be dishonest here); a real, finite
     * value passes through completely unchanged. This is what makes `null` - not the Jackson-default
     * quoted JSON string "NaN"/"Infinity" that silently broke every `number`-typed frontend consumer -
     * the one thing "no meaningful value" ever looks like at the JSON boundary.
     */
    private Double normalizeValue(double rawValue) {
        return Double.isFinite(rawValue) ? rawValue : null;
    }
}
