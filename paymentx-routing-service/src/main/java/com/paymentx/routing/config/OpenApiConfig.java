package com.paymentx.routing.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * OpenApiConfig is a configuration class in the routing module of PaymentX. It lives in package com.paymentx.routing.config and participates in routing's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through routing's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * OpenApiConfig PaymentX ke routing module ka ek configuration class hai. Ye com.paymentx.routing.config package me hai aur routing ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise routing ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class OpenApiConfig {

    @Bean
    public OpenAPI paymentXOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("PaymentX Routing Service")
                        .description("Routing rule management and route resolution")
                        .version("v1"));
    }
}
