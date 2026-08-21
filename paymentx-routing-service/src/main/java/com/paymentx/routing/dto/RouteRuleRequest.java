package com.paymentx.routing.dto;

import com.paymentx.routing.entity.RoutingScheme;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * RouteRuleRequest is a record (DTO) in the routing module of PaymentX. It lives in package com.paymentx.routing.dto and participates in routing's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through routing's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * RouteRuleRequest PaymentX ke routing module ka ek record (DTO) hai. Ye com.paymentx.routing.dto package me hai aur routing ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise routing ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public record RouteRuleRequest(
        @NotNull(message = "scheme is required")
        RoutingScheme scheme,

        @Size(max = 64)
        String participantId,

        @NotBlank(message = "targetRoute is required")
        @Size(max = 256)
        String targetRoute,

        @NotNull(message = "priority is required")
        @Min(value = 0, message = "priority must be non-negative")
        Integer priority,

        Boolean active,

        Boolean isDefault,

        @Size(max = 512)
        String description
) {
}
