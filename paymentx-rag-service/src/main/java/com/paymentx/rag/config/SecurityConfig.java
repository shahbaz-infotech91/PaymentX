package com.paymentx.rag.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * English:
 * Matches LLM Service's/Embedding Service's SecurityConfig exactly
 * (see LlmController's javadoc for the full rationale): JWT
 * verification happens at API Gateway; this service is not
 * internet-facing, so this trust boundary is safe (Step 24 - "Do not
 * expose RAG APIs publicly without authentication" is satisfied by that
 * boundary itself: only traffic that already passed API Gateway's JWT
 * check reaches this service at all). No @EnableMethodSecurity/
 * HeaderRoleAuthenticationFilter/@PreAuthorize anywhere in this module
 * - RagController has no admin/mutating endpoint.
 * Why it exists: Step 24 of the Phase 3.6 brief - "use existing
 * PaymentX authentication... do not expose RAG APIs publicly."
 * How it communicates with other components: the filter chain every
 * request to this service passes through.
 *
 * Hinglish:
 * LLM Service/Embedding Service ke SecurityConfig se exactly match
 * karta hai (poore rationale ke liye LlmController ka javadoc dekho):
 * JWT verification API Gateway par hoti hai; ye service internet-facing
 * nahi hai, isliye ye trust boundary safe hai (Step 24 - "RAG APIs ko
 * bina authentication publicly expose mat karo" khud us boundary se
 * satisfy hota hai: sirf wahi traffic jo already API Gateway ka JWT
 * check pass kar chuka hai is service tak pahunchta hai). Is module me
 * kahin bhi @EnableMethodSecurity/HeaderRoleAuthenticationFilter/
 * @PreAuthorize nahi hai - RagController ke paas koi admin/mutating
 * endpoint nahi hai.
 * Ye kyu hai: Phase 3.6 brief ka Step 24 - "existing PaymentX
 * authentication use karo... RAG APIs publicly expose mat karo."
 * Dusre components se kaise communicate karta hai: is service ki har
 * request jis filter chain se guzarti hai.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/api-docs/**").permitAll()
                        .anyRequest().permitAll() // TODO(Auth Service): replace with internal-service-token validation
                );
        return http.build();
    }
}
