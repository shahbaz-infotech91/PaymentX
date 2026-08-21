package com.paymentx.reporting.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * ====================================================================
 * ENGLISH: JWT/RBAC security config - matches every other service's
 * established trust-boundary pattern exactly (JWT verified at API
 * Gateway, X-Roles header trusted here for @PreAuthorize). Read-only
 * report endpoints stay open; generate/schedule/cancel/delete require
 * REPORTING_ADMIN.
 *
 * HINGLISH: JWT/RBAC security config - baaki har service ke established
 * trust-boundary pattern jaisa (JWT API Gateway pe verify hota hai,
 * X-Roles header yahan @PreAuthorize ke liye trust hota hai). Read-only
 * report endpoints open rehte hain; generate/schedule/cancel/delete ko
 * REPORTING_ADMIN chahiye.
 * ====================================================================
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
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
