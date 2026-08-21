package com.paymentx.routing.dto;

import com.paymentx.routing.entity.RoutingScheme;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * RouteRuleResponse is a record (DTO) in the routing module of PaymentX. It lives in package com.paymentx.routing.dto and participates in routing's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through routing's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * RouteRuleResponse PaymentX ke routing module ka ek record (DTO) hai. Ye com.paymentx.routing.dto package me hai aur routing ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise routing ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public record RouteRuleResponse(
        UUID id,
        RoutingScheme scheme,
        String participantId,
        String targetRoute,
        Integer priority,
        Boolean active,
        Boolean isDefault,
        String description,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
