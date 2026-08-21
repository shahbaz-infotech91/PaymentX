package com.paymentx.controlcenter.repository;

import com.paymentx.controlcenter.dto.postgres.PaymentFilter;
import com.paymentx.controlcenter.dto.postgres.PaymentStatsSummary;
import com.paymentx.controlcenter.dto.postgres.PaymentSummary;
import com.paymentx.controlcenter.dto.postgres.PaymentTimeseriesBucket;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * ENGLISH: The only place in this backend that runs SQL against
 * paymentx_payment.payment. Hardcoded, parameterized SELECT-only
 * queries - a plain paginated list, a Transaction-Monitor filtered/
 * searched/sorted page (every value parameterized; sort column comes
 * only from the PaymentSortField allowlist, never a raw string), real
 * COUNT(*) variants, single-row lookups by id/reference for the
 * Payment Flow feature, and a real GROUP BY status aggregate for the
 * Home page's stats tile. Why it exists (Phase 3 addition): the
 * Transaction Monitor and Home page both need real, server-side
 * filtering/sorting/aggregation - doing this client-side against the
 * full unpaginated table would defeat the whole point of pagination.
 *
 * HINGLISH: Is backend me sirf yahi jagah hai jahan
 * paymentx_payment.payment ke against SQL chalta hai. Hardcoded,
 * parameterized SELECT-only queries - ek plain paginated list, ek
 * Transaction-Monitor filtered/searched/sorted page (har value
 * parameterized; sort column sirf PaymentSortField allowlist se aata
 * hai, kabhi raw string nahi), real COUNT(*) variants, Payment Flow
 * feature ke liye id/reference se single-row lookups, aur Home page
 * ke stats tile ke liye ek real GROUP BY status aggregate. Ye
 * dashboard me kyu hai (Phase 3 addition): Transaction Monitor aur
 * Home page dono ko real, server-side filtering/sorting/aggregation
 * chahiye - ise client-side, poori unpaginated table ke against karna
 * pagination ka pura point hi khatam kar deta.
 */
@Repository
public class PaymentRepository {

    private static final String SELECT_COLUMNS =
            "id, payment_reference, correlation_id, trace_id, scheme, payment_type, channel, amount, currency, " +
                    "debtor_account, debtor_participant_id, creditor_account, creditor_participant_id, " +
                    "status, failure_reason, created_at, updated_at ";

    private static final RowMapper<PaymentSummary> ROW_MAPPER = (rs, rowNum) -> new PaymentSummary(
            rs.getString("id"),
            rs.getString("payment_reference"),
            rs.getString("correlation_id"),
            rs.getString("trace_id"),
            rs.getString("scheme"),
            rs.getString("payment_type"),
            rs.getString("channel"),
            rs.getBigDecimal("amount"),
            rs.getString("currency"),
            rs.getString("debtor_account"),
            rs.getString("debtor_participant_id"),
            rs.getString("creditor_account"),
            rs.getString("creditor_participant_id"),
            rs.getString("status"),
            rs.getString("failure_reason"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class));

    /** Real payment.status state machine (payment-service's own PaymentStatus enum) bucketed for the Home page - never an invented classification. */
    private static final Set<String> SUCCESSFUL_STATUSES = Set.of("SETTLED");
    private static final Set<String> FAILED_STATUSES = Set.of(
            "DEBIT_FAILED", "CREDIT_FAILED", "FAILED", "TIMEOUT", "CANCELLED", "RETURNED", "REVERSED");
    private static final Set<String> PROCESSING_STATUSES = Set.of(
            "PROCESSING", "ROUTING", "DEBITING", "CREDITING", "SETTLING", "RETRYING");

    /** The exact 6 windows Phase 4's Metrics page's TimeRangeSelector offers (matches PrometheusController's range endpoint) - any other value quietly falls back to 60. */
    private static final Set<Integer> ALLOWED_WINDOW_MINUTES = Set.of(5, 15, 30, 60, 360, 1440);

