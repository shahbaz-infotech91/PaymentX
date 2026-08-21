package com.paymentx.common.security;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * SecurityContext is a class in the common module of PaymentX. It lives in package com.paymentx.common.security and participates in common's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through common's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * SecurityContext PaymentX ke common module ka ek class hai. Ye com.paymentx.common.security package me hai aur common ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise common ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class SecurityContext {
    private String principalId;
    private String participantId;
    private String tokenType;
    private JwtClaims claims;

    public static SecurityContext fromClaims(JwtClaims claims) {
        return SecurityContext.builder()
                .principalId(claims.getSubject())
                .participantId(claims.getParticipantId())
                .tokenType(claims.getTokenType())
                .claims(claims)
                .build();
    }
}
