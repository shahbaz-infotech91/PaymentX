package com.paymentx.gateway.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * OpenApiConfig is a configuration class in the gateway module of PaymentX. It lives in package com.paymentx.gateway.config and participates in gateway's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through gateway's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * OpenApiConfig PaymentX ke gateway module ka ek configuration class hai. Ye com.paymentx.gateway.config package me hai aur gateway ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise gateway ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class OpenApiConfig {

    @Bean
    public OpenAPI paymentXOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("PaymentX API Gateway")
                        .description("Single entry point for all PaymentX service APIs")
                        .version("v1"))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
