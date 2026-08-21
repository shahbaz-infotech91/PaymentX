package com.paymentx.routing.controller;

import com.paymentx.common.dto.ApiResponse;
import com.paymentx.routing.dto.RouteRuleRequest;
import com.paymentx.routing.dto.RouteRuleResponse;
import com.paymentx.routing.entity.RoutingScheme;
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
 * RoutingControllerIntegrationTest is a JUnit test class in the routing module of PaymentX, package com.paymentx.routing.controller. It is used within routing's internal request/data flow, exercising real behaviour against Testcontainers-backed infrastructure to catch regressions before deployment.
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * RoutingControllerIntegrationTest PaymentX ke routing module ka ek JUnit test class hai, package com.paymentx.routing.controller me. Ye routing ke internal request/data flow me use hoti hai, Testcontainers-backed real infrastructure ke against actual behaviour test karke deployment se pehle regressions pakadti hai.
 * ====================================================================
 */
class RoutingControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("paymentx_routing_test")
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

    @Test
    void createAndGetRoutingRule_roundTripsCorrectly() {
        RouteRuleRequest request = new RouteRuleRequest(
                RoutingScheme.INSTANT_PAYMENT, "BANK999", "instant-payment-processor-bank999",
                20, true, false, "IT test rule");

        // POST is @PreAuthorize("hasRole('ROUTING_ADMIN')") - in production
        // this role comes from the X-Roles header API Gateway sets after
        // validating the caller's JWT (see HeaderRoleAuthenticationFilter).
        // Calling the endpoint directly here, we set that trusted header
        // ourselves to simulate an authorized admin request.
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Roles", "ROUTING_ADMIN");

        ResponseEntity<ApiResponse<RouteRuleResponse>> createResponse = restTemplate.exchange(
                baseUrl(), HttpMethod.POST, new HttpEntity<>(request, headers),
                new ParameterizedTypeReference<ApiResponse<RouteRuleResponse>>() {});

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResponse.getBody().data().targetRoute()).isEqualTo("instant-payment-processor-bank999");

        var id = createResponse.getBody().data().id();

        ResponseEntity<ApiResponse<RouteRuleResponse>> getResponse = restTemplate.exchange(
                baseUrl() + "/" + id, HttpMethod.GET, null,
                new ParameterizedTypeReference<ApiResponse<RouteRuleResponse>>() {});

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody().data().participantId()).isEqualTo("BANK999");
    }

    @Test
    void getById_nonExistent_returns404() {
        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                baseUrl() + "/00000000-0000-0000-0000-000000000000", HttpMethod.GET, null,
                new ParameterizedTypeReference<ApiResponse<Void>>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void resolveDefault_returnsSeededDefaultRoute() {
        ResponseEntity<ApiResponse<RouteRuleResponse>> response = restTemplate.exchange(
                baseUrl() + "/default?scheme=CARD_PAYMENT", HttpMethod.GET, null,
                new ParameterizedTypeReference<ApiResponse<RouteRuleResponse>>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().isDefault()).isTrue();
    }

    @Test
    void resolveForParticipant_prefersParticipantSpecificOverDefault() {
        ResponseEntity<ApiResponse<RouteRuleResponse>> response = restTemplate.exchange(
                baseUrl() + "/participant/BANK001?scheme=INSTANT_PAYMENT", HttpMethod.GET, null,
                new ParameterizedTypeReference<ApiResponse<RouteRuleResponse>>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().participantId()).isEqualTo("BANK001");
    }

    @Test
    void list_returnsSeededRoutes() {
        ResponseEntity<String> response = restTemplate.getForEntity(baseUrl(), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("INSTANT_PAYMENT");
    }
}
