package com.paymentx.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = {"com.paymentx.notification", "com.paymentx.common"})
@ConfigurationPropertiesScan
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * NotificationServiceApplication is a configuration class in the notification module of PaymentX. It lives in package com.paymentx.notification and participates in notification's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through notification's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * NotificationServiceApplication PaymentX ke notification module ka ek configuration class hai. Ye com.paymentx.notification package me hai aur notification ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise notification ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class NotificationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
