package com.paymentx.reporting.service.generator;

import com.paymentx.reporting.entity.ReportType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * ====================================================================
 * ENGLISH: Matches NotificationChannelFactory/SettlementFileImporterFactory's
 * exact auto-discovery pattern - Spring collects every @Component-
 * annotated ReportGenerator automatically. Adding a 12th report type
 * later needs only a new @Component, zero changes here.
 *
 * HINGLISH: NotificationChannelFactory/SettlementFileImporterFactory ke
 * exact auto-discovery pattern jaisa - Spring har @Component-annotated
 * ReportGenerator ko khud dhoondh leta hai. 12th report type add karne
 * ke liye bas ek naya @Component chahiye, yahan kuch nahi badalna.
 * ====================================================================
 */
@Component
public class ReportGeneratorFactory {

    private final Map<ReportType, ReportGenerator> generators;

    public ReportGeneratorFactory(List<ReportGenerator> reportGenerators) {
        this.generators = new EnumMap<>(ReportType.class);
        for (ReportGenerator generator : reportGenerators) {
            generators.put(generator.getSupportedType(), generator);
        }
    }

    public Optional<ReportGenerator> getGenerator(ReportType reportType) {
        return Optional.ofNullable(generators.get(reportType));
    }
}
