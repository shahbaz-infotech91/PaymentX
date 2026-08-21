package com.paymentx.reporting.event;

import com.paymentx.common.event.PaymentEvent;
import com.paymentx.reporting.constant.ReportingKafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * ====================================================================
 * ENGLISH: Publishes the 3 explicit report lifecycle events. Matches
 * Reconciliation/Audit Service's producer pattern exactly.
 *
 * HINGLISH: 3 explicit report lifecycle events publish karta hai.
 * Reconciliation/Audit Service ke producer pattern jaisa hi.
 * ====================================================================
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReportingEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishReportGenerated(ReportGeneratedEvent event, String traceId) {
        PaymentEvent<ReportGeneratedEvent> envelope = PaymentEvent.of("REPORT_GENERATED", traceId, event);
        kafkaTemplate.send(ReportingKafkaTopics.REPORT_GENERATED, event.getReportExecutionId().toString(), envelope)
                .whenComplete((r, ex) -> logIfFailed(ex, "report-generated", event.getReportExecutionId()));
    }

    public void publishReportFailed(ReportFailedEvent event, String traceId) {
        PaymentEvent<ReportFailedEvent> envelope = PaymentEvent.of("REPORT_FAILED", traceId, event);
        kafkaTemplate.send(ReportingKafkaTopics.REPORT_FAILED, event.getReportExecutionId().toString(), envelope)
                .whenComplete((r, ex) -> logIfFailed(ex, "report-failed", event.getReportExecutionId()));
    }

    public void publishReportCompleted(ReportCompletedEvent event, String traceId) {
        PaymentEvent<ReportCompletedEvent> envelope = PaymentEvent.of("REPORT_COMPLETED", traceId, event);
        kafkaTemplate.send(ReportingKafkaTopics.REPORT_COMPLETED, event.getReportExecutionId().toString(), envelope)
                .whenComplete((r, ex) -> logIfFailed(ex, "report-completed", event.getReportExecutionId()));
    }

    private void logIfFailed(Throwable ex, String eventName, Object key) {
        if (ex != null) {
            log.warn("Failed to publish {} event key={}", eventName, key, ex);
        }
    }
}
