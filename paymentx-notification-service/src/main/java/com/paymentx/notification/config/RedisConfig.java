package com.paymentx.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * RedisConfig is a configuration class in the notification module of PaymentX. It lives in package com.paymentx.notification.config and participates in notification's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through notification's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * RedisConfig PaymentX ke notification module ka ek configuration class hai. Ye com.paymentx.notification.config package me hai aur notification ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise notification ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
