package com.paymentx.reporting.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * ====================================================================
 * ENGLISH: Business metrics explicitly required - Report Duration,
 * Export Duration, Generation Time, Download Count, Failure Count -
 * all exposed via Micrometer/Prometheus for Grafana dashboards.
 *
 * HINGLISH: Explicitly required business metrics - Report Duration,
 * Export Duration, Generation Time, Download Count, Failure Count -
 * sab Micrometer/Prometheus se expose hoti hain Grafana dashboards ke
 * liye.
 * ====================================================================
 */
@Component
public class ReportingMetrics {

    private static final String GENERATION_TIMER = "reporting.generation.duration";
    private static final String EXPORT_TIMER = "reporting.export.duration";
    private static final String DOWNLOAD_COUNTER = "reporting.download.count";
    private static final String FAILURE_COUNTER = "reporting.failure.count";

    private final MeterRegistry meterRegistry;

    public ReportingMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public Timer.Sample startTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordGenerationDuration(Timer.Sample sample, String reportType) {
        sample.stop(Timer.builder(GENERATION_TIMER).tag("reportType", reportType).publishPercentileHistogram().register(meterRegistry));
    }

    public void recordExportDuration(Timer.Sample sample, String format) {
        sample.stop(Timer.builder(EXPORT_TIMER).tag("format", format).register(meterRegistry));
    }

    public void recordDownload(String format) {
        Counter.builder(DOWNLOAD_COUNTER).tag("format", format).register(meterRegistry).increment();
    }

    public void recordFailure(String reportType) {
        Counter.builder(FAILURE_COUNTER).tag("reportType", reportType).register(meterRegistry).increment();
    }
}
