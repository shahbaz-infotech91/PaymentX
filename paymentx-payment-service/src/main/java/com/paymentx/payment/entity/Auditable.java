package com.paymentx.payment.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;

/**
 * Extended by every MUTABLE entity in this service (Payment, PaymentRetry,
 * PaymentSettlement, PaymentOutbox). NOT extended by the two immutable log
 * tables (PaymentStatusHistory, PaymentAudit) - see their own class-level
 * comments for why they carry a lighter, append-only-appropriate set of
 * columns instead.
 *
 * @Version is Hibernate's optimistic locking mechanism: on every UPDATE,
 * Hibernate appends "AND version = ?" to the WHERE clause and checks the
 * affected row count. If another transaction updated (and incremented)
 * this row first, the affected count is 0 and Hibernate throws
 * OptimisticLockException - which service.impl will catch and translate
 * into an explicit retry (Part 4), rather than silently overwriting a
 * concurrent change.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * Auditable is a JPA entity in the payment module of PaymentX. It lives in package com.paymentx.payment.entity and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * Auditable PaymentX ke payment module ka ek JPA entity hai. Ye com.paymentx.payment.entity package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public abstract class Auditable {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false, length = 64)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "updated_by", nullable = false, length = 64)
    private String updatedBy;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
