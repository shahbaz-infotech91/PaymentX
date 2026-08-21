package com.paymentx.llm.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * English:
 * Matches Routing Service's/Prompt Service's trust-boundary decision
 * (JWT verification happens at API Gateway; this service is not
 * internet-facing) with one deliberate simplification: no
 * @EnableMethodSecurity, no HeaderRoleAuthenticationFilter, no
 * @PreAuthorize anywhere in this module. Unlike Prompt Service (which
 * gates create/activate/deactivate behind PROMPT_ADMIN), LlmController
 * has exactly two endpoints and neither is an admin/mutating operation
 * (see LlmController's javadoc) - adding a role-authentication filter
 * that nothing in this module ever checks would be dead code, not
 * defense in depth. If a future phase adds an admin-only endpoint here
 * (e.g. provider selection/config reload), it should copy Prompt
 * Service's HeaderRoleAuthenticationFilter at that point, not before.
 * Why it exists: Step 20-equivalent of this platform's established
 * trust-boundary convention, applied honestly to this service's actual,
 * smaller endpoint surface.
 * How it communicates with other components: the filter chain every
 * request to this service passes through.
 *
 * Hinglish:
 * Routing Service/Prompt Service ke trust-boundary decision se match
 * karta hai (JWT verification API Gateway par hoti hai; ye service
 * internet-facing nahi hai) ek jaan-boojh kar simplification ke saath:
 * is module me kahin bhi @EnableMethodSecurity nahi,
 * HeaderRoleAuthenticationFilter nahi, @PreAuthorize nahi. Prompt
 * Service (jo create/activate/deactivate ko PROMPT_ADMIN ke peeche gate
 * karta hai) ke ulat, LlmController ke paas exactly do endpoints hain
 * aur dono me se koi admin/mutating operation nahi hai (LlmController
 * ka javadoc dekho) - ek role-authentication filter add karna jise is
 * module me kuch bhi kabhi check hi nahi karta, dead code hoga, defense
 * in depth nahi. Agar ek future phase yahan ek admin-only endpoint add
 * kare (jaise provider selection/config reload), use us waqt Prompt
 * Service ka HeaderRoleAuthenticationFilter copy karna chahiye, pehle
 * nahi.
 * Ye kyu hai: is platform ke established trust-boundary convention ka
 * Step 20-equivalent, is service ke actual, chhote endpoint surface par
 * honestly apply kiya gaya.
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
