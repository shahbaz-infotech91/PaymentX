package com.paymentx.routing.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * WHY an internal Spring ApplicationEvent rather than calling
 * RoutingEventProducer directly from RoutingServiceImpl: publishing
 * straight to Kafka from inside a @Transactional method means the
 * message goes out even if the surrounding transaction later rolls back
 * (e.g. a DB constraint violation after the Kafka call, or any exception
 * thrown later in the same method). Raising this event instead, and
 * consuming it via @TransactionalEventListener(phase = AFTER_COMMIT) in
 * RuleChangeEventListener, guarantees "publish only after successful DB
 * commit" without the overhead of a full outbox table - appropriate here
 * because this is an observability signal, not a payment state
 * transition requiring outbox's stronger atomicity guarantee (see
 * RoutingEventProducer's javadoc for that distinction).
 */
@Getter
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * RuleChangeApplicationEvent is a class in the routing module of PaymentX. It lives in package com.paymentx.routing.event and participates in routing's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through routing's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * RuleChangeApplicationEvent PaymentX ke routing module ka ek class hai. Ye com.paymentx.routing.event package me hai aur routing ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise routing ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class RuleChangeApplicationEvent extends ApplicationEvent {

    private final RuleChangedEvent payload;
    private final String traceId;

    public RuleChangeApplicationEvent(Object source, RuleChangedEvent payload, String traceId) {
        super(source);
        this.payload = payload;
        this.traceId = traceId;
    }
}
