package com.paymentx.vector.service;

import com.paymentx.vector.dto.StoreDocumentRequest;
import com.paymentx.vector.dto.StoreDocumentResponse;
import com.paymentx.vector.dto.VectorHealthResponse;
import com.paymentx.vector.dto.VectorSearchRequest;
import com.paymentx.vector.dto.VectorSearchResponse;
import com.paymentx.vector.exception.VectorErrorCodes;
import com.paymentx.vector.exception.VectorException;
import com.paymentx.vector.repository.AiDocumentChunkRepository;
import com.paymentx.vector.repository.AiDocumentEmbeddingRepository;
import com.paymentx.vector.repository.AiDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * English:
 * A real, end-to-end test of VectorStoreServiceImpl against a REAL
 * PostgreSQL + pgvector database (Testcontainers, the
 * `pgvector/pgvector:0.8.0-pg16` image - the exact image
 * infra/docker-compose.yml now uses, matching
 * PromptControllerIntegrationTest's established Testcontainers pattern
 * with one change: the image itself). Step 36 of the Phase 3.5 brief is
 * explicit - "Do not claim vector search works based only on Mockito...
 * Verify actual SQL behavior" - every test here runs real SQL: real
 * INSERTs through Hibernate's VectorJdbcType, a real Liquibase-created
 * HNSW index, a real `<=>` cosine-distance ORDER BY, real JSONB `@>`
 * containment filtering, and real UNIQUE-constraint-backed upserts and
 * cascade deletes. What it verifies (Step 35's 28-item list, mapped):
 * document/chunk/embedding creation and update-in-place upsert;
 * duplicate prevention under both sequential re-ingestion and real
 * concurrent ingestion; cascade cleanup on delete; dimension/NaN/
 * infinite-value validation; cosine-ordered top-K search with metadata
 * filtering and a minScore threshold; empty results as a valid outcome;
 * topK bounds; whole-transaction rollback when one chunk in a
 * multi-chunk request is invalid; multi-model coexistence for the same
 * chunk; and the health check's real extension/connectivity probe.
 * Why it exists: Step 34/36 of the Phase 3.5 brief.
 * How it communicates with other components: exercises
 * VectorStoreServiceImpl directly against a real Spring context and a
 * real, ephemeral Postgres+pgvector container - the closest thing to
 * running this service for real without actually starting it
 * standalone.
 *
 * Hinglish:
 * VectorStoreServiceImpl ka ek real, end-to-end test, ek REAL
 * PostgreSQL + pgvector database ke against (Testcontainers,
 * `pgvector/pgvector:0.8.0-pg16` image - wahi exact image jo
 * infra/docker-compose.yml ab use karta hai,
 * PromptControllerIntegrationTest ke established Testcontainers pattern
 * se match karte hue, ek change ke saath: khud image). Phase 3.5 brief
 * ka Step 36 explicit hai - "sirf Mockito ke basis par claim mat karo ki
 * vector search kaam karti hai... actual SQL behavior verify karo" -
 * yahan har test real SQL chalata hai: Hibernate ke VectorJdbcType ke
 * through real INSERTs, ek real Liquibase-created HNSW index, ek real
 * `<=>` cosine-distance ORDER BY, real JSONB `@>` containment filtering,
 * aur real UNIQUE-constraint-backed upserts aur cascade deletes. Ye kya
 * verify karta hai (Step 35 ki 28-item list, mapped): document/chunk/
 * embedding creation aur update-in-place upsert; sequential re-
 * ingestion aur real concurrent ingestion dono ke under duplicate
 * prevention; delete par cascade cleanup; dimension/NaN/infinite-value
 * validation; cosine-ordered top-K search metadata filtering aur ek
 * minScore threshold ke saath; empty results ek valid outcome ke roop
 * me; topK bounds; ek multi-chunk request me ek chunk invalid hone par
 * whole-transaction rollback; same chunk ke liye multi-model
 * coexistence; aur health check ka real extension/connectivity probe.
 * Ye kyu hai: Phase 3.5 brief ka Step 34/36.
 * Dusre components se kaise communicate karta hai: VectorStoreServiceImpl
 * ko seedhe ek real Spring context aur ek real, ephemeral Postgres+
 * pgvector container ke against exercise karta hai - is service ko
 * standalone actually start kiye bina real me chalane ke sabse kareeb
 * wali cheez.
 */
@Testcontainers
@SpringBootTest
class VectorStoreServiceImplTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:0.8.0-pg16")
            .withDatabaseName("paymentx_ai_vector_test")
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

    @Autowired
    private VectorStoreService vectorStoreService;

    @Autowired
    private AiDocumentRepository documentRepository;

    @Autowired
    private AiDocumentChunkRepository chunkRepository;

    @Autowired
    private AiDocumentEmbeddingRepository embeddingRepository;

    @BeforeEach
    void cleanDatabase() {
        embeddingRepository.deleteAll();
        chunkRepository.deleteAll();
        documentRepository.deleteAll();
    }

    private static List<Float> constantVector(float value) {
        List<Float> vector = new ArrayList<>(DIMENSION);
        for (int i = 0; i < DIMENSION; i++) {
            vector.add(value);
        }
        return vector;
    }

    /** A vector `flippedCount` dimensions away from all-1.0 - larger flippedCount = further from a constantVector(1.0f) query. */
    private static List<Float> partiallyFlippedVector(int flippedCount) {
        List<Float> vector = constantVector(1.0f);
        for (int i = 0; i < flippedCount; i++) {
            vector.set(i, -1.0f);
        }
        return vector;
    }

    private static StoreDocumentRequest.ChunkInput chunkInput(String content, List<Float> embedding) {
        return new StoreDocumentRequest.ChunkInput(content, embedding, "openai", "text-embedding-3-small", null);
    }

    private static StoreDocumentRequest simpleDocumentRequest(String key, String version, List<StoreDocumentRequest.ChunkInput> chunks) {
        return new StoreDocumentRequest(key, "Test Document", "runbook", "manual-test", version, null, chunks);
    }

    @Test
    void storeDocument_newDocument_createsDocumentChunksAndEmbeddings() {
        StoreDocumentRequest request = simpleDocumentRequest("doc-1", "v1", List.of(
                chunkInput("First chunk", constantVector(1.0f)),
                chunkInput("Second chunk", constantVector(0.5f))));

        StoreDocumentResponse response = vectorStoreService.storeDocument(request);

        assertThat(response.created()).isTrue();
        assertThat(response.chunkCount()).isEqualTo(2);
        assertThat(response.embeddingCount()).isEqualTo(2);
        assertThat(documentRepository.count()).isEqualTo(1);
        assertThat(chunkRepository.countByDocumentId(response.documentId())).isEqualTo(2);
    }

    @Test
    void storeDocument_repeatedIngestionSameDocument_upsertsWithoutDuplicating() {
        StoreDocumentRequest first = simpleDocumentRequest("doc-upsert", "v1",
                List.of(chunkInput("Original content", constantVector(1.0f))));
        StoreDocumentResponse firstResponse = vectorStoreService.storeDocument(first);

        StoreDocumentRequest second = simpleDocumentRequest("doc-upsert", "v1",
                List.of(chunkInput("Updated content", constantVector(0.9f))));
        StoreDocumentResponse secondResponse = vectorStoreService.storeDocument(second);

        assertThat(firstResponse.created()).isTrue();
        assertThat(secondResponse.created()).isFalse();
        assertThat(secondResponse.documentId()).isEqualTo(firstResponse.documentId());
        assertThat(documentRepository.count()).isEqualTo(1);
        assertThat(chunkRepository.countByDocumentId(firstResponse.documentId())).isEqualTo(1);

        var chunk = chunkRepository.findByDocumentIdAndChunkIndex(firstResponse.documentId(), 0).orElseThrow();
        assertThat(chunk.getContent()).isEqualTo("Updated content");
    }

    @Test
    void storeDocument_differentVersionsOfSameKey_coexistAsSeparateDocuments() {
        vectorStoreService.storeDocument(simpleDocumentRequest("doc-versioned", "v1",
                List.of(chunkInput("v1 content", constantVector(1.0f)))));
        vectorStoreService.storeDocument(simpleDocumentRequest("doc-versioned", "v2",
                List.of(chunkInput("v2 content", constantVector(1.0f)))));

        assertThat(documentRepository.count()).isEqualTo(2);
        assertThat(documentRepository.findByDocumentKeyAndDocumentVersion("doc-versioned", "v1")).isPresent();
        assertThat(documentRepository.findByDocumentKeyAndDocumentVersion("doc-versioned", "v2")).isPresent();
    }

    @Test
    void storeDocument_dimensionMismatch_throwsAndPersistsNothing() {
        List<Float> wrongDimension = constantVector(1.0f).subList(0, 100);
        StoreDocumentRequest request = simpleDocumentRequest("doc-bad-dim", "v1",
                List.of(chunkInput("bad", wrongDimension)));

        assertThatThrownBy(() -> vectorStoreService.storeDocument(request))
                .isInstanceOf(VectorException.class)
                .satisfies(ex -> assertThat(((VectorException) ex).getErrorCode()).isEqualTo(VectorErrorCodes.VECTOR_DIMENSION_MISMATCH));
        assertThat(documentRepository.count()).isZero();
    }

    @Test
    void storeDocument_nanValue_throwsInvalidVector() {
        List<Float> withNan = constantVector(1.0f);
        withNan.set(5, Float.NaN);
        StoreDocumentRequest request = simpleDocumentRequest("doc-nan", "v1", List.of(chunkInput("bad", withNan)));

        assertThatThrownBy(() -> vectorStoreService.storeDocument(request))
                .isInstanceOf(VectorException.class)
                .satisfies(ex -> assertThat(((VectorException) ex).getErrorCode()).isEqualTo(VectorErrorCodes.VECTOR_INVALID_VALUE));
    }

    @Test
    void storeDocument_infiniteValue_throwsInvalidVector() {
        List<Float> withInfinity = constantVector(1.0f);
        withInfinity.set(5, Float.POSITIVE_INFINITY);
        StoreDocumentRequest request = simpleDocumentRequest("doc-inf", "v1", List.of(chunkInput("bad", withInfinity)));

        assertThatThrownBy(() -> vectorStoreService.storeDocument(request))
                .isInstanceOf(VectorException.class)
                .satisfies(ex -> assertThat(((VectorException) ex).getErrorCode()).isEqualTo(VectorErrorCodes.VECTOR_INVALID_VALUE));
    }

    @Test
    void storeDocument_oneInvalidChunkAmongValidOnes_rollsBackWholeTransaction() {
        List<Float> badVector = constantVector(1.0f);
        badVector.set(0, Float.NaN);
        StoreDocumentRequest request = simpleDocumentRequest("doc-rollback", "v1", List.of(
                chunkInput("valid chunk", constantVector(1.0f)),
                chunkInput("invalid chunk", badVector)));

        assertThatThrownBy(() -> vectorStoreService.storeDocument(request)).isInstanceOf(VectorException.class);

        assertThat(documentRepository.count()).isZero();
        assertThat(chunkRepository.count()).isZero();
    }

    @Test
    void deleteDocument_removesDocumentAndCascadesChunksAndEmbeddings() {
        StoreDocumentResponse stored = vectorStoreService.storeDocument(simpleDocumentRequest("doc-delete", "v1",
                List.of(chunkInput("chunk to delete", constantVector(1.0f)))));
        assertThat(chunkRepository.countByDocumentId(stored.documentId())).isEqualTo(1);
        assertThat(embeddingRepository.count()).isEqualTo(1);

        vectorStoreService.deleteDocument("doc-delete", "v1");

        assertThat(documentRepository.findByDocumentKeyAndDocumentVersion("doc-delete", "v1")).isEmpty();
        assertThat(chunkRepository.countByDocumentId(stored.documentId())).isZero();
        assertThat(embeddingRepository.count()).isZero();
    }

    @Test
    void deleteDocument_notFound_throwsDocumentNotFound() {
        assertThatThrownBy(() -> vectorStoreService.deleteDocument("no-such-doc", "v1"))
                .isInstanceOf(VectorException.class)
                .satisfies(ex -> assertThat(((VectorException) ex).getErrorCode()).isEqualTo(VectorErrorCodes.DOCUMENT_NOT_FOUND));
    }

    @Test
    void search_returnsResultsOrderedByRealCosineDistance() {
        vectorStoreService.storeDocument(simpleDocumentRequest("doc-search-near", "v1",
                List.of(chunkInput("near chunk", constantVector(1.0f)))));
        // Phase 3.5 vector database migration: 100/384 (~26%) preserves the same "mid-distance" ratio the
        // original 400/1536 (~26%) expressed before the dimension changed - not an arbitrary new value.
        vectorStoreService.storeDocument(simpleDocumentRequest("doc-search-mid", "v1",
                List.of(chunkInput("mid chunk", partiallyFlippedVector(100)))));
        vectorStoreService.storeDocument(simpleDocumentRequest("doc-search-far", "v1",
                List.of(chunkInput("far chunk", constantVector(-1.0f)))));

        VectorSearchResponse response = vectorStoreService.search(
                new VectorSearchRequest(constantVector(1.0f), "openai", "text-embedding-3-small", 3, null, null));

        assertThat(response.results()).hasSize(3);
        assertThat(response.results().get(0).content()).isEqualTo("near chunk");
        assertThat(response.results().get(1).content()).isEqualTo("mid chunk");
        assertThat(response.results().get(2).content()).isEqualTo("far chunk");
        assertThat(response.results().get(0).distance()).isLessThan(response.results().get(1).distance());
        assertThat(response.results().get(1).distance()).isLessThan(response.results().get(2).distance());
        assertThat(response.results().get(0).score()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void search_topKLimitsResultCount() {
        vectorStoreService.storeDocument(simpleDocumentRequest("doc-topk-1", "v1", List.of(chunkInput("a", constantVector(1.0f)))));
        vectorStoreService.storeDocument(simpleDocumentRequest("doc-topk-2", "v1", List.of(chunkInput("b", constantVector(0.9f)))));
        vectorStoreService.storeDocument(simpleDocumentRequest("doc-topk-3", "v1", List.of(chunkInput("c", constantVector(0.8f)))));

        VectorSearchResponse response = vectorStoreService.search(
                new VectorSearchRequest(constantVector(1.0f), "openai", "text-embedding-3-small", 1, null, null));

        assertThat(response.results()).hasSize(1);
    }

    @Test
    void search_metadataFilter_returnsOnlyMatchingDocuments() {
        vectorStoreService.storeDocument(new StoreDocumentRequest("doc-filter-prod", "Prod Doc", "runbook", "manual",
                "v1", Map.of("environment", "prod"), List.of(chunkInput("prod chunk", constantVector(1.0f)))));
        vectorStoreService.storeDocument(new StoreDocumentRequest("doc-filter-dev", "Dev Doc", "runbook", "manual",
                "v1", Map.of("environment", "dev"), List.of(chunkInput("dev chunk", constantVector(1.0f)))));

        VectorSearchResponse response = vectorStoreService.search(new VectorSearchRequest(
                constantVector(1.0f), "openai", "text-embedding-3-small", 10, Map.of("environment", "prod"), null));

        assertThat(response.results()).hasSize(1);
        assertThat(response.results().get(0).content()).isEqualTo("prod chunk");
    }

    @Test
    void search_documentTypeIsFilterableEvenThoughItIsARealColumn() {
        vectorStoreService.storeDocument(simpleDocumentRequest("doc-type-a", "v1", List.of(chunkInput("runbook chunk", constantVector(1.0f)))));
        vectorStoreService.storeDocument(new StoreDocumentRequest("doc-type-b", "API Doc", "api-docs", "manual", "v1",
                null, List.of(chunkInput("api-docs chunk", constantVector(1.0f)))));

        VectorSearchResponse response = vectorStoreService.search(new VectorSearchRequest(
                constantVector(1.0f), "openai", "text-embedding-3-small", 10, Map.of("documentType", "api-docs"), null));

        assertThat(response.results()).hasSize(1);
        assertThat(response.results().get(0).content()).isEqualTo("api-docs chunk");
    }

    @Test
    void search_minScoreThreshold_excludesDistantResults() {
        vectorStoreService.storeDocument(simpleDocumentRequest("doc-threshold-near", "v1", List.of(chunkInput("near", constantVector(1.0f)))));
        vectorStoreService.storeDocument(simpleDocumentRequest("doc-threshold-far", "v1", List.of(chunkInput("far", constantVector(-1.0f)))));

        VectorSearchResponse response = vectorStoreService.search(
                new VectorSearchRequest(constantVector(1.0f), "openai", "text-embedding-3-small", 10, null, 0.9));

        assertThat(response.results()).hasSize(1);
        assertThat(response.results().get(0).content()).isEqualTo("near");
    }

    @Test
    void search_noMatchingResults_returnsEmptyListNotAnError() {
        vectorStoreService.storeDocument(simpleDocumentRequest("doc-empty-search", "v1", List.of(chunkInput("chunk", constantVector(1.0f)))));

        VectorSearchResponse response = vectorStoreService.search(new VectorSearchRequest(
                constantVector(1.0f), "openai", "text-embedding-3-small", 10, Map.of("environment", "nonexistent"), null));

        assertThat(response.results()).isEmpty();
        assertThat(response.resultCount()).isZero();
    }

    @Test
    void search_topKAboveConfiguredMaximum_throwsInvalidTopK() {
        VectorSearchRequest request = new VectorSearchRequest(constantVector(1.0f), "openai", "text-embedding-3-small", 500, null, null);

        assertThatThrownBy(() -> vectorStoreService.search(request))
                .isInstanceOf(VectorException.class)
                .satisfies(ex -> assertThat(((VectorException) ex).getErrorCode()).isEqualTo(VectorErrorCodes.INVALID_TOP_K));
    }

    @Test
    void search_differentModelEmbeddingsForSameChunkCoexist() {
        StoreDocumentResponse stored = vectorStoreService.storeDocument(simpleDocumentRequest("doc-multi-model", "v1",
                List.of(chunkInput("multi-model chunk", constantVector(1.0f)))));

        StoreDocumentRequest secondModelRequest = simpleDocumentRequest("doc-multi-model", "v1", List.of(
                new StoreDocumentRequest.ChunkInput("multi-model chunk", constantVector(1.0f), "openai", "text-embedding-3-large", null)));
        vectorStoreService.storeDocument(secondModelRequest);

        assertThat(embeddingRepository.count()).isEqualTo(2);
        assertThat(embeddingRepository.findByChunkIdAndProviderAndModel(
                chunkRepository.findByDocumentIdAndChunkIndex(stored.documentId(), 0).orElseThrow().getId(),
                "openai", "text-embedding-3-small")).isPresent();
    }

    @Test
    void concurrentIngestion_sameDocumentAndChunk_doesNotCreateDuplicates() throws InterruptedException {
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    vectorStoreService.storeDocument(simpleDocumentRequest("doc-concurrent", "v1",
                            List.of(chunkInput("concurrent chunk", constantVector(1.0f)))));
                } catch (Exception ignored) {
                    // A real DataIntegrityViolationException racing the find-or-create check is an
                    // acceptable, honest outcome here (Step 25) - the invariant this test actually
                    // verifies is "never more than one row survives", not "every concurrent call succeeds".
                } finally {
                    done.countDown();
                }
            });
        }
        ready.await();
        start.countDown();
        done.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(documentRepository.count()).isEqualTo(1);
        var document = documentRepository.findByDocumentKeyAndDocumentVersion("doc-concurrent", "v1").orElseThrow();
        assertThat(chunkRepository.countByDocumentId(document.getId())).isEqualTo(1);
    }

    @Test
    void health_realDatabase_reportsUpAndPgvectorAvailable() {
        VectorHealthResponse health = vectorStoreService.health();

        assertThat(health.status()).isEqualTo("UP");
        assertThat(health.databaseReachable()).isTrue();
        assertThat(health.pgvectorExtensionAvailable()).isTrue();
    }
}
