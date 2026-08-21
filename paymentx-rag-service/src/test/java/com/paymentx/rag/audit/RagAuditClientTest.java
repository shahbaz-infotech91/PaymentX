package com.paymentx.rag.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.paymentx.rag.config.RagProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Phase 3.10.2 - Real wire-level tests for RagAuditClient, matching this platform's established
 * WireMock client-test pattern (see paymentx-mcp-gateway's PaymentServiceClientTest javadoc for the
 * precedent). Constructs RagAuditClient directly against a real WireMock server standing in for Audit
 * Service - proves the real HTTP body/headers this client sends, and proves the mandatory fail-open
 * contract (500, timeout, unreachable) never throws back to the caller.
 */
class RagAuditClientTest {

    private static final String SECRET_SENTINEL = "TEST_SECRET_SHOULD_NEVER_APPEAR_12345";

    private static WireMockServer auditService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeAll
    static void startServer() {
        auditService = new WireMockServer(0);
        auditService.start();
    }

    @AfterAll
    static void stopServer() {
        auditService.stop();
    }

    @BeforeEach
    void resetServer() {
        auditService.resetAll();
    }

    private RagAuditClient newClient() {
        RagProperties properties = new RagProperties();
        properties.setAuditServiceUrl("http://localhost:" + auditService.port());
        properties.setAuditWriteConnectTimeoutMs(500);
        properties.setAuditWriteReadTimeoutMs(500);
        return new RagAuditClient(new RestTemplateBuilder(), properties);
    }

    private RagAuditEvent sampleEvent(String status) {
        return new RagAuditEvent("corr-1", "req-1", 2, List.of("chunk-001", "chunk-002"),
                List.of(0.91, 0.42), 37L, status);
    }

    @Test
    void recordRetrieval_realSuccessfulPost_sendsCorrectBodyAndPayload() throws Exception {
        auditService.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/api/v1/audit-events"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":true}")));

        newClient().recordRetrieval(sampleEvent(RagAuditClient.STATUS_SUCCESS));

        var requests = auditService.findAll(postRequestedFor(urlPathEqualTo("/api/v1/audit-events")));
        assertThat(requests).hasSize(1);
        JsonNode body = objectMapper.readTree(requests.get(0).getBodyAsString());

        assertThat(body.path("eventType").asText()).isEqualTo("API_REQUEST");
        assertThat(body.path("sourceService").asText()).isEqualTo("rag-service");
        assertThat(body.path("correlationId").asText()).isEqualTo("corr-1");
        assertThat(requests.get(0).getHeader("X-Correlation-Id")).isEqualTo("corr-1");
        assertThat(requests.get(0).getHeader("X-Roles")).isEqualTo("AUDIT_WRITER");

        JsonNode payload = objectMapper.readTree(body.path("payload").asText());
        assertThat(payload.path("requestId").asText()).isEqualTo("req-1");
        assertThat(payload.path("retrievalCount").asInt()).isEqualTo(2);
        assertThat(payload.path("latencyMs").asLong()).isEqualTo(37L);
        assertThat(payload.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(payload.path("chunkIds")).extracting(JsonNode::asText).containsExactly("chunk-001", "chunk-002");
        assertThat(payload.path("similarityScores")).extracting(JsonNode::asDouble).containsExactly(0.91, 0.42);
    }

    /** Phase 3.10.2 Step 15 - the outgoing payload must contain ONLY the approved allow-list of keys. */
    @Test
    void recordRetrieval_payload_containsOnlyApprovedFieldsAndNoSentinel() throws Exception {
        auditService.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/api/v1/audit-events"))
                .willReturn(aResponse().withStatus(200)));

        // Even a chunk identifier and requestId that happen to be as long/suspicious as a secret must
        // still only ever appear as themselves - there is no code path that could smuggle a prompt,
        // answer, or credential into this payload, because RagAuditEvent structurally has no such field.
        RagAuditEvent event = new RagAuditEvent("corr-1", SECRET_SENTINEL, 1, List.of("chunk-001"),
                List.of(0.9), 10L, RagAuditClient.STATUS_SUCCESS);
        newClient().recordRetrieval(event);

        var requests = auditService.findAll(postRequestedFor(urlPathEqualTo("/api/v1/audit-events")));
        JsonNode body = objectMapper.readTree(requests.get(0).getBodyAsString());
        JsonNode payload = objectMapper.readTree(body.path("payload").asText());

        java.util.Set<String> allowedKeys = java.util.Set.of(
                "requestId", "retrievalCount", "chunkIds", "similarityScores", "latencyMs", "status");
        java.util.Iterator<String> fieldNames = payload.fieldNames();
        java.util.Set<String> actualKeys = new java.util.HashSet<>();
        fieldNames.forEachRemaining(actualKeys::add);
        assertThat(actualKeys).isEqualTo(allowedKeys);
        assertThat(body.toString()).doesNotContain("prompt", "answer", "content");
    }

    @Test
    void recordRetrieval_auditServiceReturns500_doesNotThrow() {
        auditService.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/api/v1/audit-events"))
                .willReturn(aResponse().withStatus(500)));

        assertThatCode(() -> newClient().recordRetrieval(sampleEvent(RagAuditClient.STATUS_SUCCESS)))
                .doesNotThrowAnyException();
    }

    @Test
    void recordRetrieval_auditServiceTimesOut_doesNotThrow() {
        auditService.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/api/v1/audit-events"))
                .willReturn(aResponse().withStatus(200).withFixedDelay(3000)));

        assertThatCode(() -> newClient().recordRetrieval(sampleEvent(RagAuditClient.STATUS_SUCCESS)))
                .doesNotThrowAnyException();
    }

    @Test
    void recordRetrieval_auditServiceUnreachable_doesNotThrow() {
        RagProperties properties = new RagProperties();
        properties.setAuditServiceUrl("http://localhost:1"); // deliberately nothing listens here
        properties.setAuditWriteConnectTimeoutMs(500);
        properties.setAuditWriteReadTimeoutMs(500);
        RagAuditClient client = new RagAuditClient(new RestTemplateBuilder(), properties);

        assertThatCode(() -> client.recordRetrieval(sampleEvent(RagAuditClient.STATUS_SUCCESS)))
                .doesNotThrowAnyException();
    }

    @Test
    void recordRetrieval_malformedAuditServiceResponse_doesNotThrow() {
        auditService.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/api/v1/audit-events"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{not valid json")));

        assertThatCode(() -> newClient().recordRetrieval(sampleEvent(RagAuditClient.STATUS_SUCCESS)))
                .doesNotThrowAnyException();
    }
}
