package com.paymentx.audit.controller;

import com.paymentx.audit.dto.AuditEventRequest;
import com.paymentx.audit.entity.EventType;
import com.paymentx.common.dto.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
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
 * AuditControllerIntegrationTest is a JUnit test class in the audit module of PaymentX, package com.paymentx.audit.controller. It is used within audit's internal request/data flow, exercising real behaviour against Testcontainers-backed infrastructure to catch regressions before deployment.
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * AuditControllerIntegrationTest PaymentX ke audit module ka ek JUnit test class hai, package com.paymentx.audit.controller me. Ye audit ke internal request/data flow me use hoti hai, Testcontainers-backed real infrastructure ke against actual behaviour test karke deployment se pehle regressions pakadti hai.
 * ====================================================================
 */
class AuditControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("paymentx_audit_test")
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
        return "http://localhost:" + port + "/api/v1/audit-events";
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Roles", "AUDIT_WRITER");
        return headers;
    }

    @Test
    void recordAndGetById_roundTripsCorrectly() {
        AuditEventRequest request = new AuditEventRequest(EventType.API_REQUEST, "api-gateway",
                "user-1", "USER", "corr-it-1", "trace-it-1", null, "BANK001", null, "{\"path\":\"/api/v1/payments\"}");

        ResponseEntity<ApiResponse<com.paymentx.audit.dto.AuditEventResponse>> createResponse = restTemplate.exchange(
                baseUrl(), HttpMethod.POST, new HttpEntity<>(request, adminHeaders()),
                new ParameterizedTypeReference<ApiResponse<com.paymentx.audit.dto.AuditEventResponse>>() {});

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var id = createResponse.getBody().data().id();

        ResponseEntity<ApiResponse<com.paymentx.audit.dto.AuditEventResponse>> getResponse = restTemplate.exchange(
                baseUrl() + "/" + id, HttpMethod.GET, null,
                new ParameterizedTypeReference<ApiResponse<com.paymentx.audit.dto.AuditEventResponse>>() {});

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody().data().correlationId()).isEqualTo("corr-it-1");
    }

    @Test
    void record_withoutWriterRole_isDenied() {
        AuditEventRequest request = new AuditEventRequest(EventType.SECURITY_EVENT, "audit-service",
                null, null, null, null, null, null, null, "{}");

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl(), HttpMethod.POST, new HttpEntity<>(request), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void search_byCorrelationId_returnsMatchingResults() {
        AuditEventRequest request = new AuditEventRequest(EventType.SYSTEM_EVENT, "audit-service",
                null, null, "corr-search-test", null, null, null, null, "{}");
        restTemplate.exchange(baseUrl(), HttpMethod.POST, new HttpEntity<>(request, adminHeaders()), String.class);

        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "?correlationId=corr-search-test", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("corr-search-test");
    }

    @Test
    void getById_nonExistent_returns404() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "/00000000-0000-0000-0000-000000000000", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
