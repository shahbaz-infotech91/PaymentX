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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;
import java.util.UUID;

/**
 * English:
 * One vector-searchable slice of an AiDocument's real text content -
 * the middle tier of Document -> Document Chunk -> Embedding Vector
 * (Step 4). `documentId` is a raw UUID column, not a JPA `@ManyToOne`
 * relationship - matches Prompt Service's PromptVersion.promptTemplateId
 * exact convention (explicit FK-id fields, not entity graph
 * navigation/lazy-loading, across this platform). `tokenCount` is
 * genuinely nullable and never populated by this phase - Step 6 is
 * explicit: "do not assume token count if it is not actually
 * calculated... do not fabricate token counts" - this service has no
 * real tokenizer, so the column exists for a future phase that adds one
 * without a schema change, and stays honestly null until then.
 * `(documentId, chunkIndex)` is unique (Step 6) - the chunker (a future
 * phase's concern, not this one - Step 27/40) is expected to number
 * chunks sequentially per document, and this constraint is what makes
 * re-ingestion idempotent (Step 16/17: the same (documentId, chunkIndex)
 * pair upserts the existing row's content rather than creating a
 * duplicate).
 * Why it exists: Step 6's exact chunk data model.
 * How it communicates with other components: AiDocumentChunkRepository
 * persists/queries this; AiDocumentEmbedding.chunkId references this
 * row's id (ON DELETE CASCADE, see the Liquibase changelog);
 * VectorStoreServiceImpl's search query joins through this table to
 * return `content` alongside a search hit's score.
 *
 * Hinglish:
 * Ek AiDocument ke real text content ka ek vector-searchable slice -
 * Document -> Document Chunk -> Embedding Vector ka middle tier (Step
 * 4). `documentId` ek raw UUID column hai, ek JPA `@ManyToOne`
 * relationship nahi - Prompt Service ke PromptVersion.promptTemplateId
 * ke exact convention se match karta hai (explicit FK-id fields, entity
 * graph navigation/lazy-loading nahi, is poore platform me). `tokenCount`
 * genuinely nullable hai aur is phase dwara kabhi populate nahi hota -
 * Step 6 explicit hai: "agar token count actually calculate nahi hua
 * toh assume mat karo... token counts fabricate mat karo" - is service
 * ke paas koi real tokenizer nahi hai, isliye ye column ek future phase
 * ke liye exist karta hai jo ek add kare bina schema change ke, aur tab
 * tak honestly null rehta hai. `(documentId, chunkIndex)` unique hai
 * (Step 6) - chunker (ek future phase ka concern, is phase ka nahi -
 * Step 27/40) se expect kiya jaata hai ki har document ke chunks ko
 * sequentially number kare, aur ye constraint hi hai jo re-ingestion ko
 * idempotent banata hai (Step 16/17: same (documentId, chunkIndex) pair
 * existing row ka content upsert karta hai, ek duplicate create karne ke
 * bajaye).
 * Ye kyu hai: Step 6 ka exact chunk data model.
 * Dusre components se kaise communicate karta hai: AiDocumentChunkRepository
 * ise persist/query karta hai; AiDocumentEmbedding.chunkId is row ke id
 * ko reference karta hai (ON DELETE CASCADE, Liquibase changelog
 * dekho); VectorStoreServiceImpl ka search query is table se hoke join
 * karta hai ek search hit ke score ke saath `content` return karne ke
 * liye.
 */
@Entity
@Table(name = "ai_document_chunk")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class AiDocumentChunk extends AuditableEntity {

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "token_count")
    private Integer tokenCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;
}
