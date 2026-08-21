package com.paymentx.controlcenter.repository;

import com.paymentx.controlcenter.dto.postgres.ParticipantSummary;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * ENGLISH: The only place in this backend that runs SQL against
 * paymentx_validation.participant. What it does: hardcoded,
 * parameterized SELECT statements (paginated list, an optional real
 * ILIKE search against bank_id/legal_name, real COUNT(*) variants) -
 * there is no method here that accepts a client-supplied WHERE clause
 * or raw SQL string, which is the actual "no arbitrary SQL" boundary
 * the Phase 2/4 briefs require. Why it exists: keeps every real column
 * name/table name/query in exactly one place per domain, matching the
 * live-verified schema from earlier PaymentX database validation work.
 * search was added in Phase 4 for the Database Viewer's Participants
 * tab.
 *
 * HINGLISH: Is backend me sirf yahi jagah hai jahan
 * paymentx_validation.participant ke against SQL chalta hai. Ye kya
 * karti hai: hardcoded, parameterized SELECT statements (paginated
 * list, ek optional real ILIKE search bank_id/legal_name ke against,
 * real COUNT(*) variants) - yahan koi aisa method nahi hai jo
 * client-supplied WHERE clause ya raw SQL string accept kare, yehi
 * actual "no arbitrary SQL" boundary hai jo Phase 2/4 briefs maangte
 * hain. Ye dashboard me kyu hai: har real column name/table name/query
 * ko har domain ke liye exactly ek jagah rakhta hai, pehle ke PaymentX
 * database validation work se live-verified schema ke match. search
 * Phase 4 me Database Viewer ke Participants tab ke liye add kiya
 * gaya.
 */
@Repository
public class ParticipantRepository {

    private static final RowMapper<ParticipantSummary> ROW_MAPPER = (rs, rowNum) -> new ParticipantSummary(
            rs.getLong("id"),
            rs.getString("bank_id"),
            rs.getString("legal_name"),
            rs.getString("status"),
            rs.getObject("onboarded_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class));

    private final JdbcTemplate jdbcTemplate;

    public ParticipantRepository(@Qualifier("validationDataSource") DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    public List<ParticipantSummary> findPage(int page, int size) {
        return jdbcTemplate.query(
                "SELECT id, bank_id, legal_name, status, onboarded_at, updated_at " +
                        "FROM participant ORDER BY id DESC LIMIT ? OFFSET ?",
                ROW_MAPPER, size, page * size);
    }

    public long countAll() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM participant", Long.class);
        return count != null ? count : 0L;
    }

    public List<ParticipantSummary> search(String query, int page, int size) {
        String pattern = "%" + query + "%";
        return jdbcTemplate.query(
                "SELECT id, bank_id, legal_name, status, onboarded_at, updated_at FROM participant " +
                        "WHERE bank_id ILIKE ? OR legal_name ILIKE ? ORDER BY id DESC LIMIT ? OFFSET ?",
                ROW_MAPPER, pattern, pattern, size, page * size);
    }

    public long countSearch(String query) {
        String pattern = "%" + query + "%";
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM participant WHERE bank_id ILIKE ? OR legal_name ILIKE ?", Long.class, pattern, pattern);
        return count != null ? count : 0L;
    }

    /**
     * ENGLISH: Real, bounded list of currently-ACTIVE participants (Phase
     * 5 E2E flow - picks two of these as the real debtor/creditor
     * instead of hardcoding bank IDs that might not exist in a given
     * environment's real seed data).
     *
     * HINGLISH: Currently-ACTIVE participants ki real, bounded list
     * (Phase 5 E2E flow - inme se do ko real debtor/creditor ke roop me
     * chunta hai, bank IDs hardcode karne ke bajaye jo shayad kisi diye
     * gaye environment ke real seed data me exist hi na karein).
     */
    public List<ParticipantSummary> findActive(int limit) {
        return jdbcTemplate.query(
                "SELECT id, bank_id, legal_name, status, onboarded_at, updated_at " +
                        "FROM participant WHERE status = 'ACTIVE' ORDER BY id ASC LIMIT ?",
                ROW_MAPPER, limit);
    }
}
