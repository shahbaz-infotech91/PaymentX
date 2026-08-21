package com.paymentx.controlcenter.repository;

import com.paymentx.controlcenter.dto.postgres.ReconciliationBatchSummary;
import com.paymentx.controlcenter.dto.postgres.ReconciliationRecordSummary;
import com.paymentx.controlcenter.dto.postgres.SettlementFileSummary;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * ENGLISH: The only place in this backend that runs SQL against the
 * paymentx_reconciliation database - covers both its real tables,
 * reconciliation_batch and settlement_file (both live in the same
 * database, so they share one DataSource/repository rather than a
 * needless second one). Hardcoded, parameterized SELECT-only queries.
 *
 * HINGLISH: Is backend me sirf yahi jagah hai jahan
 * paymentx_reconciliation database ke against SQL chalta hai - iske
 * dono real tables, reconciliation_batch aur settlement_file, ko cover
 * karta hai (dono ek hi database me rehte hain, isliye ek hi
 * DataSource/repository share karte hain, ek needless doosri ke
 * bajaye). Hardcoded, parameterized SELECT-only queries.
 */
@Repository
public class ReconciliationRepository {

    private final JdbcTemplate jdbcTemplate;

    public ReconciliationRepository(@Qualifier("reconciliationDataSource") DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    public List<ReconciliationBatchSummary> findBatchPage(int page, int size) {
        return jdbcTemplate.query(
                "SELECT id, batch_type, status, settlement_file_id, window_from, window_to, " +
                        "started_at, completed_at, total_records, matched_count, mismatch_count, " +
                        "triggered_by, failure_reason " +
                        "FROM reconciliation_batch ORDER BY started_at DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> new ReconciliationBatchSummary(
                        rs.getString("id"),
                        rs.getString("batch_type"),
                        rs.getString("status"),
                        rs.getString("settlement_file_id"),
                        rs.getObject("window_from", OffsetDateTime.class),
                        rs.getObject("window_to", OffsetDateTime.class),
                        rs.getObject("started_at", OffsetDateTime.class),
                        rs.getObject("completed_at", OffsetDateTime.class),
                        (Integer) rs.getObject("total_records"),
                        (Integer) rs.getObject("matched_count"),
                        (Integer) rs.getObject("mismatch_count"),
                        rs.getString("triggered_by"),
                        rs.getString("failure_reason")),
                size, page * size);
    }

