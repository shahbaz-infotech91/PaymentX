package com.paymentx.routing.cache;

import com.paymentx.routing.config.RoutingProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis SETNX-based idempotency check for Kafka consumers - simpler than
 * a DB idempotency table (which Payment Service uses for its payment
 * processing path) and appropriate here: this consumer's side effect
 * (deactivating routes) is itself idempotent in EFFECT (deactivating an
 * already-inactive route is a no-op), so this check exists purely to
 * avoid redundant processing/logging/cache-eviction on redelivery, not
 * to prevent a correctness bug from double-processing.
 */
@Service
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * ConsumerIdempotencyService is a service in the routing module of PaymentX. It lives in package com.paymentx.routing.cache and participates in routing's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through routing's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * ConsumerIdempotencyService PaymentX ke routing module ka ek service hai. Ye com.paymentx.routing.cache package me hai aur routing ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise routing ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class ConsumerIdempotencyService {

    private static final String KEY_PREFIX = "routing:processed-event:";

    private final StringRedisTemplate redisTemplate;
    private final RoutingProperties routingProperties;

    public ConsumerIdempotencyService(StringRedisTemplate redisTemplate, RoutingProperties routingProperties) {
        this.redisTemplate = redisTemplate;
        this.routingProperties = routingProperties;
    }

    /** Returns true if this eventId has NOT been seen before (i.e. the
     *  caller should process it) - false if it's a duplicate/redelivery. */
    public boolean markProcessedIfNew(String eventId) {
        Boolean wasNew = redisTemplate.opsForValue().setIfAbsent(
                KEY_PREFIX + eventId, "1", routingProperties.getCache().getIdempotencyTtl());
        return Boolean.TRUE.equals(wasNew);
    }
}
