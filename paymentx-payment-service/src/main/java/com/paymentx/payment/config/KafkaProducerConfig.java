package com.paymentx.payment.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * WHY this explicit bean is required (this is a NEW addition to config,
 * not a rewrite of anything pre-existing): Spring Boot's auto-configured
 * KafkaTemplate bean is generically typed KafkaTemplate<Object, Object>.
 * All 9 producers in this service declare a constructor dependency on
 * KafkaTemplate<String, Object> specifically (String keys - we key every
 * message by paymentReference). Relying on Spring's generic-aware
 * autowiring to bridge <Object,Object> into a <String,Object> injection
 * point is fragile and version-sensitive. Declaring this bean explicitly,
 * with the exact generic signature every producer expects, removes that
 * ambiguity entirely. This mirrors Validation Service's KafkaProducerConfig
 * for project-wide consistency.
 */
@Configuration
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * KafkaProducerConfig is a configuration class in the payment module of PaymentX. It lives in package com.paymentx.payment.config and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * KafkaProducerConfig PaymentX ke payment module ka ek configuration class hai. Ye com.paymentx.payment.config package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // Same reasoning as Validation Service: acks=all + idempotent
        // producer are non-negotiable for payment lifecycle events -
        // silent message loss on a broker failover is not an acceptable
        // trade-off for latency here.
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
