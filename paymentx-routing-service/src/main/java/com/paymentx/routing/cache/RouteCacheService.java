package com.paymentx.routing.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentx.routing.config.RoutingProperties;
import com.paymentx.routing.dto.RouteRuleResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * WHY cache the resolved ROUTE (scheme+participant -> RouteRuleResponse),
 * not the raw table: route selection is the hot path (every payment
 * routing decision hits this), while writes (creating/updating a rule)
 * are rare, operator-driven events - a classic read-heavy cache
 * candidate. Cache is explicitly invalidated on any write (see
 * RoutingServiceImpl) rather than relying on TTL alone, so a rule change
 * takes effect immediately rather than up to ttlSeconds late.
 */
@Service
@Slf4j
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * RouteCacheService is a service in the routing module of PaymentX. It lives in package com.paymentx.routing.cache and participates in routing's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through routing's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * RouteCacheService PaymentX ke routing module ka ek service hai. Ye com.paymentx.routing.cache package me hai aur routing ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise routing ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class RouteCacheService {

    private static final String KEY_PREFIX = "routing:rule:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RoutingProperties routingProperties;

    public RouteCacheService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper, RoutingProperties routingProperties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.routingProperties = routingProperties;
    }

    public Optional<RouteRuleResponse> get(String scheme, String participantId) {
        try {
            String value = redisTemplate.opsForValue().get(key(scheme, participantId));
            if (value == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(value, RouteRuleResponse.class));
        } catch (Exception e) {
            log.warn("Route cache read failed, falling back to database scheme={} participantId={}", scheme, participantId, e);
            return Optional.empty();
        }
    }

    public void put(String scheme, String participantId, RouteRuleResponse response) {
        try {
            redisTemplate.opsForValue().set(key(scheme, participantId),
                    objectMapper.writeValueAsString(response), routingProperties.getCache().getRouteTtl());
        } catch (JsonProcessingException e) {
            log.warn("Route cache write failed scheme={} participantId={}", scheme, participantId, e);
        }
    }

    /** Called on any rule create/update/delete - a stale cached route
     *  after a rule change could send payments down a decommissioned
     *  path until TTL expiry, which is not acceptable for routing
     *  decisions. */
    public void evictAll() {
        var keys = redisTemplate.keys(KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private String key(String scheme, String participantId) {
        return KEY_PREFIX + scheme + ":" + (participantId == null ? "default" : participantId);
    }
}
