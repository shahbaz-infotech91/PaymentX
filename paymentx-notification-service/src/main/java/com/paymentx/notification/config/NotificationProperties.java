package com.paymentx.notification.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "notification")
@Getter
@Setter
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * NotificationProperties is a configuration class in the notification module of PaymentX. It lives in package com.paymentx.notification.config and participates in notification's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through notification's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * NotificationProperties PaymentX ke notification module ka ek configuration class hai. Ye com.paymentx.notification.config package me hai aur notification ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise notification ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class NotificationProperties {

    private Webhook webhook = new Webhook();
    private Email email = new Email();
    private Retry retry = new Retry();
    private Cache cache = new Cache();
    private Consumer consumer = new Consumer();
    private Async async = new Async();

    @Getter
    @Setter
    public static class Email {
        private String from = "noreply@paymentx.local";
    }

    @Getter
    @Setter
    public static class Webhook {
        private long connectTimeoutMillis = 3000L;
        private long readTimeoutMillis = 5000L;
        private String signingSecret = "local-dev-only-webhook-secret-change-me";
    }

    @Getter
    @Setter
    public static class Retry {
        private int maxAttempts = 5;
        private long initialIntervalMillis = 2000L;
        private double multiplier = 2.0;
        private long maxIntervalMillis = 60000L;
    }

    @Getter
    @Setter
    public static class Cache {
        private Duration templateTtl = Duration.ofHours(1);
        private Duration idempotencyTtl = Duration.ofHours(24);
        private Duration dedupTtl = Duration.ofMinutes(10);
    }

    @Getter
    @Setter
    public static class Consumer {
        private long retryInitialIntervalMillis = 1000L;
        private double retryMultiplier = 2.0;
        private long retryMaxElapsedMillis = 8000L;
    }

    @Getter
    @Setter
    public static class Async {
        private int corePoolSize = 4;
        private int maxPoolSize = 16;
        private int queueCapacity = 500;
    }
}
