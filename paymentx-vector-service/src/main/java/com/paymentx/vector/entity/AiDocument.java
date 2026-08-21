package com.paymentx.vector.entity;

import com.paymentx.common.base.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

/**
 * English:
 * The stable identity and lifecycle state of one ingested source
 * document - a document_key + documentVersion pair (Step 19 - "Payment
 * API Documentation v1" and "v2" coexist as two separate rows, never
 * one overwriting the other), plus governance metadata (documentType,
 * source) and a JSONB metadata bag for whatever future
 * filtering/tagging a caller needs (Step 10 - "do NOT create hundreds
 * of individual columns for arbitrary metadata"). `documentVersion` is
 * a String (not the `version` field AuditableEntity already defines) -
 * deliberately NOT reused for two entirely different concepts:
 * AuditableEntity.version is JPA's internal optimistic-lock counter (a
 * Long, bumped on every UPDATE); documentVersion is a caller-supplied,
 * human-meaningful revision label ("v1", "2024-01", "1.3") - conflating
 * them would mean every innocuous concurrent read-then-save silently
 * corrupts the caller's own version labeling. WHY this phase does NOT
 * store the document's full binary/original file content: Step 5 -
 * "this phase stores searchable text and metadata, not files" -
 * `ai_document` has no blob/bytea column at all, only chunk `content`
 * (plain text, see AiDocumentChunk) is ever stored.
 * Why it exists: Step 4/5 of the Phase 3.5 brief's data model - the
 * root of the Document -> Document Chunk -> Embedding Vector hierarchy.
 * How it communicates with other components: AiDocumentRepository
 * persists/queries this; VectorStoreServiceImpl resolves a document by
 * (documentKey, documentVersion) before creating/updating its chunks;
 * AiDocumentChunk.documentId references this row's id, with ON DELETE
 * CASCADE (see V1_0_0__create_ai_vector_tables.yaml) so deleting a
 * document never leaves an orphan chunk/embedding (Step 18).
 *
 * Hinglish:
 * Ek ingested source document ki stable identity aur lifecycle state -
 * ek document_key + documentVersion pair (Step 19 - "Payment API
 * Documentation v1" aur "v2" do alag rows ke roop me coexist karte hain,
 * kabhi ek doosre ko overwrite nahi karta), plus governance metadata
 * (documentType, source) aur ek JSONB metadata bag jo bhi future
 * filtering/tagging ek caller ko chahiye uske liye (Step 10 - "arbitrary
 * metadata ke liye sainkdon individual columns mat banao").
 * `documentVersion` ek String hai (AuditableEntity ka `version` field
 * nahi jo already define hai) - jaan-boojh kar do bilkul alag concepts
 * ke liye reuse NAHI kiya gaya: AuditableEntity.version JPA ka internal
 * optimistic-lock counter hai (ek Long, har UPDATE par bump hota hai);
 * documentVersion ek caller-supplied, human-meaningful revision label
 * hai ("v1", "2024-01", "1.3") - inhe conflate karna matlab hota har
 * innocuous concurrent read-then-save silently caller ka apna version
 * labeling corrupt kar deta. Ye phase document ki poori binary/original
 * file content store KYU nahi karta: Step 5 - "ye phase searchable text
 * aur metadata store karta hai, files nahi" - `ai_document` ke paas koi
 * blob/bytea column hai hi nahi, sirf chunk `content` (plain text,
 * AiDocumentChunk dekho) hi kabhi store hota hai.
 * Ye kyu hai: Phase 3.5 brief ka Step 4/5 ka data model - Document ->
 * Document Chunk -> Embedding Vector hierarchy ka root.
 * Dusre components se kaise communicate karta hai: AiDocumentRepository
 * ise persist/query karta hai; VectorStoreServiceImpl (documentKey,
 * documentVersion) se ek document resolve karta hai uske chunks
 * create/update karne se pehle; AiDocumentChunk.documentId is row ke id
 * ko reference karta hai, ON DELETE CASCADE ke saath
 * (V1_0_0__create_ai_vector_tables.yaml dekho) taaki ek document delete
 * karna kabhi ek orphan chunk/embedding na chhode (Step 18).
 */
@Entity
@Table(name = "ai_document")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class AiDocument extends AuditableEntity {

    @Column(name = "document_key", nullable = false, length = 256)
    private String documentKey;

    @Column(name = "document_name", nullable = false, length = 512)
    private String documentName;

    @Column(name = "document_type", nullable = false, length = 64)
    private String documentType;

    @Column(name = "source", length = 128)
    private String source;

    @Column(name = "document_version", nullable = false, length = 32)
    private String documentVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private DocumentStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;
}
