package com.paymentx.controlcenter.repository;

import com.paymentx.controlcenter.dto.postgres.NotificationSummary;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * ENGLISH: The only place in this backend that runs SQL against
 * paymentx_notification.notification. Hardcoded, parameterized
 * SELECT-only queries - paginated list (newest first, body/template
 * content excluded), a per-payment lookup (Phase 3 Payment Flow), an
 * optional real ILIKE search across channel/status/recipient/
 * correlation_id (Phase 4 Database Viewer), and a real COUNT(*).
 *
 * HINGLISH: Is backend me sirf yahi jagah hai jahan
 * paymentx_notification.notification ke against SQL chalta hai.
 * Hardcoded, parameterized SELECT-only queries - paginated list
 * (newest pehle, body/template content excluded), ek per-payment
 * lookup (Phase 3 Payment Flow), ek optional real ILIKE search
 * channel/status/recipient/correlation_id ke against (Phase 4
 * Database Viewer), aur ek real COUNT(*).
 */
@Repository
public class NotificationRepository {

    private static final String SELECT_COLUMNS =
            "id, source_event_type, channel, status, recipient, subject, correlation_id, " +
                    "trace_id, payment_id, participant_id, retry_count, max_retries, last_attempt_at, " +
                    "next_retry_at, failure_reason, created_at ";

    private static final RowMapper<NotificationSummary> ROW_MAPPER = (rs, rowNum) -> new NotificationSummary(
            rs.getString("id"),
            rs.getString("source_event_type"),
            rs.getString("channel"),
            rs.getString("status"),
            rs.getString("recipient"),
            rs.getString("subject"),
            rs.getString("correlation_id"),
            rs.getString("trace_id"),
            rs.getString("payment_id"),
            rs.getString("participant_id"),
            (Integer) rs.getObject("retry_count"),
            (Integer) rs.getObject("max_retries"),
            rs.getObject("last_attempt_at", OffsetDateTime.class),
            rs.getObject("next_retry_at", OffsetDateTime.class),
            rs.getString("failure_reason"),
            rs.getObject("created_at", OffsetDateTime.class));

    private final JdbcTemplate jdbcTemplate;

    public NotificationRepository(@Qualifier("notificationDataSource") DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    public List<NotificationSummary> findPage(int page, int size) {
        return jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + "FROM notification ORDER BY created_at DESC LIMIT ? OFFSET ?",
                ROW_MAPPER, size, page * size);
    }

    public long countAll() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notification", Long.class);
        return count != null ? count : 0L;
    }

    /** Real per-payment notification trail (Phase 3 Payment Flow feature). */
    public List<NotificationSummary> findByPaymentId(String paymentId) {
        return jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + "FROM notification WHERE payment_id = ? ORDER BY created_at ASC",
                ROW_MAPPER, paymentId);
    }

    public List<NotificationSummary> search(String query, int page, int size) {
        String pattern = "%" + query + "%";
        return jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + "FROM notification WHERE " +
                        "channel ILIKE ? OR status ILIKE ? OR recipient ILIKE ? OR correlation_id ILIKE ? " +
                        "ORDER BY created_at DESC LIMIT ? OFFSET ?",
                ROW_MAPPER, pattern, pattern, pattern, pattern, size, page * size);
    }

    public long countSearch(String query) {
        String pattern = "%" + query + "%";
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE channel ILIKE ? OR status ILIKE ? OR recipient ILIKE ? OR correlation_id ILIKE ?",
                Long.class, pattern, pattern, pattern, pattern);
        return count != null ? count : 0L;
    }
}
