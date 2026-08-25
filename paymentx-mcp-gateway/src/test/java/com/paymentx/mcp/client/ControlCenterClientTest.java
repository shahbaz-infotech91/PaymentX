package com.paymentx.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.paymentx.mcp.config.McpGatewayProperties;
import com.paymentx.mcp.exception.McpException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 4.4 - real wire-level tests for ControlCenterClient, matching PaymentServiceClientTest's own
 * established WireMock pattern exactly (resetAll() over stop/start for downtime simulation,
 * Scenario.STARTED not the raw string). Constructed directly (not via a Spring-proxied bean), so
 * @CircuitBreaker/@Retry are not woven here - this verifies wire-format parsing and error-mapping
 * against real HTTP responses shaped like Control Center's real ApiResponse envelope.
 */
class ControlCenterClientTest {

    private static WireMockServer wireMockServer;
    private ControlCenterClient client;

    @BeforeAll
    static void startServer() {
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();
    }

    @AfterAll
    static void stopServer() {
        wireMockServer.stop();
    }

    @BeforeEach
    void setUp() {
        wireMockServer.resetAll();
        McpGatewayProperties properties = new McpGatewayProperties();
        properties.setControlCenterUrl("http://localhost:" + wireMockServer.port());
        properties.setControlCenterConnectTimeoutMs(2000);
        properties.setControlCenterReadTimeoutMs(2000);
        client = new ControlCenterClient(new RestTemplateBuilder(), properties);
    }

    @Test
    void getPaymentStats_realSuccessEnvelope_parsesRealData() {
        wireMockServer.stubFor(get(urlPathEqualTo("/api/v1/postgres/payments/stats")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"success": true, "data": {"totalPayments": 100, "successful": 80, "failed": 10,
                          "pending": 5, "processing": 5, "successRatePercent": 80.0, "failureRatePercent": 10.0,
                          "averageLatencyMillis": 1250.5, "paymentsLastHour": 12, "tps": 0.0033}}
                        """)));

        JsonNode result = client.getPaymentStats("corr-1");

        assertThat(result.path("totalPayments").asLong()).isEqualTo(100);
        assertThat(result.path("successRatePercent").asDouble()).isEqualTo(80.0);
    }

    @Test
    void getTableInfo_realSuccessEnvelope_parsesRealTableArray() {
        wireMockServer.stubFor(get(urlPathEqualTo("/api/v1/postgres/databases/payment/tables")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"success": true, "data": [{"tableName": "payment", "rowCount": 100}, {"tableName": "payment_status_history", "rowCount": 250}]}
                        """)));

        JsonNode result = client.getTableInfo("payment", "corr-1");

        assertThat(result.isArray()).isTrue();
        assertThat(result.size()).isEqualTo(2);
        assertThat(result.get(0).path("tableName").asText()).isEqualTo("payment");
    }

    @Test
    void getPaymentStats_real500_throwsRetryableTargetServiceUnavailable() {
        wireMockServer.stubFor(get(urlPathEqualTo("/api/v1/postgres/payments/stats")).willReturn(aResponse()
                .withStatus(500)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"success": false, "error": {"errorCode": "INTERNAL_ERROR", "message": "boom"}}
                        """)));

        assertThatThrownBy(() -> client.getPaymentStats("corr-1"))
                .isInstanceOf(McpException.class)
                .satisfies(ex -> assertThat(((McpException) ex).isRetryable()).isTrue());
    }

    @Test
    void getTableInfo_unreachableService_throwsRetryableTargetServiceUnavailable() {
        wireMockServer.stop();
        try {
            assertThatThrownBy(() -> client.getTableInfo("payment", "corr-1"))
                    .isInstanceOf(McpException.class)
                    .satisfies(ex -> assertThat(((McpException) ex).isRetryable()).isTrue());
        } finally {
            wireMockServer.start();
        }
    }
}
