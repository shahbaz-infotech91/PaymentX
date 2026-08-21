package com.paymentx.controlcenter.controller;

import com.paymentx.controlcenter.dto.ApiResponse;
import com.paymentx.controlcenter.dto.prometheus.PrometheusMetricQueryDescriptor;
import com.paymentx.controlcenter.dto.prometheus.PrometheusQueryResult;
import com.paymentx.controlcenter.dto.prometheus.PrometheusRangeResult;
import com.paymentx.controlcenter.service.PrometheusMetricsService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ENGLISH: The dashboard's real Prometheus API surface. What it does:
 * GET /api/v1/prometheus/metrics lists the fixed named-query catalog
 * (no execution); GET /api/v1/prometheus/metrics/all runs every named
 * query and returns real results; GET /api/v1/prometheus/metrics/
 * {slug} runs exactly one, resolved through
 * PrometheusMetricQuery.fromSlug() - an unknown slug is a 400. There
 * is deliberately no endpoint that accepts a raw PromQL string.
 *
 * HINGLISH: Dashboard ka real Prometheus API surface. Ye kya karti
 * hai: GET /api/v1/prometheus/metrics fixed named-query catalog list
 * karta hai (execution nahi); GET /api/v1/prometheus/metrics/all har
 * named query chalata hai aur real results return karta hai; GET
 * /api/v1/prometheus/metrics/{slug} exactly ek chalata hai,
 * PrometheusMetricQuery.fromSlug() ke through resolve kiya gaya - ek
 * unknown slug ek 400 hai. Jaan-boojh kar koi endpoint raw PromQL
 * string accept nahi karta.
 */
@RestController
@RequestMapping("/api/v1/prometheus")
public class PrometheusController {

    private final PrometheusMetricsService service;

    public PrometheusController(PrometheusMetricsService service) {
        this.service = service;
    }

    @GetMapping("/metrics")
    public ApiResponse<List<PrometheusMetricQueryDescriptor>> catalog() {
        return ApiResponse.success(service.catalog());
    }

    @GetMapping("/metrics/all")
    public ApiResponse<List<PrometheusQueryResult>> all() {
        return ApiResponse.success(service.runAll());
    }

    /** Phase 3.10.3 - the AI Platform Metrics page's one real data call: runs only the "ai-"-prefixed
     * subset of the catalog (see PrometheusMetricsService.runAi()'s javadoc for why this is a separate
     * method/endpoint from /metrics/all rather than that page reusing the full catalog). */
    @GetMapping("/metrics/ai")
    public ApiResponse<List<PrometheusQueryResult>> ai() {
        return ApiResponse.success(service.runAi());
    }

    @GetMapping("/metrics/{slug}")
    public ApiResponse<PrometheusQueryResult> one(@PathVariable String slug) {
        return ApiResponse.success(service.runQuery(slug));
    }

    /**
     * Phase 4 Metrics charts - real range query over one of the exact 6
     * allowed windows (5/15/30/60/360/1440 minutes); an unrecognized
     * value quietly falls back to 60 rather than erroring.
     */
    @GetMapping("/metrics/{slug}/range")
    public ApiResponse<PrometheusRangeResult> range(@PathVariable String slug, @RequestParam(required = false) Integer rangeMinutes) {
        return ApiResponse.success(service.runRangeQuery(slug, rangeMinutes));
    }
}
