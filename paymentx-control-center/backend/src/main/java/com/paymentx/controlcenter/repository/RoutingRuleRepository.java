package com.paymentx.controlcenter.repository;

import com.paymentx.controlcenter.dto.postgres.RoutingRuleSummary;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * ENGLISH: The only place in this backend that runs SQL against
 * paymentx_routing.routing_rule. Hardcoded, parameterized SELECT-only
 * queries - paginated list ordered by priority, an optional real ILIKE
 * search against scheme/participant_id/target_route/description
 * (added Phase 4 for the Database Viewer's Routing Rules tab), and
 * real COUNT(*) variants.
 *
 * HINGLISH: Is backend me sirf yahi jagah hai jahan
 * paymentx_routing.routing_rule ke against SQL chalta hai. Hardcoded,
 * parameterized SELECT-only queries - priority se ordered paginated
 * list, ek optional real ILIKE search
 * scheme/participant_id/target_route/description ke against (Phase 4
 * me Database Viewer ke Routing Rules tab ke liye add kiya gaya), aur
 * real COUNT(*) variants.
 */
@Repository
public class RoutingRuleRepository {

    private static final RowMapper<RoutingRuleSummary> ROW_MAPPER = (rs, rowNum) -> new RoutingRuleSummary(
            rs.getString("id"),
            rs.getString("scheme"),
            rs.getString("participant_id"),
            rs.getString("target_route"),
            (Integer) rs.getObject("priority"),
            (Boolean) rs.getObject("active"),
            (Boolean) rs.getObject("is_default"),
            rs.getString("description"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class));

    private final JdbcTemplate jdbcTemplate;

    public RoutingRuleRepository(@Qualifier("routingDataSource") DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    public List<RoutingRuleSummary> findPage(int page, int size) {
        return jdbcTemplate.query(
                "SELECT id, scheme, participant_id, target_route, priority, active, is_default, " +
                        "description, created_at, updated_at " +
                        "FROM routing_rule ORDER BY priority ASC, created_at DESC LIMIT ? OFFSET ?",
                ROW_MAPPER, size, page * size);
    }

    public long countAll() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM routing_rule", Long.class);
        return count != null ? count : 0L;
    }

    public List<RoutingRuleSummary> search(String query, int page, int size) {
        String pattern = "%" + query + "%";
        return jdbcTemplate.query(
                "SELECT id, scheme, participant_id, target_route, priority, active, is_default, " +
                        "description, created_at, updated_at FROM routing_rule " +
                        "WHERE scheme ILIKE ? OR participant_id ILIKE ? OR target_route ILIKE ? OR description ILIKE ? " +
                        "ORDER BY priority ASC, created_at DESC LIMIT ? OFFSET ?",
                ROW_MAPPER, pattern, pattern, pattern, pattern, size, page * size);
    }

    public long countSearch(String query) {
        String pattern = "%" + query + "%";
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM routing_rule WHERE scheme ILIKE ? OR participant_id ILIKE ? OR target_route ILIKE ? OR description ILIKE ?",
                Long.class, pattern, pattern, pattern, pattern);
        return count != null ? count : 0L;
    }
}
