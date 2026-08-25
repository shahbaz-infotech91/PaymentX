package com.paymentx.controlcenter.repository;

import com.paymentx.controlcenter.dto.agent.AgentExecutionDetail;
import com.paymentx.controlcenter.dto.agent.AgentExecutionFilter;
import com.paymentx.controlcenter.dto.agent.AgentExecutionSummary;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Phase 4.7 - Execution History's real data source: paymentx_audit.audit_event, the SAME table
 * every agent execution already, unconditionally writes one row to (AgentAuditClient, Phase 4.1
 * onward, enriched this phase with userQuery/paymentReference/answer/sources - see that class's
 * own javadoc). No new datastore, no new writable connection - this repository is read-only
 * against the same "auditDataSource" DataSource bean AuditEventRepository already uses (mirrors
 * that class's exact hardcoded-parameterized-SELECT-only convention), scoped to
 * source_service='agent-orchestrator' AND event_type='API_REQUEST' so the generic, cross-service
 * Audit Timeline feature's own repository/queries are never touched or risked.
 *
 * WHY a dedicated Control Center execution-history table was NOT created: Control Center has no
 * writable database of its own by explicit, deliberate design (see application.yml's own "no
 * datasource/JPA auto-configuration here on purpose... no @Entity classes and no single 'the'
 * database" comment) - every one of its 7 named DataSource beans is read-only against another
 * service's own database. Building a new writable one just for this feature would be a real
 * architectural change this phase's own scope does not require, when the data already exists,
 * already gets written unconditionally by the real backend execution (not something a frontend
 * click could ever fake), and is already efficiently queryable via native jsonb operators.
 */
@Repository
public class AgentExecutionRepository {

    private static final String BASE_WHERE = "source_service = 'agent-orchestrator' AND event_type = 'API_REQUEST'";

    private static final String SUMMARY_COLUMNS =
            "id, reference, correlation_id, occurred_at, " +
                    "payload->>'agentId' AS agent_id, " +
                    "payload->>'userQuery' AS user_query, " +
                    "payload->>'paymentReference' AS payment_reference, " +
                    "payload->>'status' AS outcome, " +
                    "payload->>'latencyMs' AS latency_ms, " +
                    "payload->>'toolCallCount' AS tool_call_count, " +
                    "payload->>'ragUsed' AS rag_used, " +
                    "payload->>'provider' AS provider, " +
                    "payload->>'fallbackUsed' AS fallback_used ";

    private static final RowMapper<AgentExecutionSummary> SUMMARY_ROW_MAPPER = (rs, rowNum) -> new AgentExecutionSummary(
            rs.getString("reference"),
            rs.getString("correlation_id"),
            rs.getString("agent_id"),
            rs.getString("user_query"),
            rs.getString("payment_reference"),
            rs.getString("outcome"),
            rs.getObject("occurred_at", OffsetDateTime.class),
            parseLong(rs.getString("latency_ms")),
            parseInt(rs.getString("tool_call_count")),
            parseBoolean(rs.getString("rag_used")),
            rs.getString("provider"),
            parseBoolean(rs.getString("fallback_used")));

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AgentExecutionRepository(@Qualifier("auditDataSource") DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    public List<AgentExecutionSummary> findPage(AgentExecutionFilter filter, int page, int size) {
        StringBuilder sql = new StringBuilder("SELECT " + SUMMARY_COLUMNS + "FROM audit_event WHERE " + BASE_WHERE);
        List<Object> params = new ArrayList<>();
        appendFilters(sql, params, filter);
        sql.append(" ORDER BY occurred_at DESC LIMIT ? OFFSET ?");
        params.add(size);
        params.add(page * size);
        return jdbcTemplate.query(sql.toString(), SUMMARY_ROW_MAPPER, params.toArray());
    }

    public long count(AgentExecutionFilter filter) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM audit_event WHERE " + BASE_WHERE);
        List<Object> params = new ArrayList<>();
        appendFilters(sql, params, filter);
        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
        return count != null ? count : 0L;
    }

    public Optional<AgentExecutionDetail> findByExecutionId(String executionId) {
        List<AgentExecutionDetail> rows = jdbcTemplate.query(
                "SELECT id, reference, correlation_id, occurred_at, payload FROM audit_event " +
                        "WHERE " + BASE_WHERE + " AND reference = ?",
                (rs, rowNum) -> toDetail(rs.getString("reference"), rs.getString("correlation_id"),
                        rs.getObject("occurred_at", OffsetDateTime.class), rs.getString("payload")),
                executionId);
        return rows.stream().findFirst();
    }

    private void appendFilters(StringBuilder sql, List<Object> params, AgentExecutionFilter filter) {
        if (filter == null) {
            return;
        }
        if (hasText(filter.agentId())) {
            sql.append(" AND payload->>'agentId' = ?");
            params.add(filter.agentId());
        }
        if (hasText(filter.outcome())) {
            sql.append(" AND payload->>'status' = ?");
            params.add(filter.outcome());
        }
        if (hasText(filter.paymentReference())) {
            sql.append(" AND payload->>'paymentReference' = ?");
            params.add(filter.paymentReference());
        }
        if (hasText(filter.executionId())) {
            sql.append(" AND reference = ?");
            params.add(filter.executionId());
        }
        if (filter.fromDate() != null) {
            sql.append(" AND occurred_at >= ?");
            params.add(filter.fromDate());
        }
        if (filter.toDate() != null) {
            sql.append(" AND occurred_at <= ?");
            params.add(filter.toDate());
        }
    }

    private AgentExecutionDetail toDetail(String executionId, String correlationId, OffsetDateTime occurredAt, String rawPayload) {
        JsonNode payload = parsePayload(rawPayload);
        Long latencyMs = parseLong(payload.path("latencyMs").isMissingNode() ? null : payload.path("latencyMs").asText(null));
        OffsetDateTime startedAt = latencyMs != null ? occurredAt.minusNanos(latencyMs * 1_000_000L) : null;

        List<String> sources = new ArrayList<>();
        payload.path("sources").forEach(node -> sources.add(node.asText()));

        List<AgentExecutionDetail.ToolCallNameStatus> toolsCalled = new ArrayList<>();
        payload.path("toolCalls").forEach(node -> toolsCalled.add(
                new AgentExecutionDetail.ToolCallNameStatus(node.path("tool").asText(null), node.path("status").asText(null))));

        return new AgentExecutionDetail(
                executionId,
                correlationId,
                payload.path("agentId").asText(null),
                payload.path("userQuery").asText(null),
                payload.path("paymentReference").isMissingNode() || payload.path("paymentReference").isNull()
                        ? null : payload.path("paymentReference").asText(null),
                payload.path("status").asText(null),
                payload.path("answer").isMissingNode() || payload.path("answer").isNull()
                        ? null : payload.path("answer").asText(null),
                sources,
                toolsCalled,
                startedAt,
                occurredAt,
                latencyMs,
                payload.path("iterations").isMissingNode() ? null : payload.path("iterations").asInt(),
                payload.path("ragUsed").isMissingNode() ? null : payload.path("ragUsed").asBoolean(),
                null,
                payload.path("provider").isMissingNode() || payload.path("provider").isNull()
                        ? null : payload.path("provider").asText(null),
                payload.path("fallbackUsed").isMissingNode() ? null : payload.path("fallbackUsed").asBoolean(),
                payload.path("fallbackReason").isMissingNode() || payload.path("fallbackReason").isNull()
                        ? null : payload.path("fallbackReason").asText(null));
    }

    private JsonNode parsePayload(String rawPayload) {
        try {
            return rawPayload != null ? objectMapper.readTree(rawPayload) : objectMapper.createObjectNode();
        } catch (Exception malformed) {
            return objectMapper.createObjectNode();
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static Long parseLong(String value) {
        try {
            return value != null ? Long.parseLong(value) : null;
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    private static Integer parseInt(String value) {
        try {
            return value != null ? Integer.parseInt(value) : null;
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    private static Boolean parseBoolean(String value) {
        return value != null ? Boolean.parseBoolean(value) : null;
    }
}
