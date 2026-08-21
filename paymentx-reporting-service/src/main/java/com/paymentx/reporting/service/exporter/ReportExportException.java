package com.paymentx.reporting.service.exporter;

/**
 * ====================================================================
 * ENGLISH: Checked exception for export failures - a broken PDF/XLSX/CSV
 * write must be reported clearly, not silently produce a corrupt file.
 *
 * HINGLISH: Export failures ke liye checked exception - agar
 * PDF/XLSX/CSV likhna fail ho, to ye clearly report hona chahiye, chup-
 * chaap koi corrupt file nahi banni chahiye.
 * ====================================================================
 */
public class ReportExportException extends Exception {
    public ReportExportException(String message, Throwable cause) {
        super(message, cause);
    }
}
