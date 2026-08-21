package com.paymentx.reporting.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * ====================================================================
 * ENGLISH: AFTER_COMMIT wrapper for ReportFailedEvent - same rationale
 * as ReportGeneratedApplicationEvent.
 *
 * HINGLISH: ReportFailedEvent ke liye AFTER_COMMIT wrapper - wahi
 * reasoning jo ReportGeneratedApplicationEvent ka hai.
 * ====================================================================
 */
@Getter
public class ReportFailedApplicationEvent extends ApplicationEvent {
    private final ReportFailedEvent payload;
    private final String traceId;

    public ReportFailedApplicationEvent(Object source, ReportFailedEvent payload, String traceId) {
        super(source);
        this.payload = payload;
        this.traceId = traceId;
    }
}
