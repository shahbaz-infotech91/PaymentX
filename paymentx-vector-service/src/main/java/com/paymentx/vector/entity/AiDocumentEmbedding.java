package com.paymentx.vector.entity;

import com.paymentx.common.base.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * English:
 * One real embedding vector for one AiDocumentChunk - the leaf of
 * Document -> Document Chunk -> Embedding Vector (Step 4/7). `embedding`
 * is a real Postgres `vector(384)` column (Phase 3.5 migration - was
 * `vector(1536)`; see db/changelog/changes/
 * V1_0_2__migrate_embedding_dimension_to_384.yaml), mapped via
 * `@JdbcTypeCode(SqlTypes.VECTOR)` + `@Array(length = DIMENSION)` -
 * Hibernate ORM's own native pgvector support (the `hibernate-vector`
 * module, see pom.xml's dependency comment), not a hand-rolled
 * String/PGobject converter. `DIMENSION` (384) is fixed at the Java
 * type level AND the database column-definition level to match
 * Embedding Service's (Phase 3.4) local provider, sentence-transformers/
 * all-MiniLM-L6-v2 - see this class's own "Known Limitation" note below
 * for what changing that default again would actually require. `provider`/`model`/`dimension` are stored
 * explicitly (Step 8) even though `dimension` is redundant with the
 * vector column's own fixed length today - Postgres's `vector(N)` type
 * enforces the STRUCTURAL length invariant, but does not know or care
 * WHICH provider/model produced a given vector, and a future model
 * migration needs that provenance to distinguish "old model's vectors,
 * pending re-embedding" from "new model's vectors" during a rollout.
 * `(chunkId, provider, model)` is unique, NOT `chunkId` alone (Step 9 -
 * "one chunk can have multiple embeddings if model versioning
 * requires it") - re-embedding a chunk under a new model adds a new
 * row rather than requiring the old one's deletion first, so a
 * transition period can query either model's vectors.
 * KNOWN LIMITATION - changing the configured embedding model/dimension:
 * pgvector's `vector(N)` is a fixed-width type; the column here is
 * fixed at N=1536 for the reasons above. Switching Embedding Service to
 * a different-dimension model (e.g. text-embedding-3-large, 3072)
 * requires a NEW Liquibase migration (`ALTER TABLE ... ALTER COLUMN
 * embedding TYPE vector(3072)`) AND re-embedding every existing row -
 * the old 1536-dimension vectors are not mathematically comparable to
 * 3072-dimension ones, so a type-only ALTER without re-embedding would
 * silently produce meaningless distances. This is a deliberate, real
 * constraint documented here rather than a limitation this phase tries
 * to abstract away.
 * Why it exists: Step 7/8/9's exact embedding-storage model.
 * How it communicates with other components: AiDocumentEmbeddingRepository
 * persists this via standard JPA save() (Hibernate's VectorJdbcType
 * handles the float[] <-> vector(N) binding automatically) and queries
 * it via a native SQL top-K similarity search using pgvector's `<=>`
 * cosine-distance operator (see that repository's javadoc for why the
 * search query itself is native SQL, not HQL, despite hibernate-vector
 * contributing an HQL `cosine_distance` function).
 *
 * Hinglish:
 * Ek AiDocumentChunk ke liye ek real embedding vector - Document ->
 * Document Chunk -> Embedding Vector ka leaf (Step 4/7). `embedding` ek
 * real Postgres `vector(1536)` column hai, `@JdbcTypeCode(SqlTypes.VECTOR)`
 * + `@Array(length = DIMENSION)` ke through mapped - Hibernate ORM ka
 * apna native pgvector support (`hibernate-vector` module, pom.xml ka
 * dependency comment dekho), ek hand-rolled String/PGobject converter
 * nahi. `DIMENSION` (1536) Java type level AUR database column-
 * definition level dono par fixed hai
 * (V1_0_0__create_ai_vector_tables.yaml dekho) Embedding Service
 * (Phase 3.4) ke configured default model, text-embedding-3-small, se
 * match karne ke liye - us default ko badalne ke liye actually kya
 * chahiye uske liye is class ka apna "Known Limitation" note neeche
 * dekho. `provider`/`model`/`dimension` explicitly store hote hain
 * (Step 8) chahe `dimension` aaj vector column ki apni fixed length se
 * redundant ho - Postgres ka `vector(N)` type STRUCTURAL length
 * invariant enforce karta hai, lekin ye nahi jaanta ya care karta ki
 * KAUN sa provider/model ne ek diye hue vector ko produce kiya, aur ek
 * future model migration ko us provenance ki zaroorat hoti hai "old
 * model ke vectors, re-embedding pending" ko "new model ke vectors" se
 * distinguish karne ke liye ek rollout ke dauran. `(chunkId, provider,
 * model)` unique hai, sirf `chunkId` nahi (Step 9 - "ek chunk ke
 * multiple embeddings ho sakte hain agar model versioning ko chahiye")
 * - ek chunk ko ek naye model ke under re-embed karna ek nayi row add
 * karta hai, purani ko pehle delete karne ki zaroorat ke bina, taaki ek
 * transition period dono models ke vectors query kar sake.
 * KNOWN LIMITATION - configured embedding model/dimension badalna:
 * pgvector ka `vector(N)` ek fixed-width type hai; yahan column upar
 * wale reasons ke liye N=1536 par fixed hai. Embedding Service ko ek
 * different-dimension model par switch karna (jaise text-embedding-3-
 * large, 3072) ek NAYI Liquibase migration maangta hai (`ALTER TABLE
 * ... ALTER COLUMN embedding TYPE vector(3072)`) AUR har existing row
 * ko re-embed karna - purane 1536-dimension vectors 3072-dimension
 * wale ke saath mathematically comparable nahi hain, isliye bina re-
 * embed kiye sirf ek type-only ALTER silently meaningless distances
 * produce karega. Ye ek jaan-boojh kar, real constraint hai jo yahan
 * document kiya gaya hai, is phase ka use abstract away karne ki
 * koshish ke bajaye.
 * Ye kyu hai: Step 7/8/9 ka exact embedding-storage model.
 * Dusre components se kaise communicate karta hai: AiDocumentEmbeddingRepository
 * ise standard JPA save() ke through persist karta hai (Hibernate ka
 * VectorJdbcType float[] <-> vector(N) binding automatically handle
 * karta hai) aur ise ek native SQL top-K similarity search ke through
 * query karta hai pgvector ke `<=>` cosine-distance operator use karte
 * hue (us repository ka javadoc dekho ki search query khud native SQL
 * kyun hai, HQL nahi, chahe hibernate-vector ek HQL `cosine_distance`
 * function contribute karta ho).
 */
@Entity
@Table(name = "ai_document_embedding")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class AiDocumentEmbedding extends AuditableEntity {

    // Phase 3.5 vector database migration: changed from 1536 (OpenAI text-embedding-3-small) to 384
    // (local sentence-transformers/all-MiniLM-L6-v2, Phase 3.4's new default) - see
    // db/changelog/changes/V1_0_2__migrate_embedding_dimension_to_384.yaml for the matching real schema
    // migration this constant must stay in sync with.
    public static final int DIMENSION = 384;

    @Column(name = "chunk_id", nullable = false)
    private UUID chunkId;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = DIMENSION)
    @Column(name = "embedding", nullable = false)
    private float[] embedding;

    @Column(name = "provider", nullable = false, length = 64)
    private String provider;

    @Column(name = "model", nullable = false, length = 128)
    private String model;

    @Column(name = "dimension", nullable = false)
    private Integer dimension;
}
