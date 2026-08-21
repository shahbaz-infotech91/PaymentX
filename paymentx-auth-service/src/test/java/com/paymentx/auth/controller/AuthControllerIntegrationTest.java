package com.paymentx.auth.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real HTTP round trip against a real, isolated Testcontainers Postgres
 * instance - Liquibase runs its real migrations (including the real
 * V1_0_2 dev-test-user seed changeset) against it on startup, exactly
 * like ValidationControllerIntegrationTest/ReportingControllerIntegrationTest
 * already establish as this platform's convention for this kind of test.
 * Uses the REAL seeded "test-user" identity - not a fabricated one -
 * proving the full migration -> seed -> login -> JWT chain together.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("paymentx_auth_test")
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

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void validCredentials_returns200WithRealJwt() {
        String body = """
                {"username":"test-user","password":"Test-Only-Password-Not-Real-123!"}
                """;
        ResponseEntity<String> response = restTemplate.postForEntity(url("/api/v1/auth/login"), jsonEntity(body), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"success\":true").contains("accessToken").contains("\"tokenType\":\"Bearer\"");
    }

    @Test
    void invalidPassword_returns401_genericMessage() {
        String body = """
                {"username":"test-user","password":"definitely-wrong"}
                """;
        ResponseEntity<String> response = restTemplate.postForEntity(url("/api/v1/auth/login"), jsonEntity(body), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("Invalid username or password");
    }

    @Test
    void unknownUsername_returns401_identicalGenericMessage() {
        String body = """
                {"username":"does-not-exist","password":"anything"}
                """;
        ResponseEntity<String> response = restTemplate.postForEntity(url("/api/v1/auth/login"), jsonEntity(body), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("Invalid username or password");
    }

    @Test
    void missingFields_returns400() {
        String body = """
                {"username":"","password":""}
                """;
        ResponseEntity<String> response = restTemplate.postForEntity(url("/api/v1/auth/login"), jsonEntity(body), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void undefinedPath_returns404_notLockedByDefaultSecurity() {
        ResponseEntity<String> response = restTemplate.getForEntity(url("/some/undefined/path"), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private org.springframework.http.HttpEntity<String> jsonEntity(String body) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return new org.springframework.http.HttpEntity<>(body, headers);
    }
}
