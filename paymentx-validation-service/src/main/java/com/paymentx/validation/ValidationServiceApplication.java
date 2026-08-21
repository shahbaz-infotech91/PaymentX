package com.paymentx.validation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.paymentx.validation", "com.paymentx.common"})
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * ValidationServiceApplication is a class in the validation module of PaymentX. It lives in package com.paymentx.validation and participates in validation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through validation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * ValidationServiceApplication PaymentX ke validation module ka ek class hai. Ye com.paymentx.validation package me hai aur validation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise validation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class ValidationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ValidationServiceApplication.class, args);
    }
}
