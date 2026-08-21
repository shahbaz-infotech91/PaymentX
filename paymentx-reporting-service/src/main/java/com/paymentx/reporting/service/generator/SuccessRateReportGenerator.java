package com.paymentx.reporting.service.generator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentx.reporting.entity.ReportType;
import com.paymentx.reporting.repository.SourceEventRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * ====================================================================
 * ENGLISH: Success Rate report - the one report that computes an actual
 * RATIO (successful / total), not just raw counts, from Payment
 * Service's per-status breakdown. "COMPLETED" is treated as the success
 * outcome, matching the same success-status vocabulary Reconciliation
 * Service's MatchingEngine already established.
 *
 * HINGLISH: Success Rate report - ye ek hi report hai jo ek actual
 * RATIO (successful / total) calculate karta hai, sirf raw counts
 * nahi, Payment Service ke per-status breakdown se. "COMPLETED" ko
 * success outcome maana gaya hai, wahi success-status vocabulary jo
 * Reconciliation Service ke MatchingEngine ne pehle se establish kiya
 * hai.
 * ====================================================================
 */
@Component
public class SuccessRateReportGenerator extends AbstractAggregationReportGenerator {

    private static final Set<String> SUCCESS_STATUSES = Set.of("COMPLETED", "SETTLED", "SUCCESS", "SUCCESSFUL");

    public SuccessRateReportGenerator(SourceEventRepository sourceEventRepository, ObjectMapper objectMapper) {
        super(sourceEventRepository, objectMapper);
    }

    @Override
    public ReportType getSupportedType() {
        return ReportType.SUCCESS_RATE;
    }

    @Override
    protected String sourceService() {
        return "payment-service";
    }

    @Override
    protected Map<String, Object> buildReportData(OffsetDateTime from, OffsetDateTime to) {
        var byStatus = sourceEventRepository.aggregateByStatus(sourceService(), from, to);

        long totalCount = byStatus.stream().mapToLong(SourceEventRepository.StatusAggregate::getCount).sum();
        long successCount = byStatus.stream()
                .filter(s -> s.getStatus() != null && SUCCESS_STATUSES.contains(s.getStatus().toUpperCase()))
                .mapToLong(SourceEventRepository.StatusAggregate::getCount)
                .sum();

        BigDecimal successRatePercent = totalCount == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(successCount).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(totalCount), 2, RoundingMode.HALF_UP);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("byStatus", byStatus);
        data.put("totalCount", totalCount);
        data.put("successCount", successCount);
        data.put("successRatePercent", successRatePercent);
        return data;
    }
}
