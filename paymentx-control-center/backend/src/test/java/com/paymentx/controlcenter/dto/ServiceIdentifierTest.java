package com.paymentx.controlcenter.dto;

import com.paymentx.controlcenter.config.ControlCenterProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ENGLISH: Proves the actual SSRF-allowlist behaviour of
 * ServiceIdentifier - every enum value resolves to a real,
 * server-configured URL from ControlCenterProperties.Services (never
 * null, never anything client-supplied), and an unrecognised slug is
 * rejected rather than silently accepted.
 *
 * HINGLISH: ServiceIdentifier ka actual SSRF-allowlist behaviour prove
 * karta hai - har enum value ek real, server-configured URL
 * ControlCenterProperties.Services se resolve hota hai (kabhi null
 * nahi, kabhi client-supplied kuch nahi), aur ek unrecognised slug
 * silently accept hone ke bajaye reject hota hai.
 */
class ServiceIdentifierTest {

    @Test
    void everyServiceIdentifierResolvesToARealConfiguredUrl() {
        ControlCenterProperties.Services services = new ControlCenterProperties.Services();
        for (ServiceIdentifier identifier : ServiceIdentifier.values()) {
            String url = identifier.resolveBaseUrl(services);
            assertThat(url).as("base URL for " + identifier).isNotBlank().startsWith("http://localhost:");
        }
    }

    @Test
    void fromSlugResolvesEachRealSlug() {
        assertThat(ServiceIdentifier.fromSlug("payment-service")).isEqualTo(ServiceIdentifier.PAYMENT_SERVICE);
        assertThat(ServiceIdentifier.fromSlug("api-gateway")).isEqualTo(ServiceIdentifier.API_GATEWAY);
    }

    @Test
    void fromSlugRejectsAnUnknownSlugInsteadOfGuessingAUrl() {
        assertThatThrownBy(() -> ServiceIdentifier.fromSlug("http://evil.example.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
