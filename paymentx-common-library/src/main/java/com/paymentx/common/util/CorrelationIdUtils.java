package com.paymentx.common.util;

/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * CorrelationIdUtils is a class in the common module of PaymentX. It lives in package com.paymentx.common.util and participates in common's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through common's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * CorrelationIdUtils PaymentX ke common module ka ek class hai. Ye com.paymentx.common.util package me hai aur common ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise common ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public final class CorrelationIdUtils {
    private CorrelationIdUtils() {}

    public static String generate() {
        return UUIDUtils.generate();
    }

    public static String resolve(String incoming) {
        return (incoming == null || incoming.isBlank()) ? generate() : incoming;
    }
}
