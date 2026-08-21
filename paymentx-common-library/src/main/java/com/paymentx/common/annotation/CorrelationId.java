package com.paymentx.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the field on a DTO or event payload that carries the
 * correlation/trace identifier for that type, so generic infrastructure
 * code (a logging aspect, a Kafka producer interceptor) can locate it via
 * reflection without knowing the concrete DTO type - useful anywhere one
 * piece of code must handle many different payload types uniformly.
 * Compare to how {@link com.paymentx.common.event.PaymentEvent} already
 * fixes {@code traceId} as a named field for the ONE envelope type every
 * service uses; this annotation generalizes the same idea to arbitrary
 * request/response DTOs that don't go through that envelope.
 *
 * <p>Like {@link Audit}, this is a documented contract for reflection-
 * based infrastructure to consume - it does not itself populate or read
 * anything. See {@link com.paymentx.common.logging.LoggingContext} and
 * {@link com.paymentx.common.util.CorrelationIdUtils} for the actual
 * propagation mechanics (MDC-based, not annotation-based).
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * CorrelationId is a @interface (custom annotation type) in the common module of PaymentX, package com.paymentx.common.annotation. It is used within common's internal request/data flow, and where applicable is reached indirectly by other PaymentX services through this module's REST API or Kafka events.
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * CorrelationId PaymentX ke common module ka ek @interface (custom annotation type) hai, package com.paymentx.common.annotation me. Ye common ke internal request/data flow me use hoti hai, aur jahan applicable ho, dusri PaymentX services ise is module ke REST API ya Kafka events ke through indirectly use karti hain.
 * ====================================================================
 */
public @interface CorrelationId {
}
