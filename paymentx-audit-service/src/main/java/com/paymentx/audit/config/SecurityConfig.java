package com.paymentx.audit.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Matches Routing Service's SecurityConfig exactly (trust boundary: JWT
 * verification at API Gateway, X-Roles header trusted for
 * @PreAuthorize). AuditController's record() endpoint requires
 * AUDIT_WRITER role; search/getById stay open (read-only audit APIs per
 * the explicit requirement).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * SecurityConfig is a configuration class in the audit module of PaymentX. It lives in package com.paymentx.audit.config and participates in audit's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through audit's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * SecurityConfig PaymentX ke audit module ka ek configuration class hai. Ye com.paymentx.audit.config package me hai aur audit ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise audit ke REST API ya Kafka events ke through indirectly use karti hain).
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
