package com.paymentx.common.constant;

/**
 * Cross-cutting security constants - claim names, role prefixes, token
 * type strings. NOT actual secrets/keys (those belong in AWS Secrets
 * Manager / environment configuration per service, never hardcoded in
 * a shared JAR that every service ships).
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * SecurityConstants is a class in the common module of PaymentX. It lives in package com.paymentx.common.constant and participates in common's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through common's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * SecurityConstants PaymentX ke common module ka ek class hai. Ye com.paymentx.common.constant package me hai aur common ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise common ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public final class SecurityConstants {
    private SecurityConstants() {}

    public static final String BEARER_PREFIX = "Bearer ";
    public static final String ROLE_PREFIX = "ROLE_";

    public static final String CLAIM_SUBJECT = "sub";
    public static final String CLAIM_ISSUER = "iss";
    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_PARTICIPANT_ID = "participantId";
    public static final String CLAIM_TOKEN_TYPE = "tokenType";

    public static final String TOKEN_TYPE_ACCESS = "ACCESS";
    public static final String TOKEN_TYPE_REFRESH = "REFRESH";
    public static final String TOKEN_TYPE_SERVICE = "SERVICE";
}
