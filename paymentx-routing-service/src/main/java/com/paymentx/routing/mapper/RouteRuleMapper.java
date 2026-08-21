package com.paymentx.routing.mapper;

import com.paymentx.routing.dto.RouteRuleRequest;
import com.paymentx.routing.dto.RouteRuleResponse;
import com.paymentx.routing.entity.RoutingRule;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * RouteRuleMapper is a interface in the routing module of PaymentX. It lives in package com.paymentx.routing.mapper and participates in routing's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through routing's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * RouteRuleMapper PaymentX ke routing module ka ek interface hai. Ye com.paymentx.routing.mapper package me hai aur routing ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise routing ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public interface RouteRuleMapper {

    RouteRuleResponse toResponse(RoutingRule entity);

    RoutingRule toEntity(RouteRuleRequest request);

    /** Partial update - only non-null fields in the request overwrite
     *  the existing entity, per NullValuePropertyMappingStrategy.IGNORE
     *  above. Used by PUT /routes/{id}. */
    void updateEntityFromRequest(RouteRuleRequest request, @MappingTarget RoutingRule entity);
}
