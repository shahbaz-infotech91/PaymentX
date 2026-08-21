package com.paymentx.audit.service;

import com.paymentx.audit.dto.AuditEventRequest;
import com.paymentx.audit.dto.AuditEventResponse;
import com.paymentx.audit.dto.AuditSearchCriteria;
import com.paymentx.audit.entity.EventType;
import com.paymentx.common.dto.PageResponse;

import java.util.UUID;

/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * AuditService is a interface in the audit module of PaymentX. It lives in package com.paymentx.audit.service and participates in audit's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through audit's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * AuditService PaymentX ke audit module ka ek interface hai. Ye com.paymentx.audit.service package me hai aur audit ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise audit ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public interface AuditService {

    /** Direct-write path for events not sourced from Kafka - API
     *  request/response, security events (see AuditController). */
    AuditEventResponse record(AuditEventRequest request);

    /** Kafka-sourced write path - called by AuditEventConsumer.
     *  Idempotent: silently no-ops if sourceEventId was already
     *  processed, never throws on a duplicate. paymentId/participantId/
     *  reference are best-effort extracted from the raw payload by the
     *  consumer (see TopicEventTypeResolver/AuditEventConsumer) - null
     *  when the source event's JSON shape doesn't carry that field. */
    void recordFromKafka(EventType eventType, String sourceService, String sourceEventId, String traceId,
                          String paymentId, String participantId, String reference, String payloadJson);

    AuditEventResponse getById(UUID id);

    PageResponse<AuditEventResponse> search(AuditSearchCriteria criteria, int page, int size);
}
