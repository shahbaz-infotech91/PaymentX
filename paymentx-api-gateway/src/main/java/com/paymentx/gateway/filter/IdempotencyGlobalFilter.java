package com.paymentx.gateway.filter;

import com.paymentx.common.constant.HeaderConstants;
import com.paymentx.gateway.config.GatewaySecurityProperties;
import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * WHY only state-changing methods (POST/PUT/PATCH), and only when the
 * client SUPPLIES an idempotency key: idempotency is an opt-in contract
 * between client and server, not something the gateway can infer or
 * force onto every request.
 *
 * WHY a full response-body decorator rather than caching just the
 * status code: a useful idempotency guarantee means a retried request
 * gets the IDENTICAL response the original returned (e.g. a created
 * resource's ID), not just a bare "this succeeded before" signal.
 */
@Component
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * IdempotencyGlobalFilter is a servlet filter in the gateway module of PaymentX. It lives in package com.paymentx.gateway.filter and participates in gateway's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through gateway's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * IdempotencyGlobalFilter PaymentX ke gateway module ka ek servlet filter hai. Ye com.paymentx.gateway.filter package me hai aur gateway ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise gateway ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class IdempotencyGlobalFilter implements GlobalFilter, Ordered {

    private static final List<HttpMethod> IDEMPOTENT_CHECK_METHODS = List.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH);
    private static final String KEY_PREFIX = "gateway:idempotency:";
    private static final String STATUS_SEPARATOR = "|||";

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final GatewaySecurityProperties gatewaySecurityProperties;

    public IdempotencyGlobalFilter(ReactiveRedisTemplate<String, String> redisTemplate,
                                    GatewaySecurityProperties gatewaySecurityProperties) {
        this.redisTemplate = redisTemplate;
        this.gatewaySecurityProperties = gatewaySecurityProperties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!IDEMPOTENT_CHECK_METHODS.contains(exchange.getRequest().getMethod())) {
            return chain.filter(exchange);
        }

        String idempotencyKey = exchange.getRequest().getHeaders().getFirst(HeaderConstants.IDEMPOTENCY_KEY);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return chain.filter(exchange);
        }

        String redisKey = KEY_PREFIX + idempotencyKey;

        return redisTemplate.opsForValue().get(redisKey)
                .flatMap(cached -> replay(exchange, cached))
                .switchIfEmpty(Mono.defer(() -> proceedAndCache(exchange, chain, redisKey)));
    }

    private Mono<Void> replay(ServerWebExchange exchange, String cached) {
        int separatorIndex = cached.indexOf(STATUS_SEPARATOR);
        int statusCode = Integer.parseInt(cached.substring(0, separatorIndex));
        String body = cached.substring(separatorIndex + STATUS_SEPARATOR.length());

        exchange.getResponse().setStatusCode(HttpStatus.valueOf(statusCode));
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private Mono<Void> proceedAndCache(ServerWebExchange exchange, GatewayFilterChain chain, String redisKey) {
        ServerHttpResponse originalResponse = exchange.getResponse();

        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                return DataBufferUtils.join(Flux.from(body))
                        .flatMap(dataBuffer -> {
                            byte[] bytes = new byte[dataBuffer.readableByteCount()];
                            dataBuffer.read(bytes);
                            DataBufferUtils.release(dataBuffer);

                            String responseBody = new String(bytes, StandardCharsets.UTF_8);
                            int statusCode = originalResponse.getStatusCode() != null
                                    ? originalResponse.getStatusCode().value() : 200;

                            String toCache = statusCode + STATUS_SEPARATOR + responseBody;

                            return redisTemplate.opsForValue()
                                    .set(redisKey, toCache, Duration.ofSeconds(gatewaySecurityProperties.getIdempotency().getTtlSeconds()))
                                    .then(Mono.defer(() -> {
                                        DataBuffer buffer = originalResponse.bufferFactory().wrap(bytes);
                                        return originalResponse.writeWith(Mono.just(buffer));
                                    }));
                        });
            }
        };

        return chain.filter(exchange.mutate().response(decoratedResponse).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 108;
    }
}
