package com.paymentx.controlcenter.service;

import com.paymentx.controlcenter.client.KafkaAdminMonitoringClient;
import com.paymentx.controlcenter.client.PrometheusClient;
import com.paymentx.controlcenter.client.RabbitMqManagementClient;
import com.paymentx.controlcenter.client.RedisMonitoringClient;
import com.paymentx.controlcenter.dto.ServiceHealthStatus;
import com.paymentx.controlcenter.dto.alerts.Alert;
import com.paymentx.controlcenter.dto.alerts.AlertSeverity;
import com.paymentx.controlcenter.dto.postgres.DatabaseStatus;
import com.paymentx.controlcenter.dto.postgres.PaymentStatsSummary;
import com.paymentx.controlcenter.dto.prometheus.PrometheusMetricQuery;
import com.paymentx.controlcenter.dto.prometheus.PrometheusQueryResult;
import com.paymentx.controlcenter.dto.redis.RedisHealthStatus;
import com.paymentx.controlcenter.repository.ReconciliationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * ENGLISH: Proves AlertsService's core honesty rule - "real conditions
 * only" - using mocked clients so no real infrastructure is touched.
 * What it verifies: an all-healthy snapshot produces zero alerts (an
 * empty list IS the correct answer, not a bug), and a single real
 * condition (one service reporting DOWN) produces exactly one real,
 * correctly-severity-and-categorized alert - nothing fabricated,
 * nothing missing.
 *
 * HINGLISH: AlertsService ka core honesty rule - "real conditions
 * only" - mocked clients use karke prove karta hai taaki koi real
 * infrastructure touch na ho. Ye kya verify karta hai: ek all-healthy
 * snapshot zero alerts produce karta hai (ek empty list HI correct
 * jawab hai, koi bug nahi), aur ek single real condition (ek service
 * DOWN report kare) exactly ek real, correctly-severity-and-
 * categorized alert produce karta hai - kuch fabricated nahi, kuch
 * missing nahi.
 */
@ExtendWith(MockitoExtension.class)
class AlertsServiceTest {

    @Mock private ServiceHealthAggregationService serviceHealthAggregationService;
    @Mock private PrometheusClient prometheusClient;
    @Mock private KafkaAdminMonitoringClient kafkaAdminMonitoringClient;
    @Mock private RedisMonitoringClient redisMonitoringClient;
    @Mock private RabbitMqManagementClient rabbitMqManagementClient;
    @Mock private PostgresDataService postgresDataService;
    @Mock private ReconciliationRepository reconciliationRepository;

    private AlertsService alertsService;

    @BeforeEach
    void setUp() {
        alertsService = new AlertsService(serviceHealthAggregationService, prometheusClient,
                kafkaAdminMonitoringClient, redisMonitoringClient, rabbitMqManagementClient,
                postgresDataService, reconciliationRepository);

        // Default: every real signal reports healthy/empty - individual tests override one signal at a time.
        lenient().when(serviceHealthAggregationService.checkAll()).thenReturn(List.of(
                new ServiceHealthStatus("payment-service", "Payment Service", "http://localhost:8083", "UP", 200, 5, null, OffsetDateTime.now())));
        lenient().when(prometheusClient.query(any())).thenAnswer(inv -> {
            PrometheusMetricQuery q = inv.getArgument(0);
            return new PrometheusQueryResult(q.slug(), q.promQl(), true, null, List.of());
        });
        lenient().when(kafkaAdminMonitoringClient.listConsumerGroups()).thenReturn(List.of());
        lenient().when(redisMonitoringClient.health()).thenReturn(
                new RedisHealthStatus(true, null, 5, "7.4.10", "master", 100L, 2L));
        lenient().when(rabbitMqManagementClient.queues()).thenReturn(List.of());
        lenient().when(postgresDataService.allDatabaseStatuses()).thenReturn(List.of(
                new DatabaseStatus("paymentx_payment", true, null, 10L, "V1", OffsetDateTime.now(), 2)));
        lenient().when(postgresDataService.paymentStats()).thenReturn(
                new PaymentStatsSummary(0, 0, 0, 0, 0, 0.0, 0.0, null, 0, 0.0));
        lenient().when(reconciliationRepository.findRecentWithMismatches(anyInt())).thenReturn(List.of());
    }

    @Test
    void allHealthySnapshotProducesZeroAlerts() {
        assertThat(alertsService.currentAlerts()).isEmpty();
    }

    @Test
    void oneServiceDownProducesExactlyOneCriticalServiceDownAlert() {
        when(serviceHealthAggregationService.checkAll()).thenReturn(List.of(
                new ServiceHealthStatus("payment-service", "Payment Service", "http://localhost:8083", "UP", 200, 5, null, OffsetDateTime.now()),
                new ServiceHealthStatus("audit-service", "Audit Service", "http://localhost:8085", "UNREACHABLE", null, 130, "Connection refused", OffsetDateTime.now())));

        List<Alert> alerts = alertsService.currentAlerts();

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).severity()).isEqualTo(AlertSeverity.CRITICAL);
        assertThat(alerts.get(0).category()).isEqualTo("Service Down");
        assertThat(alerts.get(0).message()).contains("Audit Service").contains("UNREACHABLE");
    }
}
