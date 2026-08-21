package com.paymentx.controlcenter.repository;

import com.paymentx.controlcenter.dto.postgres.AuditEventSummary;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * ENGLISH: The only place in this backend that runs SQL against
 * paymentx_audit.audit_event. Hardcoded, parameterized SELECT-only
 * queries - paginated list (newest first, payload jsonb excluded), a
 * per-payment lookup (Phase 3 Payment Flow), an optional real ILIKE
 * search across event_type/source_service/actor_id/correlation_id/
 * reference (Phase 4 Audit timeline), and a real COUNT(*).
 *
 * HINGLISH: Is backend me sirf yahi jagah hai jahan
 * paymentx_audit.audit_event ke against SQL chalta hai. Hardcoded,
 * parameterized SELECT-only queries - paginated list (newest pehle,
 * payload jsonb excluded), ek per-payment lookup (Phase 3 Payment
 * Flow), ek optional real ILIKE search
 * event_type/source_service/actor_id/correlation_id/reference ke
 * against (Phase 4 Audit timeline), aur ek real COUNT(*).
 */
@Repository
public class AuditEventRepository {

    private static final String SELECT_COLUMNS =
            "id, event_type, event_status, source_service, actor_id, actor_type, correlation_id, trace_id, " +
                    "payment_id, participant_id, reference, occurred_at ";

    private static final RowMapper<AuditEventSummary> ROW_MAPPER = (rs, rowNum) -> new AuditEventSummary(
            rs.getString("id"),
            rs.getString("event_type"),
            rs.getString("event_status"),
            rs.getString("source_service"),
            rs.getString("actor_id"),
            rs.getString("actor_type"),
            rs.getString("correlation_id"),
            rs.getString("trace_id"),
            rs.getString("payment_id"),
            rs.getString("participant_id"),
            rs.getString("reference"),
            rs.getObject("occurred_at", OffsetDateTime.class));

    private final JdbcTemplate jdbcTemplate;

    public AuditEventRepository(@Qualifier("auditDataSource") DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    public List<AuditEventSummary> findPage(int page, int size) {
        return jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + "FROM audit_event ORDER BY occurred_at DESC LIMIT ? OFFSET ?",
                ROW_MAPPER, size, page * size);
    }

    public long countAll() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_event", Long.class);
        return count != null ? count : 0L;
    }

    /** Real per-payment audit trail (Phase 3 Payment Flow feature) - payment_id is stored as text matching payment.id's UUID string, verified live. */
    public List<AuditEventSummary> findByPaymentId(String paymentId) {
        return jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + "FROM audit_event WHERE payment_id = ? ORDER BY occurred_at ASC",
                ROW_MAPPER, paymentId);
    }

    /**
     * Real per-payment audit trail, widened to also match on reference (Phase 12
     * defect remediation). Some source services - verified live against
     * paymentx_audit.audit_event: validation-service - record their real audit
     * events with payment_id blank (the payment does not exist yet at that point
     * in the pipeline) but DO populate the real reference column. payment_id
     * match is kept for every service that does populate it; reference match is
     * an OR, not a replacement, so nothing previously matched stops matching.
     */
    public List<AuditEventSummary> findByPaymentIdOrReference(String paymentId, String reference) {
        return jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + "FROM audit_event WHERE payment_id = ? " +
                        "OR (reference = ? AND reference IS NOT NULL AND reference <> '') ORDER BY occurred_at ASC",
                ROW_MAPPER, paymentId, reference);
    }

    public List<AuditEventSummary> search(String query, int page, int size) {
        String pattern = "%" + query + "%";
        return jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + "FROM audit_event WHERE " +
                        "event_type ILIKE ? OR source_service ILIKE ? OR actor_id ILIKE ? OR correlation_id ILIKE ? OR reference ILIKE ? " +
                        "ORDER BY occurred_at DESC LIMIT ? OFFSET ?",
                ROW_MAPPER, pattern, pattern, pattern, pattern, pattern, size, page * size);
    }

    public long countSearch(String query) {
        String pattern = "%" + query + "%";
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE " +
                        "event_type ILIKE ? OR source_service ILIKE ? OR actor_id ILIKE ? OR correlation_id ILIKE ? OR reference ILIKE ?",
                Long.class, pattern, pattern, pattern, pattern, pattern);
        return count != null ? count : 0L;
    }
}
