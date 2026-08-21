package com.paymentx.gateway.config;

import com.paymentx.common.constant.SecurityConstants;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtReactiveAuthenticationManager;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * Hardening pass fixes applied here (verify-and-complete, not a redesign):
 *  1. jwtClockSkewSeconds was previously an unused config property - now
 *     wired into a JwtTimestampValidator via DelegatingOAuth2TokenValidator,
 *     so exp/nbf checks actually tolerate clock drift between services
 *     instead of silently ignoring the configured value.
 *  2. Default Spring Security JWT authority mapping reads the "scope"/"scp"
 *     claim - PaymentX tokens carry a "roles" claim instead
 *     (SecurityConstants.CLAIM_ROLES). Without a custom
 *     JwtGrantedAuthoritiesConverter, every authenticated JWT would map to
 *     zero authorities regardless of its actual roles - now fixed with an
 *     explicit converter reading CLAIM_ROLES and prefixing with ROLE_PREFIX.
 */
@Configuration
@EnableWebFluxSecurity
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * SecurityConfig is a configuration class in the gateway module of PaymentX. It lives in package com.paymentx.gateway.config and participates in gateway's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through gateway's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * SecurityConfig PaymentX ke gateway module ka ek configuration class hai. Ye com.paymentx.gateway.config package me hai aur gateway ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise gateway ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class SecurityConfig {

    private final GatewaySecurityProperties gatewaySecurityProperties;

    public SecurityConfig(GatewaySecurityProperties gatewaySecurityProperties) {
        this.gatewaySecurityProperties = gatewaySecurityProperties;
    }

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder() {
        byte[] keyBytes = gatewaySecurityProperties.getJwtSecret().getBytes(StandardCharsets.UTF_8);
        SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "HmacSHA256");
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withSecretKey(secretKey).build();

        OAuth2TokenValidator<Jwt> withClockSkew = new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ofSeconds(gatewaySecurityProperties.getJwtClockSkewSeconds())));
        decoder.setJwtValidator(withClockSkew);

        return decoder;
    }

    private JwtGrantedAuthoritiesConverter rolesClaimAuthoritiesConverter() {
        JwtGrantedAuthoritiesConverter converter = new JwtGrantedAuthoritiesConverter();
        converter.setAuthorityPrefix(SecurityConstants.ROLE_PREFIX);
        converter.setAuthoritiesClaimName(SecurityConstants.CLAIM_ROLES);
        return converter;
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        var jwtAuthenticationConverter = new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(rolesClaimAuthoritiesConverter());

        JwtReactiveAuthenticationManager authManager = new JwtReactiveAuthenticationManager(reactiveJwtDecoder());
        authManager.setJwtAuthenticationConverter(new ReactiveJwtAuthenticationConverterAdapter(jwtAuthenticationConverter));

        AuthenticationWebFilter jwtAuthenticationWebFilter = new AuthenticationWebFilter(authManager);
        jwtAuthenticationWebFilter.setServerAuthenticationConverter(new ServerBearerTokenAuthenticationConverter());

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeExchange(exchange -> exchange.anyExchange().permitAll())
                .addFilterAt(jwtAuthenticationWebFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
