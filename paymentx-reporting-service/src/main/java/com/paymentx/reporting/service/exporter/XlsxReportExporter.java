package com.paymentx.reporting.service.exporter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentx.reporting.entity.ReportFormat;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.OutputStream;

/**
 * ====================================================================
 * ENGLISH: Excel (XLSX) export - uses Apache POI's SXSSFWorkbook
 * (streaming variant), NOT the plain XSSFWorkbook, specifically because
 * the explicit "Large report support" / "Streaming" requirements
 * demand it: XSSFWorkbook keeps every row in memory until the whole
 * file is written; SXSSFWorkbook flushes rows to a temp file as they're
 * written, keeping only a configurable window of rows (100 here) in
 * heap at once regardless of total report size.
 *
 * HINGLISH: Excel (XLSX) export - Apache POI ka SXSSFWorkbook
 * (streaming variant) use karta hai, plain XSSFWorkbook nahi, specifically
 * isliye kyunki explicit "Large report support" / "Streaming"
 * requirements yehi maangte hain: SXSSFWorkbook rows ko temp file me
 * flush karta jaata hai jaise-jaise likhte hain, sirf ek configurable
 * window ke rows heap me rakhta hai, chahe report kitna bhi bada ho.
 * ====================================================================
 */
@Component
public class XlsxReportExporter implements ReportExporter {

    private static final int STREAMING_WINDOW_ROWS = 100;

    private final ObjectMapper objectMapper;
    private final ReportDataFlattener flattener;

    public XlsxReportExporter(ObjectMapper objectMapper, ReportDataFlattener flattener) {
        this.objectMapper = objectMapper;
        this.flattener = flattener;
    }

    @Override
    public ReportFormat getSupportedFormat() {
        return ReportFormat.XLSX;
    }

    @Override
    public void export(String resultDataJson, OutputStream outputStream) throws ReportExportException {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(STREAMING_WINDOW_ROWS)) {
            SXSSFSheet sheet = workbook.createSheet("Report");

            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Section");
            header.createCell(1).setCellValue("Field");
            header.createCell(2).setCellValue("Value");

            var root = objectMapper.readTree(resultDataJson);
            int rowIndex = 1;
            for (var flatRow : flattener.flatten(root)) {
                Row row = sheet.createRow(rowIndex++);
                setCell(row, 0, flatRow.section());
                setCell(row, 1, flatRow.field());
                setCell(row, 2, flatRow.value());
            }

            workbook.write(outputStream);
            // WHY dispose() is required (not just close()): SXSSFWorkbook
            // writes intermediate rows to temp files on disk as it
            // streams - dispose() deletes those temp files after the
            // final write completes. Skipping this leaks temp files on
            // every single export.
            workbook.dispose();
        } catch (Exception e) {
            throw new ReportExportException("Failed to export report as XLSX: " + e.getMessage(), e);
        }
    }

    private void setCell(Row row, int index, String value) {
        Cell cell = row.createCell(index);
        cell.setCellValue(value != null ? value : "");
    }
}
