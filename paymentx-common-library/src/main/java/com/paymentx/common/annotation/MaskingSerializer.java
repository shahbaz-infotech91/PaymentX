package com.paymentx.common.annotation;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import com.paymentx.common.security.DataMaskingUtils;
import com.paymentx.common.security.MaskStrategy;

import java.io.IOException;

/**
 * The Jackson wiring that makes {@link Mask} actually do something at
 * serialization time, rather than being an inert marker. Implements
 * {@link ContextualSerializer} so it can read the specific {@link Mask}
 * instance annotating THIS property (its {@code strategy}/
 * {@code visibleChars}) once, in {@link #createContextual}, rather than
 * re-reading annotations via reflection on every single serialize call.
 * The actual character-masking algorithm lives in
 * {@link DataMaskingUtils}, shared with any non-Jackson caller (e.g. a
 * log statement) that needs to mask a value outside JSON serialization.
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * MaskingSerializer is a class in the common module of PaymentX. It lives in package com.paymentx.common.annotation and participates in common's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through common's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * MaskingSerializer PaymentX ke common module ka ek class hai. Ye com.paymentx.common.annotation package me hai aur common ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise common ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class MaskingSerializer extends JsonSerializer<Object> implements ContextualSerializer {

    private final MaskStrategy strategy;
    private final int visibleChars;

    /** No-arg constructor required by Jackson to instantiate this class from {@code @JsonSerialize(using = ...)} before {@link #createContextual} refines it. */
    public MaskingSerializer() {
        this(MaskStrategy.PARTIAL, 4);
    }

    private MaskingSerializer(MaskStrategy strategy, int visibleChars) {
        this.strategy = strategy;
        this.visibleChars = visibleChars;
    }

    @Override
    public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }
        gen.writeString(DataMaskingUtils.mask(String.valueOf(value), strategy, visibleChars));
    }

    @Override
    public JsonSerializer<?> createContextual(SerializerProvider prov, BeanProperty property) throws JsonMappingException {
        if (property == null) {
            return this;
        }
        Mask mask = property.getAnnotation(Mask.class);
        if (mask == null) {
            return this;
        }
        return new MaskingSerializer(mask.strategy(), mask.visibleChars());
    }
}
