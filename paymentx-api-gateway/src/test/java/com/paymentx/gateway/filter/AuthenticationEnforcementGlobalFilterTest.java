package com.paymentx.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.paymentx.gateway.config.GatewaySecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression-remediation fix (Defect 4): API Gateway had zero test classes despite
 * real, non-trivial filter logic (see paymentx-api-gateway/src/test/java's only
 * prior file, TestJwtUtil, which was an unused helper - no test ever consumed it).
 * This covers com.paymentx.gateway.filter.AuthenticationEnforcementGlobalFilter,
 * the "actual final authentication gate" per its own class javadoc: public paths
 * bypass it, everything else is rejected with 401 unless X-Participant-Id is
 * already set (by an earlier JWT or API-key filter having succeeded).
 *
 * Tested as a plain unit test against the filter directly (MockServerWebExchange +
 * a stub GatewayFilterChain), not a full @SpringBootTest - this filter has no
 * Redis/WireMock/network dependency, so booting the full reactive gateway context
 * (rate limiter, routes, JWT decoder) would only add flakiness without adding
 * coverage. Uses spring-test's existing Mock* reactive test support - no new
 * testing framework.
 */
class AuthenticationEnforcementGlobalFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private GatewaySecurityProperties propertiesWithPublicPaths(String... patterns) {
        GatewaySecurityProperties properties = new GatewaySecurityProperties();
        properties.setPublicPaths(List.of(patterns));
        return properties;
    }

    private GatewayFilterChain chainRecordingInvocation(AtomicBoolean invoked) {
        return exchange -> {
            invoked.set(true);
            return Mono.empty();
        };
    }

    @Test
    void publicPath_bypassesAuthenticationCheck() {
        AuthenticationEnforcementGlobalFilter filter =
                new AuthenticationEnforcementGlobalFilter(propertiesWithPublicPaths("/actuator/**"), objectMapper);
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/health").build());
        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        filter.filter(exchange, chainRecordingInvocation(chainInvoked)).block();

        assertThat(chainInvoked).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void nonPublicPath_withoutParticipantId_isRejectedWithUnauthorized() {
        AuthenticationEnforcementGlobalFilter filter =
                new AuthenticationEnforcementGlobalFilter(propertiesWithPublicPaths("/actuator/**"), objectMapper);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/payments/123").build());
        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        filter.filter(exchange, chainRecordingInvocation(chainInvoked)).block();

        assertThat(chainInvoked).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        String body = exchange.getResponse().getBodyAsString().block();
        assertThat(body).contains("\"success\":false").contains("Authentication required");
    }

    @Test
    void nonPublicPath_withParticipantId_isPermitted() {
        AuthenticationEnforcementGlobalFilter filter =
                new AuthenticationEnforcementGlobalFilter(propertiesWithPublicPaths("/actuator/**"), objectMapper);
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/payments/123")
                        .header("X-Participant-Id", "participant-1")
                        .build());
        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        filter.filter(exchange, chainRecordingInvocation(chainInvoked)).block();

        assertThat(chainInvoked).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }
}
