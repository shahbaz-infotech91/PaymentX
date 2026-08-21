package com.paymentx.notification.config;

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
 * Matches Audit Service's KafkaConsumerConfig pattern exactly - see that
 * class's javadoc for the full JsonNode-generic-payload rationale.
 */
@Configuration
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * KafkaConsumerConfig is a configuration class in the notification module of PaymentX. It lives in package com.paymentx.notification.config and participates in notification's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through notification's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * KafkaConsumerConfig PaymentX ke notification module ka ek configuration class hai. Ye com.paymentx.notification.config package me hai aur notification ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise notification ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class KafkaConsumerConfig {

    @Bean
    public ConsumerFactory<String, PaymentEvent<JsonNode>> notificationConsumerFactory(
            KafkaProperties kafkaProperties, ObjectMapper objectMapper) {

        Map<String, Object> props = new HashMap<>(kafkaProperties.buildConsumerProperties(null));
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);

        JsonDeserializer<PaymentEvent<JsonNode>> valueDeserializer =
                new JsonDeserializer<>(new TypeReference<PaymentEvent<JsonNode>>() {}, objectMapper, false);
        valueDeserializer.addTrustedPackages("com.paymentx.*");

        // See paymentx-audit-service's KafkaConsumerConfig for why explicit
        // ErrorHandlingDeserializer wrapping here (not just via props above)
        // is required - passing bare deserializer instances to this
        // constructor silently discards the props-level wrapping, letting a
        // poison message crash the fetch loop instead of being recovered.
        return new DefaultKafkaConsumerFactory<>(props,
                new ErrorHandlingDeserializer<>(new StringDeserializer()),
                new ErrorHandlingDeserializer<>(valueDeserializer));
    }

    @Bean
    public DefaultErrorHandler notificationErrorHandler(KafkaTemplate<String, Object> kafkaTemplate, NotificationProperties notificationProperties) {
        var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + ".DLT", record.partition()));
        var consumerProps = notificationProperties.getConsumer();
        var backOff = new ExponentialBackOff(consumerProps.getRetryInitialIntervalMillis(), consumerProps.getRetryMultiplier());
        backOff.setMaxElapsedTime(consumerProps.getRetryMaxElapsedMillis());
        return new DefaultErrorHandler(recoverer, backOff);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentEvent<JsonNode>> notificationKafkaListenerContainerFactory(
            ConsumerFactory<String, PaymentEvent<JsonNode>> notificationConsumerFactory,
            DefaultErrorHandler notificationErrorHandler) {

        ConcurrentKafkaListenerContainerFactory<String, PaymentEvent<JsonNode>> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(notificationConsumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.setCommonErrorHandler(notificationErrorHandler);
        return factory;
    }
}
