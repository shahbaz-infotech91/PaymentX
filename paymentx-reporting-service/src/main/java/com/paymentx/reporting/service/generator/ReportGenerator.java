package com.paymentx.reporting.service.generator;

import com.paymentx.reporting.entity.ReportRequest;
import com.paymentx.reporting.entity.ReportType;

/**
 * ====================================================================
 * ENGLISH:
 * The Strategy contract every one of the 11 report types implements -
 * matching Notification Service's NotificationChannelHandler and
 * Reconciliation Service's SettlementFileImporter Strategy pattern
 * exactly. ReportGeneratorFactory auto-discovers every implementation
 * via Spring's component scan - adding a 12th report type later means
 * writing one new @Component, zero changes anywhere else.
 *
 * HINGLISH:
 * Ye wo Strategy contract hai jise har ek 11 report types implement
 * karte hain - bilkul Notification/Reconciliation Service ke Strategy
 * pattern jaisa. ReportGeneratorFactory Spring ke component scan se har
 * implementation khud dhoondh leta hai - 12th report type add karne ke
 * liye bas ek naya @Component likhna hoga.
 * ====================================================================
 */
public interface ReportGenerator {

    ReportType getSupportedType();

    /** Returns the generated report data serialized as a JSON string -
     *  ReportResult.resultData stores exactly this, and ReportExporter
     *  implementations read it back to produce CSV/XLSX/PDF/JSON files. */
    String generate(ReportRequest request);
}
