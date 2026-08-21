package com.paymentx.gateway.filter;

import com.paymentx.common.constant.ErrorCodes;
import com.paymentx.common.dto.ApiResponse;
import com.paymentx.common.dto.ErrorResponse;
import com.paymentx.gateway.config.GatewaySecurityProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * WHY this filter exists: Spring Security's own authorizeExchange rule
 * (SecurityConfig) is deliberately permitAll() at the authorization
 * layer - JWT validation still runs and still rejects BAD tokens via the
 * authentication filter's own failure handling, but Security no longer
 * blocks requests that simply have NO Authorization header, because
 * those requests are exactly the ones that need to reach
 * ApiKeyAuthenticationGlobalFilter (which runs later, in Gateway's own
 * GlobalFilter chain, which always executes AFTER Spring Security's
 * WebFilter chain has already run). Without this relaxation, API-key
 * authentication could never work - Security would reject every
 * API-key-only request with 401 before Gateway's own filters got a
 * chance to validate the key.
 *
 * This filter is therefore the ACTUAL final authentication gate: by the
 * time it runs (order +108, after both JwtParticipantPropagationGlobalFilter
 * and ApiKeyAuthenticationGlobalFilter), X-Participant-Id is set if
 * EITHER auth path succeeded. No participant ID and not a public path
 * means neither auth mechanism succeeded - reject with 401.
 */
@Component
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * AuthenticationEnforcementGlobalFilter is a servlet filter in the gateway module of PaymentX. It lives in package com.paymentx.gateway.filter and participates in gateway's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through gateway's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * AuthenticationEnforcementGlobalFilter PaymentX ke gateway module ka ek servlet filter hai. Ye com.paymentx.gateway.filter package me hai aur gateway ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise gateway ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class AuthenticationEnforcementGlobalFilter implements GlobalFilter, Ordered {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final GatewaySecurityProperties gatewaySecurityProperties;
    private final ObjectMapper objectMapper;

    public AuthenticationEnforcementGlobalFilter(GatewaySecurityProperties gatewaySecurityProperties, ObjectMapper objectMapper) {
        this.gatewaySecurityProperties = gatewaySecurityProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        boolean isPublic = gatewaySecurityProperties.getPublicPaths().stream()
                .anyMatch(pattern -> PATH_MATCHER.match(pattern, path));

        if (isPublic) {
            return chain.filter(exchange);
        }

        String participantId = exchange.getRequest().getHeaders().getFirst("X-Participant-Id");
        if (participantId == null || participantId.isBlank()) {
            return reject(exchange);
        }
        return chain.filter(exchange);
    }

    private Mono<Void> reject(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ApiResponse<Void> body = ApiResponse.error(ErrorResponse.of(
                ErrorCodes.UNAUTHORIZED,
                "Authentication required - provide a valid Bearer token or X-Api-Key",
                exchange.getRequest().getPath().value()));

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (Exception e) {
            bytes = "{\"success\":false}".getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 106;
    }
}
