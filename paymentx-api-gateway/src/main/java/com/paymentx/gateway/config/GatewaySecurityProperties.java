package com.paymentx.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * WHY named GatewaySecurityProperties, not GatewayProperties: Spring
 * Cloud Gateway itself has an internal class named exactly
 * org.springframework.cloud.gateway.config.GatewayProperties, registered
 * as a bean by GatewayAutoConfiguration under the default bean name
 * "gatewayProperties" (Spring's default bean-naming convention is the
 * decapitalized simple class name). A class in THIS project also named
 * GatewayProperties registers under the identical bean name, causing
 * BeanDefinitionOverrideException at every application context startup
 * (not just tests) - this is a real naming collision that was hit in
 * practice. The name here also more accurately reflects its actual
 * scope: everything under this class binds from the gateway.security.*
 * prefix (JWT, rate-limit, idempotency), not general Spring Cloud
 * Gateway configuration.
 */
@Component
@ConfigurationProperties(prefix = "gateway.security")
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * GatewaySecurityProperties is a configuration class in the gateway module of PaymentX. It lives in package com.paymentx.gateway.config and participates in gateway's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through gateway's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * GatewaySecurityProperties PaymentX ke gateway module ka ek configuration class hai. Ye com.paymentx.gateway.config package me hai aur gateway ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise gateway ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class GatewaySecurityProperties {

    private String jwtSecret;
    private long jwtClockSkewSeconds = 30;
    private List<String> publicPaths = List.of("/actuator/**", "/swagger-ui/**", "/api-docs/**", "/v3/api-docs/**");
    private RateLimit rateLimit = new RateLimit();
    private Idempotency idempotency = new Idempotency();

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public long getJwtClockSkewSeconds() {
        return jwtClockSkewSeconds;
    }

    public void setJwtClockSkewSeconds(long jwtClockSkewSeconds) {
        this.jwtClockSkewSeconds = jwtClockSkewSeconds;
    }

    public List<String> getPublicPaths() {
        return publicPaths;
    }

    public void setPublicPaths(List<String> publicPaths) {
        this.publicPaths = publicPaths;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(RateLimit rateLimit) {
        this.rateLimit = rateLimit;
    }

    public Idempotency getIdempotency() {
        return idempotency;
    }

    public void setIdempotency(Idempotency idempotency) {
        this.idempotency = idempotency;
    }

    public static class RateLimit {
        private int replenishRatePerSecond = 20;
        private int burstCapacity = 40;
        private int requestedTokens = 1;

        public int getReplenishRatePerSecond() {
            return replenishRatePerSecond;
        }

        public void setReplenishRatePerSecond(int replenishRatePerSecond) {
            this.replenishRatePerSecond = replenishRatePerSecond;
        }

        public int getBurstCapacity() {
            return burstCapacity;
        }

        public void setBurstCapacity(int burstCapacity) {
            this.burstCapacity = burstCapacity;
        }

        public int getRequestedTokens() {
            return requestedTokens;
        }

        public void setRequestedTokens(int requestedTokens) {
            this.requestedTokens = requestedTokens;
        }
    }

    public static class Idempotency {
        private long ttlSeconds = 86400;

        public long getTtlSeconds() {
            return ttlSeconds;
        }

        public void setTtlSeconds(long ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
        }
    }
}
