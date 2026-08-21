package com.paymentx.reporting.service.exporter;

import com.paymentx.reporting.entity.ReportFormat;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * ====================================================================
 * ENGLISH: Matches ReportGeneratorFactory's exact auto-discovery
 * pattern for the export side - adding a 5th export format later needs
 * only a new @Component implementing ReportExporter.
 *
 * HINGLISH: ReportGeneratorFactory ke exact auto-discovery pattern
 * jaisa, export side ke liye - 5th export format add karne ke liye bas
 * ek naya @Component chahiye jo ReportExporter implement kare.
 * ====================================================================
 */
@Component
public class ReportExporterFactory {

    private final Map<ReportFormat, ReportExporter> exporters;

    public ReportExporterFactory(List<ReportExporter> reportExporters) {
        this.exporters = new EnumMap<>(ReportFormat.class);
        for (ReportExporter exporter : reportExporters) {
            exporters.put(exporter.getSupportedFormat(), exporter);
        }
    }

    public Optional<ReportExporter> getExporter(ReportFormat format) {
        return Optional.ofNullable(exporters.get(format));
    }
}
