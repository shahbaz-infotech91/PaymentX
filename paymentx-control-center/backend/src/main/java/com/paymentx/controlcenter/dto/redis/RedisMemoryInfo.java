package com.paymentx.controlcenter.dto.redis;

/**
 * ENGLISH: Real memory figures read from Redis's own INFO memory
 * section (used_memory, used_memory_human, maxmemory,
 * mem_fragmentation_ratio) - not derived or estimated by this backend.
 *
 * HINGLISH: Redis ke apne INFO memory section se padhe gaye real
 * memory figures (used_memory, used_memory_human, maxmemory,
 * mem_fragmentation_ratio) - is backend dwara derive ya estimate nahi
 * kiye gaye.
 */
public record RedisMemoryInfo(
        long usedMemoryBytes,
        String usedMemoryHuman,
        long maxMemoryBytes,
        double memoryFragmentationRatio
) {
}
