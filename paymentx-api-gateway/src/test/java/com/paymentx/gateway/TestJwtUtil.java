package com.paymentx.gateway;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * TestJwtUtil is a class in the gateway module of PaymentX. It lives in package com.paymentx.gateway and participates in gateway's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through gateway's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * TestJwtUtil PaymentX ke gateway module ka ek class hai. Ye com.paymentx.gateway package me hai aur gateway ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise gateway ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public final class TestJwtUtil {
    private TestJwtUtil() {}

    public static String validToken(String secret, String subject, String participantId, List<String> roles) {
        return signedToken(secret, subject, participantId, roles, Instant.now().plusSeconds(3600));
    }

    public static String expiredToken(String secret, String subject) {
        return signedToken(secret, subject, "participant-1", List.of("USER"), Instant.now().minusSeconds(60));
    }

    private static String signedToken(String secret, String subject, String participantId,
                                       List<String> roles, Instant expiry) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(subject)
                    .issuer("paymentx-test")
                    .claim("participantId", participantId)
                    .claim("roles", roles)
                    .issueTime(Date.from(Instant.now()))
                    .expirationTime(Date.from(expiry))
                    .build();

            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build(),
                    claims);

            signedJWT.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
            return signedJWT.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build test JWT", e);
        }
    }
}
