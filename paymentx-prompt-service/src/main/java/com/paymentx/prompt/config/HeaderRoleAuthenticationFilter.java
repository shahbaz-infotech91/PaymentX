package com.paymentx.prompt.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * English:
 * Matches Routing Service's HeaderRoleAuthenticationFilter exactly -
 * see that class's javadoc for the full rationale (API Gateway already
 * validates the JWT and propagates its roles as X-Roles; re-validating
 * here would duplicate that logic; this is safe specifically because
 * Prompt Service is not internet-facing, only reachable by first
 * passing through Gateway). Turns the already-trusted X-Roles header
 * into a Spring Security Authentication so
 * @PreAuthorize("hasRole('PROMPT_ADMIN')") on mutating endpoints has
 * something to check.
 * Why it exists: Step 15 of the Phase 3.2 brief - "use existing
 * authentication/authorization mechanisms... do NOT invent a
 * completely separate authentication system."
 * How it communicates with other components: registered in
 * SecurityConfig ahead of UsernamePasswordAuthenticationFilter;
 * PromptController's @PreAuthorize annotations read the Authentication
 * this filter sets.
 *
 * Hinglish:
 * Routing Service ke HeaderRoleAuthenticationFilter se exactly match
 * karta hai - poora rationale ke liye us class ka javadoc dekho (API
 * Gateway already JWT validate karta hai aur uski roles X-Roles ke
 * roop me propagate karta hai; yahan dobara validate karna us logic ko
 * duplicate karta; ye specifically isliye safe hai kyunki Prompt
 * Service internet-facing nahi hai, sirf pehle Gateway se guzar kar hi
 * reachable hai). Already-trusted X-Roles header ko ek Spring Security
 * Authentication me badalta hai taaki mutating endpoints par
 * @PreAuthorize("hasRole('PROMPT_ADMIN')") ke paas check karne layak
 * kuch ho.
 * Ye kyu hai: Phase 3.2 brief ka Step 15 - "existing authentication/
 * authorization mechanisms use karo... ek bilkul alag authentication
 * system invent mat karo."
 * Dusre components se kaise communicate karta hai:
 * UsernamePasswordAuthenticationFilter se pehle SecurityConfig me
 * register hota hai; PromptController ke @PreAuthorize annotations wahi
 * Authentication padhte hain jo ye filter set karta hai.
 */
public class HeaderRoleAuthenticationFilter extends OncePerRequestFilter {

    private static final String ROLES_HEADER = "X-Roles";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String rolesHeader = request.getHeader(ROLES_HEADER);
        if (rolesHeader != null && !rolesHeader.isBlank()) {
            List<GrantedAuthority> authorities = Arrays.stream(rolesHeader.split(","))
                    .map(String::trim)
                    .filter(role -> !role.isEmpty())
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .map(GrantedAuthority.class::cast)
                    .toList();

            var authentication = new UsernamePasswordAuthenticationToken(
                    request.getHeader("X-Participant-Id"), null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }
}
