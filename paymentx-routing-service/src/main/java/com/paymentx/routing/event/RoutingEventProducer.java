package com.paymentx.routing.event;

import com.paymentx.common.event.PaymentEvent;
import com.paymentx.routing.constant.RoutingKafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * WHY a direct KafkaTemplate.send() rather than the outbox pattern
 * Payment Service uses: route-resolution events are observability/audit
 * signals, not the payment's own state transitions that MUST NOT be lost
 * on a crash between DB write and publish - Payment Service's outbox
 * exists specifically to guarantee atomicity between a payment's status
 * change and its event; a routing lookup has no equivalent DB write to
 * be atomic WITH. If this publish is lost on a rare crash, nothing about
 * payment correctness is affected - only an observability event.
 */
@Component
@RequiredArgsConstructor
@Slf4j
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * RoutingEventProducer is a Kafka producer in the routing module of PaymentX. It lives in package com.paymentx.routing.event and participates in routing's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through routing's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * RoutingEventProducer PaymentX ke routing module ka ek Kafka producer hai. Ye com.paymentx.routing.event package me hai aur routing ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise routing ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class RoutingEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishRouteResolved(RouteResolvedEvent event, String traceId) {
        PaymentEvent<RouteResolvedEvent> envelope = PaymentEvent.of("ROUTE_RESOLVED", traceId, event);
        kafkaTemplate.send(RoutingKafkaTopics.ROUTE_RESOLVED, event.getRuleId() != null ? event.getRuleId().toString() : UUID.randomUUID().toString(), envelope)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.warn("Failed to publish route-resolved event ruleId={}", event.getRuleId(), ex);
                    }
                });
    }

    /** WHY ruleId is used as the Kafka message key (not a random UUID):
     *  same-key messages land on the same partition in order, so a
     *  consumer processing CREATED -> UPDATED -> DELETED for one rule
     *  never sees them out of order, and eventId (inside the envelope)
     *  is the idempotency key a consumer uses to detect/skip a
     *  redelivered message with the exact same content. */
    public void publishRuleChanged(RuleChangedEvent event, String traceId) {
        PaymentEvent<RuleChangedEvent> envelope = PaymentEvent.of("ROUTING_RULE_" + event.getChangeType(), traceId, event);
        kafkaTemplate.send(RoutingKafkaTopics.RULE_CHANGED, event.getRuleId().toString(), envelope)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.warn("Failed to publish rule-changed event ruleId={} changeType={}", event.getRuleId(), event.getChangeType(), ex);
                    }
                });
    }
}
