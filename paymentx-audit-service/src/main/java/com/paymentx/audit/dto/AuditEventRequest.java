package com.paymentx.audit.dto;

import com.paymentx.audit.entity.EventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * AuditEventRequest is a record (DTO) in the audit module of PaymentX. It lives in package com.paymentx.audit.dto and participates in audit's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through audit's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * AuditEventRequest PaymentX ke audit module ka ek record (DTO) hai. Ye com.paymentx.audit.dto package me hai aur audit ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise audit ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public record AuditEventRequest(
        @NotNull(message = "eventType is required")
        EventType eventType,

        @NotBlank(message = "sourceService is required")
        String sourceService,

        String actorId,
        String actorType,
        String correlationId,
        String traceId,
        String paymentId,
        String participantId,
        String reference,

        @NotBlank(message = "payload is required")
        String payload
) {
}
