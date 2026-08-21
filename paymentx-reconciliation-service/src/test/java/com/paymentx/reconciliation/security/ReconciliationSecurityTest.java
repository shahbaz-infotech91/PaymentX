package com.paymentx.reconciliation.security;

import com.paymentx.reconciliation.entity.BatchType;
import com.paymentx.reconciliation.dto.StartReconciliationRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * ReconciliationSecurityTest is a JUnit test class in the reconciliation module of PaymentX, package com.paymentx.reconciliation.security. It is used within reconciliation's internal request/data flow, exercising real behaviour against Testcontainers-backed infrastructure to catch regressions before deployment.
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * ReconciliationSecurityTest PaymentX ke reconciliation module ka ek JUnit test class hai, package com.paymentx.reconciliation.security me. Ye reconciliation ke internal request/data flow me use hoti hai, Testcontainers-backed real infrastructure ke against actual behaviour test karke deployment se pehle regressions pakadti hai.
 * ====================================================================
 */
class ReconciliationSecurityTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("paymentx_reconciliation_security_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String baseUrl() {
        return "http://localhost:" + port + "/api/v1/reconciliation";
    }

    @Test
    void startReconciliation_withoutAdminRole_isDenied() {
        StartReconciliationRequest request = new StartReconciliationRequest(BatchType.FULL, null, null, null);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/batches", HttpMethod.POST, new HttpEntity<>(request), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void startReconciliation_withAdminRole_isPermitted() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Roles", "RECONCILIATION_ADMIN");
        StartReconciliationRequest request = new StartReconciliationRequest(BatchType.FULL, null, null, null);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/batches", HttpMethod.POST, new HttpEntity<>(request, headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }

    @Test
    void getBatchStatus_noRoleRequired_returns404ForUnknownIdButNotForbidden() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "/batches/00000000-0000-0000-0000-000000000000", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
