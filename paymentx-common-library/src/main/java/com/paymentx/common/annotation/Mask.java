package com.paymentx.common.annotation;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.paymentx.common.security.MaskStrategy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field (or JavaBean getter) whose value must be obscured before
 * it leaves the process as JSON - account numbers, PANs, tokens, anything
 * that shouldn't appear verbatim in a response body or a captured HTTP
 * log. Unlike {@code annotation.Audit}/{@code annotation.CorrelationId},
 * this one is fully wired, not just a documented contract:
 * {@code @JacksonAnnotationsInside} + {@code @JsonSerialize} make Jackson
 * apply {@link MaskingSerializer} to any property carrying this
 * annotation automatically, with no extra registration needed in the
 * consuming service beyond having Jackson on the classpath (already true
 * everywhere, since {@code jackson-databind} is a direct dependency of
 * this library).
 *
 * <p>Only affects JSON SERIALIZATION. It does not scrub the value from
 * log statements written by hand - use
 * {@link com.paymentx.common.security.DataMaskingUtils} directly at the
 * log call site for that.
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@JacksonAnnotationsInside
@JsonSerialize(using = MaskingSerializer.class)
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * Mask is a @interface (custom annotation type) in the common module of PaymentX, package com.paymentx.common.annotation. It is used within common's internal request/data flow, and where applicable is reached indirectly by other PaymentX services through this module's REST API or Kafka events.
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * Mask PaymentX ke common module ka ek @interface (custom annotation type) hai, package com.paymentx.common.annotation me. Ye common ke internal request/data flow me use hoti hai, aur jahan applicable ho, dusri PaymentX services ise is module ke REST API ya Kafka events ke through indirectly use karti hain.
 * ====================================================================
 */
public @interface Mask {

    MaskStrategy strategy() default MaskStrategy.PARTIAL;

    int visibleChars() default 4;
}
