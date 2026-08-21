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
 * ENGLISH: Failed Transactions report - the full per-status breakdown
 * of Payment Service's events for the window, letting an operator see
 * exactly how many FAILED/CANCELLED/TIMEOUT payments occurred alongside
 * the successful ones for direct comparison.
 *
 * HINGLISH: Failed Transactions report - Payment Service ke events ka
 * poora per-status breakdown window ke liye, taaki operator dekh sake
 * ki exactly kitne FAILED/CANCELLED/TIMEOUT payments hue successful
 * wale ke saath compare karke.
 * ====================================================================
 */
@Component
public class FailedTransactionsReportGenerator extends AbstractAggregationReportGenerator {

    public FailedTransactionsReportGenerator(SourceEventRepository sourceEventRepository, ObjectMapper objectMapper) {
        super(sourceEventRepository, objectMapper);
    }

    @Override
    public ReportType getSupportedType() {
        return ReportType.FAILED_TRANSACTIONS;
    }

    @Override
    protected String sourceService() {
        return "payment-service";
    }

    @Override
    protected Map<String, Object> buildReportData(OffsetDateTime from, OffsetDateTime to) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("byStatus", sourceEventRepository.aggregateByStatus(sourceService(), from, to));
        return data;
    }
}
