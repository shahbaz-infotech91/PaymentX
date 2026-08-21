package com.paymentx.notification.entity;

import com.paymentx.common.base.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.OffsetDateTime;

/**
 * WHY retryCount/maxRetries live directly on this entity rather than
 * only being derivable from counting DeliveryAttempt rows: the
 * dispatcher's retry-eligibility check ("is this notification still
 * under its retry budget?") runs on every scheduler tick and every
 * consumer-triggered send - a direct integer comparison is O(1); doing
 * `COUNT(*) FROM delivery_attempt WHERE notification_id = ?` on that hot
 * path would be needless per-check query overhead for a value that only
 * changes once per attempt. DeliveryAttempt remains the source of truth
 * for the DETAILED history (error messages, response codes, timestamps
 * per attempt); retryCount here is a denormalized, always-in-sync
 * counter maintained by NotificationServiceImpl on every attempt.
 */
@Entity
@Table(name = "notification")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * Notification is a JPA entity in the notification module of PaymentX. It lives in package com.paymentx.notification.entity and participates in notification's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through notification's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * Notification PaymentX ke notification module ka ek JPA entity hai. Ye com.paymentx.notification.entity package me hai aur notification ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise notification ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class Notification extends AuditableEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "source_event_type", nullable = false, length = 32)
    private SourceEventType sourceEventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 16)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private NotificationStatus status;

    @Column(name = "recipient", nullable = false, length = 256)
    private String recipient;

    @Column(name = "subject", length = 256)
    private String subject;

    @Column(name = "body", columnDefinition = "text")
    private String body;

    @Column(name = "template_name", length = 128)
    private String templateName;

    /** Idempotency key - the originating Kafka message's eventId (see
     *  NotificationEventConsumer). A redelivered message must never
     *  produce a second Notification row. */
    @Column(name = "source_event_id", length = 64)
    private String sourceEventId;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "participant_id", length = 64)
    private String participantId;

    @Column(name = "payment_id", length = 64)
    private String paymentId;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "max_retries", nullable = false)
    private Integer maxRetries;

    @Column(name = "last_attempt_at")
    private OffsetDateTime lastAttemptAt;

    @Column(name = "next_retry_at")
    private OffsetDateTime nextRetryAt;

    @Column(name = "failure_reason", length = 1024)
    private String failureReason;
}
