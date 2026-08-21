package com.paymentx.gateway.filter;

import com.paymentx.common.constant.ErrorCodes;
import com.paymentx.common.constant.HeaderConstants;
import com.paymentx.common.dto.ApiResponse;
import com.paymentx.common.dto.ErrorResponse;
import com.paymentx.gateway.cache.ApiKeyCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * WHY this runs as a distinct GlobalFilter rather than through Spring
 * Security's filter chain (unlike JWT, handled by SecurityConfig): API
 * keys are not a Spring Security AuthenticationProvider concept out of
 * the box, and hand-rolling one adds indirection for a check this
 * simple - "does this key exist and is it active in Redis." Public
 * paths and JWT-bearing requests skip this filter entirely (both are
 * valid alternative authentication paths).
 */
@Component
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * ApiKeyAuthenticationGlobalFilter is a servlet filter in the gateway module of PaymentX. It lives in package com.paymentx.gateway.filter and participates in gateway's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through gateway's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * ApiKeyAuthenticationGlobalFilter PaymentX ke gateway module ka ek servlet filter hai. Ye com.paymentx.gateway.filter package me hai aur gateway ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise gateway ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class ApiKeyAuthenticationGlobalFilter implements GlobalFilter, Ordered {

    private final ApiKeyCacheService apiKeyCacheService;
    private final ObjectMapper objectMapper;

    public ApiKeyAuthenticationGlobalFilter(ApiKeyCacheService apiKeyCacheService, ObjectMapper objectMapper) {
        this.apiKeyCacheService = apiKeyCacheService;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String authorization = exchange.getRequest().getHeaders().getFirst(HeaderConstants.AUTHORIZATION);
        if (authorization != null && !authorization.isBlank()) {
            // A JWT (or other Authorization-based credential) is present -
            // Spring Security's filter chain owns validating it. This
            // filter only activates for the API-key path.
            return chain.filter(exchange);
        }

        String apiKey = exchange.getRequest().getHeaders().getFirst(HeaderConstants.API_KEY);
        if (apiKey == null || apiKey.isBlank()) {
            return chain.filter(exchange);
        }

        return apiKeyCacheService.isValid(apiKey)
                .flatMap(valid -> {
                    if (!valid) {
                        return reject(exchange, "API key is invalid or inactive");
                    }
                    return apiKeyCacheService.getParticipantId(apiKey)
                            .defaultIfEmpty("")
                            .flatMap(participantId -> {
                                ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                                        .headers(httpHeaders -> httpHeaders.set("X-Participant-Id", participantId))
                                        .build();
                                return chain.filter(exchange.mutate().request(mutatedRequest).build());
                            });
                });
    }

    private Mono<Void> reject(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ApiResponse<Void> body = ApiResponse.error(ErrorResponse.of(
                ErrorCodes.UNAUTHORIZED, message, exchange.getRequest().getPath().value()));

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
        return Ordered.HIGHEST_PRECEDENCE + 104;
    }
}
