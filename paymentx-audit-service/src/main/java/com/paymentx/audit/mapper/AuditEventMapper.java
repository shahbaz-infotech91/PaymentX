package com.paymentx.audit.mapper;

import com.paymentx.audit.dto.AuditEventResponse;
import com.paymentx.audit.entity.AuditEvent;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * AuditEventMapper is a interface in the audit module of PaymentX. It lives in package com.paymentx.audit.mapper and participates in audit's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through audit's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * AuditEventMapper PaymentX ke audit module ka ek interface hai. Ye com.paymentx.audit.mapper package me hai aur audit ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise audit ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public interface AuditEventMapper {

    @Mapping(target = "actorId", source = "metadata.actorId")
    @Mapping(target = "actorType", source = "metadata.actorType")
    @Mapping(target = "correlationId", source = "metadata.correlationId")
    @Mapping(target = "traceId", source = "metadata.traceId")
    @Mapping(target = "paymentId", source = "metadata.paymentId")
    @Mapping(target = "participantId", source = "metadata.participantId")
    @Mapping(target = "reference", source = "metadata.reference")
    AuditEventResponse toResponse(AuditEvent entity);
}
