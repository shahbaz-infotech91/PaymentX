package com.paymentx.routing.config;

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
 * WHY this reads a trusted header rather than validating the JWT itself:
 * API Gateway already validates the JWT and extracts its roles claim
 * (see JwtParticipantPropagationGlobalFilter's X-Participant-Id
 * propagation for the identical pattern) - re-validating the JWT here
 * would duplicate that verification logic in every downstream service,
 * exactly what SecurityConfig's javadoc says this architecture
 * deliberately avoids. This filter's ONLY job is turning the
 * already-trusted X-Roles header into a Spring Security Authentication
 * so @PreAuthorize("hasRole(...)") on admin endpoints has something to
 * check against.
 *
 * This is safe specifically because Routing Service is NOT
 * internet-facing (see SecurityConfig javadoc) - a caller cannot reach
 * this filter without first passing through Gateway, which is the only
 * component that sets this header from a validated JWT.
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * HeaderRoleAuthenticationFilter is a class in the routing module of PaymentX. It lives in package com.paymentx.routing.config and participates in routing's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through routing's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * HeaderRoleAuthenticationFilter PaymentX ke routing module ka ek class hai. Ye com.paymentx.routing.config package me hai aur routing ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise routing ke REST API ya Kafka events ke through indirectly use karti hain).
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
