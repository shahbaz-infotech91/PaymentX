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
 * ENGLISH: Participant Summary report - per-participant transaction
 * counts and amounts across Payment Service's events, answering "which
 * banks/participants are the most active this period."
 *
 * HINGLISH: Participant Summary report - Payment Service ke events me
 * har participant ke transaction counts aur amounts, jawab deta hai
 * "is period me kaunse banks/participants sabse active hain."
 * ====================================================================
 */
@Component
public class ParticipantSummaryReportGenerator extends AbstractAggregationReportGenerator {

    public ParticipantSummaryReportGenerator(SourceEventRepository sourceEventRepository, ObjectMapper objectMapper) {
        super(sourceEventRepository, objectMapper);
    }

    @Override
    public ReportType getSupportedType() {
        return ReportType.PARTICIPANT_SUMMARY;
    }

    @Override
    protected String sourceService() {
        return "payment-service";
    }

    @Override
    protected Map<String, Object> buildReportData(OffsetDateTime from, OffsetDateTime to) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", sourceEventRepository.aggregateTotal(sourceService(), from, to));
        data.put("byParticipant", sourceEventRepository.aggregateByParticipant(sourceService(), from, to));
        return data;
    }
}
