package com.paymentx.controlcenter.dto.prometheus;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 3.10.3 - Proves the AI-Platform-scoped subset of the catalog PrometheusMetricQueryTest already
 * proves generically (unique slugs, non-blank PromQL, unknown-slug rejection) is present, correctly
 * grouped, and resolvable - without touching that existing, passing test file (Step 9's "do not modify
 * unrelated tests").
 */
class PrometheusMetricQueryAiTest {

    private static List<PrometheusMetricQuery> aiQueries() {
        return Arrays.stream(PrometheusMetricQuery.values())
                .filter(q -> q.slug().startsWith("ai-"))
                .toList();
    }

    @Test
    void aiCatalogCoversAllSevenRealAiServices() {
        Set<String> slugs = aiQueries().stream().map(PrometheusMetricQuery::slug).collect(Collectors.toSet());

        assertThat(slugs).anyMatch(s -> s.startsWith("ai-llm-"));
        assertThat(slugs).anyMatch(s -> s.startsWith("ai-rag-"));
        assertThat(slugs).anyMatch(s -> s.startsWith("ai-mcp-"));
        assertThat(slugs).anyMatch(s -> s.startsWith("ai-agent-"));
        assertThat(slugs).anyMatch(s -> s.startsWith("ai-embedding-"));
        assertThat(slugs).anyMatch(s -> s.startsWith("ai-vector-"));
        assertThat(slugs).anyMatch(s -> s.startsWith("ai-prompt-"));
    }

    @Test
    void aiCatalogIsNonEmptyAndEveryEntryResolvesByItsOwnSlug() {
        List<PrometheusMetricQuery> aiQueries = aiQueries();
        assertThat(aiQueries).isNotEmpty();

        for (PrometheusMetricQuery query : aiQueries) {
            assertThat(PrometheusMetricQuery.fromSlug(query.slug())).isEqualTo(query);
        }
    }

    @Test
    void everyAiQueryHasNonBlankDescriptionAndPromQl() {
        for (PrometheusMetricQuery query : aiQueries()) {
            assertThat(query.description()).as("description for %s", query.slug()).isNotBlank();
            assertThat(query.promQl()).as("promQl for %s", query.slug()).isNotBlank();
        }
    }

    /** Only the one histogram-backed "headline" latency per service is used - never a bucket series a
     * timer never actually publishes (confirmed by reading each *Metrics.java's stop*Timer method). */
    @Test
    void latencyQueriesOnlyReferenceRealHistogramBackedTimers() {
        Set<String> knownHistogramBaseNames = Set.of(
                "llm_generate_latency", "rag_total_latency", "mcp_tool_latency",
                "agent_execution_latency", "embedding_latency", "vector_search_latency");

        for (PrometheusMetricQuery query : aiQueries()) {
            if (query.promQl().contains("histogram_quantile")) {
                assertThat(knownHistogramBaseNames)
                        .as("histogram_quantile query %s must reference a real histogram-backed timer", query.slug())
                        .anyMatch(name -> query.promQl().contains(name + "_seconds_bucket"));
            }
        }
    }

    @Test
    void unknownAiSlugIsRejectedNotTreatedAsFreeFormPromQl() {
        assertThatThrownBy(() -> PrometheusMetricQuery.fromSlug("ai-does-not-exist"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
