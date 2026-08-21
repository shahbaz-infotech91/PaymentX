package com.paymentx.audit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = {"com.paymentx.audit", "com.paymentx.common"})
@ConfigurationPropertiesScan
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * AuditServiceApplication is a configuration class in the audit module of PaymentX. It lives in package com.paymentx.audit and participates in audit's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through audit's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * AuditServiceApplication PaymentX ke audit module ka ek configuration class hai. Ye com.paymentx.audit package me hai aur audit ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise audit ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class AuditServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuditServiceApplication.class, args);
    }
}
