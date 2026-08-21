package com.paymentx.reporting.entity;

/**
 * ====================================================================
 * ENGLISH:
 * The lifecycle states of a report execution - from queued, to running,
 * to a final outcome. ReportScheduler and ReportGenerationService both
 * drive a ReportExecution row through these states as generation
 * proceeds, and the REST API surfaces this status so a caller can poll
 * "is my report ready yet."
 *
 * HINGLISH:
 * Ye ek report-execution ki lifecycle states hain - queue hone se
 * lekar chalne tak, aur phir final result tak. ReportScheduler aur
 * ReportGenerationService dono ek ReportExecution row ko in states se
 * hoke le jaate hain jab report generate ho raha hota hai, aur REST
 * API se caller ye status check kar sakta hai ki "mera report ready
 * hua ya nahi."
 * ====================================================================
 */
public enum ReportStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}
