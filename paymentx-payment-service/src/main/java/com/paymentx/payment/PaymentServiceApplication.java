package com.paymentx.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * WHY each @Enable annotation is here, specifically (not added by default -
 * every one of these is a deliberate choice matching a required feature):
 *
 * @EnableCaching     - activates Spring's cache abstraction so @Cacheable
 *                       on service methods actually routes through Redis
 *                       (spring-boot-starter-data-redis alone does NOT
 *                       enable annotation-driven caching by itself).
 *
 * @EnableScheduling   - activates @Scheduled methods in the `scheduler`
 *                       package (outbox-draining poller, retry-scan job).
 *                       Without this, @Scheduled annotations are silently
 *                       ignored - no error, the job just never runs. This
 *                       exact silent-no-op is a common "why isn't my
 *                       scheduled job firing" production debugging session.
 *
 * @EnableTransactionManagement - technically auto-enabled by Spring Boot's
 *                       JPA auto-configuration already, but declared here
 *                       EXPLICITLY as a deliberate signal: this service's
 *                       core correctness (state transitions + outbox writes
 *                       happening atomically) depends entirely on
 *                       @Transactional being wired correctly. Making an
 *                       implicit behavior explicit, for a concern this
 *                       central to the service, is a defensible style
 *                       choice - it means a future reader searching for
 *                       "where is transaction management configured" finds
 *                       an answer immediately instead of having to know
 *                       Spring Boot's auto-configuration internals.
 *
 * NOTE: Resilience4j's @CircuitBreaker/@Retry annotations do NOT need an
 * @Enable* annotation - resilience4j-spring-boot3's auto-configuration
 * wires the AOP aspects automatically once the dependency is on the
 * classpath (see pom.xml).
 */
@SpringBootApplication(scanBasePackages = {"com.paymentx.payment", "com.paymentx.common"})
@EnableCaching
@EnableScheduling
@EnableTransactionManagement
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * PaymentServiceApplication is a class in the payment module of PaymentX. It lives in package com.paymentx.payment and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * PaymentServiceApplication PaymentX ke payment module ka ek class hai. Ye com.paymentx.payment package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class PaymentServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
