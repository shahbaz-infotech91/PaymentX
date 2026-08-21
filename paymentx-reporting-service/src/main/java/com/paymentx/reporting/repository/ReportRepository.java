package com.paymentx.reporting.repository;

import com.paymentx.reporting.entity.Report;
import com.paymentx.reporting.entity.ReportType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * ====================================================================
 * ENGLISH: Spring Data repository for the Report catalog table. Backs
 * the REST API's "list reports" endpoint and the validation check that
 * a requested reportType is real before generation starts.
 *
 * HINGLISH: Report catalog table ke liye Spring Data repository. REST
 * API ke "list reports" endpoint ko aur is check ko support karta hai
 * ki requested reportType generation shuru hone se pehle real hai.
 * ====================================================================
 */
public interface ReportRepository extends JpaRepository<Report, UUID> {
    Optional<Report> findByReportType(ReportType reportType);
}
