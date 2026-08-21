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
 * ENGLISH: Notification Summary report - Notification Service's
 * consumed events by status (SENT/FAILED/DEAD_LETTERED), answering "how
 * many notifications actually reached customers this period."
 *
 * HINGLISH: Notification Summary report - Notification Service ke
 * consumed events status ke hisab se, jawab deta hai "is period me
 * kitni notifications actually customers tak pahunchi."
 * ====================================================================
 */
@Component
public class NotificationSummaryReportGenerator extends AbstractAggregationReportGenerator {

    public NotificationSummaryReportGenerator(SourceEventRepository sourceEventRepository, ObjectMapper objectMapper) {
        super(sourceEventRepository, objectMapper);
    }

    @Override
    public ReportType getSupportedType() {
        return ReportType.NOTIFICATION_SUMMARY;
    }

    @Override
    protected String sourceService() {
        return "notification-service";
    }

    @Override
    protected Map<String, Object> buildReportData(OffsetDateTime from, OffsetDateTime to) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", sourceEventRepository.aggregateTotal(sourceService(), from, to));
        data.put("byStatus", sourceEventRepository.aggregateByStatus(sourceService(), from, to));
        return data;
    }
}
