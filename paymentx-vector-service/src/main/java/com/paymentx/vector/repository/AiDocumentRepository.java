package com.paymentx.vector.repository;

import com.paymentx.vector.entity.AiDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

/**
 * English:
 * Spring Data JPA repository for AiDocument. WHY
 * findByDocumentKeyAndDocumentVersion returns Optional (a single row,
 * not a List): the database unique constraint on (document_key,
 * document_version) - see V1_0_0__create_ai_vector_tables.yaml -
 * guarantees at most one row can ever match, matching
 * PromptVersionRepository's identical
 * findByPromptTemplateIdAndStatus-returns-Optional reasoning.
 * Why it exists: the "find-or-create for upsert" lookup Step 16/17
 * requires.
 * How it communicates with other components: injected into
 * VectorStoreServiceImpl; the DB-level unique constraint this
 * repository relies on is what Step 25's concurrent-ingestion
 * requirement is actually enforced by (a concurrent duplicate insert
 * fails with a real DataIntegrityViolationException, caught by
 * GlobalExceptionHandler), not application code alone.
 *
 * Hinglish:
 * AiDocument ke liye Spring Data JPA repository.
 * findByDocumentKeyAndDocumentVersion Optional (ek single row, List
 * nahi) KYU return karta hai: (document_key, document_version) par
 * database unique constraint - V1_0_0__create_ai_vector_tables.yaml
 * dekho - guarantee karta hai ki kabhi zyada se zyada ek hi row match ho
 * sakti hai, PromptVersionRepository ke identical
 * findByPromptTemplateIdAndStatus-Optional-return-karta-hai reasoning se
 * match karte hue.
 * Ye kyu hai: Step 16/17 ki "upsert ke liye find-or-create" lookup.
 * Dusre components se kaise communicate karta hai: VectorStoreServiceImpl
 * me inject hota hai; ye repository jis DB-level unique constraint par
 * depend karta hai wahi Step 25 ki concurrent-ingestion requirement ko
 * actually enforce karta hai (ek concurrent duplicate insert ek real
 * DataIntegrityViolationException ke saath fail hota hai,
 * GlobalExceptionHandler dwara catch kiya jaata hai), sirf application
 * code nahi.
 */
public interface AiDocumentRepository extends JpaRepository<AiDocument, UUID> {

    Optional<AiDocument> findByDocumentKeyAndDocumentVersion(String documentKey, String documentVersion);

    /**
     * Real, cheap catalog check for GET /api/v1/vector/health (Step 31) - queries Postgres's own
     * pg_extension system catalog, never assumes the extension is present just because the service
     * started successfully (a missing extension only breaks the specific INSERT/SELECT that touches a
     * vector column, not JPA startup itself).
     */
    @Query(value = "SELECT COUNT(*) FROM pg_extension WHERE extname = 'vector'", nativeQuery = true)
    long countPgVectorExtension();
}
