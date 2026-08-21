package com.paymentx.routing.security;

import com.paymentx.routing.dto.RouteRuleRequest;
import com.paymentx.routing.entity.RoutingScheme;
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

/**
 * WHY this test targets @PreAuthorize behavior specifically, not full
 * JWT validation: JWT validation happens at API Gateway (see
 * SecurityConfig javadoc) - Routing Service's own security
 * responsibility is AUTHORIZATION from the trusted X-Roles header
 * Gateway propagates. These tests verify that boundary: no X-Roles
 * header (or the wrong role) is correctly denied, matching the header
 * exactly as Gateway would send it is correctly permitted.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * RoutingSecurityTest is a JUnit test class in the routing module of PaymentX, package com.paymentx.routing.security. It is used within routing's internal request/data flow, exercising real behaviour against Testcontainers-backed infrastructure to catch regressions before deployment.
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * RoutingSecurityTest PaymentX ke routing module ka ek JUnit test class hai, package com.paymentx.routing.security me. Ye routing ke internal request/data flow me use hoti hai, Testcontainers-backed real infrastructure ke against actual behaviour test karke deployment se pehle regressions pakadti hai.
 * ====================================================================
 */
class RoutingSecurityTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("paymentx_routing_security_test")
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
        return "http://localhost:" + port + "/api/v1/routes";
    }

    private RouteRuleRequest sampleRequest() {
        return new RouteRuleRequest(RoutingScheme.INSTANT_PAYMENT, "SECTEST", "sec-test-route", 500, true, false, "security test");
    }

    @Test
    void create_withoutRolesHeader_isDenied() {
        HttpEntity<RouteRuleRequest> entity = new HttpEntity<>(sampleRequest());
        ResponseEntity<String> response = restTemplate.exchange(baseUrl(), HttpMethod.POST, entity, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void create_withWrongRole_isDenied() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Roles", "READ_ONLY");
        HttpEntity<RouteRuleRequest> entity = new HttpEntity<>(sampleRequest(), headers);

        ResponseEntity<String> response = restTemplate.exchange(baseUrl(), HttpMethod.POST, entity, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void create_withAdminRole_isPermitted() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Roles", "ROUTING_ADMIN");
        HttpEntity<RouteRuleRequest> entity = new HttpEntity<>(sampleRequest(), headers);

        ResponseEntity<String> response = restTemplate.exchange(baseUrl(), HttpMethod.POST, entity, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void readEndpoints_requireNoRole() {
        ResponseEntity<String> response = restTemplate.getForEntity(baseUrl(), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
