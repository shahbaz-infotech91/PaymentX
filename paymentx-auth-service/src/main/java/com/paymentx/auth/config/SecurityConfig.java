package com.paymentx.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * WHY this class MUST exist the moment spring-boot-starter-security is on
 * the classpath: identical rationale to Validation Service's SecurityConfig
 * (see its javadoc). Without an explicit SecurityFilterChain bean, Spring
 * Security auto-configures a DEFAULT chain that locks every endpoint -
 * including /actuator/prometheus - behind HTTP Basic auth with an
 * auto-generated password.
 *
 * UPDATED (Auth implementation): this module now has exactly one real
 * endpoint, POST /api/v1/auth/login - and it MUST remain public by
 * definition (a client cannot present a JWT to obtain one). There is
 * still nothing else in this module to protect: login is the only
 * business endpoint, and it is intentionally, permanently public.
 * anyRequest().permitAll() therefore remains correct, not a stale
 * placeholder - it was already the right answer for "no endpoints yet,"
 * and it is still the right answer for "one endpoint, which must be
 * public." Every OTHER PaymentX service's downstream authorization
 * (@PreAuthorize + HeaderRoleAuthenticationFilter reading Gateway-
 * attached X-Roles/X-Participant-Id) is unchanged by this module gaining
 * a real login endpoint - this service issues the credential those
 * services already know how to trust; it does not enforce authorization
 * itself, same trust-boundary decision as every other internal,
 * not-internet-facing service in this platform.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // stateless service-to-service API, no browser session/cookies involved
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/api/v1/auth/login").permitAll() // must be public - it is how a client obtains a token
                        .anyRequest().permitAll() // no other endpoint exists in this module
                );
        return http.build();
    }

    /** Spring Security's own standard, audited BCrypt implementation -
     *  no custom cryptography. Already transitively available via
     *  spring-boot-starter-security (spring-security-crypto); no new
     *  dependency was needed for this bean. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
