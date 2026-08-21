package com.paymentx.reporting.service.exporter;

import com.paymentx.reporting.entity.ReportFormat;

import java.io.OutputStream;

/**
 * ====================================================================
 * ENGLISH:
 * The Strategy contract every export format implements - matching
 * SettlementFileImporter's exact pattern in reverse (that reads external
 * formats INTO the platform; this writes report data OUT to a format).
 * WHY OutputStream, not "return a byte[]": the explicit "Streaming
 * download" and "Large report support" requirements mean a multi-
 * million-row report must be written incrementally to the destination
 * rather than fully buffered in memory as one big array first.
 *
 * HINGLISH:
 * Ye wo Strategy contract hai jise har export format implement karta
 * hai - SettlementFileImporter ke exact pattern jaisa, ulta. WHY
 * OutputStream, "byte[] return karo" nahi: explicit "Streaming
 * download" aur "Large report support" requirements ka matlab hai ki
 * lakho-rows wala report destination me incrementally likha jaana
 * chahiye, pehle ek bade array me poora memory me buffer kiye bina.
 * ====================================================================
 */
public interface ReportExporter {

    ReportFormat getSupportedFormat();

    /** WHY the input is a raw JSON string, not a typed object: every
     *  report type has a different shape - this exporter parses
     *  whatever JSON structure arrives and renders it generically, the
     *  same way a real BI export tool would handle heterogeneous report
     *  schemas without a hardcoded model per report type. */
    void export(String resultDataJson, OutputStream outputStream) throws ReportExportException;
}
