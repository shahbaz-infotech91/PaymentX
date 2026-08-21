package com.paymentx.agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * English:
 * Matches LLM/Embedding/RAG Service's "no admin/mutating endpoint at
 * all" SecurityConfig shape - this service's two endpoints
 * (POST /api/v1/agent/execute, GET /api/v1/agent/health) are both
 * routine, non-admin operations Control Center's AiChatService needs to
 * call for every agent-backed chat request; there is nothing here that
 * should ever require a special role. Trust boundary unchanged: JWT
 * verification happens at API Gateway; this service is not internet-
 * facing. The real security enforcement this whole module exists for -
 * Step 27's "least privilege... do not treat LLM output as trusted" -
 * happens one layer below Spring Security entirely, in
 * policy/AgentToolPolicy (which tool names may ever be called) and MCP
 * Gateway's own real authorization (which this agent's tool calls still
 * pass through for real, per-call - see client/McpToolClient's
 * javadoc), not via a @PreAuthorize annotation on this service's own
 * two endpoints.
 * Why it exists: every PaymentX service needs a SecurityConfig; this one
 * documents precisely where the real security boundary actually lives.
 * How it communicates with other components: registered filter chain
 * every request to this service passes through.
 *
 * Hinglish:
 * LLM/Embedding/RAG Service ke "koi admin/mutating endpoint hai hi
 * nahi" SecurityConfig shape se match karta hai - is service ke do
 * endpoints (POST /api/v1/agent/execute, GET /api/v1/agent/health) dono
 * routine, non-admin operations hain jo Control Center ke AiChatService
 * ko har agent-backed chat request ke liye call karne hote hain; yahan
 * kuch bhi aisa nahi hai jise kabhi ek special role chahiye ho. Trust
 * boundary unchanged hai: JWT verification API Gateway par hoti hai; ye
 * service internet-facing nahi hai. Is poore module ka real security
 * enforcement - Step 27 ka "least privilege... LLM output ko trusted
 * mat maano" - Spring Security se ek layer neeche hota hai, policy/
 * AgentToolPolicy me (kaun se tool names kabhi call ho sakte hain) aur
 * MCP Gateway ki apni real authorization me (jisse is agent ki tool
 * calls har call ke liye really guzarti hain - client/McpToolClient ka
 * javadoc dekho), is service ke apne do endpoints par ek @PreAuthorize
 * annotation ke through nahi.
 * Ye kyu hai: har PaymentX service ko ek SecurityConfig chahiye; ye ek
 * precisely document karti hai ki real security boundary actually kahan
 * rehta hai.
 * Dusre components se kaise communicate karta hai: is service ki har
 * request jis registered filter chain se guzarti hai.
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
