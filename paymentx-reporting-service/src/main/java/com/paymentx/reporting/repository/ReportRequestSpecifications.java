package com.paymentx.reporting.repository;

import com.paymentx.reporting.entity.ReportRequest;
import com.paymentx.reporting.entity.ReportType;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;

/**
 * ====================================================================
 * ENGLISH: Dynamic search predicates for ReportRequest, matching the
 * platform's established Specification pattern (Routing/Audit/
 * Reconciliation Service all use the identical composable-predicate
 * approach) - each method here is one AND-able filter clause.
 *
 * HINGLISH: ReportRequest ke liye dynamic search predicates, platform
 * ke established Specification pattern jaisa - yahan har method ek
 * AND-able filter clause hai.
 * ====================================================================
 */
public final class ReportRequestSpecifications {

    private ReportRequestSpecifications() {}

    public static Specification<ReportRequest> hasReportType(ReportType reportType) {
        return (root, query, cb) -> reportType == null ? cb.conjunction() : cb.equal(root.get("reportType"), reportType);
    }

    public static Specification<ReportRequest> hasParticipantId(String participantId) {
        return (root, query, cb) -> participantId == null || participantId.isBlank()
                ? cb.conjunction() : cb.equal(root.get("participantId"), participantId);
    }

    public static Specification<ReportRequest> hasCurrency(String currency) {
        return (root, query, cb) -> currency == null || currency.isBlank()
                ? cb.conjunction() : cb.equal(root.get("currency"), currency);
    }

    public static Specification<ReportRequest> hasCorrelationId(String correlationId) {
        return (root, query, cb) -> correlationId == null || correlationId.isBlank()
                ? cb.conjunction() : cb.equal(root.get("correlationId"), correlationId);
    }

    public static Specification<ReportRequest> createdAfter(OffsetDateTime from) {
        return (root, query, cb) -> from == null ? cb.conjunction() : cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    public static Specification<ReportRequest> createdBefore(OffsetDateTime to) {
        return (root, query, cb) -> to == null ? cb.conjunction() : cb.lessThanOrEqualTo(root.get("createdAt"), to);
    }
}
