package com.paymentx.notification.cache;

import com.paymentx.notification.config.NotificationProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * WHY this exists ALONGSIDE NotificationRepository.existsBySourceEventId
 * (a DB-level idempotency check), not instead of it: the DB check is the
 * correctness guarantee (a unique constraint backs it - see Liquibase
 * changeset), but it requires a query round-trip. This Redis SETNX check
 * runs FIRST, before touching the database at all, so the overwhelming
 * majority of duplicate Kafka redeliveries (which happen far more often
 * than genuine failures needing the DB fallback) are rejected in a
 * single fast in-memory operation. Both layers exist because Redis is a
 * cache (can be evicted/restarted, must never be the ONLY source of
 * truth for idempotency) while the DB constraint is the actual guarantee.
 */
@Service
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * NotificationDedupService is a service in the notification module of PaymentX. It lives in package com.paymentx.notification.cache and participates in notification's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through notification's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * NotificationDedupService PaymentX ke notification module ka ek service hai. Ye com.paymentx.notification.cache package me hai aur notification ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise notification ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class NotificationDedupService {

    private static final String KEY_PREFIX = "notification:dedup:";

    private final StringRedisTemplate redisTemplate;
    private final NotificationProperties notificationProperties;

    public NotificationDedupService(StringRedisTemplate redisTemplate, NotificationProperties notificationProperties) {
        this.redisTemplate = redisTemplate;
        this.notificationProperties = notificationProperties;
    }

    /** Returns true if this sourceEventId has NOT been seen before (the
     *  caller should proceed to process it and fall through to the DB
     *  check) - false if this exact key was already marked (skip
     *  immediately without a DB round-trip). */
    public boolean markIfNew(String sourceEventId) {
        Boolean wasNew = redisTemplate.opsForValue().setIfAbsent(
                KEY_PREFIX + sourceEventId, "1", notificationProperties.getCache().getDedupTtl());
        return Boolean.TRUE.equals(wasNew);
    }
}
