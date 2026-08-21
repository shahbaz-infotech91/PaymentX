package com.paymentx.reporting.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression-remediation fix (Defect 4): Reporting Service had no src/test
 * directory at all despite being a substantial service (report generation,
 * export, scheduling, search). Covers report retrieval (the seeded report
 * catalog), an empty-result search, and error handling for an unknown
 * execution ID - the three behaviors this task's Defect 4 instructions call
 * out for Reporting Service specifically.
 *
 * Follows the exact same convention as
 * paymentx-validation-service's ValidationControllerIntegrationTest:
 * Testcontainers Postgres (real Liquibase migrations run against it,
 * including this service's own V1_0_2__seed_report_catalog.yaml) +
 * Testcontainers Kafka (this service's real KafkaConsumerConfig/
 * KafkaProducerConfig beans need a reachable broker at context startup) +
 * @SpringBootTest(RANDOM_PORT) + TestRestTemplate for a real end-to-end HTTP
 * round trip. Redis is intentionally left on its default localhost:6379 (the
 * platform's already-running local dev instance) since report-result caching
 * is not exercised by these three tests.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReportingControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("paymentx_reporting_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.7.0"));

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String baseUrl() {
        return "http://localhost:" + port + "/api/v1/reports";
    }

    @Test
    void listReports_returnsSeededReportCatalog() {
        ResponseEntity<String> response = restTemplate.getForEntity(baseUrl(), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).contains("\"success\":true");
        // V1_0_2__seed_report_catalog.yaml seeds this row on every fresh
        // database (including this ephemeral Testcontainers instance) - a
        // real catalog entry, not a fabricated test fixture.
        assertThat(response.getBody()).contains("reportType");
    }

    @Test
    void searchExecutions_withNoExecutions_returnsEmptyPage() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "/executions?page=0&size=20", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).contains("\"content\":[]");
        assertThat(response.getBody()).contains("\"totalElements\":0");
    }

    @Test
    void getExecutionStatus_unknownId_returns404() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "/executions/" + UUID.randomUUID(), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).contains("\"success\":false");
    }
}
