package com.paymentx.vector.controller;

import com.paymentx.common.dto.ApiResponse;
import com.paymentx.vector.dto.StoreDocumentRequest;
import com.paymentx.vector.dto.StoreDocumentResponse;
import com.paymentx.vector.dto.VectorHealthResponse;
import com.paymentx.vector.dto.VectorSearchRequest;
import com.paymentx.vector.dto.VectorSearchResponse;
import com.paymentx.vector.repository.AiDocumentChunkRepository;
import com.paymentx.vector.repository.AiDocumentEmbeddingRepository;
import com.paymentx.vector.repository.AiDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * English:
 * A real, end-to-end HTTP test of VectorController against the real
 * Spring context (SecurityConfig, GlobalExceptionHandler, real
 * Postgres+pgvector via Testcontainers) - matches
 * PromptControllerIntegrationTest's/LlmControllerIntegrationTest's
 * exact pattern. What it verifies: a blank documentKey is rejected with
 * a real 400 VALIDATION_ERROR before ever reaching
 * VectorStoreServiceImpl; storeDocument/deleteDocument reject a caller
 * with no X-Roles header (403, real AuthorizationDeniedException
 * mapping - see HeaderRoleAuthenticationFilter/SecurityConfig) and
 * accept one with VECTOR_ADMIN (Step 29); search/health stay reachable
 * with no role header at all (Step 29's "open reads" half); a real
 * search round-trips through the full stack into a real HTTP 200; a
 * dimension-mismatched embedding is rejected with a real 422 through
 * GlobalExceptionHandler; GET /health reports the real
 * database/extension state.
 * Why it exists: Step 24/27's "authorization, controller validation,
 * global exception mapping" specifically needs a real HTTP-layer test,
 * not just a service-layer unit test, since @PreAuthorize/@Valid/
 * @RestControllerAdvice are all Spring-managed behavior mocked unit
 * tests never actually exercise.
 * How it communicates with other components: boots the real Spring
 * context (SecurityConfig, GlobalExceptionHandler, Liquibase-migrated
 * schema, the works) against an ephemeral Testcontainers
 * Postgres+pgvector.
 *
 * Hinglish:
 * VectorController ka ek real, end-to-end HTTP test, real Spring
 * context ke against (SecurityConfig, GlobalExceptionHandler, real
 * Postgres+pgvector Testcontainers ke through) -
 * PromptControllerIntegrationTest/LlmControllerIntegrationTest ke exact
 * pattern se match karta hai. Ye kya verify karta hai: ek blank
 * documentKey ek real 400 VALIDATION_ERROR se reject hota hai,
 * VectorStoreServiceImpl tak pahunchne se pehle hi;
 * storeDocument/deleteDocument ek aise caller ko reject karte hain
 * jiske paas X-Roles header nahi hai (403, real
 * AuthorizationDeniedException mapping -
 * HeaderRoleAuthenticationFilter/SecurityConfig dekho) aur VECTOR_ADMIN
 * wale ko accept karte hain (Step 29); search/health kisi bhi role
 * header ke bina bhi reachable rehte hain (Step 29 ka "open reads"
 * half); ek real search poore stack se hote hue ek real HTTP 200 me
 * round-trip karta hai; ek dimension-mismatched embedding
 * GlobalExceptionHandler ke through ek real 422 se reject hota hai; GET
 * /health real database/extension state report karta hai.
 * Ye kyu hai: Step 24/27 ki "authorization, controller validation,
 * global exception mapping" ko specifically ek real HTTP-layer test
 * chahiye, sirf ek service-layer unit test nahi, kyunki @PreAuthorize/
 * @Valid/@RestControllerAdvice sab Spring-managed behavior hai jise
 * mocked unit tests actually kabhi exercise nahi karte.
 * Dusre components se kaise communicate karta hai: real Spring context
 * boot karta hai (SecurityConfig, GlobalExceptionHandler, Liquibase-
 * migrated schema, sab kuch) ek ephemeral Testcontainers
 * Postgres+pgvector ke against.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class VectorControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:0.8.0-pg16")
            .withDatabaseName("paymentx_ai_vector_controller_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    // Phase 3.5 vector database migration: matches the real, migrated schema (vector(384)).
    private static final int DIMENSION = 384;

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AiDocumentRepository documentRepository;

    @Autowired
    private AiDocumentChunkRepository chunkRepository;

    @Autowired
    private AiDocumentEmbeddingRepository embeddingRepository;

    /**
     * All tests in this class share one Spring context and one Testcontainers Postgres (matching this
     * platform's established @SpringBootTest-with-Testcontainers pattern), so without this cleanup a
     * later test's identical constantVector(1.0f) fixture would tie in cosine distance with an earlier
     * test's leftover rows - real, deterministic ordering among non-tied vectors is exactly what
     * VectorStoreServiceImplTest already proves; this cleanup exists purely to keep each HTTP-layer test
     * independent of write ordering left behind by whichever test ran before it.
     */
    @BeforeEach
    void cleanDatabase() {
        embeddingRepository.deleteAll();
        chunkRepository.deleteAll();
        documentRepository.deleteAll();
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private static List<Float> constantVector(float value) {
        List<Float> vector = new ArrayList<>(DIMENSION);
        for (int i = 0; i < DIMENSION; i++) {
            vector.add(value);
        }
        return vector;
    }

    private HttpEntity<StoreDocumentRequest> storeRequestWithRole(StoreDocumentRequest body, String... roles) {
        HttpHeaders headers = new HttpHeaders();
        if (roles.length > 0) {
            headers.add("X-Roles", String.join(",", roles));
        }
        return new HttpEntity<>(body, headers);
    }

    @Test
    void storeDocument_blankDocumentKey_returns400ValidationError() {
        StoreDocumentRequest request = new StoreDocumentRequest("   ", "Name", "runbook", null, "v1", null,
                List.of(new StoreDocumentRequest.ChunkInput("content", constantVector(1.0f), "openai", "text-embedding-3-small", null)));

        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                url("/api/v1/vector/documents"), HttpMethod.POST, storeRequestWithRole(request, "VECTOR_ADMIN"),
                new ParameterizedTypeReference<>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error().errorCode()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void storeDocument_noVectorAdminRole_returns403() {
        StoreDocumentRequest request = new StoreDocumentRequest("doc-noauth", "Name", "runbook", null, "v1", null,
                List.of(new StoreDocumentRequest.ChunkInput("content", constantVector(1.0f), "openai", "text-embedding-3-small", null)));

        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                url("/api/v1/vector/documents"), HttpMethod.POST, storeRequestWithRole(request),
                new ParameterizedTypeReference<>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void storeDocument_withVectorAdminRole_returns201AndRealResponse() {
        StoreDocumentRequest request = new StoreDocumentRequest("doc-http-store", "Name", "runbook", null, "v1", null,
                List.of(new StoreDocumentRequest.ChunkInput("content", constantVector(1.0f), "openai", "text-embedding-3-small", null)));

        ResponseEntity<ApiResponse<StoreDocumentResponse>> response = restTemplate.exchange(
                url("/api/v1/vector/documents"), HttpMethod.POST, storeRequestWithRole(request, "VECTOR_ADMIN"),
                new ParameterizedTypeReference<>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().data().created()).isTrue();
        assertThat(response.getBody().data().chunkCount()).isEqualTo(1);
    }

    @Test
    void storeDocument_dimensionMismatch_returns422WithErrorCode() {
        List<Float> wrongDimension = constantVector(1.0f).subList(0, 50);
        StoreDocumentRequest request = new StoreDocumentRequest("doc-http-baddim", "Name", "runbook", null, "v1", null,
                List.of(new StoreDocumentRequest.ChunkInput("content", wrongDimension, "openai", "text-embedding-3-small", null)));

        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                url("/api/v1/vector/documents"), HttpMethod.POST, storeRequestWithRole(request, "VECTOR_ADMIN"),
                new ParameterizedTypeReference<>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().error().errorCode()).isEqualTo("VECTOR_DIMENSION_MISMATCH");
    }

    @Test
    void deleteDocument_noVectorAdminRole_returns403() {
        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                url("/api/v1/vector/documents/some-key/versions/v1"), HttpMethod.DELETE, HttpEntity.EMPTY,
                new ParameterizedTypeReference<>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void search_openToAnyCaller_returns200WithRealResults() {
        StoreDocumentRequest storeRequest = new StoreDocumentRequest("doc-http-search", "Name", "runbook", null, "v1", null,
                List.of(new StoreDocumentRequest.ChunkInput("searchable content", constantVector(1.0f), "openai", "text-embedding-3-small", null)));
        restTemplate.exchange(url("/api/v1/vector/documents"), HttpMethod.POST, storeRequestWithRole(storeRequest, "VECTOR_ADMIN"),
                new ParameterizedTypeReference<ApiResponse<StoreDocumentResponse>>() {
                });

        VectorSearchRequest searchRequest = new VectorSearchRequest(constantVector(1.0f), "openai", "text-embedding-3-small", 5, null, null);
        ResponseEntity<ApiResponse<VectorSearchResponse>> response = restTemplate.exchange(
                url("/api/v1/vector/search"), HttpMethod.POST, new HttpEntity<>(searchRequest),
                new ParameterizedTypeReference<>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().results()).isNotEmpty();
        assertThat(response.getBody().data().results().get(0).content()).isEqualTo("searchable content");
    }

    @Test
    void search_topKAboveDtoCeiling_returns400() {
        VectorSearchRequest searchRequest = new VectorSearchRequest(constantVector(1.0f), "openai", "text-embedding-3-small", 5000, null, null);

        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                url("/api/v1/vector/search"), HttpMethod.POST, new HttpEntity<>(searchRequest),
                new ParameterizedTypeReference<>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void health_realDatabase_returns200WithUpStatus() {
        ResponseEntity<ApiResponse<VectorHealthResponse>> response = restTemplate.exchange(
                url("/api/v1/vector/health"), HttpMethod.GET, HttpEntity.EMPTY,
                new ParameterizedTypeReference<>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().status()).isEqualTo("UP");
        assertThat(response.getBody().data().pgvectorExtensionAvailable()).isTrue();
    }
}
