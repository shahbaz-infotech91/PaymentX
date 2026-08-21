package com.paymentx.gateway.filter;

import com.paymentx.common.constant.ErrorCodes;
import com.paymentx.common.dto.ApiResponse;
import com.paymentx.common.dto.ErrorResponse;
import com.paymentx.gateway.cache.ParticipantCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Component
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * ParticipantValidationGlobalFilter is a servlet filter in the gateway module of PaymentX. It lives in package com.paymentx.gateway.filter and participates in gateway's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through gateway's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * ParticipantValidationGlobalFilter PaymentX ke gateway module ka ek servlet filter hai. Ye com.paymentx.gateway.filter package me hai aur gateway ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise gateway ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class ParticipantValidationGlobalFilter implements GlobalFilter, Ordered {

    private final ParticipantCacheService participantCacheService;
    private final ObjectMapper objectMapper;

    public ParticipantValidationGlobalFilter(ParticipantCacheService participantCacheService,
                                              ObjectMapper objectMapper) {
        this.participantCacheService = participantCacheService;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String participantId = exchange.getRequest().getHeaders().getFirst("X-Participant-Id");
        if (participantId == null || participantId.isBlank()) {
            return chain.filter(exchange);
        }

        return participantCacheService.isActive(participantId)
                .flatMap(active -> {
                    if (!active) {
                        return reject(exchange, "Participant " + participantId + " is not active");
                    }
                    return chain.filter(exchange);
                });
    }

    private Mono<Void> reject(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ApiResponse<Void> body = ApiResponse.error(ErrorResponse.of(
                ErrorCodes.FORBIDDEN, message, exchange.getRequest().getPath().value()));

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
        return Ordered.HIGHEST_PRECEDENCE + 107;
    }
}
