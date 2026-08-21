package com.paymentx.reporting.service.exporter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentx.reporting.entity.ReportFormat;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

import java.io.OutputStream;

/**
 * ====================================================================
 * ENGLISH: PDF export via Apache PDFBox 3.x. WHY
 * "new PDType1Font(Standard14Fonts.FontName.HELVETICA)", not the
 * simpler "PDType1Font.HELVETICA": PDFBox 3.0 removed the static font
 * constant fields that existed in 2.x - this constructor is the correct,
 * verified 3.x replacement.
 *
 * WHY a new page is added automatically when the current one fills up:
 * "Large report support" is explicit - a report with hundreds of rows
 * must paginate across multiple PDF pages rather than the exporter
 * failing/truncating once a page's vertical space runs out.
 *
 * HINGLISH: Apache PDFBox 3.x se PDF export. WHY
 * "new PDType1Font(Standard14Fonts.FontName.HELVETICA)", simpler
 * "PDType1Font.HELVETICA" nahi: PDFBox 3.0 ne wo static font constant
 * fields hata diye jo 2.x me the - ye constructor hi sahi, verified 3.x
 * replacement hai.
 *
 * WHY jab current page bhar jaaye to naya page automatically add hota
 * hai: "Large report support" explicit hai - sau-sau rows wale report
 * ko multiple PDF pages me paginate hona chahiye.
 * ====================================================================
 */
@Component
public class PdfReportExporter implements ReportExporter {

    private static final float MARGIN = 50f;
    private static final float LEADING = 16f;
    private static final float FONT_SIZE = 10f;

    private final ObjectMapper objectMapper;
    private final ReportDataFlattener flattener;

    public PdfReportExporter(ObjectMapper objectMapper, ReportDataFlattener flattener) {
        this.objectMapper = objectMapper;
        this.flattener = flattener;
    }

    @Override
    public ReportFormat getSupportedFormat() {
        return ReportFormat.PDF;
    }

    @Override
    public void export(String resultDataJson, OutputStream outputStream) throws ReportExportException {
        try (PDDocument document = new PDDocument()) {
            PDFont font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDFont boldFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

            var root = objectMapper.readTree(resultDataJson);
            var rows = flattener.flatten(root);

            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDPageContentStream contentStream = new PDPageContentStream(document, page);
            float y = page.getMediaBox().getHeight() - MARGIN;

            contentStream.beginText();
            contentStream.setFont(boldFont, 14);
            contentStream.newLineAtOffset(MARGIN, y);
            contentStream.showText("PaymentX Report");
            contentStream.endText();
            y -= LEADING * 2;

            contentStream.setFont(font, FONT_SIZE);

            for (var row : rows) {
                if (y < MARGIN) {
                    contentStream.close();
                    page = new PDPage(PDRectangle.A4);
                    document.addPage(page);
                    contentStream = new PDPageContentStream(document, page);
                    contentStream.setFont(font, FONT_SIZE);
                    y = page.getMediaBox().getHeight() - MARGIN;
                }

                String line = (row.section() != null ? row.section() : "") + " | "
                        + (row.field() != null ? row.field() : "") + " | "
                        + (row.value() != null ? row.value() : "");

                contentStream.beginText();
                contentStream.newLineAtOffset(MARGIN, y);
                contentStream.showText(line);
                contentStream.endText();
                y -= LEADING;
            }

            contentStream.close();
            document.save(outputStream);
        } catch (Exception e) {
            throw new ReportExportException("Failed to export report as PDF: " + e.getMessage(), e);
        }
    }
}