    private static final RowMapper<PaymentTimeseriesBucket> TIMESERIES_ROW_MAPPER = (rs, rowNum) -> new PaymentTimeseriesBucket(
            rs.getObject("bucket_start", OffsetDateTime.class),
            rs.getLong("total"),
            rs.getLong("successful"),
            rs.getLong("failed"));

    private final JdbcTemplate jdbcTemplate;

    public PaymentRepository(@Qualifier("paymentDataSource") DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    public List<PaymentSummary> findPage(int page, int size) {
        return jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + "FROM payment ORDER BY created_at DESC LIMIT ? OFFSET ?",
                ROW_MAPPER, size, page * size);
    }

    public long countAll() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payment", Long.class);
        return count != null ? count : 0L;
    }

    public Optional<PaymentSummary> findById(String id) {
        List<PaymentSummary> rows = jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + "FROM payment WHERE id = ?::uuid", ROW_MAPPER, id);
        return rows.stream().findFirst();
    }

    public Optional<PaymentSummary> findByReference(String reference) {
        List<PaymentSummary> rows = jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + "FROM payment WHERE payment_reference = ?", ROW_MAPPER, reference);
        return rows.stream().findFirst();
    }

    public List<PaymentSummary> findFiltered(PaymentFilter filter, int page, int size) {
        WhereClause where = buildWhereClause(filter);
        String sql = "SELECT " + SELECT_COLUMNS + "FROM payment" + where.sql()
                + " ORDER BY " + filter.sortField().column() + (filter.sortAscending() ? " ASC" : " DESC")
                + " LIMIT ? OFFSET ?";
        List<Object> params = new ArrayList<>(where.params());
        params.add(size);
        params.add(page * size);
        return jdbcTemplate.query(sql, ROW_MAPPER, params.toArray());
    }

    public long countFiltered(PaymentFilter filter) {
        WhereClause where = buildWhereClause(filter);
        String sql = "SELECT COUNT(*) FROM payment" + where.sql();
        Long count = jdbcTemplate.queryForObject(sql, Long.class, where.params().toArray());
        return count != null ? count : 0L;
    }

    public PaymentStatsSummary stats() {
        Map<String, Long> countsByStatus = jdbcTemplate.query(
                "SELECT status, COUNT(*) AS cnt FROM payment GROUP BY status",
                rs -> {
                    Map<String, Long> map = new java.util.HashMap<>();
                    while (rs.next()) {
                        map.put(rs.getString("status"), rs.getLong("cnt"));
                    }
                    return map;
                });
        if (countsByStatus == null) countsByStatus = Map.of();

        long total = 0, successful = 0, failed = 0, processing = 0, pending = 0;
        for (Map.Entry<String, Long> entry : countsByStatus.entrySet()) {
            long count = entry.getValue();
            total += count;
            if (SUCCESSFUL_STATUSES.contains(entry.getKey())) successful += count;
            else if (FAILED_STATUSES.contains(entry.getKey())) failed += count;
            else if (PROCESSING_STATUSES.contains(entry.getKey())) processing += count;
            else pending += count;
        }

        // SUCCESSFUL_STATUSES is our own fixed Java constant (never client input), so building the IN-clause
        // placeholders from its size is safe - each value is still passed as a bound parameter below.
        String successPlaceholders = String.join(",", SUCCESSFUL_STATUSES.stream().map(s -> "?").toList());
        Double averageLatencyMillis = jdbcTemplate.queryForObject(
                "SELECT EXTRACT(EPOCH FROM AVG(updated_at - created_at)) * 1000 FROM payment WHERE status IN (" + successPlaceholders + ")",
                Double.class, SUCCESSFUL_STATUSES.toArray());

        Long paymentsLastHour = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payment WHERE created_at >= now() - interval '1 hour'", Long.class);
        long lastHour = paymentsLastHour != null ? paymentsLastHour : 0L;

        double successRate = total == 0 ? 0.0 : (successful * 100.0) / total;
        double failureRate = total == 0 ? 0.0 : (failed * 100.0) / total;
        double tps = lastHour / 3600.0;

        return new PaymentStatsSummary(total, successful, failed, pending, processing,
                successRate, failureRate, averageLatencyMillis, lastHour, tps);
    }

    /**
     * ENGLISH: Real, `date_bin`-bucketed payment activity for the Phase
     * 4 Metrics page's "Payments/minute" + "Success Rate"/"Failure Rate"
     * charts (see PaymentTimeseriesBucket's own javadoc - there is no
     * Prometheus counter for business payment volume, so this is
     * derived directly from Postgres). windowMinutes is clamped to one
     * of the same 6 real values PrometheusController's range endpoint
     * allows; bucket width scales with the window so a 24h window
     * doesn't return 1440 near-empty one-minute buckets. Every bound
     * value (bucket width, window, the fixed FAILED_STATUSES set) is a
     * real parameter - never string-concatenated into the SQL.
     *
     * HINGLISH: Phase 4 Metrics page ke "Payments/minute" +
     * "Success Rate"/"Failure Rate" charts ke liye real, `date_bin`-
     * bucketed payment activity (PaymentTimeseriesBucket ka apna
     * javadoc dekho - business payment volume ke liye koi Prometheus
     * counter nahi hai, isliye ye directly Postgres se derive kiya gaya
     * hai). windowMinutes un hi 6 real values me se ek tak clamp hota
     * hai jo PrometheusController ka range endpoint allow karta hai;
     * bucket width window ke saath scale hoti hai taaki ek 24h window
     * 1440 near-empty one-minute buckets return na kare. Har bound
     * value (bucket width, window, fixed FAILED_STATUSES set) ek real
     * parameter hai - kabhi SQL me string-concatenated nahi.
     */
    public List<PaymentTimeseriesBucket> timeseries(Integer windowMinutes) {
        int window = windowMinutes != null && ALLOWED_WINDOW_MINUTES.contains(windowMinutes) ? windowMinutes : 60;
        int bucketMinutes = window <= 30 ? 1 : window <= 60 ? 2 : window <= 360 ? 15 : 60;

        String failedPlaceholders = String.join(",", FAILED_STATUSES.stream().map(s -> "?").toList());
        String sql = "SELECT date_bin(make_interval(mins => ?), created_at, TIMESTAMP '2001-01-01') AS bucket_start, "
                + "COUNT(*) AS total, "
                + "COUNT(*) FILTER (WHERE status = 'SETTLED') AS successful, "
                + "COUNT(*) FILTER (WHERE status IN (" + failedPlaceholders + ")) AS failed "
                + "FROM payment WHERE created_at >= now() - make_interval(mins => ?) "
                + "GROUP BY bucket_start ORDER BY bucket_start";

        List<Object> params = new ArrayList<>();
        params.add(bucketMinutes);
        params.addAll(FAILED_STATUSES);
        params.add(window);
        return jdbcTemplate.query(sql, TIMESERIES_ROW_MAPPER, params.toArray());
    }

    private WhereClause buildWhereClause(PaymentFilter filter) {
        StringBuilder sql = new StringBuilder();
        List<Object> params = new ArrayList<>();
        List<String> conditions = new ArrayList<>();

        if (filter.search() != null && !filter.search().isBlank()) {
            conditions.add("(payment_reference ILIKE ? OR correlation_id ILIKE ? OR trace_id ILIKE ?)");
            String pattern = "%" + filter.search().trim() + "%";
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
        }
        if (filter.status() != null && !filter.status().isBlank()) {
            conditions.add("status = ?");
            params.add(filter.status());
        }
        if (filter.scheme() != null && !filter.scheme().isBlank()) {
            conditions.add("scheme = ?");
            params.add(filter.scheme());
        }
        if (filter.participantId() != null && !filter.participantId().isBlank()) {
            conditions.add("(debtor_participant_id = ? OR creditor_participant_id = ?)");
            params.add(filter.participantId());
            params.add(filter.participantId());
        }
        if (filter.dateFrom() != null) {
            conditions.add("created_at >= ?");
            params.add(filter.dateFrom());
        }
        if (filter.dateTo() != null) {
            conditions.add("created_at <= ?");
            params.add(filter.dateTo());
        }

        if (!conditions.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", conditions));
        }
        return new WhereClause(sql.toString(), params);
    }

    private record WhereClause(String sql, List<Object> params) {
    }
}
