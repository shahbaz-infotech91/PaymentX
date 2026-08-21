package com.paymentx.notification.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * WebClientConfig is a configuration class in the notification module of PaymentX. It lives in package com.paymentx.notification.config and participates in notification's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through notification's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * WebClientConfig PaymentX ke notification module ka ek configuration class hai. Ye com.paymentx.notification.config package me hai aur notification ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise notification ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class WebClientConfig {

    /** WHY explicit timeouts (not Spring Boot's unbounded default): the
     *  explicit "Timeout" requirement for webhook delivery - an outbound
     *  webhook to a third-party endpoint that never responds must not
     *  hang a delivery thread indefinitely, which would eventually
     *  exhaust the thread pool handling Kafka-consumer-triggered sends
     *  (see NotificationDispatcher's async executor). */
    @Bean
    public RestTemplate webhookRestTemplate(RestTemplateBuilder builder, NotificationProperties notificationProperties) {
        var webhookProps = notificationProperties.getWebhook();
        return builder
                .connectTimeout(Duration.ofMillis(webhookProps.getConnectTimeoutMillis()))
                .readTimeout(Duration.ofMillis(webhookProps.getReadTimeoutMillis()))
                .build();
    }
}
