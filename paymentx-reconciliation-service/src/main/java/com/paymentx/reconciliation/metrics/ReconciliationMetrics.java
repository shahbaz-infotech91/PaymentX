package com.paymentx.reconciliation.metrics;

import com.paymentx.reconciliation.entity.ReconciliationStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * ReconciliationMetrics is a component in the reconciliation module of PaymentX. It lives in package com.paymentx.reconciliation.metrics and participates in reconciliation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through reconciliation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * ReconciliationMetrics PaymentX ke reconciliation module ka ek component hai. Ye com.paymentx.reconciliation.metrics package me hai aur reconciliation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise reconciliation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class ReconciliationMetrics {

    private static final String BATCH_DURATION_TIMER = "reconciliation.batch.duration";
    private static final String RECORD_CLASSIFICATION_COUNTER = "reconciliation.record.classification";
    private static final String CONSUMER_LAG_TIMER = "reconciliation.kafka.consumer.lag";
    private static final String FILE_IMPORT_TIMER = "reconciliation.file.import.duration";
    private static final String BATCH_FAILURE_COUNTER = "reconciliation.batch.failure";

    private final MeterRegistry meterRegistry;

    public ReconciliationMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public Timer.Sample startTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordBatchDuration(Timer.Sample sample, String batchType) {
        sample.stop(Timer.builder(BATCH_DURATION_TIMER).tag("batchType", batchType).publishPercentileHistogram().register(meterRegistry));
    }

    public void recordClassification(ReconciliationStatus status) {
        Counter.builder(RECORD_CLASSIFICATION_COUNTER).tag("status", status.name()).register(meterRegistry).increment();
    }

    public void recordConsumerLag(long lagMillis, String topic) {
        Timer.builder(CONSUMER_LAG_TIMER).tag("topic", topic).register(meterRegistry)
                .record(java.time.Duration.ofMillis(lagMillis));
    }

    public void recordFileImportDuration(Timer.Sample sample, String fileType) {
        sample.stop(Timer.builder(FILE_IMPORT_TIMER).tag("fileType", fileType).register(meterRegistry));
    }

    public void recordBatchFailure(String batchType) {
        Counter.builder(BATCH_FAILURE_COUNTER).tag("batchType", batchType).register(meterRegistry).increment();
    }
}
