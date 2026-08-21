package com.paymentx.reconciliation.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * WHY a small, dedicated pool (not a notification-dispatch-style large
 * pool): reconciliation batches are long-running, CPU/IO-heavy single
 * jobs (one file, potentially millions of rows) rather than many small
 * concurrent sends - a handful of concurrent batch slots is the correct
 * shape.
 */
@Configuration
@EnableAsync
@EnableScheduling
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * AsyncConfig is a configuration class in the reconciliation module of PaymentX. It lives in package com.paymentx.reconciliation.config and participates in reconciliation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through reconciliation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * AsyncConfig PaymentX ke reconciliation module ka ek configuration class hai. Ye com.paymentx.reconciliation.config package me hai aur reconciliation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise reconciliation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class AsyncConfig {

    @Bean(name = "reconciliationBatchExecutor")
    public ThreadPoolTaskExecutor reconciliationBatchExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("recon-batch-");
        executor.initialize();
        return executor;
    }
}
