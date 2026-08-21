package com.paymentx.payment.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Field renamed from retryAttempt -> currentRetry, and nextRetryAt ->
 * nextRetryTime, purely to match the requested naming - semantics are
 * unchanged. maxRetry is a genuinely new field: without it, "how many
 * more times will this retry before giving up" was implicit knowledge
 * living in application config rather than being visible on the record
 * itself, making it harder to answer "why did this stop retrying" during
 * an incident without also checking application config at that point in
 * time (which may have since changed).
 */
@Entity
@Table(name = "payment_retry")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * PaymentRetry is a JPA entity in the payment module of PaymentX. It lives in package com.paymentx.payment.entity and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * PaymentRetry PaymentX ke payment module ka ek JPA entity hai. Ye com.paymentx.payment.entity package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class PaymentRetry extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "current_retry", nullable = false)
    private Integer currentRetry;

    @Column(name = "max_retry", nullable = false)
    private Integer maxRetry;

    @Column(name = "retry_reason", length = 512)
    private String retryReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RetryStatus status;

    @Column(name = "next_retry_time")
    private OffsetDateTime nextRetryTime;

    @Column(name = "last_attempted_at")
    private OffsetDateTime lastAttemptedAt;

    public boolean hasRetriesRemaining() {
        return currentRetry < maxRetry;
    }
}
