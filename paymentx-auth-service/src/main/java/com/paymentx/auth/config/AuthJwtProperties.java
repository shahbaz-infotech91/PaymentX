package com.paymentx.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * secret MUST resolve to the exact same runtime value as API Gateway's
 * own gateway.security.jwt-secret (GatewaySecurityProperties.jwtSecret) -
 * both are bound from the identical GATEWAY_JWT_SECRET environment
 * variable, with the identical self-labeled local-dev-only fallback
 * literal Gateway's own application.yml already uses, so both services
 * agree on the same value with zero duplication risk in any environment
 * where the env var IS set, and identical local-dev behavior where it
 * is not. See PAYMENTX_AUTH_IMPLEMENTATION_DESIGN.md §13 - this is not a
 * new secret-management mechanism, it is the exact existing one, reused.
 */
@Component
@ConfigurationProperties(prefix = "auth.jwt")
public class AuthJwtProperties {

    private String secret = "local-dev-only-secret-change-me-32chars";

    private String issuer = "paymentx-auth-service";

    private long accessTokenTtlSeconds = 1800;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtlSeconds;
    }

    public void setAccessTokenTtlSeconds(long accessTokenTtlSeconds) {
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
    }
}
