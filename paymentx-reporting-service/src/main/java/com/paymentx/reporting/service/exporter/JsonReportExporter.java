package com.paymentx.reporting.service.exporter;

import com.paymentx.reporting.entity.ReportFormat;
import org.springframework.stereotype.Component;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * ====================================================================
 * ENGLISH: JSON export - the simplest exporter, since ReportResult's
 * data is already stored as JSON. Writes it through unchanged, still
 * via a stream for consistency with the other 3 exporters' streaming
 * discipline.
 *
 * HINGLISH: JSON export - sabse simple exporter, kyunki ReportResult
 * ka data pehle se hi JSON ke roop me stored hai. Ise bina badle
 * likhta hai, phir bhi stream ke through, baaki 3 exporters ke
 * streaming discipline ke saath consistent rehne ke liye.
 * ====================================================================
 */
@Component
public class JsonReportExporter implements ReportExporter {

    @Override
    public ReportFormat getSupportedFormat() {
        return ReportFormat.JSON;
    }

    @Override
    public void export(String resultDataJson, OutputStream outputStream) throws ReportExportException {
        try {
            outputStream.write(resultDataJson.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new ReportExportException("Failed to export report as JSON: " + e.getMessage(), e);
        }
    }
}
