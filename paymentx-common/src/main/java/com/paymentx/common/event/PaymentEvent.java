package com.paymentx.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Base envelope for every event published to the internal Kafka bus.
 *
 * WHY an envelope instead of raw domain payloads on the topic?
 * ------------------------------------------------------------
 * In a real payment platform, every consumer downstream (Audit, Reconciliation,
 * Notification) needs THREE things regardless of what the event actually is:
 *   1. traceId       - to stitch this event back to the originating HTTP request
 *                       across service boundaries (this is what OpenTelemetry's
 *                       trace propagation hooks into later).
 *   2. eventId        - idempotency key. If Kafka redelivers this message
 *                       (at-least-once delivery is the default guarantee),
 *                       every consumer must be able to detect "I already
 *                       processed this" and no-op. We will build this
 *                       idempotency check explicitly in Payment Service.
 *   3. occurredAt     - business event time, NOT Kafka's ingestion timestamp.
 *                       These diverge under retry/replay and reconciliation
 *                       depends on the true business time.
 *
 * Putting these in a shared envelope means every consumer can write ONE
 * generic idempotency/tracing interceptor instead of every team re-inventing
 * (and inevitably getting wrong) this logic per service.
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
public class PaymentEvent<T> {

    private UUID eventId;
    private String traceId;
    private String eventType;      // e.g. "PAYMENT_VALIDATED", "INSTANT_PAYMENT_ACK"
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
