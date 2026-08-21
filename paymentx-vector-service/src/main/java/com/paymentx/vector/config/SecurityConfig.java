package com.paymentx.vector.config;

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
 * Matches Prompt Service's SecurityConfig exactly (see that class's
 * javadoc for the full "open reads, admin-gated writes" trust-boundary
 * rationale, which applies identically here): JWT verification happens
 * at API Gateway; this service is not internet-facing. Unlike LLM/
 * Embedding Service (which have no admin/mutating endpoint at all),
 * Vector Service's storeDocument/deleteDocument ARE real mutating,
 * admin-adjacent operations (Step 29) - @EnableMethodSecurity +
 * HeaderRoleAuthenticationFilter are wired here specifically so
 * @PreAuthorize("hasRole('VECTOR_ADMIN')") on those two endpoints has
 * something to enforce. search()/health() stay open to any caller that
 * reached this service at all - a future RAG Service needs to call
 * search as a routine, non-admin, service-to-service operation, the
 * same "render is open, create/activate are admin" split Prompt
 * Service's PromptController documents.
 * Why it exists: Step 29 of the Phase 3.5 brief.
 * How it communicates with other components: the filter chain every
 * request to this service passes through.
 *
 * Hinglish:
 * Prompt Service ke SecurityConfig se exactly match karta hai (poore
 * "open reads, admin-gated writes" trust-boundary rationale ke liye us
 * class ka javadoc dekho, jo yahan bhi identically apply hota hai): JWT
 * verification API Gateway par hoti hai; ye service internet-facing
 * nahi hai. LLM/Embedding Service (jinke paas koi admin/mutating
 * endpoint hai hi nahi) ke ulat, Vector Service ke
 * storeDocument/deleteDocument REAL mutating, admin-adjacent operations
 * HAIN (Step 29) - @EnableMethodSecurity + HeaderRoleAuthenticationFilter
 * yahan specifically isliye wire kiye gaye hain taaki un do endpoints
 * par @PreAuthorize("hasRole('VECTOR_ADMIN')") ke paas enforce karne
 * layak kuch ho. search()/health() kisi bhi caller ke liye open rehte
 * hain jo is service tak pahuncha - ek future RAG Service ko search ko
 * ek routine, non-admin, service-to-service operation ke roop me call
 * karna hota hai, wahi "render open hai, create/activate admin hain"
 * split jo Prompt Service ka PromptController document karta hai.
 * Ye kyu hai: Phase 3.5 brief ka Step 29.
 * Dusre components se kaise communicate karta hai: is service ki har
 * request jis filter chain se guzarti hai.
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
