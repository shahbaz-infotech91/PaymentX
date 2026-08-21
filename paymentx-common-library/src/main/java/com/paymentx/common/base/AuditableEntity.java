package com.paymentx.common.base;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
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
 * Extends {@link BaseEntity} with the standard audit column set:
 * createdAt/updatedAt/createdBy/updatedBy/version. Mirrors the pattern
 * already proven in Payment Service's {@code Auditable} mapped
 * superclass - see that class's javadoc for the full rationale on
 * {@code @Version} optimistic locking and why {@code @LastModifiedBy}
 * needs an {@code AuditorAware} bean wired in the consuming service
 * (each service configures its own - see Payment Service's
 * {@code JpaAuditingConfig} for the current interim {@code "SYSTEM"}
 * value pending real identity propagation from Auth Service).
 *
 * <p>Intended for services being built from this point forward
 * (Routing, Audit, Notification, Reconciliation, Reporting) - existing
 * entities in Payment Service are NOT migrated onto this class as part
 * of the library migration (see {@link BaseEntity}'s javadoc).
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * AuditableEntity is a JPA entity in the common module of PaymentX. It lives in package com.paymentx.common.base and participates in common's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through common's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * AuditableEntity PaymentX ke common module ka ek JPA entity hai. Ye com.paymentx.common.base package me hai aur common ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise common ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public abstract class AuditableEntity extends BaseEntity {

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
