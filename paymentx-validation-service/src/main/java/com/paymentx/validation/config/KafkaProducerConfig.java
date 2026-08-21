package com.paymentx.validation.config;

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

@Configuration
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * KafkaProducerConfig is a configuration class in the validation module of PaymentX. It lives in package com.paymentx.validation.config and participates in validation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through validation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * KafkaProducerConfig PaymentX ke validation module ka ek configuration class hai. Ye com.paymentx.validation.config package me hai aur validation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise validation ke REST API ya Kafka events ke through indirectly use karti hain).
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

        // acks=all: wait for ALL in-sync replicas to acknowledge the write,
        // not just the leader broker. For a payment event, "the message
        // might get silently lost if the leader broker dies right after
        // acking" is not an acceptable trade-off for a little latency.
        // This is a very different (and correct) choice vs. a typical
        // analytics/logging pipeline where acks=1 or acks=0 is fine.
        config.put(ProducerConfig.ACKS_CONFIG, "all");

        // Idempotent producer: prevents the PRODUCER itself from writing
        // duplicate messages on internal retry (e.g. a transient network
        // blip causes Kafka client to retry the send). This is a DIFFERENT
        // idempotency concern from our payment_validation_record table -
        // that table stops us from processing the same BUSINESS payment
        // twice; this setting stops the same KAFKA MESSAGE being written
        // twice due to producer-level retry.
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
