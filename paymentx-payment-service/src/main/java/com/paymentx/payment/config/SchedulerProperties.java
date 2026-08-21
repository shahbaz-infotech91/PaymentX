package com.paymentx.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "payment.scheduler")
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * SchedulerProperties is a configuration class in the payment module of PaymentX. It lives in package com.paymentx.payment.config and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * SchedulerProperties PaymentX ke payment module ka ek configuration class hai. Ye com.paymentx.payment.config package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class SchedulerProperties {

    private long outboxIntervalMillis = 5000;
    private int outboxBatchSize = 50;
    private int outboxMaxRetries = 5;

    private long retryIntervalMillis = 10000;

    private long timeoutIntervalMillis = 60000;
    private int timeoutStuckThresholdMinutes = 15;

    public long getOutboxIntervalMillis() {
        return outboxIntervalMillis;
    }

    public void setOutboxIntervalMillis(long outboxIntervalMillis) {
        this.outboxIntervalMillis = outboxIntervalMillis;
    }

    public int getOutboxBatchSize() {
        return outboxBatchSize;
    }

    public void setOutboxBatchSize(int outboxBatchSize) {
        this.outboxBatchSize = outboxBatchSize;
    }

    public int getOutboxMaxRetries() {
        return outboxMaxRetries;
    }

    public void setOutboxMaxRetries(int outboxMaxRetries) {
        this.outboxMaxRetries = outboxMaxRetries;
    }

    public long getRetryIntervalMillis() {
        return retryIntervalMillis;
    }

    public void setRetryIntervalMillis(long retryIntervalMillis) {
        this.retryIntervalMillis = retryIntervalMillis;
    }

    public long getTimeoutIntervalMillis() {
        return timeoutIntervalMillis;
    }

    public void setTimeoutIntervalMillis(long timeoutIntervalMillis) {
        this.timeoutIntervalMillis = timeoutIntervalMillis;
    }

    public int getTimeoutStuckThresholdMinutes() {
        return timeoutStuckThresholdMinutes;
    }

    public void setTimeoutStuckThresholdMinutes(int timeoutStuckThresholdMinutes) {
        this.timeoutStuckThresholdMinutes = timeoutStuckThresholdMinutes;
    }
}
