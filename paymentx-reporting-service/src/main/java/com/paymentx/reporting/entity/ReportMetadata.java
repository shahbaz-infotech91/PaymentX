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

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * ====================================================================
 * ENGLISH:
 * Metadata ABOUT a generated report, separate from the report's actual
 * DATA (ReportResult) - which source services were queried, how fresh
 * the underlying data was ("as of" timestamp), and how the numbers were
 * computed. This exists so a report consumer can answer "can I trust
 * these numbers" without that provenance information cluttering the
 * actual report rows in ReportResult.
 *
 * HINGLISH:
 * Ye ek generated report ke baare me metadata hai, uske actual DATA se
 * alag (jo ReportResult me hai) - kaunsi source services se data liya
 * gaya, underlying data kitna fresh tha ("as of" timestamp), aur
 * numbers kaise calculate hue. Ye isliye hai taaki report dekhne wala
 * ye jaan sake ki "in numbers pe bharosa kar sakta hoon kya" - bina ye
 * provenance-info ReportResult ki actual rows me mix kiye.
 * ====================================================================
 */
@Entity
@Table(name = "report_metadata")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ReportMetadata extends BaseEntity {

    @Column(name = "report_execution_id", nullable = false, unique = true)
    private UUID reportExecutionId;

    @Column(name = "source_services", length = 512)
    private String sourceServices;

    @Column(name = "data_as_of", nullable = false)
    private OffsetDateTime dataAsOf;

    @Column(name = "generator_version", length = 32)
    private String generatorVersion;
}
