package com.paymentx.common.base;

import java.time.Instant;
import java.util.UUID;

/**
 * Minimal contract every event-like type in the platform can implement -
 * NOT a class every event payload extends (payload records like Payment
 * Service's {@code PaymentDebitedEvent} stay plain data carriers, per the
 * "independent event classes" decision made when they were built).
 * {@link com.paymentx.common.event.PaymentEvent} implements this
 * directly via its existing Lombok-generated accessors - see that
 * class's javadoc for why {@code Instant}, not {@code OffsetDateTime},
 * is the type used here: it must match {@code PaymentEvent}'s
 * already-existing, already-depended-upon field exactly, to keep this
 * migration a genuinely zero-breaking-change addition rather than a
 * silent behavioral change to a class every service already uses.
 *
 * <p>This interface exists so infrastructure code (logging, metrics,
 * dead-letter handling) that needs to read "what event is this, when did
 * it happen" can depend on ONE contract rather than reflectively
 * inspecting whatever concrete envelope type a given service happens to
 * use.
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * BaseEvent is a interface in the common module of PaymentX. It lives in package com.paymentx.common.base and participates in common's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through common's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * BaseEvent PaymentX ke common module ka ek interface hai. Ye com.paymentx.common.base package me hai aur common ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise common ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public interface BaseEvent {

    UUID getEventId();

    String getEventType();

    Instant getOccurredAt();
}
