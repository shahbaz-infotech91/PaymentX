package com.paymentx.controlcenter.dto.redis;

import java.util.List;

/**
 * ENGLISH: Real key-population insight without ever dumping a key's
 * value. What it does: totalKeyCount is a real DBSIZE; categories is a
 * real, bounded SCAN (never KEYS - SCAN is non-blocking and safe on a
 * live broker) grouped by real key-name prefix with a real count per
 * prefix; hits/misses/hitRatePercent come from Redis's own real
 * keyspace_hits/keyspace_misses counters. scanTruncated tells the
 * caller honestly if the bounded scan hit its cap before covering the
 * whole keyspace, so category counts are not silently misread as
 * exhaustive.
 *
 * HINGLISH: Kabhi kisi key ki value dump kiye bina real key-population
 * insight. Ye kya karti hai: totalKeyCount ek real DBSIZE hai;
 * categories ek real, bounded SCAN hai (KEYS kabhi nahi - SCAN
 * non-blocking hai aur ek live broker par safe hai) real key-name
 * prefix se group kiya gaya, har prefix ka real count ke saath;
 * hits/misses/hitRatePercent Redis ke apne real keyspace_hits/
 * keyspace_misses counters se aate hain. scanTruncated caller ko
 * honestly batata hai ki kya bounded scan poore keyspace ko cover
 * karne se pehle apni cap tak pahunch gaya, taaki category counts ko
 * silently exhaustive na maan liya jaye.
 */
public record RedisKeyspaceStats(
        long totalKeyCount,
        List<RedisKeyCategoryCount> categories,
        boolean scanTruncated,
        long keyspaceHits,
        long keyspaceMisses,
        Double hitRatePercent
) {
}
