package com.paymentx.controlcenter.dto.alerts;

import java.time.OffsetDateTime;

/**
 * ENGLISH: One real, currently-true operational condition, detected
 * fresh on every call to AlertsService.currentAlerts() by directly
 * querying the same real clients every other Phase 2/3 domain uses
 * (ServiceHealthAggregationService, PrometheusMetricsService,
 * KafkaAdminMonitoringClient, RedisMonitoringClient,
 * RabbitMqManagementClient, PostgresDataService) - there is no alert
 * history table and no synthetic/sample alert; if nothing real is
 * wrong, AlertsService returns an empty list. message always embeds
 * the real number that triggered the alert (the real error rate, the
 * real lag count, the real mismatch count, ...), never a generic
 * "something is wrong" string.
 *
 * HINGLISH: Ek real, currently-true operational condition, AlertsService.
 * currentAlerts() ki har call par fresh detect ki gayi, seedhe unhi
 * real clients ko query karke jo har doosra Phase 2/3 domain use
 * karta hai (ServiceHealthAggregationService, PrometheusMetricsService,
 * KafkaAdminMonitoringClient, RedisMonitoringClient,
 * RabbitMqManagementClient, PostgresDataService) - koi alert history
 * table nahi hai aur koi synthetic/sample alert nahi hai; agar
 * genuinely kuch galat nahi hai, toh AlertsService ek empty list
 * return karta hai. message hamesha wahi real number embed karta hai
 * jisne alert trigger kiya (real error rate, real lag count, real
 * mismatch count, ...), kabhi ek generic "something is wrong" string
 * nahi.
 */
public record Alert(
        String id,
        AlertSeverity severity,
        String category,
        String title,
        String message,
        OffsetDateTime detectedAt
) {
}
