package com.paymentx.reporting.entity;

/**
 * ====================================================================
 * ENGLISH:
 * The file formats a report can be exported to. Each value maps to one
 * ReportExporter implementation (Strategy pattern, mirroring
 * Notification Service's NotificationChannelHandler and Reconciliation
 * Service's SettlementFileImporter) - adding a new export format later
 * needs only a new @Component, no changes to the exporting code path.
 *
 * HINGLISH:
 * Ye wo file formats hain jinme ek report export ho sakta hai. Har
 * value ek ReportExporter implementation se juda hai (Strategy pattern,
 * bilkul Notification Service ke channel-handler aur Reconciliation
 * Service ke file-importer jaisa) - naya export format add karne ke
 * liye sirf ek naya @Component likhna hoga, exporting code me kuch
 * badalna nahi padega.
 * ====================================================================
 */
public enum ReportFormat {
    CSV,
    XLSX,
    PDF,
    JSON
}
