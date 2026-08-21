package com.paymentx.reporting.repository;

import com.paymentx.reporting.entity.ReportResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * ====================================================================
 * ENGLISH: Spring Data repository for ReportResult - the generated
 * report data lookup by execution id.
 *
 * HINGLISH: ReportResult ke liye Spring Data repository - execution id
 * se generated report data dhoondhne ke liye.
 * ====================================================================
 */
public interface ReportResultRepository extends JpaRepository<ReportResult, UUID> {
    Optional<ReportResult> findByReportExecutionId(UUID reportExecutionId);
}
