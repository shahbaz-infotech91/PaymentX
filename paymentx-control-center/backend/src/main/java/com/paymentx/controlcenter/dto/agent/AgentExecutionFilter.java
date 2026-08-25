package com.paymentx.controlcenter.dto.agent;

import java.time.OffsetDateTime;

/** Phase 4.7 - all optional; combined with AND, mirrors MismatchSearchCriteria/PaymentFilter's own established shape in this codebase. */
public record AgentExecutionFilter(
        String agentId,
        String outcome,
        String paymentReference,
        String executionId,
        OffsetDateTime fromDate,
        OffsetDateTime toDate
) {
}
