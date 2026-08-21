package com.paymentx.mcp.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * English:
 * Real Spring context test for the one plain REST endpoint this module
 * exposes (Step 5/36) - GET /api/v1/mcp/tools. Also doubles as this
 * module's health/readiness check (Step 40 item 25): if the full
 * application context (including the real MCP server bean wiring in
 * config/McpServerConfig) fails to start, this test fails before ever
 * reaching an assertion.
 *
 * Hinglish:
 * Is module ke ek hi plain REST endpoint ke liye real Spring context
 * test (Step 5/36) - GET /api/v1/mcp/tools. Is module ka health/
 * readiness check bhi hai (Step 40 item 25): agar poora application
 * context (config/McpServerConfig me real MCP server bean wiring
 * sameत) start hone me fail hota hai, ye test kisi assertion tak
 * pahunchne se pehle hi fail ho jaata hai.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class McpToolCatalogControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void listTools_realFiveToolCatalog_allReadOnlyAndEnabled() {
        ResponseEntity<String> response = restTemplate.getForEntity("http://localhost:" + port + "/api/v1/mcp/tools", String.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("payment.lookup", "payment.status", "routing.lookup",
                "reconciliation.status", "audit.search");
        assertThat(response.getBody()).doesNotContain("\"readWrite\":\"WRITE\"");
    }

    @Test
    void actuatorHealth_realApplicationContext_reportsUp() {
        ResponseEntity<String> response = restTemplate.getForEntity("http://localhost:" + port + "/actuator/health", String.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }
}
