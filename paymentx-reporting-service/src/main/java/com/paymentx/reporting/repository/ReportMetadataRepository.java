package com.paymentx.reporting.repository;

import com.paymentx.reporting.entity.ReportMetadata;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * ====================================================================
 * ENGLISH: Spring Data repository for ReportMetadata - provenance
 * lookup by execution id.
 *
 * HINGLISH: ReportMetadata ke liye Spring Data repository - execution
 * id se provenance-info dhoondhne ke liye.
 * ====================================================================
 */
public interface ReportMetadataRepository extends JpaRepository<ReportMetadata, UUID> {
    Optional<ReportMetadata> findByReportExecutionId(UUID reportExecutionId);
}
