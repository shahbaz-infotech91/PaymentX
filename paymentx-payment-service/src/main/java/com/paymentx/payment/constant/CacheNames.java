package com.paymentx.payment.constant;

/**
 * Named Redis cache regions, referenced by @Cacheable(cacheNames = ...) in
 * service.impl (Part 4). Centralized so a cache name typo is a compile
 * error (constant reference) rather than a silent cache-miss discovered
 * in production.
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * CacheNames is a class in the payment module of PaymentX. It lives in package com.paymentx.payment.constant and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * CacheNames PaymentX ke payment module ka ek class hai. Ye com.paymentx.payment.constant package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public final class CacheNames {
    private CacheNames() {}

    public static final String PAYMENT_BY_REFERENCE = "paymentByReference";
}
