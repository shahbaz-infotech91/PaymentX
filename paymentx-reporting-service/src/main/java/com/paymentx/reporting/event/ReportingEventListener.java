package com.paymentx.reporting.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * ====================================================================
 * ENGLISH: The AFTER_COMMIT boundary that actually calls Kafka -
 * guarantees "publish only after successful DB commit" for all 3
 * report lifecycle events.
 *
 * HINGLISH: Wo AFTER_COMMIT boundary jo actually Kafka call karta hai -
 * "publish only after successful DB commit" guarantee karta hai sab 3
 * report lifecycle events ke liye.
 * ====================================================================
 */
@Component
@RequiredArgsConstructor
public class ReportingEventListener {

    private final ReportingEventProducer reportingEventProducer;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReportGenerated(ReportGeneratedApplicationEvent event) {
        reportingEventProducer.publishReportGenerated(event.getPayload(), event.getTraceId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReportFailed(ReportFailedApplicationEvent event) {
        reportingEventProducer.publishReportFailed(event.getPayload(), event.getTraceId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReportCompleted(ReportCompletedApplicationEvent event) {
        reportingEventProducer.publishReportCompleted(event.getPayload(), event.getTraceId());
    }
}
