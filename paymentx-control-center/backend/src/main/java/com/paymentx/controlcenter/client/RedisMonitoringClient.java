package com.paymentx.controlcenter.client;

import com.paymentx.controlcenter.dto.redis.RedisHealthStatus;
import com.paymentx.controlcenter.dto.redis.RedisKeyCategoryCount;
import com.paymentx.controlcenter.dto.redis.RedisKeySample;
import com.paymentx.controlcenter.dto.redis.RedisKeyspaceStats;
import com.paymentx.controlcenter.dto.redis.RedisMemoryInfo;
import com.paymentx.controlcenter.exception.ControlCenterException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/**
 * ENGLISH: The real, read-only Redis monitoring client - reuses Spring
 * Boot's own auto-configured RedisConnectionFactory (Lettuce), the
 * same real connection settings (control-center.redis and
 * spring.data.redis in application.yml) every request in this module
 * would otherwise share, and
 * never issues a write command. What it does: PING for real reachability
 * + latency, parses Redis's own real INFO output for
 * version/role/uptime/clients/memory/hit-miss counters, real DBSIZE
 * for total key count, and a bounded SCAN (never KEYS, which blocks
 * the whole server on a large keyspace) grouped by each key's real
 * two-segment prefix to report population by real category - without
 * ever reading or returning a key's value. Why it exists: this is the
 * actual Phase 2 Redis requirement - "no dumping all keys, no
 * sensitive values exposed."
 *
 * HINGLISH: Real, read-only Redis monitoring client - Spring Boot ke
 * apne auto-configured RedisConnectionFactory (Lettuce) ko reuse karta
 * hai, wahi real connection settings (application.yml ke
 * control-center.redis aur spring.data.redis) jo is module ka har
 * request warna share karta, aur kabhi koi write command issue nahi
 * karta. Ye kya karti hai: real
 * reachability + latency ke liye PING, Redis ke apne real INFO output
 * ko version/role/uptime/clients/memory/hit-miss counters ke liye
 * parse karta hai, total key count ke liye real DBSIZE, aur ek bounded
 * SCAN (KEYS kabhi nahi, jo ek bade keyspace par pura server block kar
 * deta hai) har key ke real two-segment prefix se group kiya hua taaki
 * real category ke hisaab se population report ho - kisi key ki value
 * kabhi padhe ya return kiye bina. Ye dashboard me kyu hai: yehi
 * actual Phase 2 Redis requirement hai - "no dumping all keys, no
 * sensitive values exposed."
 */
@Component
@Slf4j
public class RedisMonitoringClient {

    private static final int SCAN_BATCH_SIZE = 500;
    private static final int SCAN_MAX_KEYS = 5000;

    private final RedisConnectionFactory connectionFactory;

    public RedisMonitoringClient(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    public RedisHealthStatus health() {
        long start = System.currentTimeMillis();
        try (RedisConnection connection = connectionFactory.getConnection()) {
            String pong = connection.ping();
            long elapsed = System.currentTimeMillis() - start;
            if (!"PONG".equalsIgnoreCase(pong)) {
                return new RedisHealthStatus(false, "Unexpected PING reply: " + pong, elapsed, null, null, null, null);
            }
            Properties server = connection.info("server");
            Properties clients = connection.info("clients");
            return new RedisHealthStatus(
                    true, null, elapsed,
                    prop(server, "redis_version"),
                    prop(server, "role"),
                    longProp(server, "uptime_in_seconds"),
                    longProp(clients, "connected_clients"));
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.warn("Redis health check failed reason={}", e.getMessage());
            return new RedisHealthStatus(false, e.getMessage(), elapsed, null, null, null, null);
        }
    }

    public RedisMemoryInfo memoryInfo() {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            Properties memory = connection.info("memory");
            return new RedisMemoryInfo(
                    longProp(memory, "used_memory") != null ? longProp(memory, "used_memory") : 0L,
                    prop(memory, "used_memory_human"),
                    longProp(memory, "maxmemory") != null ? longProp(memory, "maxmemory") : 0L,
                    doubleProp(memory, "mem_fragmentation_ratio") != null ? doubleProp(memory, "mem_fragmentation_ratio") : 0.0);
        } catch (Exception e) {
            throw unreachable(e);
        }
    }

