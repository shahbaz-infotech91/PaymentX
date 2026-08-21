package com.paymentx.reporting.entity;

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
 * ====================================================================
 * ENGLISH:
 * One actual run of generating a report - tracks status, timing, and
 * retry bookkeeping. This is the row ReportGenerationService/
 * ReportScheduler drive through PENDING -> RUNNING -> COMPLETED/FAILED
 * as generation proceeds, and what the REST API's "get report status"
 * endpoint reads to answer "is my report done yet." Mirrors
 * Reconciliation Service's ReconciliationBatch (one row per run of a
 * long-running process) - the same pattern applied to reports.
 *
 * HINGLISH:
 * Ye ek report-generation ka actual run hai - status, timing, aur
 * retry-tracking rakhta hai. Ye wahi row hai jise
 * ReportGenerationService/ReportScheduler PENDING -> RUNNING ->
 * COMPLETED/FAILED se hoke le jaate hain jab report ban raha hota hai,
 * aur ise hi REST API ka "report ka status do" endpoint padhta hai
 * "mera report ban gaya kya" ka jawab dene ke liye. Reconciliation
 * Service ke ReconciliationBatch jaisa hi pattern - wahi idea reports
 * pe.
 * ====================================================================
 */
@Entity
@Table(name = "report_execution")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ReportExecution extends AuditableEntity {

    @Column(name = "report_request_id", nullable = false)
    private UUID reportRequestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ReportStatus status;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "row_count")
    private Integer rowCount;

    @Column(name = "generation_time_millis")
    private Long generationTimeMillis;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "failure_reason", length = 1024)
    private String failureReason;
}
