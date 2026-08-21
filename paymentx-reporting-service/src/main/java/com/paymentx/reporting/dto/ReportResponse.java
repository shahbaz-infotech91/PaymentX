package com.paymentx.reporting.dto;

import com.paymentx.reporting.entity.ReportType;

import java.util.UUID;

/**
 * ====================================================================
 * ENGLISH: What the "list reports" endpoint returns - one catalog
 * entry per supported report type.
 *
 * HINGLISH: "list reports" endpoint ka response - har supported report
 * type ke liye ek catalog entry.
 * ====================================================================
 */
public record ReportResponse(
        UUID id,
        ReportType reportType,
        String displayName,
        String description,
        Boolean active
) {
}
