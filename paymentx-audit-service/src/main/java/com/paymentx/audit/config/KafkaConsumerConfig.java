package com.paymentx.audit.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentx.common.event.PaymentEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * WHY JsonNode (Jackson's generic JSON tree type) as the payload type,
 * not a specific typed class per event: this single listener subscribes
 * to 14 different topics (see AuditKafkaTopics.ALL_CONSUMED_TOPICS),
 * each with a structurally different payload shape, produced by 3
 * different services' own independently-versioned event classes. Audit
 * Service's job is to durably preserve whatever arrived, not to
 * understand its business meaning - AuditEventConsumer serializes the
 * JsonNode straight into AuditEvent.payload (jsonb) without needing a
 * matching Java class for every producer's event type.
 */
@Configuration
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * KafkaConsumerConfig is a configuration class in the audit module of PaymentX. It lives in package com.paymentx.audit.config and participates in audit's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through audit's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * KafkaConsumerConfig PaymentX ke audit module ka ek configuration class hai. Ye com.paymentx.audit.config package me hai aur audit ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise audit ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class KafkaConsumerConfig {

    @Bean
    public ConsumerFactory<String, PaymentEvent<JsonNode>> auditConsumerFactory(
            KafkaProperties kafkaProperties, ObjectMapper objectMapper) {

        Map<String, Object> props = new HashMap<>(kafkaProperties.buildConsumerProperties(null));
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);

        JsonDeserializer<PaymentEvent<JsonNode>> valueDeserializer =
                new JsonDeserializer<>(new TypeReference<PaymentEvent<JsonNode>>() {}, objectMapper, false);
        valueDeserializer.addTrustedPackages("com.paymentx.*");

        // WHY wrap in ErrorHandlingDeserializer HERE (not just via props
        // above): DefaultKafkaConsumerFactory's 3-arg constructor takes
        // already-instantiated Deserializer objects, which take priority
        // over the KEY/VALUE_DESERIALIZER_CLASS_CONFIG props set above -
        // passing the bare valueDeserializer here silently discarded that
        // props-level wrapping, so a malformed message threw
        // SerializationException at the Kafka client's poll() level
        // instead of being caught per-record and handed to
        // auditErrorHandler. That crashed the container's fetch loop on
        // every retry with no backoff, producing an unbounded same-offset
        // retry storm (found during Phase 1 validation via a real poison
        // message test - filled a log file past 1GB in minutes). Wrapping
        // both deserializer instances directly is what actually activates
        // the intended per-record recovery path.
        return new DefaultKafkaConsumerFactory<>(props,
                new ErrorHandlingDeserializer<>(new StringDeserializer()),
                new ErrorHandlingDeserializer<>(valueDeserializer));
    }

    @Bean
    public DefaultErrorHandler auditErrorHandler(KafkaTemplate<String, Object> kafkaTemplate, AuditProperties auditProperties) {
        var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + ".DLT", record.partition()));
        var consumerProps = auditProperties.getConsumer();
        var backOff = new ExponentialBackOff(consumerProps.getRetryInitialIntervalMillis(), consumerProps.getRetryMultiplier());
        backOff.setMaxElapsedTime(consumerProps.getRetryMaxElapsedMillis());
        return new DefaultErrorHandler(recoverer, backOff);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentEvent<JsonNode>> auditKafkaListenerContainerFactory(
            ConsumerFactory<String, PaymentEvent<JsonNode>> auditConsumerFactory,
            DefaultErrorHandler auditErrorHandler) {

        ConcurrentKafkaListenerContainerFactory<String, PaymentEvent<JsonNode>> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(auditConsumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.setCommonErrorHandler(auditErrorHandler);
        return factory;
    }
}
