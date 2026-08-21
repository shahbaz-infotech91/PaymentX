package com.paymentx.gateway.cache;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Gateway-local participant status cache - NOT a shared-library concern
 * (Rule 3: no business domain objects in paymentx-common-library).
 * "ACTIVE"/"SUSPENDED" here is a plain cached string, not the
 * ParticipantStatus concept Validation Service owns; this exists purely
 * so the gateway can reject a suspended participant's traffic before it
 * ever reaches a backend service, without importing any business type.
 */
@Service
@RequiredArgsConstructor
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * ParticipantCacheService is a service in the gateway module of PaymentX. It lives in package com.paymentx.gateway.cache and participates in gateway's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through gateway's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * ParticipantCacheService PaymentX ke gateway module ka ek service hai. Ye com.paymentx.gateway.cache package me hai aur gateway ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise gateway ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class ParticipantCacheService {

    private static final String KEY_PREFIX = "gateway:participant:";

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    public Mono<Boolean> isActive(String participantId) {
        if (participantId == null || participantId.isBlank()) {
            return Mono.just(false);
        }
        return redisTemplate.opsForValue().get(KEY_PREFIX + participantId)
                .map("ACTIVE"::equalsIgnoreCase)
                .defaultIfEmpty(true); // cache-miss fail-open: gateway is not the
                // system of record for participant status (Validation Service is) -
                // failing closed here would take down all traffic on a cache miss
                // for a concern that downstream Validation Service already enforces
                // authoritatively as a second, real check.
    }
}
