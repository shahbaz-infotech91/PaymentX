package com.paymentx.reporting.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * ====================================================================
 * ENGLISH: All configurable, non-hardcoded values for Reporting Service
 * - cache TTLs, consumer retry backoff, and the local disk directory
 * exported report files are streamed to (mirroring Reconciliation
 * Service's disk-based SettlementFile storage rationale exactly).
 *
 * HINGLISH: Reporting Service ke sab configurable, non-hardcoded
 * values - cache TTLs, consumer retry backoff, aur local disk
 * directory jahan exported report files stream hoti hain
 * (Reconciliation Service ke disk-based storage reasoning jaisa hi).
 * ====================================================================
 */
@Component
@ConfigurationProperties(prefix = "reporting")
@Getter
@Setter
public class ReportingProperties {

    private Cache cache = new Cache();
    private Consumer consumer = new Consumer();
    private Storage storage = new Storage();
    private Retry retry = new Retry();

    @Getter
    @Setter
    public static class Cache {
        private Duration reportResultTtl = Duration.ofHours(1);
        private Duration dedupTtl = Duration.ofHours(24);
    }

    @Getter
    @Setter
    public static class Consumer {
        private long retryInitialIntervalMillis = 1000L;
        private double retryMultiplier = 2.0;
        private long retryMaxElapsedMillis = 8000L;
    }

    @Getter
    @Setter
    public static class Storage {
        private String reportExportDirectory = "/tmp/paymentx-reporting/exports";
    }

    @Getter
    @Setter
    public static class Retry {
        private int maxAttempts = 3;
    }
}
