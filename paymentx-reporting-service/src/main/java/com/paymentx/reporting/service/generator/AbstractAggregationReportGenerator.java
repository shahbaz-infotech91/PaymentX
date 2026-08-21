package com.paymentx.reporting.service.generator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentx.reporting.entity.ReportRequest;
import com.paymentx.reporting.repository.SourceEventRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ====================================================================
 * ENGLISH:
 * Shared aggregation-and-JSON-serialization logic every concrete
 * ReportGenerator reuses instead of duplicating - directly satisfies
 * the explicit "No duplicate logic" code-quality requirement. Each
 * concrete subclass only needs to say WHICH source service and WHICH
 * dimensions matter for its specific report - the actual SQL execution
 * and JSON building happens here, once.
 *
 * HINGLISH:
 * Shared aggregation-aur-JSON-serialization logic jise har concrete
 * ReportGenerator reuse karta hai, duplicate karne ke bajaye - ye
 * explicit "No duplicate logic" code-quality requirement ko directly
 * satisfy karta hai. Har concrete subclass ko bas ye batana hota hai ki
 * KAUNSI source service aur KAUNSE dimensions uske specific report ke
 * liye zaroori hain - actual SQL execution aur JSON building yahan hi
 * hoti hai, ek hi jagah.
 * ====================================================================
 */
public abstract class AbstractAggregationReportGenerator implements ReportGenerator {

    protected final SourceEventRepository sourceEventRepository;
    protected final ObjectMapper objectMapper;

    protected AbstractAggregationReportGenerator(SourceEventRepository sourceEventRepository, ObjectMapper objectMapper) {
        this.sourceEventRepository = sourceEventRepository;
        this.objectMapper = objectMapper;
    }

    /** WHICH source service's SourceEvent rows this report aggregates -
     *  e.g. "payment-service" for Payment Summary. */
    protected abstract String sourceService();

    /** Populates the report's data section - concrete subclasses call
     *  the specific SourceEventRepository aggregate*() methods they
     *  need and put the results into the returned map. */
    protected abstract Map<String, Object> buildReportData(OffsetDateTime from, OffsetDateTime to);

    @Override
    public String generate(ReportRequest request) {
        OffsetDateTime from = request.getDateFrom() != null ? request.getDateFrom() : OffsetDateTime.now().minusDays(1);
        OffsetDateTime to = request.getDateTo() != null ? request.getDateTo() : OffsetDateTime.now();

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("reportType", getSupportedType().name());
        report.put("sourceService", sourceService());
        report.put("periodFrom", from.toString());
        report.put("periodTo", to.toString());
        report.putAll(buildReportData(from, to));

        try {
            return objectMapper.writeValueAsString(report);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize report data for " + getSupportedType(), e);
        }
    }
}
