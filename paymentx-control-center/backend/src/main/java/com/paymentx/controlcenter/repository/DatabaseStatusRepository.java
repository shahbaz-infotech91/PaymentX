package com.paymentx.controlcenter.repository;

import com.paymentx.controlcenter.dto.postgres.DatabaseStatus;
import com.paymentx.controlcenter.dto.postgres.PostgresDatabaseIdentifier;
import com.paymentx.controlcenter.dto.postgres.TableInfo;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * ENGLISH: The cross-cutting "is this database actually reachable, and
 * what does Liquibase's own bookkeeping say" + "what tables exist and
 * how many rows does each real one have" repository, spanning all 7
 * real PaymentX databases. What it does: Spring autowires every
 * DataSource bean into one Map<String, DataSource> keyed by bean name
 * (validationDataSource, paymentDataSource, ...) - this class resolves
 * the right one per PostgresDatabaseIdentifier and never accepts a
 * connection string from a caller. Table names come from
 * information_schema.tables (server-derived, not client input) before
 * being interpolated into a COUNT(*) - there is still no client-
 * supplied SQL anywhere in this path. Why it exists: the Phase 2 brief
 * explicitly requires "connection status", "Liquibase migration
 * status", and "table information" per database.
 *
 * HINGLISH: Cross-cutting "kya ye database actually reachable hai, aur
 * Liquibase ki apni bookkeeping kya kehti hai" + "kaun se tables exist
 * karte hain aur har real ek me kitni rows hain" repository, saare 7
 * real PaymentX databases ke across. Ye kya karti hai: Spring har
 * DataSource bean ko ek Map<String, DataSource> me autowire karta hai
 * bean name se key kiya hua (validationDataSource, paymentDataSource,
 * ...) - ye class har PostgresDatabaseIdentifier ke liye sahi wala
 * resolve karti hai aur caller se kabhi connection string accept nahi
 * karti. Table names information_schema.tables se aate hain
 * (server-derived, client input nahi) COUNT(*) me interpolate hone se
 * pehle - is path me kahin bhi client-supplied SQL nahi hai. Ye
 * dashboard me kyu hai: Phase 2 brief explicitly har database ke liye
 * "connection status", "Liquibase migration status", aur "table
 * information" maangta hai.
 */
@Repository
public class DatabaseStatusRepository {

    private final Map<PostgresDatabaseIdentifier, JdbcTemplate> jdbcTemplatesByDatabase = new EnumMap<>(PostgresDatabaseIdentifier.class);
    private final Map<PostgresDatabaseIdentifier, DataSource> dataSourcesByDatabase = new EnumMap<>(PostgresDatabaseIdentifier.class);

    public DatabaseStatusRepository(Map<String, DataSource> dataSourcesByBeanName) {
        for (PostgresDatabaseIdentifier identifier : PostgresDatabaseIdentifier.values()) {
            DataSource dataSource = dataSourcesByBeanName.get(identifier.dataSourceBeanName());
            dataSourcesByDatabase.put(identifier, dataSource);
            jdbcTemplatesByDatabase.put(identifier, new JdbcTemplate(dataSource));
        }
    }

    public DatabaseStatus checkStatus(PostgresDatabaseIdentifier identifier, String databaseName) {
        long start = System.currentTimeMillis();
        try (var connection = dataSourcesByDatabase.get(identifier).getConnection()) {
            connection.isValid(3);
            long elapsed = System.currentTimeMillis() - start;

            JdbcTemplate jdbcTemplate = jdbcTemplatesByDatabase.get(identifier);
            Long appliedCount;
            String lastId = null;
            OffsetDateTime lastExecutedAt = null;
            try {
                appliedCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM databasechangelog", Long.class);
                Map<String, Object> lastRow = jdbcTemplate.queryForMap(
                        "SELECT id, dateexecuted FROM databasechangelog ORDER BY orderexecuted DESC LIMIT 1");
                lastId = String.valueOf(lastRow.get("id"));
                Object dateExecuted = lastRow.get("dateexecuted");
                if (dateExecuted instanceof java.sql.Timestamp ts) {
                    lastExecutedAt = ts.toInstant().atOffset(java.time.ZoneOffset.UTC);
                }
            } catch (Exception liquibaseTableMissing) {
                appliedCount = null;
            }

            return new DatabaseStatus(databaseName, true, null, appliedCount, lastId, lastExecutedAt, elapsed);
        } catch (SQLException e) {
            long elapsed = System.currentTimeMillis() - start;
            return new DatabaseStatus(databaseName, false, e.getMessage(), null, null, null, elapsed);
        }
    }

    public List<TableInfo> tableInfo(PostgresDatabaseIdentifier identifier) {
        JdbcTemplate jdbcTemplate = jdbcTemplatesByDatabase.get(identifier);
        List<String> tableNames = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name",
                String.class);

        return tableNames.stream()
                .map(tableName -> {
                    Long rowCount = jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM \"" + tableName + "\"", Long.class);
                    return new TableInfo(tableName, rowCount != null ? rowCount : 0L);
                })
                .toList();
    }
}
