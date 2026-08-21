package com.paymentx.routing.event;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Published (wrapped in the shared PaymentEvent<T> envelope from
 * paymentx-common-library - see RoutingEventProducer) whenever a routing
 * decision is made, for downstream observability/audit consumers.
 * Reuses the platform's existing event envelope rather than inventing a
 * new one, per the explicit "reuse existing Kafka architecture" mandate.
 */
@Getter
@Builder
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * RouteResolvedEvent is a class in the routing module of PaymentX. It lives in package com.paymentx.routing.event and participates in routing's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through routing's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * RouteResolvedEvent PaymentX ke routing module ka ek class hai. Ye com.paymentx.routing.event package me hai aur routing ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise routing ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class RouteResolvedEvent {
    private UUID ruleId;
    private String scheme;
    private String participantId;
    private String targetRoute;
    private OffsetDateTime resolvedAt;
}
