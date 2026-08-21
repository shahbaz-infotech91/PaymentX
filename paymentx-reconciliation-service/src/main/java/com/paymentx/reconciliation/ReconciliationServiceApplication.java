package com.paymentx.reconciliation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = {"com.paymentx.reconciliation", "com.paymentx.common"})
@ConfigurationPropertiesScan
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * ReconciliationServiceApplication is a configuration class in the reconciliation module of PaymentX. It lives in package com.paymentx.reconciliation and participates in reconciliation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through reconciliation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * ReconciliationServiceApplication PaymentX ke reconciliation module ka ek configuration class hai. Ye com.paymentx.reconciliation package me hai aur reconciliation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise reconciliation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class ReconciliationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReconciliationServiceApplication.class, args);
    }
}
