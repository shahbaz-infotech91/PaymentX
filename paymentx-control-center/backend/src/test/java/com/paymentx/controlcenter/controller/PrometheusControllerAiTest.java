package com.paymentx.controlcenter.controller;

import com.paymentx.controlcenter.dto.ApiResponse;
import com.paymentx.controlcenter.dto.prometheus.PrometheusQueryResult;
import com.paymentx.controlcenter.service.PrometheusMetricsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 3.10.3 - Proves the new GET /api/v1/prometheus/metrics/ai endpoint is a thin adapter over
 * PrometheusMetricsService.runAi() (matching AiControllerTest's own direct-instantiation, no-MockMvc
 * convention exactly), coexisting correctly with the pre-existing /metrics/all and /metrics/{slug}
 * routes on the same controller.
 */
@ExtendWith(MockitoExtension.class)
class PrometheusControllerAiTest {

    @Mock
    private PrometheusMetricsService service;

    @Test
    void ai_wrapsTheRealServiceResponseInApiResponseSuccess() {
        PrometheusController controller = new PrometheusController(service);
        List<PrometheusQueryResult> serviceResponse = List.of(
                new PrometheusQueryResult("ai-llm-request-rate", "sum(rate(llm_requests_total[5m])) by (provider)", true, null, List.of()));
        when(service.runAi()).thenReturn(serviceResponse);

        ApiResponse<List<PrometheusQueryResult>> result = controller.ai();

        assertThat(result.success()).isTrue();
        assertThat(result.data()).isEqualTo(serviceResponse);
        verify(service).runAi();
    }

    @Test
    void ai_doesNotDelegateToRunAllOrRunQuery() {
        PrometheusController controller = new PrometheusController(service);
        when(service.runAi()).thenReturn(List.of());

        controller.ai();

        verify(service, org.mockito.Mockito.never()).runAll();
        verify(service, org.mockito.Mockito.never()).runQuery(org.mockito.ArgumentMatchers.anyString());
    }
}
