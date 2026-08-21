package com.paymentx.mcp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * English:
 * Matches LLM/Embedding Service's "no admin/mutating endpoint at all"
 * SecurityConfig shape (not Vector/Prompt Service's
 * HeaderRoleAuthenticationFilter + @PreAuthorize shape) - this service's
 * only plain @RestController endpoint (controller/McpToolCatalogController,
 * GET /api/v1/mcp/tools) is a read-only, non-sensitive tool-descriptor
 * listing with nothing to authorize. The REAL authorization boundary
 * this whole module exists for - Step 12's "every tool invocation must
 * pass authorization... AI is NOT trusted" - is enforced per MCP tool
 * call, one layer below Spring Security/Spring MVC entirely, by
 * security/ToolAuthorizationService reading roles/participantId out of
 * the McpTransportContext that config/McpServerConfig's contextExtractor
 * populated directly from the raw HttpServletRequest (X-Roles/
 * X-Participant-Id, the same already-trusted headers
 * HeaderRoleAuthenticationFilter reads elsewhere in this platform - see
 * that config class's javadoc for exactly why a raw header read, not
 * Spring Security's SecurityContextHolder, is the reliable mechanism
 * here). Trust boundary otherwise unchanged: JWT verification happens at
 * API Gateway; this service is not internet-facing.
 * Why it exists: every PaymentX service needs a SecurityConfig; this one
 * documents precisely where real tool-call authorization actually lives
 * so a reader does not go looking for a missing @PreAuthorize here.
 * How it communicates with other components: registered filter chain
 * every request to this service passes through, including the manually-
 * registered MCP servlet (Spring Boot's security filter chain runs
 * ahead of every registered servlet, not just @RestController-handled
 * ones).
 *
 * Hinglish:
 * LLM/Embedding Service ke "koi admin/mutating endpoint hai hi nahi"
 * SecurityConfig shape se match karta hai (Vector/Prompt Service ke
 * HeaderRoleAuthenticationFilter + @PreAuthorize shape se nahi) - is
 * service ka ek hi plain @RestController endpoint
 * (controller/McpToolCatalogController, GET /api/v1/mcp/tools) ek read-
 * only, non-sensitive tool-descriptor listing hai jisme authorize karne
 * layak kuch nahi hai. Is poore module ka REAL authorization boundary -
 * Step 12 ka "har tool invocation ko authorization pass karna hoga...
 * AI TRUSTED NAHI hai" - har MCP tool call par enforce hota hai, Spring
 * Security/Spring MVC se ek layer neeche, security/
 * ToolAuthorizationService dwara jo roles/participantId ko us
 * McpTransportContext se padhta hai jise config/McpServerConfig ka
 * contextExtractor seedhe raw HttpServletRequest se populate karta hai
 * (X-Roles/X-Participant-Id, wahi already-trusted headers jo
 * HeaderRoleAuthenticationFilter is platform me kahin aur padhta hai -
 * us config class ka javadoc dekho ki yahan ek raw header read, Spring
 * Security ka SecurityContextHolder nahi, reliable mechanism kyu hai).
 * Trust boundary waise unchanged hai: JWT verification API Gateway par
 * hoti hai; ye service internet-facing nahi hai.
 * Ye kyu hai: har PaymentX service ko ek SecurityConfig chahiye; ye ek
 * precisely document karti hai ki real tool-call authorization actually
 * kahan rehta hai taaki ek reader yahan ek missing @PreAuthorize dhoondhne
 * na jaaye.
 * Dusre components se kaise communicate karta hai: is service ki har
 * request jis registered filter chain se guzarti hai, including manually-
 * registered MCP servlet (Spring Boot ka security filter chain har
 * registered servlet se pehle chalta hai, sirf @RestController-handled
 * wale se nahi).
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
