package com.paymentx.reporting.cache;

import com.paymentx.reporting.config.ReportingProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * ====================================================================
 * ENGLISH: Redis SETNX-based Kafka event idempotency check, matching
 * Reconciliation/Notification Service's identical pattern.
 *
 * HINGLISH: Redis SETNX-based Kafka event idempotency check,
 * Reconciliation/Notification Service ke identical pattern jaisa.
 * ====================================================================
 */
@Service
public class ReportingDedupService {

    private static final String KEY_PREFIX = "reporting:dedup:event:";

    private final StringRedisTemplate redisTemplate;
    private final ReportingProperties reportingProperties;

    public ReportingDedupService(StringRedisTemplate redisTemplate, ReportingProperties reportingProperties) {
        this.redisTemplate = redisTemplate;
        this.reportingProperties = reportingProperties;
    }

    public boolean markIfNew(String eventId) {
        Boolean wasNew = redisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + eventId, "1", reportingProperties.getCache().getDedupTtl());
        return Boolean.TRUE.equals(wasNew);
    }
}
