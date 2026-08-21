package com.paymentx.notification.metrics;

import com.paymentx.notification.entity.NotificationChannel;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * NotificationMetrics is a component in the notification module of PaymentX. It lives in package com.paymentx.notification.metrics and participates in notification's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through notification's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * NotificationMetrics PaymentX ke notification module ka ek component hai. Ye com.paymentx.notification.metrics package me hai aur notification ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise notification ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class NotificationMetrics {

    private static final String CHANNEL_SUCCESS_COUNTER = "notification.channel.success";
    private static final String CHANNEL_FAILURE_COUNTER = "notification.channel.failure";
    private static final String PROCESSING_TIMER = "notification.processing.time";
    private static final String CONSUMER_LAG_TIMER = "notification.kafka.consumer.lag";
    private static final String RETRY_COUNTER = "notification.retry.count";
    private static final String DLQ_COUNTER = "notification.dlq.count";
    private static final String DEDUP_SKIPPED_COUNTER = "notification.dedup.skipped";

    private final MeterRegistry meterRegistry;

    public NotificationMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordChannelSuccess(NotificationChannel channel) {
        Counter.builder(CHANNEL_SUCCESS_COUNTER).tag("channel", channel.name()).register(meterRegistry).increment();
    }

    public void recordChannelFailure(NotificationChannel channel) {
        Counter.builder(CHANNEL_FAILURE_COUNTER).tag("channel", channel.name()).register(meterRegistry).increment();
    }

    public Timer.Sample startTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordProcessingTime(Timer.Sample sample, NotificationChannel channel) {
        sample.stop(Timer.builder(PROCESSING_TIMER).tag("channel", channel.name()).publishPercentileHistogram().register(meterRegistry));
    }

    public void recordConsumerLag(long lagMillis, String topic) {
        Timer.builder(CONSUMER_LAG_TIMER).tag("topic", topic).register(meterRegistry)
                .record(java.time.Duration.ofMillis(lagMillis));
    }

    public void recordRetry(NotificationChannel channel) {
        Counter.builder(RETRY_COUNTER).tag("channel", channel.name()).register(meterRegistry).increment();
    }

    public void recordDeadLettered(NotificationChannel channel) {
        Counter.builder(DLQ_COUNTER).tag("channel", channel.name()).register(meterRegistry).increment();
    }

    public void recordDedupSkipped() {
        Counter.builder(DEDUP_SKIPPED_COUNTER).register(meterRegistry).increment();
    }
}
