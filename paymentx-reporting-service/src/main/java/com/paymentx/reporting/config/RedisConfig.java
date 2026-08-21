package com.paymentx.reporting.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * ====================================================================
 * ENGLISH: Provides the StringRedisTemplate bean every Redis-backed
 * class in this service depends on (dedup, report-result cache).
 *
 * HINGLISH: StringRedisTemplate bean provide karta hai jispe is service
 * ki har Redis-backed class depend karti hai (dedup, report-result
 * cache).
 * ====================================================================
 */
@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
