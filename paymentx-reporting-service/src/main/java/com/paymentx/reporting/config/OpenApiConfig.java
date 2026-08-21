package com.paymentx.reporting.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ====================================================================
 * ENGLISH: Swagger/OpenAPI documentation bean - powers the
 * /swagger-ui.html page every other service also exposes.
 *
 * HINGLISH: Swagger/OpenAPI documentation bean - /swagger-ui.html page
 * power karta hai jo baaki har service bhi expose karti hai.
 * ====================================================================
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI paymentXOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("PaymentX Reporting Service")
                        .description("Operational, business, and compliance reporting across the PaymentX platform")
                        .version("v1"));
    }
}
