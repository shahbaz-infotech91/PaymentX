package com.paymentx.notification.entity;

import com.paymentx.common.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * WHY BaseEntity (id only), not AuditableEntity: a delivery attempt is
 * itself an immutable historical fact the instant it's written (an
 * attempt that happened at 10:03:12 with a specific outcome never
 * changes retroactively) - createdBy/updatedBy/version on every attempt
 * row would be pure overhead for a table that functions as
 * Notification's audit log, not as a mutable resource of its own.
 * attemptedAt (below) is this row's one meaningful timestamp.
 */
@Entity
@Table(name = "delivery_attempt")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * DeliveryAttempt is a JPA entity in the notification module of PaymentX. It lives in package com.paymentx.notification.entity and participates in notification's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through notification's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * DeliveryAttempt PaymentX ke notification module ka ek JPA entity hai. Ye com.paymentx.notification.entity package me hai aur notification ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise notification ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class DeliveryAttempt extends BaseEntity {

    @Column(name = "notification_id", nullable = false)
    private UUID notificationId;

    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private NotificationStatus status;

    @Column(name = "error_message", length = 1024)
    private String errorMessage;

    @Column(name = "response_code")
    private Integer responseCode;

    @Column(name = "attempted_at", nullable = false)
    private OffsetDateTime attemptedAt;
}
