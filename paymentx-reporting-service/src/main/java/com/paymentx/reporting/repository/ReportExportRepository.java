package com.paymentx.reporting.repository;

import com.paymentx.reporting.entity.ReportExport;
import com.paymentx.reporting.entity.ReportFormat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * ====================================================================
 * ENGLISH: Spring Data repository for ReportExport - the download
 * endpoint uses findByReportExecutionIdAndFormat to locate an already-
 * exported file before regenerating it.
 *
 * HINGLISH: ReportExport ke liye Spring Data repository - download
 * endpoint findByReportExecutionIdAndFormat use karta hai ye check
 * karne ke liye ki file already exported hai ya nahi, dobara generate
 * karne se pehle.
 * ====================================================================
 */
public interface ReportExportRepository extends JpaRepository<ReportExport, UUID> {
    Optional<ReportExport> findByReportExecutionIdAndFormat(UUID reportExecutionId, ReportFormat format);

    List<ReportExport> findByReportExecutionId(UUID reportExecutionId);
}
