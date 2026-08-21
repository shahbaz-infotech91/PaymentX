package com.paymentx.reporting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * ====================================================================
 * ENGLISH: The Spring Boot entry point - starts Reporting Service,
 * scanning both this service's own packages and paymentx.common (the
 * shared library's exception/DTO/event classes every controller and
 * consumer here depends on).
 *
 * HINGLISH: Spring Boot entry point - Reporting Service ko start karta
 * hai, is service ke apne packages aur paymentx.common (shared library
 * jiske exception/DTO/event classes pe yahan ka har controller/consumer
 * depend karta hai) dono ko scan karta hai.
 * ====================================================================
 */
@SpringBootApplication(scanBasePackages = {"com.paymentx.reporting", "com.paymentx.common"})
@ConfigurationPropertiesScan
public class ReportingServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReportingServiceApplication.class, args);
    }
}
