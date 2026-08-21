package com.paymentx.audit.dto;

import com.paymentx.audit.entity.EventStatus;
import com.paymentx.audit.entity.EventType;

import java.time.OffsetDateTime;

/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * AuditSearchCriteria is a record (DTO) in the audit module of PaymentX. It lives in package com.paymentx.audit.dto and participates in audit's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through audit's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * AuditSearchCriteria PaymentX ke audit module ka ek record (DTO) hai. Ye com.paymentx.audit.dto package me hai aur audit ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise audit ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public record AuditSearchCriteria(
        String correlationId,
        String paymentId,
        String participantId,
        String reference,
        EventStatus status,
        EventType eventType,
        OffsetDateTime fromDate,
        OffsetDateTime toDate
) {
}
