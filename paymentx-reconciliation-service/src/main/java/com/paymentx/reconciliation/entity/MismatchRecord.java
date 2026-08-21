package com.paymentx.reconciliation.entity;

import com.paymentx.common.base.AuditableEntity;
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
 * WHY this is a SEPARATE entity from ReconciliationRecord rather than
 * just filtering ReconciliationRecord by reconciliationStatus != MATCHED:
 * a mismatch needs its own mutable RESOLUTION workflow (resolved/
 * resolvedBy/resolvedAt/resolutionNotes) that a MATCHED
 * ReconciliationRecord never needs.
 */
@Entity
@Table(name = "mismatch_record")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * MismatchRecord is a JPA entity in the reconciliation module of PaymentX. It lives in package com.paymentx.reconciliation.entity and participates in reconciliation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through reconciliation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * MismatchRecord PaymentX ke reconciliation module ka ek JPA entity hai. Ye com.paymentx.reconciliation.entity package me hai aur reconciliation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise reconciliation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class MismatchRecord extends AuditableEntity {

    @Column(name = "reconciliation_record_id", nullable = false)
    private UUID reconciliationRecordId;

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "mismatch_type", nullable = false, length = 24)
    private ReconciliationStatus mismatchType;

    @Column(name = "description", length = 1024)
    private String description;

    @Column(name = "resolved", nullable = false)
    private Boolean resolved;

    @Column(name = "resolved_by", length = 64)
    private String resolvedBy;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Column(name = "resolution_notes", length = 1024)
    private String resolutionNotes;
}
