package com.paymentx.reconciliation.scheduler;

import com.paymentx.reconciliation.entity.BatchStatus;
import com.paymentx.reconciliation.entity.BatchType;
import com.paymentx.reconciliation.entity.ReconciliationBatch;
import com.paymentx.reconciliation.event.BatchStartRequestedApplicationEvent;
import com.paymentx.reconciliation.repository.ReconciliationBatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * WHY the cron expression is a property placeholder
 * (${reconciliation.batch.scheduled-cron}, resolved against
 * application.yml), not a hardcoded @Scheduled(cron = "0 0 2 * * *")
 * literal: "No hardcoded business logic" is explicit - the operational
 * team may need to change when the nightly reconciliation runs without
 * a code deployment. This is Spring's standard, fully-supported
 * mechanism for externalizing a cron expression (a simple property
 * placeholder resolved by ScheduledAnnotationBeanPostProcessor at
 * startup, not SpEL evaluation) - the idiomatic way to do exactly this.
 */
@Component
@RequiredArgsConstructor
@Slf4j
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * ReconciliationScheduler is a component in the reconciliation module of PaymentX. It lives in package com.paymentx.reconciliation.scheduler and participates in reconciliation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through reconciliation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * ReconciliationScheduler PaymentX ke reconciliation module ka ek component hai. Ye com.paymentx.reconciliation.scheduler package me hai aur reconciliation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise reconciliation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class ReconciliationScheduler {

    private final ReconciliationBatchRepository reconciliationBatchRepository;
    // See BatchStartRequestedApplicationEvent's javadoc for why this is an
    // event publisher, not a direct ReconciliationBatchProcessor reference.
    private final ApplicationEventPublisher applicationEventPublisher;

    @Scheduled(cron = "${reconciliation.batch.scheduled-cron}")
    @Transactional
    public void runScheduledReconciliation() {
        log.info("Scheduled reconciliation triggered");

        ReconciliationBatch batch = ReconciliationBatch.builder()
                .batchType(BatchType.SCHEDULED)
                .status(BatchStatus.PENDING)
                .windowFrom(OffsetDateTime.now().minusDays(1))
                .windowTo(OffsetDateTime.now())
                .triggeredBy("SCHEDULER")
                .build();

        ReconciliationBatch saved = reconciliationBatchRepository.save(batch);
        applicationEventPublisher.publishEvent(new BatchStartRequestedApplicationEvent(this, saved.getId()));
    }
}
