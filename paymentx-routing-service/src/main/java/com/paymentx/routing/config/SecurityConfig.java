package com.paymentx.routing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Matches Validation Service's SecurityConfig's trust-boundary decision
 * (JWT verification happens at API Gateway, this service is not
 * internet-facing) with one addition: @EnableMethodSecurity +
 * HeaderRoleAuthenticationFilter, needed because RoutingController's
 * admin endpoints (create/update/delete) use @PreAuthorize("hasRole(...)")
 * - see HeaderRoleAuthenticationFilter's javadoc for why a trusted header
 * is sufficient here rather than re-validating the JWT.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * SecurityConfig is a configuration class in the routing module of PaymentX. It lives in package com.paymentx.routing.config and participates in routing's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through routing's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * SecurityConfig PaymentX ke routing module ka ek configuration class hai. Ye com.paymentx.routing.config package me hai aur routing ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise routing ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
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
                )
                .addFilterBefore(new HeaderRoleAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
