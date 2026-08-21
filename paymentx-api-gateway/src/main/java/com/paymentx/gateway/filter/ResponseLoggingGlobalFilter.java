package com.paymentx.gateway.filter;

import com.paymentx.common.constant.HeaderConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * WHY the lowest order value (outermost wrapper) of all filters in this
 * package: a GlobalFilter's pre-chain.filter() code runs in ASCENDING
 * order value; its post-chain.filter() code (via .then()/doFinally) runs
 * in the REVERSE order - the outermost (lowest order) filter's
 * post-processing runs LAST. Giving this filter the lowest value
 * guarantees it captures the true end-to-end duration and final response
 * status after every other filter (including the actual route call) has
 * completed.
 */
@Component
@Slf4j
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * ResponseLoggingGlobalFilter is a servlet filter in the gateway module of PaymentX. It lives in package com.paymentx.gateway.filter and participates in gateway's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through gateway's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * ResponseLoggingGlobalFilter PaymentX ke gateway module ka ek servlet filter hai. Ye com.paymentx.gateway.filter package me hai aur gateway ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise gateway ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class ResponseLoggingGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startNanos = System.nanoTime();
        String correlationId = exchange.getRequest().getHeaders().getFirst(HeaderConstants.CORRELATION_ID);

        return chain.filter(exchange)
                .doFinally(signalType -> {
                    long durationMillis = (System.nanoTime() - startNanos) / 1_000_000;
                    log.info("Completed request correlationId={} status={} durationMillis={} signal={}",
                            correlationId,
                            exchange.getResponse().getStatusCode(),
                            durationMillis,
                            signalType);
                });
    }

    @Override
    public int getOrder() {
        // Shifted from HIGHEST_PRECEDENCE to +1 - SecurityHeaderSanitizationGlobalFilter
        // now occupies HIGHEST_PRECEDENCE itself (must run before literally
        // everything, including this filter, to strip spoofed headers
        // before any logging/processing touches them). This filter still
        // wraps every other filter in this package (+100 and above).
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
