package com.paymentx.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * HARDENING FIX: X-Participant-Id is a SERVER-DERIVED trust signal
 * (set only after JWT/API-key validation succeeds - see
 * JwtParticipantPropagationGlobalFilter and ApiKeyAuthenticationGlobalFilter),
 * consumed downstream by ParticipantValidationGlobalFilter and forwarded
 * to backend services as an implicit trust boundary. Before this filter
 * existed, a client could set this header directly on the inbound
 * request and it would flow through unmodified for any code path that
 * didn't happen to overwrite it (e.g. a request with no JWT and no API
 * key would carry a completely unverified, client-supplied participant
 * ID all the way to the backend). Running first (lowest order value,
 * before even correlation/trace ID assignment) guarantees no other
 * filter or route ever sees a client-supplied value for this header.
 */
@Component
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * SecurityHeaderSanitizationGlobalFilter is a servlet filter in the gateway module of PaymentX. It lives in package com.paymentx.gateway.filter and participates in gateway's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through gateway's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * SecurityHeaderSanitizationGlobalFilter PaymentX ke gateway module ka ek servlet filter hai. Ye com.paymentx.gateway.filter package me hai aur gateway ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise gateway ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class SecurityHeaderSanitizationGlobalFilter implements GlobalFilter, Ordered {

    private static final String PARTICIPANT_ID_HEADER = "X-Participant-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (exchange.getRequest().getHeaders().containsKey(PARTICIPANT_ID_HEADER)) {
            ServerHttpRequest sanitizedRequest = exchange.getRequest().mutate()
                    .headers(httpHeaders -> httpHeaders.remove(PARTICIPANT_ID_HEADER))
                    .build();
            return chain.filter(exchange.mutate().request(sanitizedRequest).build());
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
