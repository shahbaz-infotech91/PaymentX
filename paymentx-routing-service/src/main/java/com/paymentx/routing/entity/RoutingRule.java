package com.paymentx.routing.entity;

import com.paymentx.common.base.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * A single routing rule: for a given scheme (and optionally a specific
 * participant), which target route/endpoint should a payment be sent to,
 * at what priority, is it currently usable.
 *
 * WHY participantId is nullable rather than a separate "default route"
 * table: a rule with participantId=null IS the default/fallback rule for
 * that scheme - one table naturally expresses both "route for this
 * specific participant" and "route for everyone else" without a
 * parallel-but-almost-identical schema. isDefault is still an explicit
 * column (not just "participantId == null") so an operator can mark a
 * PARTICIPANT-SPECIFIC rule as the fallback too, if the routing policy
 * ever needs that (e.g. "if nothing else matches, use BANK001's route").
 *
 * WHY priority is a plain int, ascending = higher priority (lower number
 * wins), rather than an enum: operators need fine-grained reordering
 * (insert a rule between priority 10 and 20) that a fixed enum can't
 * express without a schema change every time.
 */
@Entity
@Table(name = "routing_rule")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * RoutingRule is a JPA entity in the routing module of PaymentX. It lives in package com.paymentx.routing.entity and participates in routing's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through routing's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * RoutingRule PaymentX ke routing module ka ek JPA entity hai. Ye com.paymentx.routing.entity package me hai aur routing ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise routing ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class RoutingRule extends AuditableEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "scheme", nullable = false, length = 32)
    private RoutingScheme scheme;

    /** Null = this rule is the default/fallback for the scheme, not tied
     *  to one specific participant. */
    @Column(name = "participant_id", length = 64)
    private String participantId;

    @Column(name = "target_route", nullable = false, length = 256)
    private String targetRoute;

    @Column(name = "priority", nullable = false)
    private Integer priority;

    @Column(name = "active", nullable = false)
    private Boolean active;

    @Column(name = "is_default", nullable = false)
    private Boolean isDefault;

    @Column(name = "description", length = 512)
    private String description;
}
