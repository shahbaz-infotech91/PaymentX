package com.paymentx.audit.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * AuditMetrics is a component in the audit module of PaymentX. It lives in package com.paymentx.audit.metrics and participates in audit's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through audit's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * AuditMetrics PaymentX ke audit module ka ek component hai. Ye com.paymentx.audit.metrics package me hai aur audit ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise audit ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class AuditMetrics {

    private static final String WRITE_TIMER = "audit.write.latency";
    private static final String SEARCH_TIMER = "audit.search.latency";
    private static final String CONSUMER_LAG_TIMER = "audit.kafka.consumer.lag";
    private static final String DUPLICATE_COUNTER = "audit.kafka.duplicate.skipped";
    private static final String CACHE_HIT_COUNTER = "audit.search.cache.hit";
    private static final String CACHE_MISS_COUNTER = "audit.search.cache.miss";

    private final MeterRegistry meterRegistry;

    public AuditMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public Timer.Sample startTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordWrite(Timer.Sample sample, String eventType) {
        sample.stop(Timer.builder(WRITE_TIMER).tag("eventType", eventType).publishPercentileHistogram().register(meterRegistry));
    }

    public void recordSearch(Timer.Sample sample) {
        sample.stop(Timer.builder(SEARCH_TIMER).publishPercentileHistogram().register(meterRegistry));
    }

    /** WHY this is called "consumer lag" but measured as occurredAt ->
     *  now, not Kafka's own partition-offset lag metric: Spring Kafka /
     *  Micrometer already auto-expose true partition-offset consumer lag
     *  (kafka.consumer.fetch.manager metrics, enabled by
     *  micrometer-registry-prometheus + spring-kafka's built-in
     *  KafkaListenerObservationConvention) - this custom timer measures
     *  something those don't: true end-to-end latency from when the
     *  business event actually happened at the source (PaymentEvent's
     *  occurredAt) to when Audit Service durably recorded it, which is
     *  the metric that actually answers "how far behind is the audit
     *  trail from reality," combining network, queueing, AND processing
     *  time in one number. */
    public void recordConsumerLag(long lagMillis, String topic) {
        Timer.builder(CONSUMER_LAG_TIMER).tag("topic", topic).register(meterRegistry)
                .record(java.time.Duration.ofMillis(lagMillis));
    }

    public void recordDuplicateSkipped(String topic) {
        Counter.builder(DUPLICATE_COUNTER).tag("topic", topic).register(meterRegistry).increment();
    }

    public void recordCacheHit() {
        Counter.builder(CACHE_HIT_COUNTER).register(meterRegistry).increment();
    }

    public void recordCacheMiss() {
        Counter.builder(CACHE_MISS_COUNTER).register(meterRegistry).increment();
    }
}
