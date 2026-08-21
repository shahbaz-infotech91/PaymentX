package com.paymentx.reporting.cache;

import com.paymentx.reporting.config.ReportingProperties;
import com.paymentx.reporting.dto.ReportResultResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * ====================================================================
 * ENGLISH: Caches generated report results in Redis - the explicit
 * "Cache generated reports, TTL, Eviction, Refresh" requirement.
 * WHY caching by executionId (not by report-criteria like
 * AuditSearchCacheService does): a report's result is immutable once
 * generated (see ReportResult entity's javadoc) - unlike a search
 * query that can return different rows over time, "give me execution
 * X's result" always returns the exact same data, making a simple TTL
 * cache both correct and sufficient (no invalidation-on-write needed,
 * since nothing ever writes to an existing ReportResult).
 *
 * HINGLISH: Generated report results ko Redis me cache karta hai -
 * explicit "Cache generated reports, TTL, Eviction, Refresh"
 * requirement. WHY executionId se cache karte hain (report-criteria se
 * nahi, jaisa AuditSearchCacheService karta hai): ek report ka result
 * generate hone ke baad immutable hota hai - ek search query se alag
 * jo time ke saath alag rows return kar sakti hai, "execution X ka
 * result do" hamesha exact same data return karta hai, isliye ek simple
 * TTL cache hi correct aur sufficient hai.
 * ====================================================================
 */
@Service
@Slf4j
public class ReportResultCacheService {

    private static final String KEY_PREFIX = "reporting:result:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ReportingProperties reportingProperties;

    public ReportResultCacheService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper, ReportingProperties reportingProperties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.reportingProperties = reportingProperties;
    }

    public void put(UUID executionId, ReportResultResponse response) {
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + executionId,
                    objectMapper.writeValueAsString(response), reportingProperties.getCache().getReportResultTtl());
        } catch (Exception e) {
            log.warn("Failed to cache report result executionId={}", executionId, e);
        }
    }

    public Optional<ReportResultResponse> get(UUID executionId) {
        try {
            String value = redisTemplate.opsForValue().get(KEY_PREFIX + executionId);
            if (value == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(value, ReportResultResponse.class));
        } catch (Exception e) {
            log.warn("Failed to read cached report result executionId={}", executionId, e);
            return Optional.empty();
        }
    }

    /** Explicit eviction - called when a report is regenerated via
     *  reprocess, so a stale cached result is never served after a
     *  fresh generation. */
    public void evict(UUID executionId) {
        redisTemplate.delete(KEY_PREFIX + executionId);
    }
}
