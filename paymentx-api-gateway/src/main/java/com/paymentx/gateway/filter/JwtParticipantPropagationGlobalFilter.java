package com.paymentx.gateway.filter;

import com.paymentx.common.constant.SecurityConstants;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * HARDENING FIX: previously, a JWT-authenticated request never had its
 * participantId claim propagated anywhere - ParticipantValidationGlobalFilter
 * only ever saw a value on the API-key path (set explicitly by
 * ApiKeyAuthenticationGlobalFilter). This meant participant-status
 * checking silently never ran for JWT-authenticated traffic at all.
 *
 * Reads the authenticated JwtAuthenticationToken from Spring Security's
 * reactive context (populated by SecurityConfig's AuthenticationWebFilter,
 * which - per Spring WebFlux's filter ordering - has already run by the
 * time any GlobalFilter executes) and sets X-Participant-Id from the
 * "participantId" claim. Safe to always overwrite here: incoming
 * client-supplied values were already stripped by
 * SecurityHeaderSanitizationGlobalFilter (runs first).
 *
 * BUG FIX (double filter-chain execution on every JWT-authenticated
 * request): the previous implementation called chain.filter(...) - which
 * returns Mono<Void> - inside .flatMap(), then used .switchIfEmpty(...)
 * to handle the "no security context" case. Mono<Void> can only ever
 * complete via onComplete with zero onNext signals (Void has no
 * instances), so from switchIfEmpty's perspective the flatMap'd sequence
 * ALWAYS looked empty even after chain.filter(...) had already run to
 * completion and fully written the response. That made switchIfEmpty's
 * fallback (chain.filter(exchange) with the ORIGINAL, unmutated request)
 * fire a SECOND time on every JWT request, re-running the entire
 * downstream chain - including AuthenticationEnforcementGlobalFilter,
 * which then saw no X-Participant-Id (since this second pass used the
 * original exchange) and tried to write a 401 onto the already-committed
 * response, throwing UnsupportedOperationException and forcibly closing
 * the connection mid-stream (corrupting the chunked-encoding response the
 * client had already started receiving). Fixed by resolving the target
 * exchange (mutated or original) as a plain value BEFORE ever calling
 * chain.filter(), using Mono<ServerWebExchange> - which, unlike
 * Mono<Void>, genuinely emits a value - and .defaultIfEmpty() so
 * chain.filter() is invoked EXACTLY ONCE regardless of auth path.
 */
@Component
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * JwtParticipantPropagationGlobalFilter is a servlet filter in the gateway module of PaymentX. It lives in package com.paymentx.gateway.filter and participates in gateway's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through gateway's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * JwtParticipantPropagationGlobalFilter PaymentX ke gateway module ka ek servlet filter hai. Ye com.paymentx.gateway.filter package me hai aur gateway ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise gateway ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class JwtParticipantPropagationGlobalFilter implements GlobalFilter, Ordered {

    private static final String PARTICIPANT_ID_HEADER = "X-Participant-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> securityContext.getAuthentication())
                .map(authentication -> resolveExchange(exchange, authentication))
                // No SecurityContext at all (public path, or API-key path
                // where Spring Security never authenticated anything) -
                // proceed unchanged; ApiKeyAuthenticationGlobalFilter or
                // "no auth required" public-path handling owns that case.
                .defaultIfEmpty(exchange)
                .flatMap(chain::filter);
    }

    private ServerWebExchange resolveExchange(ServerWebExchange exchange, org.springframework.security.core.Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            String participantId = jwt.getClaimAsString(SecurityConstants.CLAIM_PARTICIPANT_ID);
            if (participantId != null && !participantId.isBlank()) {
                ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                        .headers(httpHeaders -> httpHeaders.set(PARTICIPANT_ID_HEADER, participantId))
                        .build();
                return exchange.mutate().request(mutatedRequest).build();
            }
        }
        return exchange;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 105;
    }
}
