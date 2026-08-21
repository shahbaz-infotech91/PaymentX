package com.paymentx.controlcenter.dto.prometheus;

/**
 * ENGLISH: One entry of the catalog listing - what named metric
 * queries this backend supports, without running any of them. Powers
 * a metrics picker in the frontend.
 *
 * HINGLISH: Catalog listing ka ek entry - ye backend kaunse named
 * metric queries support karta hai, unme se kisi ko chalaye bina.
 * Frontend me ek metrics picker ko power karta hai.
 */
public record PrometheusMetricQueryDescriptor(
        String slug,
        String description,
        String promQl
) {
}
