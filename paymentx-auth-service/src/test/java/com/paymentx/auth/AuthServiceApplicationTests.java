package com.paymentx.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Regression-remediation fix (Defect 4): Auth Service previously had zero test
 * classes and, per Defect 3, no working datasource at all - so a bug like
 * "context fails to start because the datasource is misconfigured" (the exact
 * regression this remediation fixed) had no automated test that would have
 * caught it. This is the same real Spring context-load smoke test convention
 * every other module already uses (see
 * paymentx-control-center/backend/.../ControlCenterApplicationTests.java) -
 * it boots the full application context, including the real HikariCP
 * DataSource wired up in Defect 3, and fails loudly if any bean cannot wire.
 * Uses an isolated Testcontainers Postgres (same pattern as
 * paymentx-routing-service's RoutingSecurityTest) rather than the shared dev
 * instance, so this test does not depend on - or contend for connections
 * with - the local dev Postgres.
 */
@Testcontainers
@SpringBootTest
class AuthServiceApplicationTests {

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

    @Test
    void contextLoads() {
        // Intentionally empty - @SpringBootTest already proves the
        // context (including the real datasource) loads cleanly.
    }
}
