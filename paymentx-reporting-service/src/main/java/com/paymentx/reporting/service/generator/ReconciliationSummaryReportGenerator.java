package com.paymentx.reporting.service.generator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentx.reporting.entity.ReportType;
import com.paymentx.reporting.repository.SourceEventRepository;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ====================================================================
 * ENGLISH: Reconciliation Summary report - Reconciliation Service's
 * consumed events by status, distinct from Settlement Summary (which
 * focuses on matched vs unmatched amounts) - this one gives the batch-
 * outcome view (COMPLETED/FAILED/PARTIALLY_COMPLETED batches this
 * period).
 *
 * HINGLISH: Reconciliation Summary report - Reconciliation Service ke
 * consumed events status ke hisab se, Settlement Summary se alag - ye
 * batch-outcome wali view deta hai.
 * ====================================================================
 */
@Component
public class ReconciliationSummaryReportGenerator extends AbstractAggregationReportGenerator {

    public ReconciliationSummaryReportGenerator(SourceEventRepository sourceEventRepository, ObjectMapper objectMapper) {
        super(sourceEventRepository, objectMapper);
    }

    @Override
    public ReportType getSupportedType() {
        return ReportType.RECONCILIATION_SUMMARY;
    }

    @Override
    protected String sourceService() {
        return "reconciliation-service";
    }

    @Override
    protected Map<String, Object> buildReportData(OffsetDateTime from, OffsetDateTime to) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", sourceEventRepository.aggregateTotal(sourceService(), from, to));
        data.put("byStatus", sourceEventRepository.aggregateByStatus(sourceService(), from, to));
        return data;
    }
}
