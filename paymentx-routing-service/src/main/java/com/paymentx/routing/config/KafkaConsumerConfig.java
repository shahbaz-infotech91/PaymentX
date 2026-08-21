package com.paymentx.routing.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentx.common.event.PaymentEvent;
import com.paymentx.routing.event.ParticipantDeactivatedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * WHY container-level DLQ/retry (DefaultErrorHandler +
 * DeadLetterPublishingRecoverer) rather than Payment Service's
 * DB-table-driven retry (PaymentRetry + RetryScheduler): Payment
 * Service's consumer failures are PAYMENT PROCESSING failures with
 * business-specific retry semantics (exponential backoff tracked per
 * payment, operator-visible retry status) - a stateless reactive
 * handler like this one (deactivate routes when a participant is
 * deactivated) has no equivalent business state machine to drive retry
 * from. Spring Kafka's standard container-level DefaultErrorHandler is
 * the correct, idiomatic fit: retry a few times with backoff, then
 * publish to a dead-letter topic rather than blocking the partition
 * indefinitely or silently dropping the message.
 */
@Configuration
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * KafkaConsumerConfig is a configuration class in the routing module of PaymentX. It lives in package com.paymentx.routing.config and participates in routing's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through routing's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * KafkaConsumerConfig PaymentX ke routing module ka ek configuration class hai. Ye com.paymentx.routing.config package me hai aur routing ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise routing ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class KafkaConsumerConfig {

    @Bean
    public ConsumerFactory<String, PaymentEvent<ParticipantDeactivatedEvent>> participantDeactivatedConsumerFactory(
            KafkaProperties kafkaProperties, ObjectMapper objectMapper) {

        Map<String, Object> props = new HashMap<>(kafkaProperties.buildConsumerProperties(null));
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);

        JsonDeserializer<PaymentEvent<ParticipantDeactivatedEvent>> valueDeserializer =
                new JsonDeserializer<>(new TypeReference<PaymentEvent<ParticipantDeactivatedEvent>>() {}, objectMapper, false);
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
    public DefaultErrorHandler routingErrorHandler(org.springframework.kafka.core.KafkaTemplate<String, Object> kafkaTemplate,
                                                     com.paymentx.routing.config.RoutingProperties routingProperties) {
        var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> new org.apache.kafka.common.TopicPartition(record.topic() + ".DLT", record.partition()));
        var consumerProps = routingProperties.getConsumer();
        var backOff = new ExponentialBackOff(consumerProps.getRetryInitialIntervalMillis(), consumerProps.getRetryMultiplier());
        backOff.setMaxElapsedTime(consumerProps.getRetryMaxElapsedMillis());
        return new DefaultErrorHandler(recoverer, backOff);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentEvent<ParticipantDeactivatedEvent>>
            participantDeactivatedKafkaListenerContainerFactory(
                    ConsumerFactory<String, PaymentEvent<ParticipantDeactivatedEvent>> participantDeactivatedConsumerFactory,
                    DefaultErrorHandler routingErrorHandler) {

        ConcurrentKafkaListenerContainerFactory<String, PaymentEvent<ParticipantDeactivatedEvent>> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(participantDeactivatedConsumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.setCommonErrorHandler(routingErrorHandler);
        return factory;
    }
}
