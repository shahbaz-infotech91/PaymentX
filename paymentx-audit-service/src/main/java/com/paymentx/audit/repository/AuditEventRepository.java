package com.paymentx.audit.repository;

import com.paymentx.audit.entity.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * AuditEventRepository is a interface in the audit module of PaymentX. It lives in package com.paymentx.audit.repository and participates in audit's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through audit's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * AuditEventRepository PaymentX ke audit module ka ek interface hai. Ye com.paymentx.audit.repository package me hai aur audit ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise audit ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID>, JpaSpecificationExecutor<AuditEvent> {

    /** Idempotency check before insert - see AuditEventConsumer. A
     *  redelivered Kafka message with the same source eventId must
     *  never create a duplicate audit record (an audit trail with
     *  duplicate entries is itself a data-integrity bug). */
    boolean existsBySourceEventId(String sourceEventId);
}
