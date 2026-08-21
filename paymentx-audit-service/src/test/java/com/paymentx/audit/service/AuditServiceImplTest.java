package com.paymentx.audit.service;

import com.paymentx.audit.cache.AuditSearchCacheService;
import com.paymentx.audit.dto.AuditEventRequest;
import com.paymentx.audit.dto.AuditEventResponse;
import com.paymentx.audit.entity.AuditEvent;
import com.paymentx.audit.entity.EventType;
import com.paymentx.audit.mapper.AuditEventMapper;
import com.paymentx.audit.metrics.AuditMetrics;
import com.paymentx.audit.repository.AuditEventRepository;
import com.paymentx.audit.service.impl.AuditServiceImpl;
import com.paymentx.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * AuditServiceImplTest is a JUnit test class in the audit module of PaymentX, package com.paymentx.audit.service. It is used within audit's internal request/data flow, exercising real behaviour against Testcontainers-backed infrastructure to catch regressions before deployment.
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * AuditServiceImplTest PaymentX ke audit module ka ek JUnit test class hai, package com.paymentx.audit.service me. Ye audit ke internal request/data flow me use hoti hai, Testcontainers-backed real infrastructure ke against actual behaviour test karke deployment se pehle regressions pakadti hai.
 * ====================================================================
 */
class AuditServiceImplTest {

    @Mock
    private AuditEventRepository auditEventRepository;
    @Mock
    private AuditEventMapper auditEventMapper;
    @Mock
    private AuditSearchCacheService auditSearchCacheService;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    private AuditMetrics auditMetrics;
    private AuditServiceImpl auditService;

    @BeforeEach
    void setUp() {
        auditMetrics = new AuditMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        auditService = new AuditServiceImpl(auditEventRepository, auditEventMapper, auditSearchCacheService, auditMetrics, applicationEventPublisher);
    }

    @Test
    void record_validRequest_savesAndReturnsResponse() {
        AuditEventRequest request = new AuditEventRequest(EventType.SECURITY_EVENT, "audit-service",
                "actor-1", "USER", "corr-1", "trace-1", null, null, null, "{\"detail\":\"login\"}");
        AuditEvent saved = AuditEvent.builder().eventType(EventType.SECURITY_EVENT).build();
        AuditEventResponse response = new AuditEventResponse(UUID.randomUUID(), EventType.SECURITY_EVENT, null,
                "audit-service", "actor-1", "USER", "corr-1", "trace-1", null, null, null, "{\"detail\":\"login\"}", null, null);

        when(auditEventRepository.save(any())).thenReturn(saved);
        when(auditEventMapper.toResponse(saved)).thenReturn(response);

        AuditEventResponse result = auditService.record(request);

        assertThat(result.eventType()).isEqualTo(EventType.SECURITY_EVENT);
        verify(auditEventRepository).save(any());
    }

    @Test
    void recordFromKafka_duplicateSourceEventId_skipsInsertAndDoesNotPublishEvent() {
        when(auditEventRepository.existsBySourceEventId("evt-1")).thenReturn(true);

        auditService.recordFromKafka(EventType.PAYMENT_COMPLETED, "payment-service", "evt-1", "trace-1",
                "pay-1", "BANK001", "ref-1", "{}");

        verify(auditEventRepository, never()).save(any());
        verify(applicationEventPublisher, never()).publishEvent(any());
    }

    @Test
    void recordFromKafka_newSourceEventId_savesAndPublishesCompletionEvent() {
        when(auditEventRepository.existsBySourceEventId("evt-2")).thenReturn(false);
        AuditEvent saved = AuditEvent.builder().eventType(EventType.PAYMENT_COMPLETED).build();
        when(auditEventRepository.save(any())).thenReturn(saved);

        auditService.recordFromKafka(EventType.PAYMENT_COMPLETED, "payment-service", "evt-2", "trace-2",
                "pay-2", "BANK002", "ref-2", "{}");

        verify(auditEventRepository).save(any());
        verify(applicationEventPublisher, times(1)).publishEvent(any());
    }

    @Test
    void getById_notFound_throwsResourceNotFound() {
        UUID id = UUID.randomUUID();
        when(auditEventRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> auditService.getById(id)).isInstanceOf(ResourceNotFoundException.class);
    }
}
