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

import java.util.UUID;

/**
 * ====================================================================
 * ENGLISH:
 * One exported FILE of a report in a specific format - tracks where it
 * lives on disk (mirroring Reconciliation Service's disk-based
 * settlement-file storage rather than storing large binary blobs in
 * Postgres) and how many times it has been downloaded. Separate from
 * ReportResult (the raw JSON data) because the SAME result can be
 * exported to CSV, XLSX, PDF, and JSON independently, each producing
 * its own ReportExport row.
 *
 * HINGLISH:
 * Ye ek report ki ek specific format me exported FILE hai - kahan disk
 * pe rakhi hai (Reconciliation Service ke disk-based settlement-file
 * storage jaisa hi) aur kitni baar download hui, ye track karta hai.
 * ReportResult (raw JSON data) se alag hai kyunki SAME result ko CSV,
 * XLSX, PDF, aur JSON me independently export kiya ja sakta hai, har
 * ek apna alag ReportExport row banata hai.
 * ====================================================================
 */
@Entity
@Table(name = "report_export")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ReportExport extends AuditableEntity {

    @Column(name = "report_execution_id", nullable = false)
    private UUID reportExecutionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "format", nullable = false, length = 16)
    private ReportFormat format;

    @Column(name = "storage_path", nullable = false, length = 512)
    private String storagePath;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "download_count", nullable = false)
    @Builder.Default
    private Integer downloadCount = 0;
}
