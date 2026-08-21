package com.paymentx.reporting.service;

import com.paymentx.common.dto.PageResponse;
import com.paymentx.reporting.dto.GenerateReportRequest;
import com.paymentx.reporting.dto.ReportExecutionResponse;
import com.paymentx.reporting.dto.ReportResponse;
import com.paymentx.reporting.dto.ReportResultResponse;
import com.paymentx.reporting.dto.ReportSearchCriteria;
import com.paymentx.reporting.dto.ScheduleReportRequest;
import com.paymentx.reporting.entity.ReportFormat;

import java.util.List;
import java.util.UUID;

/**
 * ====================================================================
 * ENGLISH: The application-facing report orchestration contract -
 * every REST endpoint and the scheduler go through this interface,
 * never touching repositories or generators directly.
 *
 * HINGLISH: Application-facing report orchestration contract - har REST
 * endpoint aur scheduler isی interface ke through jaate hain,
 * repositories ya generators ko seedhe kabhi nahi chhoote.
 * ====================================================================
 */
public interface ReportingService {

    List<ReportResponse> listReports();

    /** Synchronous kickoff, async execution - returns immediately with
     *  a PENDING execution while actual generation runs on a dedicated
     *  executor. */
    ReportExecutionResponse generateReport(GenerateReportRequest request, String requestedBy, String correlationId);

    ReportExecutionResponse getExecutionStatus(UUID executionId);

    ReportResultResponse getResult(UUID executionId);

    PageResponse<ReportExecutionResponse> searchExecutions(ReportSearchCriteria criteria, int page, int size);

    /** Streams the exported file's bytes for download - admin-triggered
     *  export-on-demand if no export in that format exists yet. */
    byte[] downloadReport(UUID executionId, ReportFormat format);

    UUID scheduleReport(ScheduleReportRequest request, String createdBy);

    void cancelReport(UUID executionId);

    void deleteReport(UUID executionId);
}
