package com.paymentx.common.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * The platform's canonical Jackson settings, as a framework-agnostic
 * static factory rather than a Spring {@code @Bean} - deliberately usable
 * from code that has no Spring application context at all: a Kafka
 * {@code Serializer}/{@code Deserializer} (constructed directly by the
 * Kafka client, outside DI), a static utility
 * ({@link com.paymentx.common.util.JsonUtils}), or a unit test. Services
 * that DO run inside Spring should prefer {@link JacksonConfig}, which
 * applies these same settings to the auto-configured {@code ObjectMapper}
 * Spring MVC/Actuator actually use, instead of calling this class
 * directly and ending up with two divergently-configured mappers.
 *
 * <p>Settings chosen:
 * <ul>
 *   <li>{@link JavaTimeModule} - every timestamp in this platform
 *   ({@code Instant}, {@code OffsetDateTime}) must round-trip correctly;
 *   without this module Jackson fails on {@code Instant} entirely.</li>
 *   <li>Dates as ISO-8601 strings, not epoch arrays - human-readable in
 *   logs and API responses, and what every service's DTOs already assume
 *   (Validation Service's {@code GlobalExceptionHandler} formats
 *   timestamps via {@code Instant.now().toString()}, i.e. ISO-8601).</li>
 *   <li>Unknown JSON properties ignored on deserialization - a producer
 *   adding a new field to an event payload must never break an older
 *   consumer that hasn't been updated yet (forward compatibility for the
 *   Kafka contracts this library's {@code event} package defines).</li>
 * </ul>
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * ObjectMapperConfig is a class in the common module of PaymentX. It lives in package com.paymentx.common.config and participates in common's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through common's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * ObjectMapperConfig PaymentX ke common module ka ek class hai. Ye com.paymentx.common.config package me hai aur common ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise common ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public final class ObjectMapperConfig {

    private ObjectMapperConfig() {}

    public static ObjectMapper defaultObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }
}
