package com.paymentx.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * GatewayConfig is a configuration class in the gateway module of PaymentX. It lives in package com.paymentx.gateway.config and participates in gateway's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through gateway's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * GatewayConfig PaymentX ke gateway module ka ek configuration class hai. Ye com.paymentx.gateway.config package me hai aur gateway ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise gateway ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class GatewayConfig {

    private final GatewaySecurityProperties gatewaySecurityProperties;

    @Value("${gateway.routes.validation-service-uri}")
    private String validationServiceUri;

    @Value("${gateway.routes.payment-service-uri}")
    private String paymentServiceUri;

    @Value("${gateway.request.max-size-bytes:1048576}")
    private long maxRequestSizeBytes;

    public GatewayConfig(GatewaySecurityProperties gatewaySecurityProperties) {
        this.gatewaySecurityProperties = gatewaySecurityProperties;
    }

    @Bean
    public RedisRateLimiter redisRateLimiter() {
        GatewaySecurityProperties.RateLimit rl = gatewaySecurityProperties.getRateLimit();

        return new RedisRateLimiter(
                rl.getReplenishRatePerSecond(),
                rl.getBurstCapacity(),
                rl.getRequestedTokens()
        );
    }

    @Bean
    public KeyResolver rateLimitKeyResolver() {
        return exchange -> {
            String participantId =
                    exchange.getRequest().getHeaders().getFirst("X-Participant-Id");

            if (participantId != null && !participantId.isBlank()) {
                return Mono.just(participantId);
            }

            if (exchange.getRequest().getRemoteAddress() != null
                    && exchange.getRequest().getRemoteAddress().getAddress() != null) {
                return Mono.just(
                        exchange.getRequest()
                                .getRemoteAddress()
                                .getAddress()
                                .getHostAddress());
            }

            return Mono.just("unknown");
        };
    }

    @Bean
    public RouteLocator customRouteLocator(
            RouteLocatorBuilder builder,
            RedisRateLimiter redisRateLimiter,
            KeyResolver keyResolver) {

        return builder.routes()

                .route("validation-service", r -> r
                        .path("/api/v1/validations/**")
                        .filters(f -> f
                                .requestRateLimiter(c -> c
                                        .setRateLimiter(redisRateLimiter)
                                        .setKeyResolver(keyResolver))
                                .setRequestSize(maxRequestSizeBytes))
                        .uri(validationServiceUri))

                .route("payment-service", r -> r
                        .path("/api/v1/payments/**")
                        .filters(f -> f
                                .requestRateLimiter(c -> c
                                        .setRateLimiter(redisRateLimiter)
                                        .setKeyResolver(keyResolver))
                                .setRequestSize(maxRequestSizeBytes))
                        .uri(paymentServiceUri))

                .build();
    }
}