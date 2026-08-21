package com.paymentx.reporting.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * ====================================================================
 * ENGLISH: Dedicated thread pool for report generation - matches
 * Reconciliation Service's small-dedicated-pool rationale (report
 * generation is a handful of concurrent long-running jobs, not many
 * lightweight sends).
 *
 * HINGLISH: Report generation ke liye dedicated thread pool -
 * Reconciliation Service ke small-dedicated-pool reasoning jaisa
 * (report generation ek handful concurrent long-running jobs hain,
 * bahut saare lightweight sends nahi).
 * ====================================================================
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    @Bean(name = "reportGenerationExecutor")
    public ThreadPoolTaskExecutor reportGenerationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("report-gen-");
        executor.initialize();
        return executor;
    }
}
