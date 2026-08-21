package com.paymentx.reconciliation.cache;

import com.paymentx.reconciliation.config.ReconciliationProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * WHY one dedup service handles BOTH Kafka event idempotency AND
 * within-file duplicate-row detection: both are the exact same
 * operation (SETNX-based "have I seen this key before") against the
 * exact same Redis instance, just with different key prefixes/inputs -
 * a single, reusable, well-tested primitive is preferable to two
 * near-identical classes.
 */
@Service
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * ReconciliationDedupService is a service in the reconciliation module of PaymentX. It lives in package com.paymentx.reconciliation.cache and participates in reconciliation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through reconciliation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * ReconciliationDedupService PaymentX ke reconciliation module ka ek service hai. Ye com.paymentx.reconciliation.cache package me hai aur reconciliation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise reconciliation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class ReconciliationDedupService {

    private static final String EVENT_KEY_PREFIX = "reconciliation:dedup:event:";
    private static final String RECORD_KEY_PREFIX = "reconciliation:dedup:record:";

    private final StringRedisTemplate redisTemplate;
    private final ReconciliationProperties reconciliationProperties;

    public ReconciliationDedupService(StringRedisTemplate redisTemplate, ReconciliationProperties reconciliationProperties) {
        this.redisTemplate = redisTemplate;
        this.reconciliationProperties = reconciliationProperties;
    }

    public boolean markEventIfNew(String eventId) {
        return markIfNew(EVENT_KEY_PREFIX + eventId);
    }

    /** WHY a separate key prefix from markEventIfNew: a settlement-file
     *  row's "identity" is its paymentId+batchId combination (a business
     *  key), completely unrelated to a Kafka message's eventId. */
    public boolean markRecordIfNew(String paymentId, UUID batchId) {
        return markIfNew(RECORD_KEY_PREFIX + batchId + ":" + paymentId);
    }

    private boolean markIfNew(String key) {
        Boolean wasNew = redisTemplate.opsForValue().setIfAbsent(key, "1", reconciliationProperties.getCache().getDedupTtl());
        return Boolean.TRUE.equals(wasNew);
    }
}
