# PaymentX — Phase 3.5: Vector Database

**Status:** Complete
**Depends on:** Phase 3.0–3.4 (Architecture, AI Chat, Prompt Service, LLM Service, Embedding Service)
**Does NOT implement:** RAG orchestration, document ingestion pipeline (PDF/DOCX/CSV parsing), semantic-search orchestration, MCP, Agents (Phase 3.6+)

---

## 1. Purpose

Phase 3.5 delivers `paymentx-vector-service`, a standalone Spring Boot microservice that stores document
chunks and their real embedding vectors, and serves top-K cosine-similarity search with metadata
filtering. It is the fourth real AI Platform backend service and the storage foundation the future RAG
Service will build on. It stores and searches vectors only — it does not generate embeddings (Phase 3.4)
and does not orchestrate retrieval-augmented generation.

## 2. Why pgvector (Not a Dedicated Vector Database)

Per `PAYMENTX_PHASE_3_ARCHITECTURE.md` §7/§8, the explicit decision was PostgreSQL + pgvector, not
Pinecone/Weaviate/Milvus/Qdrant/Chroma:

- **Reuses existing infrastructure.** PaymentX already runs Postgres 16 for every other service; adding a
  second database product for one feature would be new operational surface (backups, monitoring,
  credentials, connection pooling) this platform does not need.
