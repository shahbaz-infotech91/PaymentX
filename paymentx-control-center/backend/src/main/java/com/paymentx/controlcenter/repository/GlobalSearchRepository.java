package com.paymentx.controlcenter.repository;

import com.paymentx.controlcenter.dto.search.SearchResult;
import com.paymentx.controlcenter.dto.search.SearchResultType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.List;

/**
 * ENGLISH: The only place in this backend that runs the Global Search
 * queries - three hardcoded, parameterized ILIKE lookups, one per
 * real database (paymentx_payment, paymentx_validation,
 * paymentx_reconciliation), each reusing the same read-only
 * DataSource beans PostgresDataSourceConfig already built for the
 * Phase 2 domain repositories (no new pools). Every value the caller
 * supplies is bound as a parameter inside a fixed `%?%` ILIKE pattern
 * - there is no code path where the search term becomes part of the
 * SQL text itself. Why it exists (Phase 3 addition): Global Search
 * needs to look across 3 separate Postgres databases in one logical
 * operation, which a single repository/datasource cannot do.
 *
 * HINGLISH: Is backend me sirf yahi jagah hai jahan Global Search
 * queries chalti hain - teen hardcoded, parameterized ILIKE lookups,
 * ek har real database ke liye (paymentx_payment, paymentx_validation,
 * paymentx_reconciliation), har ek wahi read-only DataSource beans
 * reuse karta hai jo PostgresDataSourceConfig ne Phase 2 domain
 * repositories ke liye already banaye the (koi naya pool nahi). Caller
 * jo bhi value supply karta hai wo ek fixed `%?%` ILIKE pattern ke
 * andar ek parameter ke roop me bind hoti hai - yahan koi code path
 * nahi hai jahan search term khud SQL text ka hissa ban jaye. Ye
 * dashboard me kyu hai (Phase 3 addition): Global Search ko 3 alag
 * Postgres databases me ek logical operation me dekhna hota hai, jo
 * ek single repository/datasource nahi kar sakta.
 */
@Repository
public class GlobalSearchRepository {

    private final JdbcTemplate paymentJdbcTemplate;
    private final JdbcTemplate validationJdbcTemplate;
    private final JdbcTemplate reconciliationJdbcTemplate;

    public GlobalSearchRepository(@Qualifier("paymentDataSource") DataSource paymentDataSource,
                                   @Qualifier("validationDataSource") DataSource validationDataSource,
                                   @Qualifier("reconciliationDataSource") DataSource reconciliationDataSource) {
        this.paymentJdbcTemplate = new JdbcTemplate(paymentDataSource);
        this.validationJdbcTemplate = new JdbcTemplate(validationDataSource);
        this.reconciliationJdbcTemplate = new JdbcTemplate(reconciliationDataSource);
    }

    public List<SearchResult> searchPayments(String query, int limit) {
        String pattern = "%" + query + "%";
        return paymentJdbcTemplate.query(
                "SELECT payment_reference, correlation_id, trace_id, status, amount, currency " +
                        "FROM payment WHERE payment_reference ILIKE ? OR correlation_id ILIKE ? OR trace_id ILIKE ? " +
                        "ORDER BY created_at DESC LIMIT ?",
                (rs, rowNum) -> new SearchResult(
                        SearchResultType.PAYMENT,
                        rs.getString("payment_reference"),
                        rs.getString("payment_reference"),
                        rs.getString("status") + " · " + rs.getBigDecimal("amount") + " " + rs.getString("currency")),
                pattern, pattern, pattern, limit);
    }

    public List<SearchResult> searchParticipants(String query, int limit) {
        String pattern = "%" + query + "%";
        return validationJdbcTemplate.query(
                "SELECT bank_id, legal_name, status FROM participant WHERE bank_id ILIKE ? OR legal_name ILIKE ? " +
                        "ORDER BY legal_name ASC LIMIT ?",
                (rs, rowNum) -> new SearchResult(
                        SearchResultType.PARTICIPANT,
                        rs.getString("bank_id"),
                        rs.getString("legal_name"),
                        rs.getString("bank_id") + " · " + rs.getString("status")),
                pattern, pattern, limit);
    }

    public List<SearchResult> searchSettlementFiles(String query, int limit) {
        String pattern = "%" + query + "%";
        return reconciliationJdbcTemplate.query(
                "SELECT id, file_name, status FROM settlement_file WHERE file_name ILIKE ? OR checksum_hash ILIKE ? " +
                        "ORDER BY created_at DESC LIMIT ?",
                (rs, rowNum) -> new SearchResult(
                        SearchResultType.SETTLEMENT_FILE,
                        rs.getString("id"),
                        rs.getString("file_name"),
                        rs.getString("status")),
                pattern, pattern, limit);
    }
}
