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
 * ENGLISH: Transaction Volume report - overall count and total value of
 * payments moving through the platform in the requested window, broken
 * down by currency.
 *
 * HINGLISH: Transaction Volume report - requested window me platform
 * se guzarne wale payments ka overall count aur total value, currency
 * ke hisab se break-down.
 * ====================================================================
 */
@Component
public class TransactionVolumeReportGenerator extends AbstractAggregationReportGenerator {

    public TransactionVolumeReportGenerator(SourceEventRepository sourceEventRepository, ObjectMapper objectMapper) {
        super(sourceEventRepository, objectMapper);
    }

    @Override
    public ReportType getSupportedType() {
        return ReportType.TRANSACTION_VOLUME;
    }

    @Override
    protected String sourceService() {
        return "payment-service";
    }

    @Override
    protected Map<String, Object> buildReportData(OffsetDateTime from, OffsetDateTime to) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", sourceEventRepository.aggregateTotal(sourceService(), from, to));
        data.put("byCurrency", sourceEventRepository.aggregateByCurrency(sourceService(), from, to));
        return data;
    }
}
