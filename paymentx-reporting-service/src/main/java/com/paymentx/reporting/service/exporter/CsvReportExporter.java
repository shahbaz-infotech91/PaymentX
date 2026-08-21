package com.paymentx.reporting.service.exporter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVWriter;
import com.paymentx.reporting.entity.ReportFormat;
import org.springframework.stereotype.Component;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/**
 * ====================================================================
 * ENGLISH: CSV export - writes each flattened (section, field, value)
 * row via OpenCSV's streaming CSVWriter, the same library already
 * verified and used by Reconciliation Service's settlement-file
 * importer.
 *
 * HINGLISH: CSV export - har flattened (section, field, value) row ko
 * OpenCSV ke streaming CSVWriter se likhta hai, wahi library jo pehle
 * se verify hui aur Reconciliation Service ke settlement-file importer
 * me use hoti hai.
 * ====================================================================
 */
@Component
public class CsvReportExporter implements ReportExporter {

    private final ObjectMapper objectMapper;
    private final ReportDataFlattener flattener;

    public CsvReportExporter(ObjectMapper objectMapper, ReportDataFlattener flattener) {
        this.objectMapper = objectMapper;
        this.flattener = flattener;
    }

    @Override
    public ReportFormat getSupportedFormat() {
        return ReportFormat.CSV;
    }

    @Override
    public void export(String resultDataJson, OutputStream outputStream) throws ReportExportException {
        try (CSVWriter writer = new CSVWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {
            writer.writeNext(new String[]{"Section", "Field", "Value"});
            var root = objectMapper.readTree(resultDataJson);
            for (var row : flattener.flatten(root)) {
                writer.writeNext(new String[]{row.section(), row.field(), row.value()});
            }
        } catch (Exception e) {
            throw new ReportExportException("Failed to export report as CSV: " + e.getMessage(), e);
        }
    }
}
