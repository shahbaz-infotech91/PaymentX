package com.paymentx.reporting.repository;

import com.paymentx.reporting.entity.ReportExecution;
import com.paymentx.reporting.entity.ReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

/**
 * ====================================================================
 * ENGLISH: Spring Data repository for ReportExecution. The
 * findByStatus lookup backs the "Retry Failed Reports" scheduler job -
 * it needs every FAILED execution, not one at a time, to avoid N+1
 * queries when retrying a batch of failures.
 *
 * HINGLISH: ReportExecution ke liye Spring Data repository.
 * findByStatus lookup "Retry Failed Reports" scheduler job ko support
 * karta hai - use har FAILED execution chahiye, ek-ek karke nahi, taaki
 * failures ka batch retry karte waqt N+1 queries na ho.
 * ====================================================================
 */
public interface ReportExecutionRepository extends JpaRepository<ReportExecution, UUID>, JpaSpecificationExecutor<ReportExecution> {
    List<ReportExecution> findByStatus(ReportStatus status);

    /** Batch-fetch executions for a page of ReportRequest ids in ONE
     *  query - used by ReportingServiceImpl.searchExecutions() to avoid
     *  the N+1 (or worse, full-table-scan-per-page) anti-pattern the
     *  explicit "No N+1"/"Efficient SQL" requirement forbids. */
    List<ReportExecution> findByReportRequestIdIn(List<UUID> reportRequestIds);
}
