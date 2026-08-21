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
 * ENGLISH: Payment Summary report - total, per-status, and per-currency
 * counts/amounts for Payment Service's consumed events over the
 * requested window. The most commonly-requested operational report.
 *
 * HINGLISH: Payment Summary report - Payment Service ke consumed events
 * ka total, per-status, aur per-currency counts/amounts requested
 * window ke liye. Sabse zyada maanga jaane wala operational report.
 * ====================================================================
 */
@Component
public class PaymentSummaryReportGenerator extends AbstractAggregationReportGenerator {

    public PaymentSummaryReportGenerator(SourceEventRepository sourceEventRepository, ObjectMapper objectMapper) {
        super(sourceEventRepository, objectMapper);
    }

    @Override
    public ReportType getSupportedType() {
        return ReportType.PAYMENT_SUMMARY;
    }

    @Override
    protected String sourceService() {
        return "payment-service";
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
