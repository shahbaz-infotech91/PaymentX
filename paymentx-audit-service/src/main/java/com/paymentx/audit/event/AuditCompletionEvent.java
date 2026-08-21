package com.paymentx.audit.event;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * AuditCompletionEvent is a class in the audit module of PaymentX. It lives in package com.paymentx.audit.event and participates in audit's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through audit's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * AuditCompletionEvent PaymentX ke audit module ka ek class hai. Ye com.paymentx.audit.event package me hai aur audit ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise audit ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class AuditCompletionEvent {
    private UUID auditEventId;
    private String eventType;
    private String sourceService;
}