- **Expected scale fits it.** The initial corpus (PaymentX's own runbooks/API docs/ADRs) is realistically
  low thousands of chunks — far below the range where pgvector's HNSW index loses to purpose-built vector
  engines.
- **One query, not two round trips.** Metadata filtering (JSONB `@>`) and vector distance ordering
  (`<=>`) run in the *same* SQL query — a dedicated vector DB would need a second filter round trip or
  its own metadata-indexing feature to match this.
- **Migration path stays open.** If corpus size or query volume ever genuinely outgrows pgvector, this
  service's shape (id, document reference, content, vector, metadata) is already close to what any
  dedicated vector DB's ingestion format expects.

## 3. Why the Existing PostgreSQL Instance

No second Postgres instance was created. `paymentx-vector-service` connects to the **same** shared
`paymentx_ai` database Prompt Service (Phase 3.2) already uses — a deliberate, previously-documented
deviation from one-database-per-service (`PAYMENTX_PHASE_3_ARCHITECTURE.md` §6), extended here rather
than repeated as a new pattern. The **only** infrastructure change was swapping
`infra/docker-compose.yml`'s `postgres` image from `postgres:16-alpine` to `pgvector/pgvector:0.8.0-pg16`
— built FROM the official Postgres 16 image with the pgvector extension's binaries added, same data
directory format, so the existing `paymentx-postgres-data` named volume and all its data are untouched by
the tag change alone.

## 4. Architecture

```
VectorController → VectorStoreService (interface) → VectorStoreServiceImpl
                                                              │
                                              ┌───────────────┼───────────────┐
                                              ▼               ▼               ▼
                                   AiDocumentRepository  AiDocumentChunk-  AiDocumentEmbedding-
                                                          Repository       Repository
                                              │               │               │
                                              └───────────────┴───────────────┘
                                                              │
                                                   PostgreSQL + pgvector
                                                   (shared `paymentx_ai` database)
```

Isolated from LLM provider code, Embedding provider code, AI Chat UI, and payment business logic — this
module has zero compile-time dependency on `paymentx-llm-service`, `paymentx-embedding-service`,
`paymentx-control-center`, or any business service.

## 5. Database Schema

Three tables, mirroring Document → Document Chunk → Embedding Vector:

### `ai_document`
| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `document_key` | VARCHAR(256) NOT NULL | Business identifier |
| `document_name` | VARCHAR(512) NOT NULL | |
| `document_type` | VARCHAR(64) NOT NULL | e.g. "runbook", "api-docs" |
| `source` | VARCHAR(128) | Nullable |
| `document_version` | VARCHAR(32) NOT NULL | e.g. "v1", "2024-01" — **not** the JPA optimistic-lock `version` column |
| `status` | VARCHAR(16) NOT NULL | `ACTIVE` / `ARCHIVED`, CHECK-constrained |
| `metadata` | JSONB | Merged: caller-supplied metadata + `documentType`/`source`/`documentVersion` (see §12) |
| `created_at`/`updated_at`/`created_by`/`updated_by`/`version` | AuditableEntity standard columns | |

**Unique:** `(document_key, document_version)` — the real enforcement behind upsert/versioning (§16, §18).
**Indexes:** btree on `status`; GIN on `metadata`.

### `ai_document_chunk`
| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `document_id` | UUID NOT NULL, FK → `ai_document(id)` **ON DELETE CASCADE** | |
| `chunk_index` | INT NOT NULL | 0-based, assigned server-side from request list order |
| `content` | TEXT NOT NULL | |
| `token_count` | INT, nullable | **Never populated this phase** — no real tokenizer exists here |
| `metadata` | JSONB | |
| audit columns | | |

**Unique:** `(document_id, chunk_index)`. **Index:** btree on `document_id`.

### `ai_document_embedding`
| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `chunk_id` | UUID NOT NULL, FK → `ai_document_chunk(id)` **ON DELETE CASCADE** | |
| `embedding` | `vector(1536)` | pgvector native type |
| `provider` | VARCHAR(64) NOT NULL | e.g. "openai" |
| `model` | VARCHAR(128) NOT NULL | e.g. "text-embedding-3-small" |
| `dimension` | INT NOT NULL | CHECK `= 1536` |
| audit columns | | |

**Unique:** `(chunk_id, provider, model)` — **not** `chunk_id` alone, so one chunk can hold embeddings from
multiple models simultaneously during a model migration. **Indexes:** btree on `chunk_id`; **HNSW** on
`embedding` (`vector_cosine_ops`, see §10).

## 6. Relationships

`ai_document` 1→N `ai_document_chunk` 1→N `ai_document_embedding`. Both foreign keys are `ON DELETE
CASCADE` — deleting a document row removes all its chunks and their embeddings in one statement, at the
database level, never leaving an orphan vector.

## 7. pgvector Extension

Activated by `V1_0_0__create_pgvector_extension.yaml` (`CREATE EXTENSION IF NOT EXISTS vector`) — the
first changeset in this service's own Liquibase changelog, applied through the same mechanism every other
PaymentX schema change uses, never by hand. Verified live: PostgreSQL 16, pgvector **0.8.0** (from the
`pgvector/pgvector:0.8.0-pg16` image), which supports HNSW (added in pgvector 0.5.0).

## 8. Vector Dimension

Fixed at **1536** — both at the Java entity level (`AiDocumentEmbedding.DIMENSION`, `@Array(length =
1536)`) and the database column level (`vector(1536)`, CHECK `dimension = 1536`) — matching Embedding
Service's (Phase 3.4) configured default model, `text-embedding-3-small`. **Known limitation:** switching
Embedding Service to a different-dimension model requires a new Liquibase migration
(`ALTER COLUMN embedding TYPE vector(N)`) *and* re-embedding every existing row — old and new vectors are
not mathematically comparable, so this is a deliberate, documented constraint, not an oversight.

## 9. Embedding Model Compatibility

`provider`/`model`/`dimension` are stored explicitly on every embedding row (not just implied by the
column's fixed width) so a future model migration can distinguish "old model, pending re-embedding" from
"new model" during a rollout. `VectorSearchRequest.provider`/`model` are required (defaulting to
`vector.default-provider`/`vector.default-model` if omitted) because a query vector from one model is not
mathematically comparable to embeddings stored under a different model — the search query filters on
`provider`/`model` explicitly, never comparing across them.

## 10. Index Strategy

**HNSW**, not IVFFlat — `CREATE INDEX ... USING hnsw (embedding vector_cosine_ops)`. Reasoning:

- Expected corpus size (low thousands of chunks) doesn't need IVFFlat's cluster-based scaling.
- IVFFlat requires a pre-training pass and degrades badly on a table that starts empty and grows — HNSW
  builds incrementally with no such cold-start problem.
- Better recall/latency tradeoff at this scale, at the cost of somewhat higher build time — acceptable
  for a corpus this size.
- `vector_cosine_ops` matches the `<=>` operator the search query actually uses; an HNSW index built with
  a mismatched ops class silently cannot accelerate a query using a different distance operator.

## 11. Similarity Strategy

**Cosine distance**, via pgvector's `<=>` operator. `distance` is returned verbatim (mathematically in
`[0, 2]`, 0 = identical direction, 2 = opposite). `score` is *not* an independently computed value — it
is literally `1 - distance` (real cosine similarity, `[-1, 1]`), included because "higher is better" is
the friendlier caller-facing convention, but `distance` stays alongside it so a caller can verify the math
rather than trust an opaque number (never labeled "similarity" without being mathematically one).

## 12. Search Algorithm

```sql
SELECT e.id, c.id, d.id, d.document_key, c.chunk_index, c.content, CAST(d.metadata AS text),
       e.provider, e.model, (e.embedding <=> CAST(:queryEmbedding AS vector)) AS distance
FROM ai_document_embedding e
JOIN ai_document_chunk c ON c.id = e.chunk_id
JOIN ai_document d ON d.id = c.document_id
WHERE d.status = 'ACTIVE'
  AND e.provider = :provider AND e.model = :model
  AND d.metadata @> CAST(:filtersJson AS jsonb)
  AND (e.embedding <=> CAST(:queryEmbedding AS vector)) <= :maxDistance
ORDER BY e.embedding <=> CAST(:queryEmbedding AS vector) ASC
LIMIT :topK
```

One query, database-layer filtering, no "fetch thousands then filter in Java." Native SQL (not HQL),
despite `hibernate-vector` contributing a real `cosine_distance` HQL function — JPQL has no JSONB operator
support, so the whole query stays native SQL for consistency rather than splitting vector-distance (HQL)
from metadata (something else). Result mapped as `List<Object[]>` with explicit, documented column-index
mapping (not an interface projection) — avoids depending on native-query projection matching behavior
across Spring Data versions.

## 13. Metadata Filtering

An empty `filters` map still works with the *same* static query (no dynamic SQL branching): Postgres's
`@>` treats an empty object `'{}'::jsonb` as contained in every object, so `d.metadata @> '{}'::jsonb` is
unconditionally true. `documentType`/`source`/`documentVersion` — real columns on `ai_document` — are
*also* merged into the stored `metadata` JSONB at store time (`mergeDocumentMetadata`), so ALL filtering
(well-known and arbitrary custom tags alike) goes through one mechanism, not a mix of column-equality and
JSONB checks.

## 14. Top-K

Two-layer bound, matching Embedding Service's `maxInputLength` pattern: `VectorSearchRequest.topK` has a
fixed `@Max(100)` at the API boundary; `VectorProperties.maxTopK` (default 100, tunable) is enforced again
in `VectorStoreServiceImpl` against the live config value. `defaultTopK` (5) applies when a request omits
`topK` entirely.

## 15. Upsert / Idempotency

Real database UNIQUE constraints, not application-only checks: `(document_key, document_version)` on
`ai_document`, `(document_id, chunk_index)` on `ai_document_chunk`, `(chunk_id, provider, model)` on
`ai_document_embedding`. A repeat `POST /api/v1/vector/documents` call with the same
`(documentKey, documentVersion)` updates the existing document/chunks/embeddings in place rather than
creating duplicates — verified with a real Testcontainers test (`storeDocument_repeatedIngestionSameDocument_upsertsWithoutDuplicating`).

## 16. Versioning

`(documentKey, documentVersion)` pairs coexist as separate rows — "Payment API Documentation v1" and "v2"
never overwrite each other (verified:
`storeDocument_differentVersionsOfSameKey_coexistAsSeparateDocuments`). `DocumentStatus.ARCHIVED` lets a
superseded version stop appearing in default search results without being physically deleted.

## 17. Transactions

`storeDocument` is one `@Transactional` method spanning document + all chunks + all embeddings — a real
`VectorException` (e.g. one bad vector among several valid chunks) or a real
`DataIntegrityViolationException` rolls back everything written so far in that call
(`storeDocument_oneInvalidChunkAmongValidOnes_rollsBackWholeTransaction`, verified against a real
database — zero partial rows survive). `search`/`health` are `@Transactional(readOnly = true)`.

## 18. Concurrency

Real UNIQUE constraints are the actual enforcement — a concurrent duplicate insert racing past this
service's own find-or-create check fails with a real `DataIntegrityViolationException` at the database
level, mapped to a real HTTP 409 by `GlobalExceptionHandler` (a genuine gap found and fixed during this
phase: the handler was initially missing `AuthorizationDeniedException` → 403 mapping, causing a false
500; both are now correctly handled). Verified with a real 5-thread concurrent-ingestion test asserting
exactly one surviving document/chunk row regardless of how many threads raced.

## 19. Security

Same JWT-at-API-Gateway trust boundary as every PaymentX service. `storeDocument`/`deleteDocument` require
`hasRole('VECTOR_ADMIN')` (real mutating operations); `search`/`health` stay open (the routine,
non-admin operation a future RAG Service needs) — matches Prompt Service's exact "open reads, admin-gated
writes" split. No arbitrary SQL or arbitrary vector queries are ever accepted from a caller — every query
this service runs is one of two fixed, parameterized statements.

## 20. Observability

10 Micrometer metrics (`VectorMetrics`): `vector_store_requests_total`, `vector_store_success_total`,
`vector_store_failure_total`, `vector_search_total`, `vector_search_latency`, `vector_insert_latency`,
`vector_update_total`, `vector_delete_total`, `vector_search_results`, `vector_dimension_errors`. Never
logs vector values or full chunk content. `CorrelationIdFilter` propagates `X-Correlation-Id`.

## 21. Testing

**27/27 tests passing, against a real PostgreSQL + pgvector database (Testcontainers,
`pgvector/pgvector:0.8.0-pg16` — the same image `infra/docker-compose.yml` now uses):**

| Test class | Kind | Coverage |
|---|---|---|
| `VectorStoreServiceImplTest` | Real Spring context + real Testcontainers Postgres+pgvector (19 tests) | Store/upsert, versioning, dimension/NaN/infinite validation, whole-transaction rollback, cascade delete, real cosine-ordered search, topK bounds, metadata filtering, minScore threshold, empty results, multi-model coexistence, 5-thread concurrent ingestion, real health check |
| `VectorControllerIntegrationTest` | Real Spring context + real Testcontainers Postgres+pgvector (8 tests) | Validation → 400, VECTOR_ADMIN-gated 403/201, dimension mismatch → 422, open search → 200, topK ceiling → 400, health → 200 |

Two real bugs found and fixed during this phase: (1) `GlobalExceptionHandler` was missing an
`AuthorizationDeniedException` handler, causing a 403-worthy rejection to surface as a 500 — fixed by
adding the handler Prompt Service already established; (2) a test-isolation bug in
`VectorControllerIntegrationTest` (no per-test database cleanup) caused a flaky assertion when two tests'
identical constant-vector fixtures tied in cosine distance — fixed by adding the same `@BeforeEach`
cleanup `VectorStoreServiceImplTest` already used.

## 22. Docker / Infra

`infra/docker-compose.yml`'s `postgres` image changed from `postgres:16-alpine` to
`pgvector/pgvector:0.8.0-pg16` — the one infra-affecting change the Phase 3.0 architecture called for.
Pinned to an exact version, not the floating `pg16` tag. No new container, no new volume, no destroyed
data — same data directory format, same named volume. `infra/prometheus.yml` gained a
`paymentx-vector-service` scrape target (port 8095).

## 23. Liquibase

Two changesets, applied through this service's own `db.changelog-master.yaml`, in the shared `paymentx_ai`
database: `V1_0_0__create_pgvector_extension.yaml` (extension activation), `V1_0_1__create_ai_vector_tables.yaml`
(three tables, constraints, indexes, HNSW index). No explicit `rollback:` blocks — matches this platform's
existing convention (Prompt Service's/Routing Service's own raw `sql:` changesets carry none either);
Liquibase's changelog-tracking table is what actually prevents re-running a changeset.

## 24. Future RAG Integration

- `POST /api/v1/vector/documents` and `POST /api/v1/vector/search` are the exact storage/retrieval
  boundary a future RAG Service will call — this phase provides the API, not the orchestration (Step 28).
- The future flow: `User Query → Embedding Service → Query Vector → Vector Service (this phase) → Top-K
  Chunks → Prompt Construction → LLM Service → Answer` — every piece except "Prompt Construction" and
  the orchestration loop itself already exists as a real service after Phase 3.5.
- A document-ingestion pipeline (chunker, PDF/DOCX/CSV parsing) is explicitly NOT built here — this
  service accepts already-chunked, already-embedded text only.

## 25. Known Limitations

- Fixed 1536-dimension embedding column — changing the configured model/dimension needs a real migration
  plus re-embedding (§8).
- No document-ingestion pipeline — chunking and file parsing are future-phase concerns.
- `token_count` is always null — no real tokenizer exists in this service.
- Chunk-level `metadata` is stored but not currently used as a search-filter dimension (only
  document-level metadata is filterable) — a deliberate scope simplification, not a missing feature that
  silently fails.
- Single-region, single-instance Postgres — no read-replica routing for search traffic in this phase.

---

## Explicitly NOT Implemented in Phase 3.5

- **RAG Service / RAG orchestration** — NOT IMPLEMENTED.
- **Document ingestion pipeline** — NOT IMPLEMENTED.
- **PDF parsing** — NOT IMPLEMENTED.
- **DOCX parsing** — NOT IMPLEMENTED.
- **MCP Gateway** — NOT IMPLEMENTED.
- **Agent Orchestrator** — NOT IMPLEMENTED.
- **Multi-Agent** — NOT IMPLEMENTED.
- **AI Security platform** — NOT IMPLEMENTED.

These are reserved for Phase 3.6 and later, per `PAYMENTX_PHASE_3_ARCHITECTURE.md`.
