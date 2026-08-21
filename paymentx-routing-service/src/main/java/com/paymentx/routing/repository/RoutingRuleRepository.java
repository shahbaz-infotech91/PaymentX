package com.paymentx.routing.repository;

import com.paymentx.routing.entity.RoutingRule;
import com.paymentx.routing.entity.RoutingScheme;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * RoutingRuleRepository is a interface in the routing module of PaymentX. It lives in package com.paymentx.routing.repository and participates in routing's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through routing's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * RoutingRuleRepository PaymentX ke routing module ka ek interface hai. Ye com.paymentx.routing.repository package me hai aur routing ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise routing ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public interface RoutingRuleRepository extends JpaRepository<RoutingRule, UUID>, JpaSpecificationExecutor<RoutingRule> {

    List<RoutingRule> findByScheme(RoutingScheme scheme);

    List<RoutingRule> findByParticipantId(String participantId);

    List<RoutingRule> findByParticipantIdAndActiveTrue(String participantId);

    List<RoutingRule> findByActiveTrue();

    long countByActiveTrue();

    /** The core route-selection lookup: highest-priority (lowest number)
     *  active rule specific to this participant+scheme, if one exists. */
    Optional<RoutingRule> findFirstBySchemeAndParticipantIdAndActiveTrueOrderByPriorityAsc(
            RoutingScheme scheme, String participantId);

    /** Fallback when no participant-specific rule matches. */
    Optional<RoutingRule> findFirstBySchemeAndIsDefaultTrueAndActiveTrueOrderByPriorityAsc(RoutingScheme scheme);

    /** Used to prevent duplicate rules - same scheme+participant+priority
     *  combination is ambiguous (which one wins?) and must be rejected. */
    boolean existsBySchemeAndParticipantIdAndPriority(RoutingScheme scheme, String participantId, Integer priority);
}
