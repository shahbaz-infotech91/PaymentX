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
 * ENGLISH: Validation Summary report - Validation Service's consumed
 * events by status, showing how many payments passed/failed scheme
 * validation this period.
 *
 * HINGLISH: Validation Summary report - Validation Service ke consumed
 * events status ke hisab se, dikhata hai ki is period me kitne
 * payments scheme-validation pass/fail hue.
 * ====================================================================
 */
@Component
public class ValidationSummaryReportGenerator extends AbstractAggregationReportGenerator {

    public ValidationSummaryReportGenerator(SourceEventRepository sourceEventRepository, ObjectMapper objectMapper) {
        super(sourceEventRepository, objectMapper);
    }

    @Override
    public ReportType getSupportedType() {
        return ReportType.VALIDATION_SUMMARY;
    }

    @Override
    protected String sourceService() {
        return "validation-service";
    }

    @Override
    protected Map<String, Object> buildReportData(OffsetDateTime from, OffsetDateTime to) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", sourceEventRepository.aggregateTotal(sourceService(), from, to));
        data.put("byStatus", sourceEventRepository.aggregateByStatus(sourceService(), from, to));
        return data;
    }
}
