package com.paymentx.reporting.entity;

import com.paymentx.common.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * ====================================================================
 * ENGLISH:
 * A flattened, locally-persisted copy of every consumed cross-service
 * Kafka event's aggregable fields (amount, currency, status,
 * participant, timestamp) - the shared substrate every ReportGenerator
 * queries via SQL aggregation (COUNT/SUM/GROUP BY). WHY this exists
 * rather than each of the 11 report generators querying other
 * services' own databases directly: Reporting Service does not have
 * (and per the platform's established cross-service data ownership
 * principle - see Reconciliation Service's
 * InternalTransactionCacheService javadoc - should not be given) direct
 * DB access to another service's schema. WHY Postgres, not only Redis:
 * reports need genuine SQL aggregation (GROUP BY currency, COUNT WHERE
 * status=FAILED, date-range SUM) over potentially months of history -
 * Redis key-value lookups are the wrong tool for that; a real
 * relational table with indexes is. This design also directly
 * satisfies the explicit "No duplicate logic" code-quality requirement.
 *
 * HINGLISH:
 * Ye har consumed cross-service Kafka event ke aggregable fields
 * (amount, currency, status, participant, timestamp) ka ek flattened,
 * locally-persisted copy hai - shared substrate jise har ReportGenerator
 * SQL aggregation (COUNT/SUM/GROUP BY) ke through query karta hai. WHY
 * ye hai, na ki har 11 report generators doosri services ke apne
 * databases seedhe query karein: Reporting Service ke paas doosri
 * service ke schema ka direct DB access nahi hai (aur platform ke
 * established cross-service data-ownership principle ke hisab se hona
 * bhi nahi chahiye). WHY Postgres, sirf Redis nahi: reports ko genuine
 * SQL aggregation chahiye mahino ke history pe - Redis key-value
 * lookups iske liye galat tool hai. Ye design explicit "No duplicate
 * logic" code-quality requirement bhi directly satisfy karta hai.
 * ====================================================================
 */
@Entity
@Table(name = "source_event")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class SourceEvent extends BaseEntity {

    @Column(name = "source_service", nullable = false, length = 64)
    private String sourceService;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "payment_id", length = 64)
    private String paymentId;

    @Column(name = "reference_id", length = 128)
    private String referenceId;

    @Column(name = "participant_id", length = 64)
    private String participantId;

    @Column(name = "amount", precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "status", length = 32)
    private String status;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "source_event_id", length = 64)
    private String sourceEventId;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;
}
