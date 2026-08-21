package com.paymentx.controlcenter.dto.prometheus;

import java.util.List;

/**
 * ENGLISH: The result of running one named PrometheusMetricQuery -
 * which real query slug/PromQL was run, whether Prometheus answered
 * successfully, and the real samples it returned (empty list is a
 * legitimate, honest outcome - e.g. Kafka lag metrics only exist for
 * the 2 instrumented consumers, so most job labels will have none).
 *
 * HINGLISH: Ek named PrometheusMetricQuery chalane ka result - konsa
 * real query slug/PromQL chalaya gaya, Prometheus ne successfully
 * jawab diya ya nahi, aur usne jo real samples return kiye (empty
 * list bhi ek legitimate, honest outcome hai - jaise Kafka lag metrics
 * sirf 2 instrumented consumers ke liye exist karte hain, isliye
 * zyadatar job labels ke paas koi nahi honge).
 */
public record PrometheusQueryResult(
        String querySlug,
        String promQl,
        boolean success,
        String errorMessage,
        List<PrometheusSample> samples
) {
}
