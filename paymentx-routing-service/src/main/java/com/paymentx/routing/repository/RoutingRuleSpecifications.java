package com.paymentx.routing.repository;

import com.paymentx.routing.entity.RoutingRule;
import com.paymentx.routing.entity.RoutingScheme;
import org.springframework.data.jpa.domain.Specification;

/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * RoutingRuleSpecifications is a class in the routing module of PaymentX. It lives in package com.paymentx.routing.repository and participates in routing's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through routing's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * RoutingRuleSpecifications PaymentX ke routing module ka ek class hai. Ye com.paymentx.routing.repository package me hai aur routing ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise routing ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public final class RoutingRuleSpecifications {

    private RoutingRuleSpecifications() {}

    public static Specification<RoutingRule> hasScheme(RoutingScheme scheme) {
        return (root, query, cb) -> scheme == null ? cb.conjunction() : cb.equal(root.get("scheme"), scheme);
    }

    public static Specification<RoutingRule> hasParticipantId(String participantId) {
        return (root, query, cb) -> participantId == null || participantId.isBlank()
                ? cb.conjunction() : cb.equal(root.get("participantId"), participantId);
    }

    public static Specification<RoutingRule> isActive(Boolean active) {
        return (root, query, cb) -> active == null ? cb.conjunction() : cb.equal(root.get("active"), active);
    }

    public static Specification<RoutingRule> isDefault(Boolean isDefault) {
        return (root, query, cb) -> isDefault == null ? cb.conjunction() : cb.equal(root.get("isDefault"), isDefault);
    }
}
