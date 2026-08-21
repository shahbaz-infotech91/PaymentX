package com.paymentx.controlcenter.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ENGLISH: Proves the actual Phase 5 API Tester security boundary -
 * ApiTesterAllowlist.match() only ever returns a real endpoint for an
 * exact (service, method, path) combination that is really on the
 * fixed catalog, never for an arbitrary host/path someone tries to
 * smuggle through the service/path fields, and DELETE is rejected
 * everywhere except the two real endpoints this platform explicitly
 * allows it on.
 *
 * HINGLISH: Phase 5 API Tester ka actual security boundary prove karta
 * hai - ApiTesterAllowlist.match() sirf tabhi ek real endpoint return
 * karta hai jab (service, method, path) ka combination exactly fixed
 * catalog par ho, kabhi kisi arbitrary host/path ke liye nahi jise koi
 * service/path fields ke through smuggle karne ki koshish kare, aur
 * DELETE har jagah reject hota hai sivaay un do real endpoints ke jahan
 * ye platform explicitly ise allow karta hai.
 */
class ApiTesterAllowlistTest {

    private final ApiTesterAllowlist allowlist = new ApiTesterAllowlist();

    @Test
    void matchesARealKnownEndpoint() {
        assertThat(allowlist.match("api-gateway", "GET", "/api/v1/payments/ABC123")).isPresent();
    }

    @Test
    void matchIsCaseInsensitiveOnMethod() {
        assertThat(allowlist.match("api-gateway", "get", "/api/v1/payments/ABC123")).isPresent();
    }

    @Test
    void rejectsAPathThatDoesNotMatchAnyRealTemplate() {
        assertThat(allowlist.match("api-gateway", "GET", "/api/v1/totally-made-up-endpoint")).isEmpty();
    }

    @Test
    void rejectsAnUnknownService() {
        assertThat(allowlist.match("evil-service", "GET", "/actuator/health")).isEmpty();
    }

    @Test
    void rejectsAMethodNotAllowedOnARealPath() {
        // GET /api/v1/routes/{id} is real; DELETE is only real for a different, explicit purpose.
        assertThat(allowlist.match("routing-service", "PATCH", "/api/v1/routes/1")).isEmpty();
    }

    @Test
    void deleteIsOnlyAllowedOnTheTwoRealExplicitlyAllowedEndpoints() {
        long deleteCount = allowlist.all().stream().filter(e -> "DELETE".equals(e.method())).count();
        assertThat(deleteCount).isEqualTo(2);
        assertThat(allowlist.match("routing-service", "DELETE", "/api/v1/routes/42")).isPresent();
        assertThat(allowlist.match("reporting-service", "DELETE", "/api/v1/reports/executions/42")).isPresent();
        assertThat(allowlist.match("payment-service", "DELETE", "/api/v1/payments/ABC123")).isEmpty();
    }

    @Test
    void everyAllowlistedEndpointDescriptorIsWellFormed() {
        assertThat(allowlist.all()).isNotEmpty();
        allowlist.all().forEach(entry -> {
            assertThat(entry.service()).isNotBlank();
            assertThat(entry.method()).isNotBlank();
            assertThat(entry.pathTemplate()).startsWith("/");
            assertThat(entry.description()).isNotBlank();
        });
    }
}
