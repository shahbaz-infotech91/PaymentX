package com.paymentx.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

@Configuration
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * RedisConfig is a configuration class in the gateway module of PaymentX. It lives in package com.paymentx.gateway.config and participates in gateway's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through gateway's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * RedisConfig PaymentX ke gateway module ka ek configuration class hai. Ye com.paymentx.gateway.config package me hai aur gateway ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise gateway ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class RedisConfig {

    @Bean
    public ReactiveStringRedisTemplate reactiveStringRedisTemplate(
            ReactiveRedisConnectionFactory connectionFactory) {

        return new ReactiveStringRedisTemplate(connectionFactory);
    }

}