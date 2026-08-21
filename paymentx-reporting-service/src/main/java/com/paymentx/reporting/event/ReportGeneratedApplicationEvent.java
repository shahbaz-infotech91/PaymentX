package com.paymentx.reporting.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * ====================================================================
 * ENGLISH: Internal Spring event decoupling the transactional write
 * from the Kafka publish - see Reconciliation/Audit Service's
 * identical AFTER_COMMIT rationale.
 *
 * HINGLISH: Internal Spring event jo transactional write ko Kafka
 * publish se decouple karta hai - Reconciliation/Audit Service ka
 * identical AFTER_COMMIT reasoning.
 * ====================================================================
 */
@Getter
public class ReportGeneratedApplicationEvent extends ApplicationEvent {
    private final ReportGeneratedEvent payload;
    private final String traceId;

    public ReportGeneratedApplicationEvent(Object source, ReportGeneratedEvent payload, String traceId) {
        super(source);
        this.payload = payload;
        this.traceId = traceId;
    }
}
