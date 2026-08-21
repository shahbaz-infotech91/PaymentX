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
 * ENGLISH: Audit Summary report - Audit Service's consumed events by
 * status, giving a compliance-facing view of how many events were
 * durably audited this period.
 *
 * HINGLISH: Audit Summary report - Audit Service ke consumed events
 * status ke hisab se, ek compliance-facing view deta hai ki is period
 * me kitne events durably audit hue.
 * ====================================================================
 */
@Component
public class AuditSummaryReportGenerator extends AbstractAggregationReportGenerator {

    public AuditSummaryReportGenerator(SourceEventRepository sourceEventRepository, ObjectMapper objectMapper) {
        super(sourceEventRepository, objectMapper);
    }

    @Override
    public ReportType getSupportedType() {
        return ReportType.AUDIT_SUMMARY;
    }

    @Override
    protected String sourceService() {
        return "audit-service";
    }

    @Override
    protected Map<String, Object> buildReportData(OffsetDateTime from, OffsetDateTime to) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", sourceEventRepository.aggregateTotal(sourceService(), from, to));
        data.put("byStatus", sourceEventRepository.aggregateByStatus(sourceService(), from, to));
        return data;
    }
}
