package com.paymentx.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Optional, richer metadata a service can attach alongside a
 * {@link PaymentEvent} payload when the core envelope's 5 fields
 * (eventId/traceId/eventType/occurredAt/payload) aren't enough - for
 * example, a service that needs to record which specific instance
 * produced an event (for debugging a multi-replica deployment) or a
 * schema version for forward-compatible consumers.
 *
 * <p>NOT embedded into {@link PaymentEvent} itself - that class's shape
 * is fixed (zero breaking changes, per ADR 0004). A service that wants
 * this richer metadata sets {@code PaymentEvent<EventMetadata>} or
 * includes an {@code EventMetadata} field inside its own payload type.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * EventMetadata is a class in the common module of PaymentX. It lives in package com.paymentx.common.event and participates in common's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through common's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * EventMetadata PaymentX ke common module ka ek class hai. Ye com.paymentx.common.event package me hai aur common ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise common ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class EventMetadata {

    private String producerService;
    private String producerVersion;
    private String schemaVersion;
    private String correlationId;
}
