package com.paymentx.common.event;

import com.paymentx.common.base.BaseEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Migrated from {@code paymentx-common} (Module 1) into
 * {@code paymentx-common-library} - see ADR 0003 and ADR 0004.
 * Field-for-field IDENTICAL to the original: every service already
 * depending on this class's shape (Validation Service, Payment Service,
 * and their Kafka message contracts) continues to work unchanged after
 * the {@code pom.xml} dependency swap from {@code paymentx-common} to
 * {@code paymentx-common-library} - same package
 * ({@code com.paymentx.common.event}), same class name, same fields, same
 * accessor return types.
 *
 * <p>WHY an envelope instead of raw domain payloads on the topic - see
 * the original design rationale (unchanged): every consumer downstream
 * needs traceId (cross-service tracing), eventId (idempotency key), and
 * occurredAt (true business event time, distinct from Kafka's ingestion
 * timestamp) regardless of what the specific payload is.
 *
 * <p>The only addition versus the original is {@code implements BaseEvent}
 * - satisfied entirely by Lombok's already-existing generated accessors
 * ({@code getEventId()}, {@code getEventType()}, {@code getOccurredAt()}),
 * with zero additional code and zero change to the class's behavior or
 * wire format.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * PaymentEvent is a class in the common module of PaymentX. It lives in package com.paymentx.common.event and participates in common's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through common's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * PaymentEvent PaymentX ke common module ka ek class hai. Ye com.paymentx.common.event package me hai aur common ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise common ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class PaymentEvent<T> implements BaseEvent {

    private UUID eventId;
    private String traceId;
    private String eventType;
    private Instant occurredAt;
    private T payload;

    public static <T> PaymentEvent<T> of(String eventType, String traceId, T payload) {
        return PaymentEvent.<T>builder()
                .eventId(UUID.randomUUID())
                .traceId(traceId)
                .eventType(eventType)
                .occurredAt(Instant.now())
                .payload(payload)
                .build();
    }
}
