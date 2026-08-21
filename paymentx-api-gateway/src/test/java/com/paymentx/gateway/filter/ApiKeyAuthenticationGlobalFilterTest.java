package com.paymentx.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.paymentx.gateway.cache.ApiKeyCacheService;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression-remediation fix (Defect 4): covers
 * com.paymentx.gateway.filter.ApiKeyAuthenticationGlobalFilter - the platform's
 * X-Api-Key authentication path (the JWT path is covered separately at
 * SecurityConfig/TestJwtUtil). ApiKeyCacheService is mocked rather than backed by
 * real Redis: its own contract (isValid/getParticipantId) is the unit under test's
 * only collaborator, and this filter's actual logic - what it does with a
 * valid/invalid/absent key - is independent of Redis itself being reachable.
 */
class ApiKeyAuthenticationGlobalFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private GatewayFilterChain chainCapturingRequest(AtomicReference<ServerWebExchange> captured) {
        return exchange -> {
            captured.set(exchange);
            return Mono.empty();
        };
    }

    @Test
    void validApiKey_setsParticipantIdAndPermitsRequest() {
        ApiKeyCacheService cacheService = mock(ApiKeyCacheService.class);
        when(cacheService.isValid("valid-key")).thenReturn(Mono.just(true));
        when(cacheService.getParticipantId("valid-key")).thenReturn(Mono.just("participant-42"));
        ApiKeyAuthenticationGlobalFilter filter = new ApiKeyAuthenticationGlobalFilter(cacheService, objectMapper);

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/payments").header("X-Api-Key", "valid-key").build());
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();

        filter.filter(exchange, chainCapturingRequest(captured)).block();

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().getRequest().getHeaders().getFirst("X-Participant-Id"))
                .isEqualTo("participant-42");
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void invalidApiKey_isRejectedWithUnauthorized() {
        ApiKeyCacheService cacheService = mock(ApiKeyCacheService.class);
        when(cacheService.isValid("bad-key")).thenReturn(Mono.just(false));
        ApiKeyAuthenticationGlobalFilter filter = new ApiKeyAuthenticationGlobalFilter(cacheService, objectMapper);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/payments").header("X-Api-Key", "bad-key").build());
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();

        filter.filter(exchange, chainCapturingRequest(captured)).block();

        assertThat(captured.get()).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        String body = exchange.getResponse().getBodyAsString().block();
        assertThat(body).contains("API key is invalid or inactive");
    }

    @Test
    void authorizationHeaderPresent_bypassesApiKeyCheckEntirely() {
        ApiKeyCacheService cacheService = mock(ApiKeyCacheService.class);
        ApiKeyAuthenticationGlobalFilter filter = new ApiKeyAuthenticationGlobalFilter(cacheService, objectMapper);

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/payments").header("Authorization", "Bearer some.jwt.token").build());
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();

        filter.filter(exchange, chainCapturingRequest(captured)).block();

        assertThat(captured.get()).isNotNull();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void noAuthorizationAndNoApiKey_passesThroughUnauthenticated() {
        // Not this filter's job to reject - AuthenticationEnforcementGlobalFilter
        // (which runs later) is the actual final gate for unauthenticated requests.
        ApiKeyCacheService cacheService = mock(ApiKeyCacheService.class);
        ApiKeyAuthenticationGlobalFilter filter = new ApiKeyAuthenticationGlobalFilter(cacheService, objectMapper);

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/payments").build());
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();

        filter.filter(exchange, chainCapturingRequest(captured)).block();

        assertThat(captured.get()).isNotNull();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }
}
