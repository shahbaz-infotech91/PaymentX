package com.paymentx.controlcenter.dto.prometheus;

import java.util.Map;

/**
 * ENGLISH: One real time series sample from a Prometheus instant
 * query result - its labels (job, instance, area, etc, exactly as
 * Prometheus returned them) and its one real numeric value, or `null`
 * when Prometheus could not produce a meaningful number (NaN/+Inf/-Inf
 * - PromQL can legitimately produce these, e.g. a 0/0 error-rate
 * division when a service has had zero traffic, or histogram_quantile()
 * with too few samples in its rate window). `value` is intentionally
 * `Double` (boxed), not `double` (primitive): a primitive cannot
 * represent "no meaningful value" at all, forcing every non-finite
 * result into either a fabricated 0 (dishonest - see this record's
 * original javadoc) or, via Jackson's default QUOTE_NON_NUMERIC_NUMBERS
 * behavior, a quoted JSON string "NaN"/"Infinity" that silently violates
 * every consumer's `number` type contract at the JSON boundary (the
 * real, confirmed root cause of the Phase 3.10.3 AI Metrics UI crash -
 * PrometheusClient.parseSamples now maps non-finite doubles to `null`
 * before they ever reach this record). A `null` value here means
 * exactly one thing: Prometheus could not compute a meaningful number
 * for this series - never confuse it with a real, measured 0.
 *
 * HINGLISH: Ek Prometheus instant query result se ek real time series
 * sample - iske labels (job, instance, area, etc, bilkul waise jaise
 * Prometheus ne return kiye) aur iski ek real numeric value, ya `null`
 * jab Prometheus ek meaningful number produce nahi kar saka (NaN/+Inf/
 * -Inf). `value` jaan-boojh kar `Double` (boxed) hai, `double`
 * (primitive) nahi: ek primitive "koi meaningful value nahi" represent
 * hi nahi kar sakta, har non-finite result ko ya toh ek fabricated 0 me
 * majboor karta, ya Jackson ke default QUOTE_NON_NUMERIC_NUMBERS
 * behavior ke through ek quoted JSON string "NaN"/"Infinity" me, jo
 * JSON boundary par har consumer ka `number` type contract silently
 * violate karta hai (Phase 3.10.3 AI Metrics UI crash ka real, confirmed
 * root cause - PrometheusClient.parseSamples ab non-finite doubles ko
 * `null` me map karta hai is record tak pahunchne se pehle). Yahan ek
 * `null` value ka matlab exactly ek cheez hai: Prometheus is series ke
 * liye ek meaningful number compute nahi kar saka - ise kabhi ek real,
 * measured 0 se confuse mat karo.
 */
public record PrometheusSample(
        Map<String, String> labels,
        Double value,
        long timestampEpochSeconds
) {
}
