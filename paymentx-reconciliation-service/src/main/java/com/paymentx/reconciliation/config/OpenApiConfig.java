package com.paymentx.reconciliation.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * OpenApiConfig is a configuration class in the reconciliation module of PaymentX. It lives in package com.paymentx.reconciliation.config and participates in reconciliation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through reconciliation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * OpenApiConfig PaymentX ke reconciliation module ka ek configuration class hai. Ye com.paymentx.reconciliation.config package me hai aur reconciliation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise reconciliation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class OpenApiConfig {

    @Bean
    public OpenAPI paymentXOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("PaymentX Reconciliation Service")
                        .description("Settlement reconciliation - matches internal transactions against external settlement records")
                        .version("v1"));
    }
}
