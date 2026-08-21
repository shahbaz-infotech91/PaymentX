package com.paymentx.controlcenter.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ENGLISH: Proves the actual Phase 6 authentication gate behaviour -
 * a misconfigured "enabled but no real token" setup refuses to start
 * rather than run open; when disabled (this repo's real default),
 * every request passes through unauthenticated exactly like every
 * prior phase already verified; when enabled, a real request without a
 * matching real Authorization: Bearer token is rejected with a real
 * 401, the real health check is exempt, and the real correct token is
 * accepted.
 *
 * HINGLISH: Phase 6 authentication gate ka actual behaviour prove
 * karta hai - ek misconfigured "enabled lekin koi real token nahi"
 * setup open chalne ke bajaye start hi nahi hota; jab disabled ho (is
 * repo ka real default), har request bina authentication ke guzarti
 * hai exactly jaise har pichle phase me already verify kiya gaya; jab
 * enabled ho, ek real request bina matching real Authorization: Bearer
 * token ke ek real 401 se reject hoti hai, real health check exempt
 * hai, aur real sahi token accept hota hai.
 */
class DashboardAuthFilterTest {

    // Mirrors the real Spring-managed ObjectMapper bean this filter is actually constructed with in production
    // (Spring Boot auto-registers JavaTimeModule whenever jackson-datatype-jsr310 is on the classpath, which it
    // is here) - a bare `new ObjectMapper()` would fail to serialize ApiResponse's real OffsetDateTime field.
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void refusesToStartWhenEnabledWithoutARealToken() {
        ControlCenterProperties properties = new ControlCenterProperties();
        properties.getSecurity().setEnabled(true);
        properties.getSecurity().setDashboardToken(" ");

        assertThatThrownBy(() -> new DashboardAuthFilter(properties, objectMapper))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dashboard-token is blank");
    }

    @Test
    void startsCleanlyWhenDisabled() {
        ControlCenterProperties properties = new ControlCenterProperties();
        properties.getSecurity().setEnabled(false);

        DashboardAuthFilter filter = new DashboardAuthFilter(properties, objectMapper);

        assertThat(filter).isNotNull();
    }

    @Test
    void whenDisabledEveryRequestPassesThroughUnauthenticated() throws Exception {
        ControlCenterProperties properties = new ControlCenterProperties();
        properties.getSecurity().setEnabled(false);
        DashboardAuthFilter filter = new DashboardAuthFilter(properties, objectMapper);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/postgres/payments");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200); // MockHttpServletResponse default when the chain runs cleanly
    }

    @Test
    void whenEnabledARequestWithNoTokenIsRejectedWithARealUnauthorized() throws Exception {
        ControlCenterProperties properties = new ControlCenterProperties();
        properties.getSecurity().setEnabled(true);
        properties.getSecurity().setDashboardToken("real-token-value");
        DashboardAuthFilter filter = new DashboardAuthFilter(properties, objectMapper);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/postgres/payments");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("UNAUTHORIZED");
    }

    @Test
    void whenEnabledTheRealHealthCheckIsExemptFromAuthentication() throws Exception {
        ControlCenterProperties properties = new ControlCenterProperties();
        properties.getSecurity().setEnabled(true);
        properties.getSecurity().setDashboardToken("real-token-value");
        DashboardAuthFilter filter = new DashboardAuthFilter(properties, objectMapper);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void whenEnabledTheRealAiHealthCheckIsExemptFromAuthentication() throws Exception {
        ControlCenterProperties properties = new ControlCenterProperties();
        properties.getSecurity().setEnabled(true);
        properties.getSecurity().setDashboardToken("real-token-value");
        DashboardAuthFilter filter = new DashboardAuthFilter(properties, objectMapper);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/ai/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void whenEnabledTheRealCorrectTokenIsAccepted() throws Exception {
        ControlCenterProperties properties = new ControlCenterProperties();
        properties.getSecurity().setEnabled(true);
        properties.getSecurity().setDashboardToken("real-token-value");
        DashboardAuthFilter filter = new DashboardAuthFilter(properties, objectMapper);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/postgres/payments");
        request.addHeader("Authorization", "Bearer real-token-value");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void whenEnabledAWrongTokenIsRejected() throws Exception {
        ControlCenterProperties properties = new ControlCenterProperties();
        properties.getSecurity().setEnabled(true);
        properties.getSecurity().setDashboardToken("real-token-value");
        DashboardAuthFilter filter = new DashboardAuthFilter(properties, objectMapper);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/postgres/payments");
        request.addHeader("Authorization", "Bearer wrong-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
    }
}
