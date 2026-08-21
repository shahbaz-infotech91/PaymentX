package com.paymentx.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentx.common.config.ObjectMapperConfig;

import java.io.UncheckedIOException;

/**
 * Static JSON helpers built on {@link ObjectMapperConfig}'s canonical
 * {@link ObjectMapper} - for the many call sites (a Kafka producer
 * manually serializing a payload, a quick debug log line, a test
 * fixture) that just need "give me the JSON string" without wiring a
 * mapper through DI.
 *
 * <p>{@link JsonProcessingException} is checked and {@code extends
 * IOException} - wrapping it in {@link UncheckedIOException} (a standard
 * JDK type, not a bespoke exception this library invents) keeps this
 * class's methods usable in lambdas/streams without forcing every caller
 * to declare or catch a checked exception for what is, in practice, an
 * unrecoverable programming error (a DTO that isn't actually JSON-
 * serializable) rather than an expected control-flow outcome.
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * JsonUtils is a class in the common module of PaymentX. It lives in package com.paymentx.common.util and participates in common's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through common's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * JsonUtils PaymentX ke common module ka ek class hai. Ye com.paymentx.common.util package me hai aur common ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise common ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public final class JsonUtils {

    private static final ObjectMapper MAPPER = ObjectMapperConfig.defaultObjectMapper();

    private JsonUtils() {}

    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            String type = value == null ? "null" : value.getClass().getName();
            throw new UncheckedIOException("Failed to serialize value of type " + type + " to JSON", e);
        }
    }

    public static <T> T fromJson(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("Failed to deserialize JSON into " + type.getName(), e);
        }
    }

    public static <T> T fromJson(String json, TypeReference<T> typeReference) {
        try {
            return MAPPER.readValue(json, typeReference);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("Failed to deserialize JSON into " + typeReference.getType(), e);
        }
    }

    public static boolean isValidJson(String json) {
        if (json == null || json.isBlank()) {
            return false;
        }
        try {
            MAPPER.readTree(json);
            return true;
        } catch (JsonProcessingException e) {
            return false;
        }
    }
}
