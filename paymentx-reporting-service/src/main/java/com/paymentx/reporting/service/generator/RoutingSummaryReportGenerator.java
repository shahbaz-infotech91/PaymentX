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
 * ENGLISH: Routing Summary report - Routing Service's consumed events
 * by status, showing how route-resolution and rule-change activity
 * looked this period.
 *
 * HINGLISH: Routing Summary report - Routing Service ke consumed
 * events status ke hisab se, dikhata hai ki is period me route-
 * resolution aur rule-change activity kaisi rahi.
 * ====================================================================
 */
@Component
public class RoutingSummaryReportGenerator extends AbstractAggregationReportGenerator {

    public RoutingSummaryReportGenerator(SourceEventRepository sourceEventRepository, ObjectMapper objectMapper) {
        super(sourceEventRepository, objectMapper);
    }

    @Override
    public ReportType getSupportedType() {
        return ReportType.ROUTING_SUMMARY;
    }

    @Override
    protected String sourceService() {
        return "routing-service";
    }

    @Override
    protected Map<String, Object> buildReportData(OffsetDateTime from, OffsetDateTime to) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", sourceEventRepository.aggregateTotal(sourceService(), from, to));
        data.put("byStatus", sourceEventRepository.aggregateByStatus(sourceService(), from, to));
        return data;
    }
}
