package com.paymentx.reporting.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * ====================================================================
 * ENGLISH: The actual generated report content, wrapped with its
 * provenance (which services it came from, how fresh) - what a caller
 * gets from "view report result" (as opposed to downloading an exported
 * file).
 *
 * HINGLISH: Actual generated report content, uski provenance ke saath
 * (kahan se aaya, kitna fresh) - "view report result" se caller ko yehi
 * milta hai (exported file download karne se alag).
 * ====================================================================
 */
public record ReportResultResponse(
        UUID reportExecutionId,
        String resultData,
        String sourceServices,
        OffsetDateTime dataAsOf
) {
}
