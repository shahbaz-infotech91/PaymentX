package com.paymentx.reporting.config;

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
 * ====================================================================
 * ENGLISH: Reads the Gateway-propagated, trusted X-Roles header and
 * populates Spring Security's Authentication - matches every other
 * service's identical filter, needed for @PreAuthorize("hasRole(...)")
 * to have something to check against.
 *
 * HINGLISH: Gateway-propagated, trusted X-Roles header padhta hai aur
 * Spring Security ka Authentication populate karta hai - baaki har
 * service ke identical filter jaisa, @PreAuthorize("hasRole(...)") ko
 * check karne ke liye kuch chahiye hota hai, wahi ye deta hai.
 * ====================================================================
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
