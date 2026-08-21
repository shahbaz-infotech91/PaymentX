package com.paymentx.routing.event;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * RuleChangeEventListener is a component in the routing module of PaymentX. It lives in package com.paymentx.routing.event and participates in routing's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through routing's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * RuleChangeEventListener PaymentX ke routing module ka ek component hai. Ye com.paymentx.routing.event package me hai aur routing ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise routing ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class RuleChangeEventListener {

    private final RoutingEventProducer routingEventProducer;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRuleChanged(RuleChangeApplicationEvent event) {
        routingEventProducer.publishRuleChanged(event.getPayload(), event.getTraceId());
    }
}
