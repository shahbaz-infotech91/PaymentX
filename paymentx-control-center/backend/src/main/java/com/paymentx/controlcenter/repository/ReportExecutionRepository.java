package com.paymentx.controlcenter.repository;

import com.paymentx.controlcenter.dto.postgres.ReportExecutionSummary;
import com.paymentx.controlcenter.dto.postgres.ReportFileSummary;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * ENGLISH: The only place in this backend that runs SQL against
 * paymentx_reporting.report_execution. Hardcoded, parameterized
 * SELECT-only queries - paginated list (newest first), and a real
 * COUNT(*).
 *
 * HINGLISH: Is backend me sirf yahi jagah hai jahan
 * paymentx_reporting.report_execution ke against SQL chalta hai.
 * Hardcoded, parameterized SELECT-only queries - paginated list
 * (newest pehle), aur ek real COUNT(*).
 */
@Repository
public class ReportExecutionRepository {

    private final JdbcTemplate jdbcTemplate;

    public ReportExecutionRepository(@Qualifier("reportingDataSource") DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    public List<ReportExecutionSummary> findPage(int page, int size) {
        return jdbcTemplate.query(
                "SELECT id, report_request_id, status, started_at, completed_at, row_count, " +
                        "generation_time_millis, retry_count, failure_reason " +
                        "FROM report_execution ORDER BY started_at DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> new ReportExecutionSummary(
                        rs.getString("id"),
                        rs.getString("report_request_id"),
                        rs.getString("status"),
                        rs.getObject("started_at", OffsetDateTime.class),
                        rs.getObject("completed_at", OffsetDateTime.class),
                        (Integer) rs.getObject("row_count"),
                        (Long) rs.getObject("generation_time_millis"),
                        (Integer) rs.getObject("retry_count"),
                        rs.getString("failure_reason")),
                size, page * size);
    }

    public long countAll() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM report_execution", Long.class);
        return count != null ? count : 0L;
    }

    private static final String REPORT_FILE_SELECT =
            "SELECT ex.id AS export_id, r.report_type, r.display_name, ex.format, rex.status AS execution_status, " +
                    "ex.file_size_bytes, ex.download_count, ex.storage_path, ex.created_at " +
                    "FROM report_export ex " +
                    "JOIN report_execution rex ON ex.report_execution_id = rex.id " +
                    "JOIN report_request rr ON rex.report_request_id = rr.id " +
                    "JOIN report r ON r.report_type = rr.report_type ";

    /** Real, downloadable report files (Phase 4 Reporting page) - one row per real report_export. */
    public List<ReportFileSummary> findReportFilePage(int page, int size) {
        return jdbcTemplate.query(REPORT_FILE_SELECT + "ORDER BY ex.created_at DESC LIMIT ? OFFSET ?",
                REPORT_FILE_ROW_MAPPER, size, page * size);
    }

    public long countReportFiles() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM report_export", Long.class);
        return count != null ? count : 0L;
    }

    private static final org.springframework.jdbc.core.RowMapper<ReportFileSummary> REPORT_FILE_ROW_MAPPER = (rs, rowNum) -> new ReportFileSummary(
            rs.getString("export_id"),
            rs.getString("report_type"),
            rs.getString("display_name"),
            rs.getString("format"),
            rs.getString("execution_status"),
            (Long) rs.getObject("file_size_bytes"),
            (Integer) rs.getObject("download_count"),
            Path.of(rs.getString("storage_path")).getFileName().toString(),
            rs.getObject("created_at", OffsetDateTime.class));
}
