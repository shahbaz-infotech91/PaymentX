package com.paymentx.common.security;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * JwtClaims is a class in the common module of PaymentX. It lives in package com.paymentx.common.security and participates in common's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through common's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * JwtClaims PaymentX ke common module ka ek class hai. Ye com.paymentx.common.security package me hai aur common ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise common ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class JwtClaims {
    private String subject;
    private String issuer;
    private String participantId;
    private List<String> roles;
    private String tokenType;
    private Instant issuedAt;
    private Instant expiresAt;

    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }

    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(Instant.now());
    }
}
