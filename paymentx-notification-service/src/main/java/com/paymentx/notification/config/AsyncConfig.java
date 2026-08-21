package com.paymentx.notification.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * WHY a dedicated executor for notification dispatch, not Spring's
 * common ForkJoinPool default: channel sends (SMTP handshake, an
 * outbound webhook HTTP call) are blocking I/O with unpredictable
 * latency - isolating them in their own bounded pool means a slow/stuck
 * webhook endpoint can never starve unrelated @Async work elsewhere.
 *
 * WHY CallerRunsPolicy for queue-full rejection ("Backpressure" in the
 * explicit requirements): when the bounded queue (queueCapacity) is
 * full, running the task on the CALLING thread (the Kafka consumer
 * thread) rather than throwing RejectedExecutionException naturally
 * slows down consumption - the consumer literally cannot fetch and
 * dispatch the next message until this one's send() call returns. This
 * is deliberate, self-throttling backpressure: it slows Kafka
 * consumption to match actual channel-send throughput instead of
 * unboundedly growing an in-memory queue toward OutOfMemoryError.
 */
@Configuration
@EnableAsync
@RequiredArgsConstructor
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * AsyncConfig is a configuration class in the notification module of PaymentX. It lives in package com.paymentx.notification.config and participates in notification's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through notification's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * AsyncConfig PaymentX ke notification module ka ek configuration class hai. Ye com.paymentx.notification.config package me hai aur notification ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise notification ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class AsyncConfig {

    private final NotificationProperties notificationProperties;

    @Bean(name = "notificationDispatchExecutor")
    public ThreadPoolTaskExecutor notificationDispatchExecutor() {
        var asyncProps = notificationProperties.getAsync();

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(asyncProps.getCorePoolSize());
        executor.setMaxPoolSize(asyncProps.getMaxPoolSize());
        executor.setQueueCapacity(asyncProps.getQueueCapacity());
        executor.setThreadNamePrefix("notif-dispatch-");
        executor.setRejectedExecutionHandler(callerRunsPolicy());
        executor.initialize();
        return executor;
    }

    private RejectedExecutionHandler callerRunsPolicy() {
        return new ThreadPoolExecutor.CallerRunsPolicy();
    }
}
