package com.paymentx.audit.entity;

import com.paymentx.common.base.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * WHY this is functionally append-only despite extending AuditableEntity
 * (which provides updatedAt/updatedBy/version, implying updates are
 * possible): an audit trail's core guarantee is that a record, once
 * written, is never silently altered. This service's REST API (see
 * AuditController) deliberately exposes NO update or delete endpoint -
 * updatedAt/version exist only because AuditableEntity is the platform's
 * shared base class (consistency with every other entity), not because
 * this entity is meant to be mutated. The only writers are
 * AuditEventConsumer (Kafka) and AuditServiceImpl.record() (direct API
 * writes for API request/response and security events) - both only
 * ever INSERT, never UPDATE.
 *
 * WHY payload is a raw JSON string (jsonb column), not a typed Java
 * object: this table stores 15 structurally different event categories
 * (see EventType) originating from 4 different services, each with its
 * own payload shape - forcing one Java type to represent all of them
 * would mean either a huge nullable-everything class or losing fields.
 * The typed, structured fields that actually matter for SEARCH
 * (correlationId, paymentId, participantId, reference - see
 * AuditMetadata) are extracted into real indexed columns; the full
 * original payload is preserved verbatim in JSON for audit completeness.
 */
@Entity
@Table(name = "audit_event")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * AuditEvent is a JPA entity in the audit module of PaymentX. It lives in package com.paymentx.audit.entity and participates in audit's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through audit's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * AuditEvent PaymentX ke audit module ka ek JPA entity hai. Ye com.paymentx.audit.entity package me hai aur audit ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise audit ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class AuditEvent extends AuditableEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private EventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_status", nullable = false, length = 32)
    private EventStatus eventStatus;

    /** Which upstream service produced this event - "validation-service",
     *  "payment-service", "routing-service", "api-gateway", or
     *  "audit-service" itself for locally-recorded events (API
     *  request/response, security events). */
    @Column(name = "source_service", nullable = false, length = 64)
    private String sourceService;

    @Embedded
    private AuditMetadata metadata;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb")
    private String payload;

    /** When the event actually happened at the source, distinct from
     *  createdAt (AuditableEntity, when THIS record was persisted) -
     *  under Kafka redelivery/consumer-lag, these can genuinely differ,
     *  and search-by-date-range should reflect business time, not
     *  ingestion time. */
    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    /** The originating Kafka message's eventId (from the shared
     *  PaymentEvent envelope) - the idempotency key AuditEventConsumer
     *  checks before inserting, and preserved here for traceability
     *  even after the Redis-based dedup window expires. */
    @Column(name = "source_event_id", length = 64)
    private String sourceEventId;
}
