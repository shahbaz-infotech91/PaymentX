package com.paymentx.routing.service;

import com.paymentx.common.dto.PageResponse;
import com.paymentx.routing.dto.RouteRuleRequest;
import com.paymentx.routing.dto.RouteRuleResponse;
import com.paymentx.routing.dto.RouteSearchCriteria;
import com.paymentx.routing.entity.RoutingScheme;

import java.util.UUID;

/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * RoutingService is a interface in the routing module of PaymentX. It lives in package com.paymentx.routing.service and participates in routing's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through routing's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * RoutingService PaymentX ke routing module ka ek interface hai. Ye com.paymentx.routing.service package me hai aur routing ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise routing ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public interface RoutingService {

    RouteRuleResponse create(RouteRuleRequest request);

    RouteRuleResponse update(UUID id, RouteRuleRequest request);

    void delete(UUID id);

    RouteRuleResponse getById(UUID id);

    PageResponse<RouteRuleResponse> search(RouteSearchCriteria criteria, int page, int size);

    /** The core routing decision: resolve the best route for a
     *  scheme+participant, falling back to the scheme's default rule if
     *  no participant-specific active rule exists. */
    RouteRuleResponse resolveRoute(RoutingScheme scheme, String participantId, String traceId);

    /** Called when an upstream ParticipantDeactivatedEvent is consumed -
     *  deactivates every active route for that participant so payments
     *  stop being routed there. */
    void deactivateRoutesForParticipant(String participantId, String traceId);
}
