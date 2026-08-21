package com.paymentx.controlcenter.dto.prometheus;

/**
 * ENGLISH: One real (timestamp, value) sample from a Prometheus range-
 * query result - exactly as Prometheus returned it, no interpolation
 * or smoothing applied by this backend. `value` is `Double` (boxed),
 * not `double` (primitive) - `null` means Prometheus could not compute
 * a meaningful number for this point (NaN/+Inf/-Inf, e.g. a sparse
 * histogram_quantile()), never a fabricated 0 - see PrometheusSample's
 * javadoc for the full rationale this record shares.
 *
 * HINGLISH: Ek Prometheus range-query result se ek real (timestamp,
 * value) sample - bilkul waisa jaisa Prometheus ne return kiya, is
 * backend dwara koi interpolation ya smoothing apply nahi ki gayi.
 * `value` `Double` (boxed) hai, `double` (primitive) nahi - `null` ka
 * matlab Prometheus is point ke liye ek meaningful number compute nahi
 * kar saka, kabhi ek fabricated 0 nahi - poore rationale ke liye
 * PrometheusSample ka javadoc dekho, ye record wahi share karta hai.
 */
public record PrometheusRangePoint(
        long timestampEpochSeconds,
        Double value
) {
}
