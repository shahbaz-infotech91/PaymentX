package com.paymentx.gateway.filter;

import com.paymentx.common.constant.HeaderConstants;
import com.paymentx.common.util.TraceIdUtils;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Distinct from correlationId (see CorrelationIdGlobalFilter's javadoc
 * for the per-hop vs end-to-end distinction). Gateway generates traceId
 * if absent, since it is the platform's actual entry point - Validation
 * Service's own traceId-generation fallback (unchanged, out of scope for
 * this task) remains a safety net if a request ever reaches it without
 * this header for any reason.
 */
@Component
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * TraceIdGlobalFilter is a servlet filter in the gateway module of PaymentX. It lives in package com.paymentx.gateway.filter and participates in gateway's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through gateway's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * TraceIdGlobalFilter PaymentX ke gateway module ka ek servlet filter hai. Ye com.paymentx.gateway.filter package me hai aur gateway ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise gateway ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class TraceIdGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String incoming = exchange.getRequest().getHeaders().getFirst(HeaderConstants.TRACE_ID);
        String traceId = TraceIdUtils.resolve(incoming);

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .headers(httpHeaders -> httpHeaders.set(HeaderConstants.TRACE_ID, traceId))
                .build();

        ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();
        mutatedExchange.getResponse().getHeaders().add(HeaderConstants.TRACE_ID, traceId);

        return chain.filter(mutatedExchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 101;
    }
}
