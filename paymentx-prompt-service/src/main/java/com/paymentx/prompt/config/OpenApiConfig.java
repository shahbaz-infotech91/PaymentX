package com.paymentx.prompt.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * English:
 * Matches Routing Service's OpenApiConfig exactly - registers this
 * service's OpenAPI/Swagger UI metadata (already-present dependency,
 * springdoc-openapi-starter-webmvc-ui, no new one added).
 * Why it exists: consistent with every other PaymentX service's
 * self-documenting REST API convention.
 * How it communicates with other components: consumed by springdoc's
 * auto-configuration to serve /api-docs and /swagger-ui.html (see
 * application.yml's springdoc.* paths).
 *
 * Hinglish:
 * Routing Service ke OpenApiConfig se exactly match karta hai - is
 * service ka OpenAPI/Swagger UI metadata register karta hai
 * (already-present dependency, springdoc-openapi-starter-webmvc-ui,
 * koi naya add nahi kiya gaya).
 * Ye kyu hai: har doosri PaymentX service ke self-documenting REST API
 * convention se consistent.
 * Dusre components se kaise communicate karta hai: /api-docs aur
 * /swagger-ui.html serve karne ke liye springdoc ke auto-configuration
 * dwara consume hota hai (application.yml ke springdoc.* paths dekho).
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI paymentXOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("PaymentX Prompt Service")
                        .description("Prompt template management, versioning, and safe rendering for the PaymentX AI Platform")
                        .version("v1"));
    }
}
