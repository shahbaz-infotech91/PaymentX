package com.paymentx.audit.service.impl;

import com.paymentx.audit.cache.AuditSearchCacheService;
import com.paymentx.audit.dto.AuditEventRequest;
import com.paymentx.audit.dto.AuditEventResponse;
import com.paymentx.audit.dto.AuditSearchCriteria;
import com.paymentx.audit.entity.AuditEvent;
import com.paymentx.audit.entity.AuditMetadata;
import com.paymentx.audit.entity.EventStatus;
import com.paymentx.audit.entity.EventType;
import com.paymentx.audit.event.AuditCompletionApplicationEvent;
import com.paymentx.audit.mapper.AuditEventMapper;
import com.paymentx.audit.metrics.AuditMetrics;
import com.paymentx.audit.repository.AuditEventRepository;
import com.paymentx.audit.repository.AuditEventSpecifications;
import com.paymentx.audit.service.AuditService;
import com.paymentx.common.dto.PageResponse;
import com.paymentx.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * AuditServiceImpl is a service in the audit module of PaymentX. It lives in package com.paymentx.audit.service.impl and participates in audit's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through audit's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * AuditServiceImpl PaymentX ke audit module ka ek service hai. Ye com.paymentx.audit.service.impl package me hai aur audit ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise audit ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class AuditServiceImpl implements AuditService {

    private final AuditEventRepository auditEventRepository;
    private final AuditEventMapper auditEventMapper;
    private final AuditSearchCacheService auditSearchCacheService;
    private final AuditMetrics auditMetrics;
    private final org.springframework.context.ApplicationEventPublisher applicationEventPublisher;

    @Override
    public AuditEventResponse record(AuditEventRequest request) {
        var timerSample = auditMetrics.startTimer();

        AuditEvent event = AuditEvent.builder()
                .eventType(request.eventType())
                .eventStatus(EventStatus.RECORDED)
                .sourceService(request.sourceService())
                .metadata(new AuditMetadata(
                        request.actorId(), request.actorType(), request.correlationId(),
                        request.traceId(), request.paymentId(), request.participantId(), request.reference()))
                .payload(request.payload())
                .occurredAt(OffsetDateTime.now())
                .build();

        AuditEvent saved = auditEventRepository.save(event);
        auditMetrics.recordWrite(timerSample, request.eventType().name());
        log.info("Audit event recorded id={} eventType={} sourceService={}", saved.getId(), saved.getEventType(), saved.getSourceService());

        return auditEventMapper.toResponse(saved);
    }

    @Override
    public void recordFromKafka(EventType eventType, String sourceService, String sourceEventId, String traceId,
                                 String paymentId, String participantId, String reference, String payloadJson) {
        var timerSample = auditMetrics.startTimer();

        // Idempotency: a redelivered Kafka message with the same
        // sourceEventId must never create a second audit row (see
        // AuditEvent's javadoc on append-only guarantees).
        if (sourceEventId != null && auditEventRepository.existsBySourceEventId(sourceEventId)) {
            log.info("Skipping duplicate Kafka-sourced audit event sourceEventId={}", sourceEventId);
            auditMetrics.recordDuplicateSkipped(sourceService);
            return;
        }

        AuditEvent event = AuditEvent.builder()
                .eventType(eventType)
                .eventStatus(EventStatus.RECORDED)
                .sourceService(sourceService)
                .sourceEventId(sourceEventId)
                .metadata(new AuditMetadata(null, "SYSTEM", traceId, traceId, paymentId, participantId, reference))
                .payload(payloadJson)
                .occurredAt(OffsetDateTime.now())
                .build();

        AuditEvent saved = auditEventRepository.save(event);
        auditMetrics.recordWrite(timerSample, eventType.name());
        applicationEventPublisher.publishEvent(new AuditCompletionApplicationEvent(this, saved.getId(), eventType.name(), sourceService, traceId));
        log.info("Kafka-sourced audit event recorded eventType={} sourceService={} sourceEventId={}", eventType, sourceService, sourceEventId);
    }

    @Override
    @Transactional(readOnly = true)
    public AuditEventResponse getById(UUID id) {
        return auditEventMapper.toResponse(auditEventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AuditEvent", id.toString())));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuditEventResponse> search(AuditSearchCriteria criteria, int page, int size) {
        var timerSample = auditMetrics.startTimer();

        var cached = auditSearchCacheService.get(criteria, page, size);
        if (cached.isPresent()) {
            auditMetrics.recordCacheHit();
            auditMetrics.recordSearch(timerSample);
            return cached.get();
        }
        auditMetrics.recordCacheMiss();

        Specification<AuditEvent> spec = Specification
                .allOf(AuditEventSpecifications.hasCorrelationId(criteria.correlationId()))
                .and(AuditEventSpecifications.hasPaymentId(criteria.paymentId()))
                .and(AuditEventSpecifications.hasParticipantId(criteria.participantId()))
                .and(AuditEventSpecifications.hasReference(criteria.reference()))
                .and(AuditEventSpecifications.hasStatus(criteria.status()))
                .and(AuditEventSpecifications.hasEventType(criteria.eventType()))
                .and(AuditEventSpecifications.occurredAfter(criteria.fromDate()))
                .and(AuditEventSpecifications.occurredBefore(criteria.toDate()));

        var pageResult = auditEventRepository.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt")));

        PageResponse<AuditEventResponse> response = PageResponse.of(
                pageResult.getContent().stream().map(auditEventMapper::toResponse).toList(),
                pageResult.getNumber(), pageResult.getSize(), pageResult.getTotalElements());

        auditSearchCacheService.put(criteria, page, size, response);
        auditMetrics.recordSearch(timerSample);
        return response;
    }
}
