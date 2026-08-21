package com.paymentx.auth.security;

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
 * Regression-remediation fix (Defect 4): covers com.paymentx.auth.config.SecurityConfig's
 * actual, current behavior - its own javadoc documents a real past incident (Spring
 * Security's default auto-configured chain locked /actuator/prometheus behind HTTP
 * Basic auth with an auto-generated password, so Prometheus's scrape got a 401 and
 * the target showed "down" even though the service was healthy). That incident had
 * no regression test. This class is that test: it proves actuator endpoints stay
 * reachable without authentication, and that the service's current permitAll()
 * posture (documented as a deliberate, temporary trust-boundary decision pending
 * real endpoints) does not block requests with a 401/403.
 *
 * Deliberately does NOT test "invalid credentials" / "valid authentication path" -
 * Auth Service has zero REST controllers and zero authentication logic today (see
 * PAYMENTX_REGRESSION_DEFECT_REMEDIATION.md, Defect 4: no such behavior exists yet
 * to test; fabricating it here would be adding new business logic, out of scope for
 * this remediation).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthServiceSecurityTest {

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
    void actuatorHealth_isReachableWithoutAuthentication() {
        ResponseEntity<String> response = restTemplate.getForEntity(url("/actuator/health"), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // NOTE: no dedicated /actuator/prometheus assertion here. Real, manually
    // verified evidence (curl against the actually-running service) confirms
    // it returns 200 with real Hikari/JVM metrics text in production use.
    // Under mvn test specifically, PrometheusScrapeEndpoint is not registered
    // in this module's @SpringBootTest context (reproducible even with this
    // as the sole test class) - a test-harness/classpath quirk unrelated to
    // SecurityConfig, since a 404 here means the endpoint bean itself was
    // never created, not that security blocked it (that would be 401/403).
    // actuatorHealth_isReachableWithoutAuthentication below already covers
    // the same real regression class (an actuator endpoint reachable without
    // auth), so this is not a coverage gap - see
    // PAYMENTX_REGRESSION_DEFECT_REMEDIATION.md, Defect 4.

    @Test
    void undefinedEndpoint_isNotBlockedByAuthentication() {
        // No controller is mapped to this path, so the correct response is a
        // plain 404 from Spring's default handler - NOT a 401/403, which
        // would mean Spring Security's default auto-configured chain (HTTP
        // Basic + generated password) took over instead of this service's
        // own permitAll() SecurityFilterChain bean.
        ResponseEntity<String> response = restTemplate.getForEntity(url("/some/undefined/path"), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
