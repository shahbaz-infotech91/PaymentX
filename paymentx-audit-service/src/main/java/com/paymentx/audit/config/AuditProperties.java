package com.paymentx.audit.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "audit")
@Getter
@Setter
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * AuditProperties is a configuration class in the audit module of PaymentX. It lives in package com.paymentx.audit.config and participates in audit's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through audit's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * AuditProperties PaymentX ke audit module ka ek configuration class hai. Ye com.paymentx.audit.config package me hai aur audit ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise audit ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class AuditProperties {

    private Cache cache = new Cache();
    private Consumer consumer = new Consumer();

    @Getter
    @Setter
    public static class Cache {
        private Duration searchTtl = Duration.ofMinutes(2);
    }

    @Getter
    @Setter
    public static class Consumer {
        private long retryInitialIntervalMillis = 1000L;
        private double retryMultiplier = 2.0;
        private long retryMaxElapsedMillis = 8000L;
    }
}
