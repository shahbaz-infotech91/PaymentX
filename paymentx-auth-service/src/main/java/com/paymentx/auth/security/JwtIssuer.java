package com.paymentx.auth.security;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.paymentx.auth.config.AuthJwtProperties;
import com.paymentx.common.constant.SecurityConstants;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * The one class in this module that produces a real JWT. Same Nimbus API
 * shape API Gateway's own TestJwtUtil already uses to hand-craft tokens
 * for its test suite (com.nimbusds.jwt.SignedJWT + JWSHeader(HS256) +
 * MACSigner) - not a new pattern, the exact one this codebase already
 * proves is compatible with Gateway's real NimbusReactiveJwtDecoder.
 *
 * Issues every claim the existing Common Library contract defines
 * (SecurityConstants) even though Gateway today only functionally reads
 * "roles" and "participantId" - see
 * PAYMENTX_AUTH_IMPLEMENTATION_DESIGN.md §3/§8 for the exact, verified
 * distinction between "Gateway enforces this" and "contract defines this,
 * for completeness."
 */
@Component
public class JwtIssuer {

    private final AuthJwtProperties properties;

    public JwtIssuer(AuthJwtProperties properties) {
        this.properties = properties;
    }

    public IssuedToken issue(String subject, String participantId, List<String> roles) {
        try {
            Instant now = Instant.now();
            Instant expiry = now.plusSeconds(properties.getAccessTokenTtlSeconds());

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(subject)
                    .issuer(properties.getIssuer())
                    .claim(SecurityConstants.CLAIM_ROLES, roles)
                    .claim(SecurityConstants.CLAIM_PARTICIPANT_ID, participantId)
                    .claim(SecurityConstants.CLAIM_TOKEN_TYPE, SecurityConstants.TOKEN_TYPE_ACCESS)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(expiry))
                    .build();

            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build(),
                    claims);

            signedJWT.sign(new MACSigner(properties.getSecret().getBytes(StandardCharsets.UTF_8)));

            return new IssuedToken(signedJWT.serialize(), properties.getAccessTokenTtlSeconds());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to issue JWT", e);
        }
    }

    public record IssuedToken(String token, long expiresInSeconds) {
    }
}
