package com.paymentx.controlcenter.dto.postgres;

import java.time.OffsetDateTime;

/**
 * ENGLISH: One real row from paymentx_routing.routing_rule
 * (scheme, participant_id, target_route, priority, active, is_default,
 * description, timestamps). Read-only.
 *
 * HINGLISH: paymentx_routing.routing_rule ki ek real row (scheme,
 * participant_id, target_route, priority, active, is_default,
 * description, timestamps). Read-only hai.
 */
public record RoutingRuleSummary(
        String id,
        String scheme,
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
