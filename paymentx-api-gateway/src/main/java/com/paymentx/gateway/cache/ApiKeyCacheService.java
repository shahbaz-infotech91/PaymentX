package com.paymentx.gateway.cache;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * ApiKeyCacheService is a service in the gateway module of PaymentX. It lives in package com.paymentx.gateway.cache and participates in gateway's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through gateway's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * ApiKeyCacheService PaymentX ke gateway module ka ek service hai. Ye com.paymentx.gateway.cache package me hai aur gateway ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise gateway ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class ApiKeyCacheService {

    private static final String KEY_PREFIX = "gateway:apikey:";

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    public Mono<Boolean> isValid(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return Mono.just(false);
        }
        return redisTemplate.hasKey(KEY_PREFIX + apiKey);
    }

    public Mono<String> getParticipantId(String apiKey) {
        return redisTemplate.opsForValue().get(KEY_PREFIX + apiKey);
    }
}
