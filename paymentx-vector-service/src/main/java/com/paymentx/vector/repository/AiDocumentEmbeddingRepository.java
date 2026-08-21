package com.paymentx.vector.repository;

import com.paymentx.vector.entity.AiDocumentEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * English:
 * Spring Data JPA repository for AiDocumentEmbedding - owns the one
 * genuinely complex query in this service, `topKSimilaritySearch`.
 * findByChunkIdAndProviderAndModel is the upsert-lookup Step 16/17
 * need (re-embedding a chunk under the SAME model updates that row;
 * under a DIFFERENT model adds a new one - see AiDocumentEmbedding's
 * "one chunk, multiple embeddings" javadoc).
 * WHY topKSimilaritySearch is raw native SQL, not HQL/JPQL (even though
 * hibernate-vector contributes a real `cosine_distance` HQL function -
 * see pom.xml's dependency comment): this query also needs a single
 * Postgres JSONB containment check (`d.metadata @> CAST(:filtersJson AS
 * jsonb)`) combined with the vector ORDER BY in the SAME query (Step
 * 20 - filtering must happen at the database layer, not two round
 * trips) - JPQL has no JSONB operator support at all, so the query is
 * native SQL end to end for consistency, rather than splitting the
 * vector-distance part into HQL and the JSONB part into something else.
 * WHY the result is List&lt;Object[]&gt;, not an interface projection:
 * Spring Data's interface-based projections for NATIVE queries resolve
 * getter names against raw ResultSet column labels via a matching
 * strategy that is not perfectly reliable across Spring Data versions
 * for this exact mix of aliased expressions/JOINs/CAST - Object[] with
 * explicit, documented column-index mapping in
 * VectorStoreServiceImpl.search is unambiguous and does not depend on
 * that matching behavior at all.
 * WHY `:queryEmbedding`/`:filtersJson` are bound as String, not a
 * native Java array/Map: this is a native query, so Hibernate's
 * VectorArgumentTypeResolver (which auto-resolves float[] parameters
 * for HQL/Criteria queries - see hibernate-vector's own machinery) does
 * not apply here; the caller (VectorStoreServiceImpl) formats the query
 * vector as pgvector's own text literal syntax ("[0.1,0.2,...]") and
 * the filters map as a JSON string, and this query CASTs each
 * explicitly (`CAST(:queryEmbedding AS vector)`, `CAST(:filtersJson AS
 * jsonb)`) - avoids depending on any native-query vector/JSONB
 * parameter-binding support that may or may not exist for this exact
 * driver/Hibernate combination.
 * WHY an empty `filters` map still works with one static query (no
 * dynamic SQL branching in Java): Postgres's jsonb `@>` (contains)
 * operator treats an empty object `'{}'::jsonb` as contained in every
 * object, so `d.metadata @> '{}'::jsonb` is unconditionally true - the
 * exact same WHERE clause correctly expresses "no filters" and "these N
 * filters" without an if/else in Java.
 * Why it exists: Step 11/12/13/20's exact top-K similarity + metadata-
 * filtering + threshold requirements, in one database-layer query.
 * How it communicates with other components: injected into
 * VectorStoreServiceImpl.search.
 *
 * Hinglish:
 * AiDocumentEmbedding ke liye Spring Data JPA repository - is service
 * ki us ek genuinely complex query, `topKSimilaritySearch`, ki malik.
 * findByChunkIdAndProviderAndModel wahi upsert-lookup hai jo Step 16/17
 * ko chahiye (ek chunk ko SAME model ke under re-embed karna us row ko
 * update karta hai; ek DIFFERENT model ke under ek nayi add karta hai -
 * AiDocumentEmbedding ka "ek chunk, multiple embeddings" javadoc dekho).
 * topKSimilaritySearch raw native SQL KYU hai, HQL/JPQL nahi (chahe
 * hibernate-vector ek real `cosine_distance` HQL function contribute
 * karta ho - pom.xml ka dependency comment dekho): is query ko ek
 * single Postgres JSONB containment check bhi chahiye (`d.metadata @>
 * CAST(:filtersJson AS jsonb)`) vector ORDER BY ke saath SAME query me
 * combine karke (Step 20 - filtering database layer par hona chahiye,
 * do round trips nahi) - JPQL me koi JSONB operator support hai hi
 * nahi, isliye query native SQL end to end hai consistency ke liye,
 * vector-distance part ko HQL me aur JSONB part ko kisi aur cheez me
 * split karne ke bajaye. Result List&lt;Object[]&gt; KYU hai, ek
 * interface projection nahi: Spring Data ke NATIVE queries ke liye
 * interface-based projections getter names ko raw ResultSet column
 * labels ke against ek matching strategy se resolve karte hain jo
 * aliased expressions/JOINs/CAST ke is exact mix ke liye Spring Data
 * versions ke across perfectly reliable nahi hai - Object[] explicit,
 * documented column-index mapping ke saath
 * VectorStoreServiceImpl.search me unambiguous hai aur us matching
 * behavior par bilkul depend nahi karta.
 * `:queryEmbedding`/`:filtersJson` String ke roop me KYU bind hote hain,
 * ek native Java array/Map nahi: ye ek native query hai, isliye
 * Hibernate ka VectorArgumentTypeResolver (jo HQL/Criteria queries ke
 * liye float[] parameters auto-resolve karta hai - hibernate-vector ki
 * apni machinery dekho) yahan apply nahi hota; caller
 * (VectorStoreServiceImpl) query vector ko pgvector ke apne text
 * literal syntax me format karta hai ("[0.1,0.2,...]") aur filters map
 * ko ek JSON string me, aur ye query har ek ko explicitly CAST karti hai
 * (`CAST(:queryEmbedding AS vector)`, `CAST(:filtersJson AS jsonb)`) -
 * kisi bhi native-query vector/JSONB parameter-binding support par
 * depend karne se bachta hai jo is exact driver/Hibernate combination
 * ke liye exist kare ya na kare.
 * Ek khali `filters` map ek hi static query ke saath (Java me koi
 * dynamic SQL branching nahi) kaam KYU karta hai: Postgres ka jsonb
 * `@>` (contains) operator ek khali object `'{}'::jsonb` ko har object
 * me contained treat karta hai, isliye `d.metadata @> '{}'::jsonb`
 * unconditionally true hota hai - wahi exact WHERE clause "no filters"
 * aur "ye N filters" dono ko correctly express karta hai bina Java me
 * ek if/else ke.
 * Ye kyu hai: Step 11/12/13/20 ki exact top-K similarity + metadata-
 * filtering + threshold requirements, ek database-layer query me.
 * Dusre components se kaise communicate karta hai: VectorStoreServiceImpl.
 * search me inject hota hai.
 */
public interface AiDocumentEmbeddingRepository extends JpaRepository<AiDocumentEmbedding, UUID> {

    Optional<AiDocumentEmbedding> findByChunkIdAndProviderAndModel(UUID chunkId, String provider, String model);

    long countByProviderAndModel(String provider, String model);

    @Query(value = """
            SELECT
              e.id,
              c.id,
              d.id,
              d.document_key,
              c.chunk_index,
              c.content,
              CAST(d.metadata AS text),
              e.provider,
              e.model,
              (e.embedding <=> CAST(:queryEmbedding AS vector)) AS distance
            FROM ai_document_embedding e
            JOIN ai_document_chunk c ON c.id = e.chunk_id
            JOIN ai_document d ON d.id = c.document_id
            WHERE d.status = 'ACTIVE'
              AND e.provider = :provider
              AND e.model = :model
              AND d.metadata @> CAST(:filtersJson AS jsonb)
              AND (e.embedding <=> CAST(:queryEmbedding AS vector)) <= :maxDistance
            ORDER BY e.embedding <=> CAST(:queryEmbedding AS vector) ASC
            LIMIT :topK
            """, nativeQuery = true)
    List<Object[]> topKSimilaritySearch(
            @Param("queryEmbedding") String queryEmbeddingLiteral,
            @Param("provider") String provider,
            @Param("model") String model,
            @Param("filtersJson") String filtersJson,
            @Param("maxDistance") double maxDistance,
            @Param("topK") int topK);
}
