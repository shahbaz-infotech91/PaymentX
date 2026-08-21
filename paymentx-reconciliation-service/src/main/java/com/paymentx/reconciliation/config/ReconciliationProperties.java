package com.paymentx.reconciliation.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "reconciliation")
@Getter
@Setter
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * ReconciliationProperties is a configuration class in the reconciliation module of PaymentX. It lives in package com.paymentx.reconciliation.config and participates in reconciliation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through reconciliation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * ReconciliationProperties PaymentX ke reconciliation module ka ek configuration class hai. Ye com.paymentx.reconciliation.config package me hai aur reconciliation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise reconciliation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class ReconciliationProperties {

    private Cache cache = new Cache();
    private Consumer consumer = new Consumer();
    private Matching matching = new Matching();
    private Batch batch = new Batch();
    private Storage storage = new Storage();

    @Getter
    @Setter
    public static class Storage {
        private String settlementFileDirectory = "/tmp/paymentx-reconciliation/settlement-files";
    }

    @Getter
    @Setter
    public static class Cache {
        private Duration internalTransactionTtl = Duration.ofDays(7);
        private Duration settlementMetadataTtl = Duration.ofDays(7);
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
    public static class Matching {
        /** Settlement is considered "delayed" (not yet late) if it lands
         *  within this many hours after the internal transaction's own
         *  completion - beyond lateSettlementThresholdHours, it's
         *  classified LATE_SETTLEMENT instead of SETTLEMENT_DELAY. */
        private long settlementDelayThresholdHours = 24L;
        private long lateSettlementThresholdHours = 72L;
        /** Amount comparison tolerance - real-world settlement files
         *  occasionally differ by sub-cent rounding; anything within
         *  this tolerance is still MATCHED, not AMOUNT_MISMATCH. */
        private BigDecimal amountToleranceThreshold = new BigDecimal("0.01");
    }

    @Getter
    @Setter
    public static class Batch {
        private int insertBatchSize = 500;
        private String scheduledCron = "0 0 2 * * *";
    }
}
