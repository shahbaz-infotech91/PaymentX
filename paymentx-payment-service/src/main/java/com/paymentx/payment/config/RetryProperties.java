package com.paymentx.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Externalizes retry policy so it's tunable without a code change/redeploy
 * - "no hardcoded values" applies to operational policy just as much as
 * to business data. Bound from payment.retry.* in application.yml.
 */
@Component
@ConfigurationProperties(prefix = "payment.retry")
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * RetryProperties is a configuration class in the payment module of PaymentX. It lives in package com.paymentx.payment.config and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * RetryProperties PaymentX ke payment module ka ek configuration class hai. Ye com.paymentx.payment.config package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class RetryProperties {

    private int maxAttempts = 3;
    private int initialBackoffSeconds = 30;
    private int backoffMultiplier = 2;

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public int getInitialBackoffSeconds() {
        return initialBackoffSeconds;
    }

    public void setInitialBackoffSeconds(int initialBackoffSeconds) {
        this.initialBackoffSeconds = initialBackoffSeconds;
    }

    public int getBackoffMultiplier() {
        return backoffMultiplier;
    }

    public void setBackoffMultiplier(int backoffMultiplier) {
        this.backoffMultiplier = backoffMultiplier;
    }
}
