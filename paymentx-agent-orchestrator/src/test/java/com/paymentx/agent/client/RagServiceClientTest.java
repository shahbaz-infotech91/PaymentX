package com.paymentx.agent.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.paymentx.agent.config.AgentOrchestratorProperties;
import com.paymentx.agent.exception.AgentException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.boot.web.client.RestTemplateBuilder;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.notMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * English:
 * Real wire-level tests for RagServiceClient - matches this platform's
 * established WireMock client-test pattern (PaymentServiceClientTest in
 * paymentx-mcp-gateway, Phase 3.7; AiPlatformClientTest in Control
 * Center). Verifies real success-envelope parsing (including real
 * sources), that a real INSUFFICIENT_CONTEXT/REFUSED status is returned
 * as data, never thrown as an exception, and that real 5xx/unreachable
 * failures are thrown as retryable AgentException.
 *
 * Hinglish:
 * RagServiceClient ke liye real wire-level tests - is platform ke
 * established WireMock client-test pattern se match karte hain
 * (paymentx-mcp-gateway ka PaymentServiceClientTest, Phase 3.7; Control
 * Center ka AiPlatformClientTest). Real success-envelope parsing
 * verify karta hai (real sources sameत), ki ek real INSUFFICIENT_CONTEXT/
 * REFUSED status data ke roop me return hota hai, kabhi ek exception ke
 * roop me throw nahi hota, aur ki real 5xx/unreachable failures
 * retryable AgentException ke roop me throw hote hain.
 */
class RagServiceClientTest {

    private static WireMockServer wireMockServer;
    private RagServiceClient client;

    @BeforeAll
    static void startServer() {
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();
    }

    @AfterAll
    static void stopServer() {
        wireMockServer.stop();
    }

    @BeforeEach
    void setUp() {
        wireMockServer.resetAll();
        AgentOrchestratorProperties properties = new AgentOrchestratorProperties();
        properties.setRagServiceUrl("http://localhost:" + wireMockServer.port());
        properties.setRagConnectTimeoutMs(2000);
        properties.setRagReadTimeoutMs(2000);
        client = new RagServiceClient(new RestTemplateBuilder(), properties);
    }

