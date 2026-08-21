package com.paymentx.controlcenter.dto.prometheus;

import java.util.List;
import java.util.Map;

/**
 * ENGLISH: One real time series from a Prometheus range-query result -
 * its real labels (e.g. job) and its real ordered list of (timestamp,
 * value) points across the requested window.
 *
 * HINGLISH: Ek Prometheus range-query result se ek real time series -
 * iske real labels (jaise job) aur requested window ke across iski
 * real ordered (timestamp, value) points ki list.
 */
public record PrometheusRangeSeries(
        Map<String, String> labels,
        List<PrometheusRangePoint> points
) {
}
