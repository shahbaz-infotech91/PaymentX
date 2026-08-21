package com.paymentx.reporting.repository;

import com.paymentx.reporting.entity.ReportRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

/**
 * ====================================================================
 * ENGLISH: Spring Data repository for ReportRequest, with
 * Specification support for the explicit multi-field search
 * requirement (date range, participant, status, currency, amount,
 * reference, correlationId).
 *
 * HINGLISH: ReportRequest ke liye Spring Data repository, Specification
 * support ke saath jo explicit multi-field search requirement ke liye
 * chahiye.
 * ====================================================================
 */
public interface ReportRequestRepository extends JpaRepository<ReportRequest, UUID>, JpaSpecificationExecutor<ReportRequest> {
}
