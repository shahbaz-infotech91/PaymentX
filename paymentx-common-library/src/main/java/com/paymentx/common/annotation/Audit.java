package com.paymentx.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method whose invocation should produce an audit trail entry -
 * e.g. "payment approved by user X at time Y". This is a CONTRACT
 * annotation only: {@code paymentx-common-library} deliberately does not
 * ship the AOP aspect that intercepts it - the same "additive, not yet
 * wired in" choice already made for
 * {@link com.paymentx.common.event.EventType} (see that class's javadoc).
 * Adding a mandatory {@code spring-boot-starter-aop} dependency here would
 * force an aspect-weaving decision onto every consumer, including
 * services that don't need method-level interception at all. Each
 * service - or Audit Service centrally, reading these via reflection off
 * a shared classpath scan - wires its own {@code @Aspect} that reads
 * {@link #action()}/{@link #resource()} and forwards to wherever audit
 * records are persisted.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * Audit is a @interface (custom annotation type) in the common module of PaymentX, package com.paymentx.common.annotation. It is used within common's internal request/data flow, and where applicable is reached indirectly by other PaymentX services through this module's REST API or Kafka events.
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * Audit PaymentX ke common module ka ek @interface (custom annotation type) hai, package com.paymentx.common.annotation me. Ye common ke internal request/data flow me use hoti hai, aur jahan applicable ho, dusri PaymentX services ise is module ke REST API ya Kafka events ke through indirectly use karti hain.
 * ====================================================================
 */
public @interface Audit {

    /** Short, stable action identifier, e.g. {@code "PAYMENT_APPROVED"} - same "stable versioned string" discipline as {@link com.paymentx.common.exception.PaymentXException}'s errorCode. */
    String action();

    /** Optional free-text description of the resource being acted on. */
    String resource() default "";
}
