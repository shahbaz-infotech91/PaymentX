package com.paymentx.routing.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "routing")
@Getter
@Setter
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * RoutingProperties is a configuration class in the routing module of PaymentX. It lives in package com.paymentx.routing.config and participates in routing's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through routing's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * RoutingProperties PaymentX ke routing module ka ek configuration class hai. Ye com.paymentx.routing.config package me hai aur routing ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise routing ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class RoutingProperties {

    private Cache cache = new Cache();
    private Consumer consumer = new Consumer();

    @Getter
    @Setter
    public static class Cache {
        private Duration routeTtl = Duration.ofMinutes(10);
        private Duration idempotencyTtl = Duration.ofHours(24);
    }

    @Getter
    @Setter
    public static class Consumer {
        private long retryInitialIntervalMillis = 1000L;
        private double retryMultiplier = 2.0;
        private long retryMaxElapsedMillis = 8000L;
    }
}
