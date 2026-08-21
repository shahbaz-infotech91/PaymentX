package com.paymentx.audit.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentx.audit.config.AuditProperties;
import com.paymentx.audit.dto.AuditEventResponse;
import com.paymentx.audit.dto.AuditSearchCriteria;
import com.paymentx.common.dto.PageResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * WHY only search RESULTS are cached, not individual audit records by
 * id: a direct getById() lookup is already a single indexed-PK query -
 * caching it would add cache-consistency overhead for a query Postgres
 * already answers in constant time. A multi-filter SEARCH with
 * pagination is the actually expensive, repeatable query (e.g. an
 * operator dashboard re-running "all events for correlationId X" or "all
 * failures in the last hour" repeatedly) - that's the real cache
 * candidate.
 *
 * WHY a short TTL (see AuditProperties) rather than write-through
 * invalidation like Routing Service's RouteCacheService: audit events
 * are append-only (see AuditEvent's javadoc) - a cached search result
 * only goes stale by MISSING newly-arrived events, never by containing
 * incorrect data about an existing one. A short TTL bounds that
 * staleness window without needing to track and invalidate every
 * possible search-criteria cache key on every single insert (which,
 * given search has 8 independent filter dimensions, would be
 * impractical to enumerate).
 */
@Service
@Slf4j
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * AuditSearchCacheService is a service in the audit module of PaymentX. It lives in package com.paymentx.audit.cache and participates in audit's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through audit's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * AuditSearchCacheService PaymentX ke audit module ka ek service hai. Ye com.paymentx.audit.cache package me hai aur audit ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise audit ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class AuditSearchCacheService {

    private static final String KEY_PREFIX = "audit:search:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AuditProperties auditProperties;

    public AuditSearchCacheService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper, AuditProperties auditProperties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.auditProperties = auditProperties;
    }

    public Optional<PageResponse<AuditEventResponse>> get(AuditSearchCriteria criteria, int page, int size) {
        try {
            String value = redisTemplate.opsForValue().get(key(criteria, page, size));
            if (value == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(value, new TypeReference<PageResponse<AuditEventResponse>>() {}));
        } catch (Exception e) {
            log.warn("Audit search cache read failed, falling back to database", e);
            return Optional.empty();
        }
    }

    public void put(AuditSearchCriteria criteria, int page, int size, PageResponse<AuditEventResponse> result) {
        try {
            redisTemplate.opsForValue().set(key(criteria, page, size),
                    objectMapper.writeValueAsString(result), auditProperties.getCache().getSearchTtl());
        } catch (Exception e) {
            log.warn("Audit search cache write failed", e);
        }
    }

    private String key(AuditSearchCriteria criteria, int page, int size) {
        return KEY_PREFIX + criteria.hashCode() + ":" + page + ":" + size;
    }
}
