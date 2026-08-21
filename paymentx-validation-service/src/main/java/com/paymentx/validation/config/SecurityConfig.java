package com.paymentx.validation.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * WHY this class MUST exist the moment spring-boot-starter-security is on
 * the classpath: Spring Security auto-configures a DEFAULT security chain
 * the instant it's on the classpath - it locks every endpoint behind HTTP
 * Basic auth with an auto-generated password printed to the console log on
 * startup. Without an explicit SecurityFilterChain bean like this one,
 * EVERY request (including our own tests, and Payment Service calling us)
 * gets a 401 - this is one of the most common "why is my Spring Boot app
 * suddenly returning 401 on everything" issues after adding the security
 * starter.
 *
 * WHY we permitAll() here rather than requiring auth on this service
 * DIRECTLY: per the PaymentX architecture, JWT verification happens
 * upstream at JWT Verify Service / API Gateway - Validation Service sits
 * BEHIND that boundary and trusts requests that reach it (in a real
 * deployment, this trust boundary is enforced at the network layer -
 * Validation Service is not internet-facing, only reachable from inside
 * the cluster/VPC). This is a deliberate, temporary decision: once Auth
 * Service (next module) exists, we will add a filter here that validates
 * the propagated JWT/service-identity token on every internal call, not
 * re-implement full JWT verification redundantly in every downstream
 * service. Flagging this explicitly so it isn't mistaken for an oversight -
 * shipping this as-is to a public-facing endpoint in real production would
 * be a real vulnerability.
 */
@Configuration
@EnableWebSecurity
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * SecurityConfig is a configuration class in the validation module of PaymentX. It lives in package com.paymentx.validation.config and participates in validation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through validation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * SecurityConfig PaymentX ke validation module ka ek configuration class hai. Ye com.paymentx.validation.config package me hai aur validation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise validation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // stateless service-to-service API, no browser session/cookies involved
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        .anyRequest().permitAll() // TODO(Module 3 - Auth Service): replace with internal-service-token validation
                );
        return http.build();
    }
}
