package com.paymentx.controlcenter.service;

import com.paymentx.controlcenter.client.PrometheusClient;
import com.paymentx.controlcenter.dto.prometheus.PrometheusMetricQuery;
import com.paymentx.controlcenter.dto.prometheus.PrometheusQueryResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Phase 3.10.3 - Proves PrometheusMetricsService.runAi() runs exactly the AI-Platform-scoped subset of
 * the catalog (never the full ~37-entry catalog runAll() runs), and that one failed query among many
 * never prevents the others from returning real data (Step 9 items 3/6/7 - valid AI metric query,
 * missing metric, partial metric failure).
 */
@ExtendWith(MockitoExtension.class)
class PrometheusMetricsServiceAiTest {

    @Mock
    private PrometheusClient client;

    @Test
    void runAi_onlyQueriesTheAiScopedSubsetOfTheCatalog() {
        when(client.query(any())).thenAnswer(inv -> {
            PrometheusMetricQuery q = inv.getArgument(0);
            return new PrometheusQueryResult(q.slug(), q.promQl(), true, null, List.of());
        });
        PrometheusMetricsService service = new PrometheusMetricsService(client);

        List<PrometheusQueryResult> results = service.runAi();

        long expectedAiCount = List.of(PrometheusMetricQuery.values()).stream()
                .filter(q -> q.slug().startsWith("ai-")).count();
        assertThat(results).hasSize((int) expectedAiCount);
        assertThat(results).allSatisfy(r -> assertThat(r.querySlug()).startsWith("ai-"));

        ArgumentCaptor<PrometheusMetricQuery> captor = ArgumentCaptor.forClass(PrometheusMetricQuery.class);
        org.mockito.Mockito.verify(client, org.mockito.Mockito.times((int) expectedAiCount)).query(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(q -> assertThat(q.slug()).startsWith("ai-"));
    }

    @Test
    void runAi_neverQueriesNonAiCatalogEntries() {
        when(client.query(any())).thenAnswer(inv -> {
            PrometheusMetricQuery q = inv.getArgument(0);
            return new PrometheusQueryResult(q.slug(), q.promQl(), true, null, List.of());
        });
        PrometheusMetricsService service = new PrometheusMetricsService(client);

        service.runAi();

        ArgumentCaptor<PrometheusMetricQuery> captor = ArgumentCaptor.forClass(PrometheusMetricQuery.class);
        org.mockito.Mockito.verify(client, org.mockito.Mockito.atLeastOnce()).query(captor.capture());
        assertThat(captor.getAllValues()).noneMatch(q -> q.slug().startsWith("business-") || q.slug().equals("request-rate"));
    }

    /** One query among the AI subset returning a real Prometheus failure (or an empty/missing-metric
     * result) must never prevent the others from returning real data - each PrometheusQueryResult is
     * independent. */
    @Test
    void runAi_oneQueryFailing_stillReturnsRealResultsForAllOthers() {
        when(client.query(any())).thenAnswer(inv -> {
            PrometheusMetricQuery q = inv.getArgument(0);
            if (q == PrometheusMetricQuery.AI_LLM_LATENCY_P95) {
                return new PrometheusQueryResult(q.slug(), q.promQl(), false, "Could not reach Prometheus: connection refused", List.of());
            }
            return new PrometheusQueryResult(q.slug(), q.promQl(), true, null, List.of());
        });
        PrometheusMetricsService service = new PrometheusMetricsService(client);

        List<PrometheusQueryResult> results = service.runAi();

        PrometheusQueryResult failed = results.stream()
                .filter(r -> r.querySlug().equals(PrometheusMetricQuery.AI_LLM_LATENCY_P95.slug()))
                .findFirst().orElseThrow();
        assertThat(failed.success()).isFalse();
        assertThat(failed.errorMessage()).isNotBlank();

        long successfulCount = results.stream().filter(PrometheusQueryResult::success).count();
        assertThat(successfulCount).isEqualTo(results.size() - 1);
    }

    /** A query that succeeded but Prometheus genuinely has no samples for (missing metric / metric
     * temporarily has no data) must be represented honestly - empty samples, success:true, never a
     * fabricated zero value. */
    @Test
    void runAi_missingMetric_returnsSuccessWithEmptySamplesNeverAFabricatedValue() {
        when(client.query(any())).thenAnswer(inv -> {
            PrometheusMetricQuery q = inv.getArgument(0);
            return new PrometheusQueryResult(q.slug(), q.promQl(), true, null, List.of());
        });
        PrometheusMetricsService service = new PrometheusMetricsService(client);

        List<PrometheusQueryResult> results = service.runAi();

        assertThat(results).allSatisfy(r -> {
            assertThat(r.success()).isTrue();
            assertThat(r.samples()).isEmpty();
        });
    }
}