    public long countBatches() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM reconciliation_batch", Long.class);
        return count != null ? count : 0L;
    }

    public List<SettlementFileSummary> findSettlementFilePage(int page, int size) {
        return jdbcTemplate.query(
                "SELECT id, file_name, file_type, status, file_size_bytes, record_count, " +
                        "checksum_hash, storage_path, failure_reason, created_at, updated_at " +
                        "FROM settlement_file ORDER BY created_at DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> new SettlementFileSummary(
                        rs.getString("id"),
                        rs.getString("file_name"),
                        rs.getString("file_type"),
                        rs.getString("status"),
                        (Long) rs.getObject("file_size_bytes"),
                        (Integer) rs.getObject("record_count"),
                        rs.getString("checksum_hash"),
                        rs.getString("storage_path"),
                        rs.getString("failure_reason"),
                        rs.getObject("created_at", OffsetDateTime.class),
                        rs.getObject("updated_at", OffsetDateTime.class)),
                size, page * size);
    }

    public long countSettlementFiles() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM settlement_file", Long.class);
        return count != null ? count : 0L;
    }

    /** Real batches with at least one real mismatch (Phase 3 Alerts feature) - never a fabricated mismatch. */
    public List<ReconciliationBatchSummary> findRecentWithMismatches(int limit) {
        return jdbcTemplate.query(
                "SELECT id, batch_type, status, settlement_file_id, window_from, window_to, " +
                        "started_at, completed_at, total_records, matched_count, mismatch_count, " +
                        "triggered_by, failure_reason " +
                        "FROM reconciliation_batch WHERE mismatch_count > 0 ORDER BY started_at DESC LIMIT ?",
                (rs, rowNum) -> new ReconciliationBatchSummary(
                        rs.getString("id"),
                        rs.getString("batch_type"),
                        rs.getString("status"),
                        rs.getString("settlement_file_id"),
                        rs.getObject("window_from", OffsetDateTime.class),
                        rs.getObject("window_to", OffsetDateTime.class),
                        rs.getObject("started_at", OffsetDateTime.class),
                        rs.getObject("completed_at", OffsetDateTime.class),
                        (Integer) rs.getObject("total_records"),
                        (Integer) rs.getObject("matched_count"),
                        (Integer) rs.getObject("mismatch_count"),
                        rs.getString("triggered_by"),
                        rs.getString("failure_reason")),
                limit);
    }

    private static final String RECORD_SELECT =
            "SELECT rr.id, rr.batch_id, rr.reference_id, rr.participant_id, rr.internal_amount, rr.external_amount, " +
                    "rr.internal_currency, rr.external_currency, rr.internal_status, rr.external_status, " +
                    "rr.reconciliation_status, sf.file_name AS settlement_file_name, " +
                    "(SELECT mr.description FROM mismatch_record mr WHERE mr.reconciliation_record_id = rr.id " +
                    "ORDER BY mr.created_at DESC LIMIT 1) AS mismatch_reason, rr.created_at " +
                    "FROM reconciliation_record rr " +
                    "JOIN reconciliation_batch rb ON rr.batch_id = rb.id " +
                    "LEFT JOIN settlement_file sf ON rb.settlement_file_id = sf.id ";

    private static final RowMapper<ReconciliationRecordSummary> RECORD_ROW_MAPPER = (rs, rowNum) -> new ReconciliationRecordSummary(
            rs.getString("id"),
            rs.getString("batch_id"),
            rs.getString("reference_id"),
            rs.getString("participant_id"),
            rs.getBigDecimal("internal_amount"),
            rs.getBigDecimal("external_amount"),
            rs.getString("internal_currency"),
            rs.getString("external_currency"),
            rs.getString("internal_status"),
            rs.getString("external_status"),
            rs.getString("reconciliation_status"),
            rs.getString("settlement_file_name"),
            rs.getString("mismatch_reason"),
            rs.getObject("created_at", OffsetDateTime.class));

    /** Real record-level match/mismatch results (Phase 4 Reconciliation Records tab). */
    public List<ReconciliationRecordSummary> findRecordPage(int page, int size) {
        return jdbcTemplate.query(RECORD_SELECT + "ORDER BY rr.created_at DESC LIMIT ? OFFSET ?",
                RECORD_ROW_MAPPER, size, page * size);
    }

    public long countRecords() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM reconciliation_record", Long.class);
        return count != null ? count : 0L;
    }

    public List<ReconciliationRecordSummary> searchRecords(String query, String status, int page, int size) {
        String pattern = "%" + query + "%";
        StringBuilder sql = new StringBuilder(RECORD_SELECT)
                .append("WHERE (rr.reference_id ILIKE ? OR rr.participant_id ILIKE ?) ");
        List<Object> params = new java.util.ArrayList<>(List.of(pattern, pattern));
        if (status != null && !status.isBlank()) {
            sql.append("AND rr.reconciliation_status = ? ");
            params.add(status);
        }
        sql.append("ORDER BY rr.created_at DESC LIMIT ? OFFSET ?");
        params.add(size);
        params.add(page * size);
        return jdbcTemplate.query(sql.toString(), RECORD_ROW_MAPPER, params.toArray());
    }

    public long countSearchRecords(String query, String status) {
        String pattern = "%" + query + "%";
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM reconciliation_record rr WHERE (rr.reference_id ILIKE ? OR rr.participant_id ILIKE ?) ");
        List<Object> params = new java.util.ArrayList<>(List.of(pattern, pattern));
        if (status != null && !status.isBlank()) {
            sql.append("AND rr.reconciliation_status = ? ");
            params.add(status);
        }
        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
        return count != null ? count : 0L;
    }
}
