package com.paymentx.prompt.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * English:
 * Matches Routing Service's/Validation Service's trust-boundary
 * decision exactly (see those classes' javadoc): JWT verification
 * happens at API Gateway, this service is not internet-facing, so
 * every request is permitted through Spring Security's own filter
 * chain - real authorization for mutating endpoints
 * (create/createVersion/activate/deactivate, all @PreAuthorize
 * ("hasRole('PROMPT_ADMIN')") on PromptController) is enforced from the
 * X-Roles header HeaderRoleAuthenticationFilter turns into an
 * Authentication, not from anything in this filter chain itself. Read
 * endpoints (get/list/render) stay open to any caller that reached this
 * service at all - the same "open reads, admin-gated writes" split
 * Routing Service's RoutingController already documents, and the
 * correct one for render specifically: AI Chat Service and the future
 * LLM Service/Agent Orchestrator need to call render as an ordinary
 * service-to-service operation, not as an admin action.
 * Why it exists: Step 15 of the Phase 3.2 brief.
 * How it communicates with other components: HeaderRoleAuthenticationFilter
 * is wired in ahead of UsernamePasswordAuthenticationFilter here;
 * PromptController's method-level @PreAuthorize relies on
 * @EnableMethodSecurity below.
 *
 * Hinglish:
 * Routing Service/Validation Service ke trust-boundary decision se
 * exactly match karta hai (un classes ka javadoc dekho): JWT
 * verification API Gateway par hoti hai, ye service internet-facing
 * nahi hai, isliye har request Spring Security ki apni filter chain se
 * guzarne di jaati hai - mutating endpoints ke liye real authorization
 * (create/createVersion/activate/deactivate, PromptController par sab
 * @PreAuthorize("hasRole('PROMPT_ADMIN')")) X-Roles header se enforce
 * hoti hai jise HeaderRoleAuthenticationFilter ek Authentication me
 * badalta hai, is filter chain me kisi cheez se nahi. Read endpoints
 * (get/list/render) kisi bhi caller ke liye open rehte hain jo is
 * service tak pahuncha - wahi "open reads, admin-gated writes" split
 * jo Routing Service ka RoutingController already document karta hai,
 * aur render ke liye specifically sahi hai: AI Chat Service aur future
 * LLM Service/Agent Orchestrator ko render ek ordinary service-to-
 * service operation ke roop me call karna hota hai, ek admin action ke
 * roop me nahi.
 * Ye kyu hai: Phase 3.2 brief ka Step 15.
 * Dusre components se kaise communicate karta hai:
 * HeaderRoleAuthenticationFilter yahan UsernamePasswordAuthenticationFilter
 * se pehle wire hota hai; PromptController ka method-level @PreAuthorize
 * neeche ke @EnableMethodSecurity par rely karta hai.
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
