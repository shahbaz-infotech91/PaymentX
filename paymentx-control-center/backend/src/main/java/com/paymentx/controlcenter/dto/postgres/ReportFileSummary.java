package com.paymentx.controlcenter.dto.postgres;

import java.time.OffsetDateTime;

/**
 * ENGLISH: One real, downloadable report file - assembled from a real
 * join across report_export (the file itself: format, size, download
 * count), report_execution (its real status), report_request, and
 * report (report_type, display_name) in paymentx_reporting. fileName
 * is the bare filename extracted from report_export's real
 * storage_path (Path.getFileName()) - the exact name
 * GET /api/v1/files/download/{fileName} expects, since reporting-
 * service writes real export files into the same directory this
 * backend's SafeFileService is configured to serve from. Never the
 * full filesystem path (that stays server-side only).
 *
 * HINGLISH: Ek real, downloadable report file - paymentx_reporting me
 * report_export (khud file: format, size, download count),
 * report_execution (uska real status), report_request, aur report
 * (report_type, display_name) ke across ek real join se assemble kiya
 * gaya. fileName report_export ke real storage_path se extract kiya
 * gaya bare filename hai (Path.getFileName()) - exactly wahi naam jo
 * GET /api/v1/files/download/{fileName} expect karta hai, kyunki
 * reporting-service real export files usi directory me likhta hai
 * jahan se is backend ka SafeFileService serve karne ke liye
 * configured hai. Kabhi pura filesystem path nahi (wo hamesha
 * server-side hi rehta hai).
 */
public record ReportFileSummary(
        String exportId,
        String reportType,
        String displayName,
        String format,
        String executionStatus,
        Long fileSizeBytes,
        Integer downloadCount,
        String fileName,
        OffsetDateTime createdAt
) {
}
