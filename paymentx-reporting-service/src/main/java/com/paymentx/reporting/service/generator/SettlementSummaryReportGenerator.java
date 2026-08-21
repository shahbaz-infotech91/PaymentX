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
 * ENGLISH: Settlement Summary report - aggregates Reconciliation
 * Service's consumed events (settlement outcomes) by status and
 * currency, answering "how much settled, how much didn't, this period."
 *
 * HINGLISH: Settlement Summary report - Reconciliation Service ke
 * consumed events (settlement outcomes) ko status aur currency se
 * aggregate karta hai, jawab deta hai "is period me kitna settle hua,
 * kitna nahi hua."
 * ====================================================================
 */
@Component
public class SettlementSummaryReportGenerator extends AbstractAggregationReportGenerator {

    public SettlementSummaryReportGenerator(SourceEventRepository sourceEventRepository, ObjectMapper objectMapper) {
        super(sourceEventRepository, objectMapper);
    }

    @Override
    public ReportType getSupportedType() {
        return ReportType.SETTLEMENT_SUMMARY;
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
        data.put("byCurrency", sourceEventRepository.aggregateByCurrency(sourceService(), from, to));
        return data;
    }
}
