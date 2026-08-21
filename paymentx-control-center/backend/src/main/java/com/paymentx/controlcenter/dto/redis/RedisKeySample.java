package com.paymentx.controlcenter.dto.redis;

/**
 * ENGLISH: One real Redis key's name and real remaining TTL - never
 * its value. ttlSeconds is null when the key has no expiry set (Redis
 * TTL command returns -1), matching real key semantics rather than
 * showing a fabricated "0" or "expired".
 *
 * HINGLISH: Ek real Redis key ka naam aur real remaining TTL - kabhi
 * uski value nahi. ttlSeconds null hota hai jab key par koi expiry set
 * na ho (Redis TTL command -1 return karta hai), real key semantics ke
 * match karte hue, ek fabricated "0" ya "expired" dikhane ke bajaye.
 */
public record RedisKeySample(
        String key,
        Long ttlSeconds
) {
}
