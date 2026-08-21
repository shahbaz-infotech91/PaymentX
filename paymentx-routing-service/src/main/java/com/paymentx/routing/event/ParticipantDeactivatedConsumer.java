package com.paymentx.routing.event;

import com.paymentx.common.event.PaymentEvent;
import com.paymentx.routing.cache.ConsumerIdempotencyService;
import com.paymentx.routing.service.RoutingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * WHY the idempotency check happens BEFORE calling the service, not
 * relying solely on "deactivating an already-inactive route is a no-op":
 * that's true for correctness, but redelivery would still redundantly
 * evict the Redis cache and emit duplicate RuleChanged/log events on
 * every redelivery of the same message (which Kafka's at-least-once
 * delivery guarantee makes a normal, expected occurrence, not an edge
 * case) - the eventId check turns "redundant but harmless" into "skipped
 * entirely, cleanly."
 */
@Component
@RequiredArgsConstructor
@Slf4j
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * ParticipantDeactivatedConsumer is a Kafka consumer in the routing module of PaymentX. It lives in package com.paymentx.routing.event and participates in routing's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through routing's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * ParticipantDeactivatedConsumer PaymentX ke routing module ka ek Kafka consumer hai. Ye com.paymentx.routing.event package me hai aur routing ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise routing ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class ParticipantDeactivatedConsumer {

    private final RoutingService routingService;
    private final ConsumerIdempotencyService idempotencyService;

    @KafkaListener(
            topics = "participant.deactivated",
            groupId = "routing-service",
            containerFactory = "participantDeactivatedKafkaListenerContainerFactory")
    public void onParticipantDeactivated(ConsumerRecord<String, PaymentEvent<ParticipantDeactivatedEvent>> record,
                                          Acknowledgment acknowledgment) {
        PaymentEvent<ParticipantDeactivatedEvent> envelope = record.value();

        if (!idempotencyService.markProcessedIfNew(envelope.getEventId().toString())) {
            log.info("Skipping duplicate/redelivered event eventId={}", envelope.getEventId());
            acknowledgment.acknowledge();
            return;
        }

        ParticipantDeactivatedEvent payload = envelope.getPayload();
        log.info("Processing participant-deactivated event participantId={} reason={} traceId={}",
                payload.getParticipantId(), payload.getReason(), envelope.getTraceId());

        routingService.deactivateRoutesForParticipant(payload.getParticipantId(), envelope.getTraceId());

        acknowledgment.acknowledge();
    }
}
