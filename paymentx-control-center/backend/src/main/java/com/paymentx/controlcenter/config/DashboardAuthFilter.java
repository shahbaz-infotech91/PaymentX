package com.paymentx.controlcenter.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentx.controlcenter.dto.ApiResponse;
import com.paymentx.controlcenter.dto.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

/**
 * ENGLISH: The Phase 6 production-hardening authentication gate for
 * this backend's own API - see ControlCenterProperties.Security's
 * javadoc for the full rationale (single shared dashboard token, not
 * multi-user auth; off by default; fails to start rather than run
 * silently unauthenticated when misconfigured). What it does: when
 * control-center.security.enabled is true, every request under /api/**
 * (except the real health check, which monitoring/liveness probes need
 * to reach without a token) must carry a real
 * "Authorization: Bearer &lt;dashboardToken&gt;" header matching the
 * real, server-configured token - compared in constant time
 * (MessageDigest.isEqual) so response timing cannot leak how much of
 * the token matched. A missing/wrong token gets a real 401 in this
 * module's own real ApiResponse/ErrorResponse shape, not a generic
 * servlet-container error page. When disabled (the default), this
 * filter is a complete no-op - every prior phase's verified local-dev
 * behavior is unchanged.
 *
 * HINGLISH: Is backend ke apne API ke liye Phase 6 production-hardening
 * authentication gate - poora rationale ke liye
 * ControlCenterProperties.Security ka javadoc dekho (single shared
 * dashboard token, multi-user auth nahi; default off; misconfigured
 * hone par silently unauthenticated chalne ke bajaye start hi nahi
 * hota). Ye kya karti hai: jab control-center.security.enabled true ho,
 * /api/** ke neeche har request (sivaay real health check ke, jise
 * monitoring/liveness probes ko bina token ke pahunchna hota hai) ko ek
 * real "Authorization: Bearer &lt;dashboardToken&gt;" header carry
 * karna padta hai jo real, server-configured token se match kare -
 * constant time me compare kiya gaya (MessageDigest.isEqual) taaki
 * response timing ye leak na kare ki token kitna match hua. Ek
 * missing/wrong token ko is module ke apne real ApiResponse/
 * ErrorResponse shape me ek real 401 milta hai, koi generic
 * servlet-container error page nahi. Jab disabled ho (default), ye
 * filter ek complete no-op hai - har pichle phase ka verified local-dev
 * behavior unchanged rehta hai.
 */
@Component
public class DashboardAuthFilter extends OncePerRequestFilter {

    // Phase 3.1: /api/v1/ai/health is exempt for the same reason /api/v1/health is - a health/readiness
    // probe (including the frontend's own AIStatus indicator, checked before a user has necessarily
    // unlocked the dashboard) must be reachable without first clearing this gate.
    private static final Set<String> UNAUTHENTICATED_PATHS = Set.of("/api/v1/health", "/api/v1/ai/health");

    private final ControlCenterProperties.Security security;
    private final ObjectMapper objectMapper;

    public DashboardAuthFilter(ControlCenterProperties properties, ObjectMapper objectMapper) {
        this.security = properties.getSecurity();
        this.objectMapper = objectMapper;
        if (security.isEnabled() && (security.getDashboardToken() == null || security.getDashboardToken().isBlank())) {
            throw new IllegalStateException(
                    "control-center.security.enabled=true but control-center.security.dashboard-token is blank - "
                            + "refusing to start rather than run every endpoint unauthenticated. "
                            + "Set CONTROL_CENTER_SECURITY_DASHBOARD_TOKEN to a real, random token.");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!security.isEnabled() || !path.startsWith("/api/") || UNAUTHENTICATED_PATHS.contains(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String provided = extractBearerToken(request.getHeader(HttpHeaders.AUTHORIZATION));
        if (provided == null || !constantTimeEquals(provided, security.getDashboardToken())) {
            writeUnauthorized(response, path);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return null;
        }
        return authorizationHeader.substring("Bearer ".length()).trim();
    }

    private boolean constantTimeEquals(String provided, String real) {
        return MessageDigest.isEqual(provided.getBytes(StandardCharsets.UTF_8), real.getBytes(StandardCharsets.UTF_8));
    }

    private void writeUnauthorized(HttpServletResponse response, String path) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ApiResponse<Void> body = ApiResponse.error(new ErrorResponse(
                "UNAUTHORIZED", "Missing or invalid dashboard access token.", path));
        objectMapper.writeValue(response.getWriter(), body);
    }
}
