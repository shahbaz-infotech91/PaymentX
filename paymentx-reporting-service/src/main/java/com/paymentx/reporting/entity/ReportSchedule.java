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

import java.time.OffsetDateTime;

/**
 * ====================================================================
 * ENGLISH:
 * A recurring report configuration - "generate the Payment Summary
 * report every day at 2am" - read by ReportScheduler on each cron tick
 * to decide which reports are due to run. Separate from ReportRequest
 * (a single, already-fired request) because a schedule is standing
 * configuration that fires MANY requests over time, one per tick it's
 * due.
 *
 * HINGLISH:
 * Ye ek recurring report ka configuration hai - "Payment Summary
 * report roz subah 2 baje generate karo" - ReportScheduler har cron
 * tick pe ise padhta hai ye decide karne ke liye ki kaunse reports ab
 * chalne wale hain. ReportRequest se alag hai kyunki ek schedule ek
 * standing configuration hai jo time ke saath MANY requests fire karta
 * hai, jab bhi due hota hai.
 * ====================================================================
 */
@Entity
@Table(name = "report_schedule")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ReportSchedule extends AuditableEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 32)
    private ReportType reportType;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_format", nullable = false, length = 16)
    private ReportFormat reportFormat;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false, length = 16)
    private ReportFrequency frequency;

    @Column(name = "cron_expression", nullable = false, length = 64)
    private String cronExpression;

    @Column(name = "active", nullable = false)
    private Boolean active;

    @Column(name = "last_run_at")
    private OffsetDateTime lastRunAt;

    @Column(name = "next_run_at")
    private OffsetDateTime nextRunAt;

    @Column(name = "created_by_user", length = 64)
    private String createdByUser;
}
