package com.paymentx.audit.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

/**
 * WHY an internal Spring ApplicationEvent rather than calling
 * AuditCompletionProducer directly from AuditServiceImpl: publishing
 * straight to Kafka from inside a @Transactional method means the
 * message goes out even if the surrounding transaction later rolls
 * back. Raising this event instead, consumed via
 * @TransactionalEventListener(phase = AFTER_COMMIT) in
 * AuditCompletionEventListener, guarantees "publish only after
 * successful DB commit" - the explicit, mandatory requirement for this
 * service's Kafka integration.
 */
@Getter
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * AuditCompletionApplicationEvent is a class in the audit module of PaymentX. It lives in package com.paymentx.audit.event and participates in audit's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through audit's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * AuditCompletionApplicationEvent PaymentX ke audit module ka ek class hai. Ye com.paymentx.audit.event package me hai aur audit ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise audit ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class AuditCompletionApplicationEvent extends ApplicationEvent {

    private final UUID auditEventId;
    private final String eventType;
    private final String sourceService;
    private final String traceId;

    public AuditCompletionApplicationEvent(Object source, UUID auditEventId, String eventType, String sourceService, String traceId) {
        super(source);
        this.auditEventId = auditEventId;
        this.eventType = eventType;
        this.sourceService = sourceService;
        this.traceId = traceId;
    }
}
