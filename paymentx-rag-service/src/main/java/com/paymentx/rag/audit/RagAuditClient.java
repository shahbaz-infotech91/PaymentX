package com.paymentx.rag.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentx.common.constant.HeaderConstants;
import com.paymentx.rag.config.RagProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * English:
 * Phase 3.10.2's RAG Service audit layer - deliberately mirrors
 * paymentx-mcp-gateway's proven McpAuditClient pattern verbatim (constructor shape, timeout wiring,
 * fire-and-forget POST to the real, already-existing Audit Service, fail-open try/catch) rather than
 * inventing a new one, per Phase 3.10's approved requirement. Calls the real, already-existing Audit
 * Service POST /api/v1/audit-events - no new audit database, no schema change. Uses
 * EventType.API_REQUEST (the same closest-real-fit value McpAuditClient already uses, for the same
 * reason: this codebase's audit-service has no dedicated "AI_RETRIEVAL"/"RAG_QUERY" enum value, and
 * inventing one would require an out-of-scope Audit Service schema/enum change - see
 * PAYMENTX_PHASE_3_10_2_RAG_AUDIT.md §19 for why that was not done). `payload` is a small, redacted JSON
 * string carrying ONLY requestId/retrievalCount/chunkIds/similarityScores/latencyMs/status - never the
 * user's query, never the LLM's answer, never a secret/credential, never full chunk/document content
 * (see RagAuditEvent's own javadoc for exactly what each field means).
 * FAIL-OPEN (Phase 3.10.2's mandatory requirement): every exception this call could throw (connection
 * refused, timeout, 5xx, malformed response) is caught here and only logged - a downed/slow Audit
 * Service must never make a real RAG query fail or appear to fail. This is the exact same fail-open
 * contract McpAuditClient already established; only the underlying RAG retrieval failure itself (a real
 * RagException from embedding/vector service) is ever allowed to propagate, and it does so from
 * RagServiceImpl BEFORE this client is even called on that path - audit failure and RAG failure are two
 * structurally distinct, never-conflated things.
 * Why it exists: Phase 3.10.2's approved RAG Service audit layer requirement.
 * How it communicates with other components: called once by RagServiceImpl.query() per real query, never
 * called from any other class.
 *
 * Hinglish:
 * Phase 3.10.2 ka RAG Service audit layer - jaan-boojh kar paymentx-mcp-gateway ke proven
 * McpAuditClient pattern ko verbatim mirror karta hai (constructor shape, timeout wiring, real,
 * already-existing Audit Service ko fire-and-forget POST, fail-open try/catch), ek naya invent karne ke
 * bajaye, Phase 3.10 ke approved requirement ke hisaab se. Real, already-existing Audit Service ke POST
 * /api/v1/audit-events ko call karta hai - koi nayi audit database nahi, koi schema change nahi.
 * EventType.API_REQUEST use karta hai (wahi closest-real-fit value jo McpAuditClient already use karta
 * hai, usi reason se: is codebase ki audit-service ke paas koi dedicated "AI_RETRIEVAL"/"RAG_QUERY" enum
 * value hai hi nahi, aur ek naya invent karne ke liye ek out-of-scope Audit Service schema/enum change
 * chahiye hota). `payload` ek chhota, redacted JSON string hai jo SIRF requestId/retrievalCount/
 * chunkIds/similarityScores/latencyMs/status carry karta hai - kabhi user ka query nahi, kabhi LLM ka
 * answer nahi, kabhi koi secret/credential nahi, kabhi full chunk/document content nahi.
 * FAIL-OPEN (Phase 3.10.2 ka mandatory requirement): is call se aa sakne wali har exception (connection
 * refused, timeout, 5xx, malformed response) yahan catch hoti hai aur sirf log hoti hai - ek down/slow
 * Audit Service ko kabhi ek real RAG query ko fail ya fail jaisa dikhana nahi chahiye. Ye wahi exact
 * fail-open contract hai jo McpAuditClient already establish kar chuka hai; sirf real RAG retrieval
 * failure khud (embedding/vector service se ek real RagException) hi propagate hone ki ijazat paata hai,
 * aur wo us path par RagServiceImpl se PEHLE hi hota hai is client ko call kiye jaane se - audit failure
 * aur RAG failure do structurally distinct, kabhi conflate na hone wali cheezein hain.
 * Ye kyu hai: Phase 3.10.2 ka approved RAG Service audit layer requirement.
 * Dusre components se kaise communicate karta hai: RagServiceImpl.query() dwara har real query ke liye
 * ek baar call hota hai, kisi doosri class se kabhi call nahi hota.
 */
@Component
@Slf4j
public class RagAuditClient {

    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_NO_RESULTS = "NO_RESULTS";
    public static final String STATUS_FAILURE = "FAILURE";

    private final RestTemplate restTemplate;
    private final RagProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RagAuditClient(RestTemplateBuilder builder, RagProperties properties) {
        this.properties = properties;
        this.restTemplate = builder
                .requestFactory(SimpleClientHttpRequestFactory::new)
                .connectTimeout(Duration.ofMillis(properties.getAuditWriteConnectTimeoutMs()))
                .readTimeout(Duration.ofMillis(properties.getAuditWriteReadTimeoutMs()))
                .build();
    }

    public void recordRetrieval(RagAuditEvent event) {
        try {
            Map<String, Object> payloadMap = new LinkedHashMap<>();
            payloadMap.put("requestId", event.requestId());
            payloadMap.put("retrievalCount", event.retrievalCount());
            payloadMap.put("chunkIds", event.chunkIds());
            payloadMap.put("similarityScores", event.similarityScores());
            payloadMap.put("latencyMs", event.latencyMs());
            payloadMap.put("status", event.status());
            String payload = objectMapper.writeValueAsString(payloadMap);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("eventType", "API_REQUEST");
            body.put("sourceService", "rag-service");
            body.put("actorId", "rag-service");
            body.put("actorType", "AI_RETRIEVAL");
            body.put("correlationId", event.correlationId());
            body.put("reference", "rag.query");
            body.put("payload", payload);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.add("X-Roles", "AUDIT_WRITER");
            if (event.correlationId() != null) {
                headers.add(HeaderConstants.CORRELATION_ID, event.correlationId());
            }

            restTemplate.postForEntity(properties.getAuditServiceUrl() + "/api/v1/audit-events",
                    new HttpEntity<>(body, headers), String.class);
        } catch (Exception auditWriteFailure) {
            // Deliberately swallowed - see class javadoc: a downed/slow Audit Service must never make the
            // real RAG query this event describes fail or appear to fail (Phase 3.10.2's fail-open
            // requirement).
            log.warn("Failed to record RAG audit event requestId={} reason={}", event.requestId(), auditWriteFailure.getMessage());
        }
    }
}
