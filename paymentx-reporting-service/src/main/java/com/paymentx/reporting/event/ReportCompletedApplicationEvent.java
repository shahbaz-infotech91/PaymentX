package com.paymentx.reporting.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * ====================================================================
 * ENGLISH: AFTER_COMMIT wrapper for ReportCompletedEvent - same
 * rationale as ReportGeneratedApplicationEvent.
 *
 * HINGLISH: ReportCompletedEvent ke liye AFTER_COMMIT wrapper - wahi
 * reasoning jo ReportGeneratedApplicationEvent ka hai.
 * ====================================================================
 */
@Getter
public class ReportCompletedApplicationEvent extends ApplicationEvent {
    private final ReportCompletedEvent payload;
    private final String traceId;

    public ReportCompletedApplicationEvent(Object source, ReportCompletedEvent payload, String traceId) {
        super(source);
        this.payload = payload;
        this.traceId = traceId;
    }
}