    public RedisKeyspaceStats keyspaceStats() {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            long totalKeys = connection.serverCommands().dbSize() != null ? connection.serverCommands().dbSize() : 0L;
            Properties stats = connection.info("stats");
            Long hits = longProp(stats, "keyspace_hits");
            Long misses = longProp(stats, "keyspace_misses");
            Double hitRate = null;
            if (hits != null && misses != null && (hits + misses) > 0) {
                hitRate = (hits * 100.0) / (hits + misses);
            }

            Map<String, Long> categoryCounts = new LinkedHashMap<>();
            int scanned = 0;
            boolean truncated = false;
            try (var cursor = connection.keyCommands().scan(ScanOptions.scanOptions().count(SCAN_BATCH_SIZE).build())) {
                while (cursor.hasNext()) {
                    if (scanned >= SCAN_MAX_KEYS) {
                        truncated = true;
                        break;
                    }
                    String key = new String(cursor.next());
                    categoryCounts.merge(prefixOf(key), 1L, Long::sum);
                    scanned++;
                }
            }

            List<RedisKeyCategoryCount> categories = categoryCounts.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .map(e -> new RedisKeyCategoryCount(e.getKey(), e.getValue()))
                    .toList();

            return new RedisKeyspaceStats(totalKeys, categories, truncated,
                    hits != null ? hits : 0L, misses != null ? misses : 0L, hitRate);
        } catch (Exception e) {
            throw unreachable(e);
        }
    }

    private static final int SAMPLE_MAX_KEYS = 20;
    private static final int SAMPLE_PER_CATEGORY = 3;

    /**
     * Real, bounded sample of key names (never values) with their real
     * TTL, capped at SAMPLE_PER_CATEGORY per real key-prefix category
     * and SAMPLE_MAX_KEYS overall (Phase 4 "Keys where safe" + "TTL
     * where relevant"). Uses a bounded SCAN, never KEYS.
     */
    public List<RedisKeySample> sampleKeys() {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            List<RedisKeySample> samples = new ArrayList<>();
            Map<String, Integer> perCategoryCount = new TreeMap<>();
            try (var cursor = connection.keyCommands().scan(ScanOptions.scanOptions().count(SCAN_BATCH_SIZE).build())) {
                while (cursor.hasNext() && samples.size() < SAMPLE_MAX_KEYS) {
                    byte[] rawKey = cursor.next();
                    String key = new String(rawKey);
                    String category = prefixOf(key);
                    int countSoFar = perCategoryCount.getOrDefault(category, 0);
                    if (countSoFar >= SAMPLE_PER_CATEGORY) continue;

                    Long ttl = connection.keyCommands().ttl(rawKey);
                    samples.add(new RedisKeySample(key, (ttl == null || ttl < 0) ? null : ttl));
                    perCategoryCount.put(category, countSoFar + 1);
                }
            }
            return samples;
        } catch (Exception e) {
            throw unreachable(e);
        }
    }

    /** Turns a real, uncaught connection failure into the same clear "REDIS_UNREACHABLE" signal health() already reports, instead of a generic 500. */
    private ControlCenterException unreachable(Exception e) {
        return new ControlCenterException("REDIS_UNREACHABLE", "Could not reach Redis: " + e.getMessage(), e);
    }

    private String prefixOf(String key) {
        String[] parts = key.split(":", 3);
        if (parts.length >= 2) {
            return parts[0] + ":" + parts[1];
        }
        return parts[0];
    }

    private String prop(Properties properties, String key) {
        return properties != null ? properties.getProperty(key) : null;
    }

    private Long longProp(Properties properties, String key) {
        String value = prop(properties, key);
        try {
            return value != null ? Long.parseLong(value.trim()) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double doubleProp(Properties properties, String key) {
        String value = prop(properties, key);
        try {
            return value != null ? Double.parseDouble(value.trim()) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
