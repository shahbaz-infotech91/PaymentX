package com.paymentx.gateway.filter;

import com.paymentx.common.constant.HeaderConstants;
import com.paymentx.common.util.CorrelationIdUtils;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * WHY this is a reactive GlobalFilter, NOT a servlet OncePerRequestFilter
 * like Validation Service's and Payment Service's own
 * CorrelationIdFilter classes: those are correct for their MVC-based
 * stacks but fundamentally incompatible with Spring Cloud
 * Gateway, which runs on WebFlux/Netty. A servlet filter cannot be
 * registered in a reactive application at all. MDC (ThreadLocal-based)
 * also does not propagate reliably across reactive operator chains,
 * which can hop threads - Reactor Context is the reactive equivalent
 * used here instead.
 *
 * WHY the header constant is reused from HeaderConstants rather than
 * duplicated: the STRING VALUE is shared (no duplication), even though
 * the filter mechanics must differ by stack (servlet vs reactive).
 *
 * Gateway is the platform's actual entry point for every external
 * request, so this filter always generates a correlation ID if the
 * caller didn't supply one, then propagates it downstream via the same
 * header so every backend service sees a consistent value.
 */
@Component
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * CorrelationIdGlobalFilter is a servlet filter in the gateway module of PaymentX. It lives in package com.paymentx.gateway.filter and participates in gateway's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through gateway's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * CorrelationIdGlobalFilter PaymentX ke gateway module ka ek servlet filter hai. Ye com.paymentx.gateway.filter package me hai aur gateway ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise gateway ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class CorrelationIdGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String incoming = exchange.getRequest().getHeaders().getFirst(HeaderConstants.CORRELATION_ID);
        String correlationId = CorrelationIdUtils.resolve(incoming);

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .headers(httpHeaders -> httpHeaders.set(HeaderConstants.CORRELATION_ID, correlationId))
                .build();

        ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();
        mutatedExchange.getResponse().getHeaders().add(HeaderConstants.CORRELATION_ID, correlationId);

        return chain.filter(mutatedExchange)
                .contextWrite(Context.of(HeaderConstants.CORRELATION_ID, correlationId));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }
}
