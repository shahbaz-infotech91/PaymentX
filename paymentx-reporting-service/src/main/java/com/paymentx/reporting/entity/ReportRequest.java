package com.paymentx.reporting.entity;

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

import java.time.OffsetDateTime;

/**
 * ====================================================================
 * ENGLISH:
 * One caller's request to generate a report - WHAT report, for WHICH
 * date range/filters, in WHICH format, and WHO asked for it. This is
 * the input; ReportExecution tracks the actual work of fulfilling it.
 * Splitting request from execution mirrors Notification Service's
 * split between "what should be sent" and "what actually happened when
 * we tried" (DeliveryAttempt) - the same request-vs-attempt separation
 * applied here.
 *
 * HINGLISH:
 * Ye ek caller ki report-generate-karne-ki request hai - KAUNSA report,
 * KAUNSI date-range/filters ke saath, KAUNSE format me, aur KISNE
 * maanga. Ye input hai; ReportExecution actual kaam track karta hai
 * jab ye request poori ki jaati hai. Request aur execution ko alag
 * rakhna Notification Service ke pattern jaisa hai - wahi separation
 * yahan bhi.
 * ====================================================================
 */
@Entity
@Table(name = "report_request")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ReportRequest extends AuditableEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 32)
    private ReportType reportType;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_format", nullable = false, length = 16)
    private ReportFormat reportFormat;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false, length = 16)
    private ReportFrequency frequency;

    @Column(name = "date_from")
    private OffsetDateTime dateFrom;

    @Column(name = "date_to")
    private OffsetDateTime dateTo;

    @Column(name = "participant_id", length = 64)
    private String participantId;

    @Column(name = "currency", length = 3)
    private String currency;

    /** WHY a JSON column, not a fixed set of filter columns: the
     *  explicit search filters (status, amount, reference,
     *  correlationId, ...) vary by report type - a fixed-column schema
     *  would need a different table per report type. One flexible
     *  filters column keeps ReportRequest generic across all 11 report
     *  types while still keeping the universally-useful filters
     *  (dateFrom/dateTo/participantId/currency above) as real, indexed
     *  columns. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "additional_filters", columnDefinition = "jsonb")
    private String additionalFilters;

    @Column(name = "requested_by", nullable = false, length = 64)
    private String requestedBy;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;
}
