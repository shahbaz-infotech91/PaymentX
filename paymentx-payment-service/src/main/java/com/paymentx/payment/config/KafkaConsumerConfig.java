package com.paymentx.payment.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentx.common.event.PaymentEvent;
import com.paymentx.payment.event.PaymentValidatedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * WHY a hand-written ConsumerFactory bean instead of relying purely on
 * Spring Boot's auto-configured one: the incoming message's payload type
 * is the GENERIC com.paymentx.common.event.PaymentEvent<PaymentValidatedEvent>.
 * Java erases generic type parameters at runtime, so without an explicit
 * TypeReference, Jackson deserializes the `payload` field as a raw
 * LinkedHashMap instead of a PaymentValidatedEvent instance - a
 * ClassCastException waiting to happen the first time consumer code
 * calls a PaymentValidatedEvent getter on what is actually a Map.
 * JsonDeserializer's TypeReference-accepting constructor is the
 * documented Spring Kafka mechanism for exactly this generic-wrapper
 * scenario.
 *
 * WHY ErrorHandlingDeserializer wraps the JsonDeserializer: if a message
 * arrives that genuinely cannot be deserialized (corrupt payload, schema
 * drift), a bare JsonDeserializer throws during poll() and can crash the
 * entire listener container thread, taking down consumption for every
 * OTHER (valid) message on that partition too. ErrorHandlingDeserializer
 * catches the deserialization exception per-record and hands it to the
 * container's error handler instead, so one bad message doesn't halt the
 * whole consumer. Full dead-letter-topic recovery wiring for these
 * poison-pill messages is addressed in Batch 6 (exception package).
 *
 * WHY ContainerProperties.AckMode.MANUAL is set explicitly here rather
 * than relying solely on application.yml's spring.kafka.listener.ack-mode:
 * that YAML property only auto-applies to Spring Boot's OWN
 * auto-configured container factory bean. The moment we declare our own
 * ConcurrentKafkaListenerContainerFactory bean (required for the generic
 * deserializer above), that auto-configuration is bypassed, and every
 * setting it would have applied - including ack-mode - must be set here
 * explicitly instead.
 */
@Configuration
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * KafkaConsumerConfig is a configuration class in the payment module of PaymentX. It lives in package com.paymentx.payment.config and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * KafkaConsumerConfig PaymentX ke payment module ka ek configuration class hai. Ye com.paymentx.payment.config package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class KafkaConsumerConfig {

    @Bean
    public ConsumerFactory<String, PaymentEvent<PaymentValidatedEvent>> paymentValidatedConsumerFactory(
            KafkaProperties kafkaProperties, ObjectMapper objectMapper) {

        // buildConsumerProperties() (no-args) was deprecated in Spring Boot
        // 3.2 in favor of the overload accepting SslBundles - it exists to
        // let KafkaProperties resolve SSL bundle references (e.g.
        // spring.kafka.ssl.bundle=my-bundle) when building connection
        // properties. We aren't using SSL bundles for local Kafka, so null
        // is the correct, honest argument here - not a workaround.
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildConsumerProperties(null));
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);

        JsonDeserializer<PaymentEvent<PaymentValidatedEvent>> valueDeserializer =
                new JsonDeserializer<>(
                        new TypeReference<PaymentEvent<PaymentValidatedEvent>>() {},
                        objectMapper,
                        false // false = ignore type headers on the message; ALWAYS deserialize
                              // into the TypeReference above. Necessary because the message was
                              // produced by Validation Service, whose type header (if trusted)
                              // would reference ITS package names, not ours.
                );
        valueDeserializer.addTrustedPackages("com.paymentx.*");

        // WHY explicit ErrorHandlingDeserializer wrapping HERE, not just via
        // the props above: DefaultKafkaConsumerFactory's 3-arg constructor
        // takes already-instantiated Deserializer objects, which take
        // priority over the KEY/VALUE_DESERIALIZER_CLASS_CONFIG props set
        // above - passing the bare valueDeserializer silently discarded
        // that wrapping, so the protection this class's own top-of-file
        // comment describes was never actually active: a malformed message
        // threw SerializationException at the Kafka client's poll() level
        // (not caught per-record) and could crash/wedge this consumer -
        // the most business-critical one in the platform, since it's what
        // creates every payment. Found via a real poison-message test
        // against a sibling service during Phase 1 validation; this
        // service shared the identical bug.
        return new DefaultKafkaConsumerFactory<>(props,
                new ErrorHandlingDeserializer<>(new StringDeserializer()),
                new ErrorHandlingDeserializer<>(valueDeserializer));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentEvent<PaymentValidatedEvent>>
            paymentValidatedKafkaListenerContainerFactory(
                    ConsumerFactory<String, PaymentEvent<PaymentValidatedEvent>> paymentValidatedConsumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, PaymentEvent<PaymentValidatedEvent>> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(paymentValidatedConsumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        return factory;
    }
}
