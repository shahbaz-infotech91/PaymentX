package com.paymentx.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = {"com.paymentx.gateway", "com.paymentx.common"})
@ConfigurationPropertiesScan
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * ApiGatewayApplication is a configuration class in the gateway module of PaymentX. It lives in package com.paymentx.gateway and participates in gateway's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through gateway's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * ApiGatewayApplication PaymentX ke gateway module ka ek configuration class hai. Ye com.paymentx.gateway package me hai aur gateway ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise gateway ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class ApiGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
