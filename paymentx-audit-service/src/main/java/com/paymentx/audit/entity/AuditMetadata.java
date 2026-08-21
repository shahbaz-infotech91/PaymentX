package com.paymentx.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * WHY @Embeddable rather than a separate joined table: this context
 * (who triggered the event, and which business identifiers it relates
 * to) has no identity of its own and never exists independently of its
 * owning AuditEvent - the exact same DDD-value-object rationale as
 * Payment Service's Money embeddable. Every field here is also part of
 * the primary search-index surface (see AuditEventSpecifications), so
 * keeping them as direct columns on the audit_event table (via
 * @Embedded, not a join) is also the right performance choice for a
 * search-heavy read path.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * AuditMetadata is a class in the audit module of PaymentX. It lives in package com.paymentx.audit.entity and participates in audit's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through audit's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * AuditMetadata PaymentX ke audit module ka ek class hai. Ye com.paymentx.audit.entity package me hai aur audit ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise audit ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class AuditMetadata {

    @Column(name = "actor_id", length = 128)
    private String actorId;

    @Column(name = "actor_type", length = 32)
    private String actorType;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "payment_id", length = 64)
    private String paymentId;

    @Column(name = "participant_id", length = 64)
    private String participantId;

    @Column(name = "reference", length = 128)
    private String reference;
}
