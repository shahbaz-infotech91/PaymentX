package com.paymentx.controlcenter.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.paymentx.controlcenter.config.ControlCenterProperties;
import com.paymentx.controlcenter.dto.prometheus.PrometheusMetricQuery;
import com.paymentx.controlcenter.dto.prometheus.PrometheusQueryResult;
import com.paymentx.controlcenter.dto.prometheus.PrometheusRangeResult;
import com.paymentx.controlcenter.dto.prometheus.PrometheusSample;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 3.10.3 NaN-crash fix - real wire-level proof that PrometheusClient normalizes every
 * non-finite Prometheus value (NaN, +Inf, -Inf - most commonly a sparse histogram_quantile()) to a
 * real `null` before it ever reaches a PrometheusSample/PrometheusRangePoint, while a genuinely finite
 * value (including a real, measured 0) passes through completely unchanged. This is the exact root
 * cause of the confirmed /ai-metrics crash: Prometheus's own JSON API always encodes sample values as
 * strings (so it can represent NaN/Inf at all, since neither is valid JSON), and Jackson's default
 * serialization of a raw Java `double` re-encodes a non-finite value as a quoted JSON string
 * ("NaN"/"Infinity") rather than the JSON `null` every typed consumer actually needs.
 */
class PrometheusClientNaNNormalizationTest {

    private static WireMockServer prometheus;

    @BeforeAll
    static void startServer() {
        prometheus = new WireMockServer(0);
        prometheus.start();
    }

    @AfterAll
    static void stopServer() {
        prometheus.stop();
    }

    @BeforeEach
    void resetServer() {
        prometheus.resetAll();
    }

    private PrometheusClient newClient() {
        ControlCenterProperties properties = new ControlCenterProperties();
        properties.getPrometheus().setBaseUrl("http://localhost:" + prometheus.port());
        return new PrometheusClient(new RestTemplateBuilder().build(), properties);
    }

    private void stubInstantQuery(String rawValue) {
        prometheus.stubFor(get(urlPathEqualTo("/api/v1/query")).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json")
                .withBody("""
                        {"status":"success","data":{"resultType":"vector","result":[
                          {"metric":{"job":"paymentx-rag-service"},"value":[1700000000,"%s"]}
                        ]}}
                        """.formatted(rawValue))));
    }

    private void stubRangeQuery(String rawValue) {
        prometheus.stubFor(get(urlPathEqualTo("/api/v1/query_range")).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json")
                .withBody("""
                        {"status":"success","data":{"resultType":"matrix","result":[
                          {"metric":{"job":"paymentx-rag-service"},"values":[[1700000000,"%s"]]}
                        ]}}
                        """.formatted(rawValue))));
    }

    // ---- Item 1: valid latency percentile is preserved exactly ----

    @Test
    void query_realFiniteValue_passesThroughUnchanged() {
        stubInstantQuery("9.84");

        PrometheusQueryResult result = newClient().query(PrometheusMetricQuery.AI_RAG_LATENCY_P95);

        assertThat(result.success()).isTrue();
        assertThat(result.samples()).hasSize(1);
        assertThat(result.samples().get(0).value()).isEqualTo(9.84);
    }

    // ---- Item 2: NaN -> null ----

    @Test
    void query_nanValue_normalizesToNullNeverAFabricatedNumberOrRawString() {
        stubInstantQuery("NaN");

        PrometheusQueryResult result = newClient().query(PrometheusMetricQuery.AI_RAG_LATENCY_P95);

        assertThat(result.success()).isTrue();
        assertThat(result.samples()).hasSize(1);
        assertThat(result.samples().get(0).value()).isNull();
    }

    // ---- Item 3: Infinity -> null ----

    @Test
    void query_positiveInfinityValue_normalizesToNull() {
        stubInstantQuery("+Inf");

        PrometheusQueryResult result = newClient().query(PrometheusMetricQuery.AI_RAG_LATENCY_P95);

        assertThat(result.samples().get(0).value()).isNull();
    }

    @Test
    void query_negativeInfinityValue_normalizesToNull() {
        stubInstantQuery("-Inf");

        PrometheusQueryResult result = newClient().query(PrometheusMetricQuery.AI_RAG_LATENCY_P95);

        assertThat(result.samples().get(0).value()).isNull();
    }

    // ---- Item 5: a real, measured zero must remain zero, never become null ----

    @Test
    void query_realZeroValue_remainsZeroNeverNormalizedToNull() {
        stubInstantQuery("0");

        PrometheusQueryResult result = newClient().query(PrometheusMetricQuery.AI_RAG_LATENCY_P95);

        PrometheusSample sample = result.samples().get(0);
        assertThat(sample.value()).isNotNull();
        assertThat(sample.value()).isEqualTo(0.0);
    }

    // ---- Item 6: mixed series - only the non-finite one is normalized, the real one is untouched ----

    @Test
    void query_mixedSeries_onlyTheNonFiniteSampleIsNormalized() {
        prometheus.stubFor(get(urlPathEqualTo("/api/v1/query")).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json")
                .withBody("""
                        {"status":"success","data":{"resultType":"vector","result":[
                          {"metric":{"provider":"anthropic"},"value":[1700000000,"12.5"]},
                          {"metric":{"provider":"local"},"value":[1700000000,"NaN"]}
                        ]}}
                        """)));

        PrometheusQueryResult result = newClient().query(PrometheusMetricQuery.AI_LLM_LATENCY_P95);

        assertThat(result.samples()).hasSize(2);
        assertThat(result.samples().get(0).value()).isEqualTo(12.5);
        assertThat(result.samples().get(1).value()).isNull();
    }

    // ---- Same normalization applies to range-query points (queryRange), the /metrics page's own path ----

    @Test
    void queryRange_nanPoint_normalizesToNull() {
        stubRangeQuery("NaN");

        PrometheusRangeResult result = newClient().queryRange(PrometheusMetricQuery.AI_RAG_LATENCY_P95, 0, 300, 15);

        assertThat(result.series()).hasSize(1);
        assertThat(result.series().get(0).points()).hasSize(1);
        assertThat(result.series().get(0).points().get(0).value()).isNull();
    }

    @Test
    void queryRange_realFiniteValue_passesThroughUnchanged() {
        stubRangeQuery("42.5");

        PrometheusRangeResult result = newClient().queryRange(PrometheusMetricQuery.AI_RAG_LATENCY_P95, 0, 300, 15);

        assertThat(result.series().get(0).points().get(0).value()).isEqualTo(42.5);
    }
}
