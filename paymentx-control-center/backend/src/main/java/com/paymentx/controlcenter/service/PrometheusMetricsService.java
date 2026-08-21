package com.paymentx.controlcenter.service;

import com.paymentx.controlcenter.client.PrometheusClient;
import com.paymentx.controlcenter.dto.prometheus.PrometheusMetricQuery;
import com.paymentx.controlcenter.dto.prometheus.PrometheusMetricQueryDescriptor;
import com.paymentx.controlcenter.dto.prometheus.PrometheusQueryResult;
import com.paymentx.controlcenter.dto.prometheus.PrometheusRangeResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * ENGLISH: Thin orchestration layer between PrometheusController and
 * PrometheusClient - lists the fixed metric-query catalog, and runs
 * one real query (or all of them, for a metrics-overview page) by
 * resolving a slug through PrometheusMetricQuery.fromSlug() first.
 *
 * HINGLISH: PrometheusController aur PrometheusClient ke beech ek
 * patla orchestration layer - fixed metric-query catalog list karta
 * hai, aur ek real query (ya sab, ek metrics-overview page ke liye)
 * chalata hai, pehle slug ko PrometheusMetricQuery.fromSlug() ke
 * through resolve karke.
 */
@Service
public class PrometheusMetricsService {

    /** Phase 4's exact 6 time-range options - an unrecognized value quietly falls back to 60 (1h), same non-throwing philosophy as PaymentSortField. */
    private static final Set<Integer> ALLOWED_RANGE_MINUTES = Set.of(5, 15, 30, 60, 360, 1440);
    private static final int DEFAULT_RANGE_MINUTES = 60;
    private static final int TARGET_POINT_COUNT = 120;
    private static final int MIN_STEP_SECONDS = 15;

    private final PrometheusClient client;

    public PrometheusMetricsService(PrometheusClient client) {
        this.client = client;
    }

    public List<PrometheusMetricQueryDescriptor> catalog() {
        return List.of(PrometheusMetricQuery.values()).stream()
                .map(q -> new PrometheusMetricQueryDescriptor(q.slug(), q.description(), q.promQl()))
                .toList();
    }

    public PrometheusQueryResult runQuery(String slug) {
        return client.query(PrometheusMetricQuery.fromSlug(slug));
    }

    public List<PrometheusQueryResult> runAll() {
        return List.of(PrometheusMetricQuery.values()).stream()
                .map(client::query)
                .toList();
    }

    /**
     * Phase 3.10.3 - runs only the AI-Platform-scoped subset of the catalog (the "ai-" slug prefix -
     * see PrometheusMetricQuery's own javadoc for why a slug prefix, not a second field, was used to
     * mark this group), for the Control Center AI Metrics page. Deliberately a separate method from
     * runAll() rather than that page reusing runAll(): the full catalog is ~37 entries after this
     * phase, and the AI page only ever needs the ~23 AI-specific ones - running the other ~14
     * infra/business queries on every AI Metrics page load would be pure waste. Each query result is
     * independent (matches runAll()'s own contract - see PrometheusClient.query's javadoc): one query
     * failing never prevents the others in this list from returning real data.
     */
    public List<PrometheusQueryResult> runAi() {
        return List.of(PrometheusMetricQuery.values()).stream()
                .filter(query -> query.slug().startsWith("ai-"))
                .map(client::query)
                .toList();
    }

    public PrometheusRangeResult runRangeQuery(String slug, Integer rangeMinutes) {
        int minutes = ALLOWED_RANGE_MINUTES.contains(rangeMinutes) ? rangeMinutes : DEFAULT_RANGE_MINUTES;
        int stepSeconds = Math.max(MIN_STEP_SECONDS, (minutes * 60) / TARGET_POINT_COUNT);
        long end = System.currentTimeMillis() / 1000;
        long start = end - (minutes * 60L);
        return client.queryRange(PrometheusMetricQuery.fromSlug(slug), start, end, stepSeconds);
    }
}
