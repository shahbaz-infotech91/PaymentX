package com.paymentx.audit.repository;

import com.paymentx.audit.entity.AuditEvent;
import com.paymentx.audit.entity.EventStatus;
import com.paymentx.audit.entity.EventType;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;

/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * AuditEventSpecifications is a class in the audit module of PaymentX. It lives in package com.paymentx.audit.repository and participates in audit's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through audit's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * AuditEventSpecifications PaymentX ke audit module ka ek class hai. Ye com.paymentx.audit.repository package me hai aur audit ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise audit ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public final class AuditEventSpecifications {

    private AuditEventSpecifications() {}

    public static Specification<AuditEvent> hasCorrelationId(String correlationId) {
        return (root, query, cb) -> correlationId == null || correlationId.isBlank()
                ? cb.conjunction() : cb.equal(root.get("metadata").get("correlationId"), correlationId);
    }

    public static Specification<AuditEvent> hasPaymentId(String paymentId) {
        return (root, query, cb) -> paymentId == null || paymentId.isBlank()
                ? cb.conjunction() : cb.equal(root.get("metadata").get("paymentId"), paymentId);
    }

    public static Specification<AuditEvent> hasParticipantId(String participantId) {
        return (root, query, cb) -> participantId == null || participantId.isBlank()
                ? cb.conjunction() : cb.equal(root.get("metadata").get("participantId"), participantId);
    }

    public static Specification<AuditEvent> hasReference(String reference) {
        return (root, query, cb) -> reference == null || reference.isBlank()
                ? cb.conjunction() : cb.equal(root.get("metadata").get("reference"), reference);
    }

    public static Specification<AuditEvent> hasStatus(EventStatus status) {
        return (root, query, cb) -> status == null ? cb.conjunction() : cb.equal(root.get("eventStatus"), status);
    }

    public static Specification<AuditEvent> hasEventType(EventType eventType) {
        return (root, query, cb) -> eventType == null ? cb.conjunction() : cb.equal(root.get("eventType"), eventType);
    }

    public static Specification<AuditEvent> occurredAfter(OffsetDateTime from) {
        return (root, query, cb) -> from == null ? cb.conjunction() : cb.greaterThanOrEqualTo(root.get("occurredAt"), from);
    }

    public static Specification<AuditEvent> occurredBefore(OffsetDateTime to) {
        return (root, query, cb) -> to == null ? cb.conjunction() : cb.lessThanOrEqualTo(root.get("occurredAt"), to);
    }
}
