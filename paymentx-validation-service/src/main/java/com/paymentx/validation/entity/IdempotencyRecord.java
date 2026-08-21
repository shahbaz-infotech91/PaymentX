package com.paymentx.validation.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * The idempotency guard. See the extensive comment on the V1_0_5 Liquibase
 * changeset for why paymentReference is UNIQUE and why that constraint -
 * not application code - is what makes duplicate detection race-safe.
 */
@Entity
@Table(name = "idempotency_record")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * IdempotencyRecord is a JPA entity in the validation module of PaymentX. It lives in package com.paymentx.validation.entity and participates in validation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through validation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * IdempotencyRecord PaymentX ke validation module ka ek JPA entity hai. Ye com.paymentx.validation.entity package me hai aur validation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise validation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_reference", nullable = false, unique = true, length = 128)
    private String paymentReference;

    @Column(name = "trace_id", nullable = false, length = 64)
    private String traceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scheme", nullable = false, length = 16)
    private Scheme scheme;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_status", nullable = false, length = 16)
    private ValidationStatus resultStatus;

    @Column(name = "first_seen_at", nullable = false, updatable = false)
    private OffsetDateTime firstSeenAt;
}
