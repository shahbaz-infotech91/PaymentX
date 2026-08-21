package com.paymentx.vector.config;

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
 * Matches Prompt Service's/Routing Service's HeaderRoleAuthenticationFilter
 * exactly - see that class's javadoc for the full rationale (API
 * Gateway already validates the JWT and propagates its roles as
 * X-Roles; re-validating here would duplicate that logic; safe because
 * this service is not internet-facing). Turns the already-trusted
 * X-Roles header into a Spring Security Authentication so
 * @PreAuthorize("hasRole('VECTOR_ADMIN')") on
 * VectorController.storeDocument/deleteDocument has something to check
 * (Step 29 - "Protect vector APIs using existing PaymentX
 * authentication").
 * Why it exists: Step 29 - reuse existing authentication/authorization,
 * do not invent a new system.
 * How it communicates with other components: registered in
 * SecurityConfig ahead of UsernamePasswordAuthenticationFilter;
 * VectorController's @PreAuthorize annotations read the Authentication
 * this filter sets.
 *
 * Hinglish:
 * Prompt Service/Routing Service ke HeaderRoleAuthenticationFilter se
 * exactly match karta hai - poora rationale ke liye us class ka javadoc
 * dekho (API Gateway already JWT validate karta hai aur uski roles
 * X-Roles ke roop me propagate karta hai; yahan dobara validate karna
 * us logic ko duplicate karta; safe hai kyunki ye service internet-
 * facing nahi hai). Already-trusted X-Roles header ko ek Spring
 * Security Authentication me badalta hai taaki
 * VectorController.storeDocument/deleteDocument par
 * @PreAuthorize("hasRole('VECTOR_ADMIN')") ke paas check karne layak
 * kuch ho (Step 29 - "existing PaymentX authentication use karke vector
 * APIs protect karo").
 * Ye kyu hai: Step 29 - existing authentication/authorization reuse
 * karo, ek nayi system invent mat karo.
 * Dusre components se kaise communicate karta hai:
 * UsernamePasswordAuthenticationFilter se pehle SecurityConfig me
 * register hota hai; VectorController ke @PreAuthorize annotations wahi
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
