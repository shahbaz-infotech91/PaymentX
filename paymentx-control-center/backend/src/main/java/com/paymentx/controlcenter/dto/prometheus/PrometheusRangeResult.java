package com.paymentx.controlcenter.dto.prometheus;

import java.util.List;

/**
 * ENGLISH: The result of running one named PrometheusMetricQuery as a
 * real range query (Phase 4 Metrics charts) - which real query
 * slug/PromQL ran, over what real start/end/step, whether Prometheus
 * answered successfully, and the real series it returned. An empty
 * series list is a genuine, honest outcome when Prometheus has no
 * samples in the requested window (e.g. no PaymentX service has been
 * running/scraped recently) - never backfilled with interpolated or
 * zero-filled points.
 *
 * HINGLISH: Ek named PrometheusMetricQuery ko ek real range query ke
 * roop me chalane ka result (Phase 4 Metrics charts) - konsa real
 * query slug/PromQL chalaya gaya, kaunse real start/end/step ke
 * across, Prometheus ne successfully jawab diya ya nahi, aur usne jo
 * real series return ki. Ek empty series list ek genuine, honest
 * outcome hai jab Prometheus ke paas requested window me koi samples
 * na ho (jaise koi PaymentX service recently chal/scrape nahi hui) -
 * kabhi interpolated ya zero-filled points se backfill nahi kiya
 * jaata.
 */
public record PrometheusRangeResult(
        String querySlug,
        String promQl,
        boolean success,
        String errorMessage,
        List<PrometheusRangeSeries> series
) {
}
