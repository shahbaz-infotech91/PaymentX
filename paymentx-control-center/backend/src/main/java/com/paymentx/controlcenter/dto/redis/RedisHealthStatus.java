package com.paymentx.controlcenter.dto.redis;

/**
 * ENGLISH: The real outcome of a live Redis PING plus real facts read
 * from the Redis INFO command - never a fabricated "UP" when the
 * connection actually failed.
 *
 * HINGLISH: Ek live Redis PING ka real outcome plus Redis INFO command
 * se padhe gaye real facts - jab connection actually fail ho jaye toh
 * kabhi fabricated "UP" nahi.
 */
public record RedisHealthStatus(
        boolean reachable,
        String errorMessage,
        long pingLatencyMillis,
        String redisVersion,
        String role,
        Long uptimeSeconds,
        Long connectedClients
) {
}
