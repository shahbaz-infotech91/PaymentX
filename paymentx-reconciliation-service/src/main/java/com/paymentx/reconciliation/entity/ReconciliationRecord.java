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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One row per compared transaction - internal side (from consumed
 * Payment Service events) vs external side (from an imported
 * SettlementFile row, when one exists). Either side can be null: a
 * MISSING result has an internal side with no external match; an ORPHAN/
 * UNEXPECTED_SETTLEMENT result has an external side with no internal
 * match - see MatchingEngine for how each ReconciliationStatus maps to
 * which side(s) are populated.
 */
@Entity
@Table(name = "reconciliation_record")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * ReconciliationRecord is a JPA entity in the reconciliation module of PaymentX. It lives in package com.paymentx.reconciliation.entity and participates in reconciliation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through reconciliation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * ReconciliationRecord PaymentX ke reconciliation module ka ek JPA entity hai. Ye com.paymentx.reconciliation.entity package me hai aur reconciliation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise reconciliation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class ReconciliationRecord extends AuditableEntity {

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    @Column(name = "payment_id", length = 64)
    private String paymentId;

    @Column(name = "reference_id", length = 128)
    private String referenceId;

    @Column(name = "participant_id", length = 64)
    private String participantId;

    @Column(name = "internal_amount", precision = 19, scale = 4)
    private BigDecimal internalAmount;

    @Column(name = "external_amount", precision = 19, scale = 4)
    private BigDecimal externalAmount;

    @Column(name = "internal_currency", length = 3)
    private String internalCurrency;

    @Column(name = "external_currency", length = 3)
    private String externalCurrency;

    @Column(name = "internal_status", length = 32)
    private String internalStatus;

    @Column(name = "external_status", length = 32)
    private String externalStatus;

    @Column(name = "internal_settlement_date")
    private OffsetDateTime internalSettlementDate;

    @Column(name = "external_settlement_date")
    private OffsetDateTime externalSettlementDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "reconciliation_status", nullable = false, length = 24)
    private ReconciliationStatus reconciliationStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;
}
