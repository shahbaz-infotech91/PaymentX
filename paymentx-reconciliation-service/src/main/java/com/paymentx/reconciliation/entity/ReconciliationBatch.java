package com.paymentx.reconciliation.entity;

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
import java.util.UUID;

/**
 * One run of the reconciliation engine - either against a specific
 * uploaded SettlementFile (settlementFileId set) or against a date-range
 * window with no file (incremental/full reconciliation driven purely by
 * internal Payment Service events already consumed - settlementFileId
 * null in that case).
 */
@Entity
@Table(name = "reconciliation_batch")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * ReconciliationBatch is a JPA entity in the reconciliation module of PaymentX. It lives in package com.paymentx.reconciliation.entity and participates in reconciliation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through reconciliation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * ReconciliationBatch PaymentX ke reconciliation module ka ek JPA entity hai. Ye com.paymentx.reconciliation.entity package me hai aur reconciliation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise reconciliation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class ReconciliationBatch extends AuditableEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "batch_type", nullable = false, length = 16)
    private BatchType batchType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private BatchStatus status;

    @Column(name = "settlement_file_id")
    private UUID settlementFileId;

    @Column(name = "window_from")
    private OffsetDateTime windowFrom;

    @Column(name = "window_to")
    private OffsetDateTime windowTo;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "total_records", nullable = false)
    @Builder.Default
    private Integer totalRecords = 0;

    @Column(name = "matched_count", nullable = false)
    @Builder.Default
    private Integer matchedCount = 0;

    @Column(name = "mismatch_count", nullable = false)
    @Builder.Default
    private Integer mismatchCount = 0;

    @Column(name = "triggered_by", length = 64)
    private String triggeredBy;

    @Column(name = "failure_reason", length = 1024)
    private String failureReason;
}