    @Test
    void query_realSuccessEnvelope_parsesRealAnswerAndSources() {
        wireMockServer.stubFor(post(urlPathEqualTo("/api/v1/rag/query")).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json").withBody("""
                        {"success": true, "data": {"answer": "Real grounded answer.", "status": "SUCCESS",
                          "sources": [{"documentId": "d1", "chunkId": "c1", "source": "Runbook", "score": 0.8}],
                          "metadata": {}}}
                        """)));

        RagServiceClient.RagQueryResult result = client.query("why do duplicate payments fail", "corr-1");

        assertThat(result.answer()).isEqualTo("Real grounded answer.");
        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.sources()).hasSize(1);
        assertThat(result.sources().get(0).source()).isEqualTo("Runbook");
        wireMockServer.verify(postRequestedFor(urlPathEqualTo("/api/v1/rag/query"))
                .withHeader("X-Correlation-Id", equalTo("corr-1")));
    }

    @Test
    void query_realInsufficientContextStatus_returnedAsDataNotException() {
        wireMockServer.stubFor(post(urlPathEqualTo("/api/v1/rag/query")).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json").withBody("""
                        {"success": true, "data": {"answer": "insufficient", "status": "INSUFFICIENT_CONTEXT", "sources": [], "metadata": {}}}
                        """)));

        RagServiceClient.RagQueryResult result = client.query("unrelated question", "corr-1");

        assertThat(result.status()).isEqualTo("INSUFFICIENT_CONTEXT");
    }

    @Test
    void query_real503_throwsRetryableRagServiceUnavailable() {
        wireMockServer.stubFor(post(urlPathEqualTo("/api/v1/rag/query")).willReturn(aResponse()
                .withStatus(503).withHeader("Content-Type", "application/json").withBody("""
                        {"success": false, "error": {"errorCode": "EMBEDDING_SERVICE_UNAVAILABLE", "message": "down"}}
                        """)));

        assertThatThrownBy(() -> client.query("a question", "corr-1"))
                .isInstanceOf(AgentException.class)
                .satisfies(ex -> assertThat(((AgentException) ex).isRetryable()).isTrue());
    }

    @Test
    void query_unreachableService_throwsRetryableRagServiceUnavailable() {
        wireMockServer.stop();
        try {
            assertThatThrownBy(() -> client.query("a question", "corr-1"))
                    .isInstanceOf(AgentException.class)
                    .satisfies(ex -> assertThat(((AgentException) ex).isRetryable()).isTrue());
        } finally {
            wireMockServer.start();
        }
    }

    // ================================================================
    // Phase 4.2.2 - RAG metadata-filter propagation (Part D items 1-2, 7-8)
    // ================================================================

    private void stubGenericSuccess() {
        wireMockServer.stubFor(post(urlPathEqualTo("/api/v1/rag/query")).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json").withBody("""
                        {"success": true, "data": {"answer": "ok", "status": "SUCCESS", "sources": [], "metadata": {}}}
                        """)));
    }

    @Test
    void query_existingTwoArgOverload_neverSendsAFiltersKey() {
        // Item 1 - the pre-existing signature, unchanged, must produce byte-identical requests to
        // before this phase.
        stubGenericSuccess();

        client.query("payment timeout", "corr-1");

        wireMockServer.verify(postRequestedFor(urlPathEqualTo("/api/v1/rag/query"))
                .withRequestBody(notMatching(".*filters.*")));
    }

    @Test
    void query_threeArgOverloadWithNullFilters_behavesIdenticallyToTwoArgOverload() {
        stubGenericSuccess();

        RagServiceClient.RagQueryResult result = client.query("payment timeout", null, "corr-1");

        assertThat(result.status()).isEqualTo("SUCCESS");
        wireMockServer.verify(postRequestedFor(urlPathEqualTo("/api/v1/rag/query"))
                .withRequestBody(notMatching(".*filters.*")));
    }

    @Test
    void query_emptyRagQueryFilters_sendsNoFiltersKeyEither() {
        stubGenericSuccess();

        client.query("payment timeout", new RagQueryFilters(null, null, null, null, null, null), "corr-1");

        wireMockServer.verify(postRequestedFor(urlPathEqualTo("/api/v1/rag/query"))
                .withRequestBody(notMatching(".*filters.*")));
    }

    @ParameterizedTest
    @EnumSource(PaymentScheme.class)
    void query_withPaymentSchemeFilter_propagatesExactSchemeToRequestBody(PaymentScheme scheme) {
        // Items 2-5 - all three real schemes, parameterized, each verified to reach the real
        // downstream request body unchanged.
        stubGenericSuccess();

        client.query("payment timeout", new RagQueryFilters(null, null, scheme, null, null, null), "corr-1");

        wireMockServer.verify(postRequestedFor(urlPathEqualTo("/api/v1/rag/query"))
                .withRequestBody(matchingJsonPath("$.filters.paymentScheme", equalTo(scheme.name()))));
    }

    @Test
    void query_withAllFilterFields_propagatesTheExactRequestBodyRagServiceExpects() {
        // Item 7 - matches RagServiceImpl.validateFilters' real constraints exactly: simple
        // string/boolean values, well under its 10-filter cap.
        stubGenericSuccess();
        RagQueryFilters filters = new RagQueryFilters(
                "ERROR_CODE_REFERENCE", "paymentx-validation-service", PaymentScheme.REAL_TIME_PAYMENT,
                "DUPLICATE_PAYMENT_REFERENCE", "MEDIUM", false);

        client.query("why did this real-time payment fail", filters, "corr-1");

        wireMockServer.verify(postRequestedFor(urlPathEqualTo("/api/v1/rag/query"))
                .withRequestBody(equalToJson("""
                        {
                          "query": "why did this real-time payment fail",
                          "filters": {
                            "documentType": "ERROR_CODE_REFERENCE",
                            "service": "paymentx-validation-service",
                            "paymentScheme": "REAL_TIME_PAYMENT",
                            "errorCode": "DUPLICATE_PAYMENT_REFERENCE",
                            "severity": "MEDIUM",
                            "retryable": false
                          }
                        }
                        """, true, true)));
    }

    // Item 6 ("unsupported scheme is rejected or safely handled"): PaymentScheme is a closed Java
    // enum with exactly the three real values - an unsupported scheme cannot be constructed at
    // all, a compile-time guarantee stronger than a runtime check. See
    // RagQueryFiltersTest.paymentScheme_isExactlyTheThreeRealPaymentXSchemes for the regression
    // guard against silently widening that enum.

    // Item 8 ("no authorization bypass is possible through filters"): structurally guaranteed,
    // not merely tested - RagServiceClient has zero dependency on policy.AgentToolPolicy,
    // planning.AgentPlanValidator, or client.McpToolClient (confirmed by this class's own import
    // list), and RagQueryFilters carries only document-metadata fields, never a tool name,
    // permission, or role. There is no code path through which a filter value could reach MCP
    // Gateway's authorization or this service's own tool policy at all.
}
