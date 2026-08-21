package com.paymentx.controlcenter.dto.redis;

/**
 * ENGLISH: A real count of keys sharing one real two-segment prefix
 * (e.g. "reconciliation:dedup", "gateway:idempotency") - the prefix
 * itself is discovered live from real scanned key names, never a
 * hardcoded guess at what categories "should" exist, so it stays
 * accurate as real PaymentX services' key-naming evolves.
 *
 * HINGLISH: Ek real two-segment prefix (jaise "reconciliation:dedup",
 * "gateway:idempotency") share karne wali keys ka ek real count -
 * prefix khud real scanned key names se live discover hota hai, kabhi
 * ek hardcoded guess nahi ki kaunse categories "hone chahiye", isliye
 * real PaymentX services ki key-naming evolve hone par bhi accurate
 * rehta hai.
 */
public record RedisKeyCategoryCount(
        String prefix,
        long count
) {
}
