package com.paymentx.vector.repository;

import com.paymentx.vector.entity.AiDocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * English:
 * Spring Data JPA repository for AiDocumentChunk.
 * findByDocumentIdAndChunkIndex is the exact lookup Step 16/17's
 * upsert semantics need: re-ingesting the same document at the same
 * chunk position updates that chunk's row (content/metadata) in place
 * rather than inserting a duplicate - the database unique constraint on
 * (document_id, chunk_index) (see V1_0_0__create_ai_vector_tables.yaml)
 * is the real, enforced backstop behind this application-level check.
 * Deleting a document's chunks is never done explicitly by this
 * repository/application code - see AiDocument's javadoc: the
 * document_id foreign key's ON DELETE CASCADE handles it at the
 * database level the moment the parent ai_document row is deleted
 * (Step 18).
 * Why it exists: the chunk-level lookups Step 6/16/17 require.
 * How it communicates with other components: injected into
 * VectorStoreServiceImpl.
 *
 * Hinglish:
 * AiDocumentChunk ke liye Spring Data JPA repository.
 * findByDocumentIdAndChunkIndex wahi exact lookup hai jo Step 16/17 ki
 * upsert semantics ko chahiye: same document ko same chunk position par
 * dobara ingest karna us chunk ki row (content/metadata) ko in place
 * update karta hai, ek duplicate insert karne ke bajaye - (document_id,
 * chunk_index) par database unique constraint
 * (V1_0_0__create_ai_vector_tables.yaml dekho) is application-level
 * check ke peeche real, enforced backstop hai. Ek document ke chunks ko
 * delete karna is repository/application code dwara kabhi explicitly
 * nahi kiya jaata - AiDocument ka javadoc dekho: document_id foreign
 * key ka ON DELETE CASCADE ise database level par handle karta hai jis
 * pal parent ai_document row delete hoti hai (Step 18).
 * Ye kyu hai: Step 6/16/17 ke chunk-level lookups.
 * Dusre components se kaise communicate karta hai: VectorStoreServiceImpl
 * me inject hota hai.
 */
public interface AiDocumentChunkRepository extends JpaRepository<AiDocumentChunk, UUID> {

    Optional<AiDocumentChunk> findByDocumentIdAndChunkIndex(UUID documentId, Integer chunkIndex);

    List<AiDocumentChunk> findByDocumentIdOrderByChunkIndex(UUID documentId);

    long countByDocumentId(UUID documentId);
}
