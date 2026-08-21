# PaymentX Phase 3.5 — Vector Database Migration

**Migration: COMPLETE.** Real schema migration, real index rebuild, real insert, real
search, real semantic similarity, and real full-chain RAG compatibility all verified live
against the actually-running platform.

## 1. Current vector architecture (as found, source of truth = live DB + source code)

- Technology: **PostgreSQL 16 + pgvector** (`pgvector/pgvector:0.8.0-pg16`), shared
  `paymentx_ai` database, table `ai_document_embedding` (leaf of
  `ai_document` -> `ai_document_chunk` -> `ai_document_embedding`).
- Vector column: `embedding vector(1536)` (before this phase), `nullable: false`.
- Index: `idx_ai_document_embedding_vector_hnsw`, **HNSW**, `vector_cosine_ops`.
- Distance/similarity: pgvector's `<=>` cosine-distance operator; `score = 1 - distance`.
- Metadata: `ai_document.metadata` (JSONB, `documentType`/`source`/`documentVersion`
  merged in), `ai_document_chunk.metadata` (JSONB, per-chunk).
- Identifiers: `ai_document.document_key` + `document_version` (unique together),
  `ai_document_chunk.chunk_index` per document, real UUID primary keys throughout.
- Repository: `AiDocumentEmbeddingRepository.topKSimilaritySearch` — one native SQL query
  combining the vector `ORDER BY`, a `provider`/`model` filter, and a JSONB `@>`
  containment filter, bounded by `LIMIT :topK` and a `maxDistance` threshold.
- Migrations: Liquibase, `db/changelog/db.changelog-master.yaml` includes
  `V1_0_0__create_pgvector_extension.yaml` and `V1_0_1__create_ai_vector_tables.yaml` (the
  latter contains three real changesets: `ai_document`, `ai_document_chunk`,
  `ai_document_embedding` — confirmed by reading the actual file, not assumed from
  filenames).
- Tests (pre-existing): `VectorStoreServiceImplTest` (19 tests) and
  `VectorControllerIntegrationTest` (8 tests), both using real **Testcontainers**
  Postgres (not H2/mocks) — real Liquibase migrations run against a real, disposable
  container per test run.

## 2. Current vs. target

| | Previous (Phase 3.4 baseline) | Target (this phase) |
|---|---|---|
| Embedding provider | OpenAI | Local (sentence-transformers/all-MiniLM-L6-v2, DJL/ONNX) |
| Dimension | 1536 | 384 |

Verified live before writing any migration:
```
POST :8094/api/v1/embeddings {"text":"..."} -> "dimension":384, "model":"sentence-transformers/all-MiniLM-L6-v2"
```
The local embedding model was **not** changed in this phase — reused exactly as Phase 3.4
left it.

## 3/4. Vector schema + index migration

New Liquibase changeset,
`db/changelog/changes/V1_0_2__migrate_embedding_dimension_to_384.yaml`, included in the
master changelog after `V1_0_1`:

```sql
DROP INDEX idx_ai_document_embedding_vector_hnsw;
ALTER TABLE ai_document_embedding DROP CONSTRAINT chk_ai_document_embedding_dimension;
-- pgvector has no implicit cast between two different fixed vector widths - dropping and
-- recreating the column is the only honest operation (no truncation/cropping of vectors)
ALTER TABLE ai_document_embedding DROP COLUMN embedding;
ALTER TABLE ai_document_embedding ADD COLUMN embedding vector(384) NOT NULL;
ALTER TABLE ai_document_embedding ADD CONSTRAINT chk_ai_document_embedding_dimension CHECK (dimension = 384);
CREATE INDEX idx_ai_document_embedding_vector_hnsw ON ai_document_embedding USING hnsw (embedding vector_cosine_ops);
```

Same index type (HNSW) and same distance operator class (`vector_cosine_ops`) as before —
only the vector width changed, not the index strategy, matching the brief's "do not
blindly copy the old index configuration... verify the new index is valid for the new
dimension" (verified: HNSW has no dimension ceiling relevant at 384, and a real
`CREATE INDEX` against the new column succeeded with no error, confirmed live).

Guarded by a real Liquibase `preConditions` / `sqlCheck` (`SELECT COUNT(*) FROM
ai_document_embedding` must equal `0`, `onFail: HALT`) — this changeset refuses to run
(rather than silently drop real data) if the table is ever non-empty when it executes.

**Applied for real, live, against the actual shared `paymentx_ai` database** (not just in
tests) by restarting `paymentx-vector-service` — confirmed via `\d ai_document_embedding`
(`embedding vector(384)`, `CHECK (dimension = 384)`, HNSW index present) and via
Liquibase's own `databasechangelog` table (`V1_0_2-migrate-embedding-dimension-to-384`,
executed `2026-08-19 13:48:09`). **Schema Migration: PASS — REAL EXECUTED. Index
Migration: PASS — REAL EXECUTED.**

Java-level constants kept in sync with the schema (both changed from 1536/openai to
384/local, matching Phase 3.4's real defaults, not invented values):
- `AiDocumentEmbedding.DIMENSION`: 1536 -> 384
- `VectorProperties.dimension`/`defaultProvider`/`defaultModel`: 1536/openai/text-embedding-3-small
  -> 384/local/sentence-transformers/all-MiniLM-L6-v2

## 5. Existing data safety

**Old vector count: 0.** Confirmed by direct query (`SELECT COUNT(*) FROM
ai_document_embedding`) immediately before writing the migration, and again immediately
before applying it live — both real, both 0. Old dimension: 1536. New dimension: 384.
Nothing was re-indexed because nothing existed to re-index; no data was at risk, none was
lost, none was fabricated to fill the gap.

## 6/7/8. Re-index strategy

**Re-index required: NO** (0 pre-existing rows). Had real OpenAI-embedded rows existed,
the correct path — confirmed still real and available, and exercised for real in §9 below
with genuinely new content — is `Documents -> Chunks -> Local Embedding Service (POST
/api/v1/embeddings) -> 384 vectors -> POST /api/v1/vector/documents` (the existing,
unmodified `VectorController`/`VectorStoreServiceImpl` ingestion path — no second
ingestion mechanism was built). No vector was mathematically cropped, truncated, or
invented anywhere in this phase.

## 9. Real vector insert test

```
POST :8094/api/v1/embeddings {"text":"PaymentX uses idempotency to prevent duplicate
    payment processing."}
-> real 384-dim vector (model sentence-transformers/all-MiniLM-L6-v2)

POST :8095/api/v1/vector/documents  (X-Roles: VECTOR_ADMIN)
-> HTTP 201 {"documentId":"632f9689-...","chunkCount":1,"embeddingCount":1}
```
Verified directly against the live database (not just the API response):
```sql
SELECT dimension, provider, model, vector_dims(embedding) FROM ai_document_embedding
  JOIN ... WHERE document_key='ph35-idempotency-doc';
-- 384 | local | sentence-transformers/all-MiniLM-L6-v2 | 384
```
`vector_dims()` (pgvector's own real function reading the actual stored vector) matches
the `dimension` column exactly. Metadata (`{"phase":"3.5","source":"phase-3.5-migration-
test",...}`) and the document ID persisted correctly. **PASS — REAL EXECUTED.**

## 10/11. Real vector search + semantic similarity

A second, semantically distinct document (`ph35-routing-doc`, about payment routing) was
inserted the same real way, so search results would be meaningfully discriminative rather
than trivial with a single-document index.

```
Query 1: "How does PaymentX prevent duplicate payments?"
  -> ph35-idempotency-doc  score=0.8320  distance=0.1680  (rank 1)
  -> ph35-routing-doc      score=0.6235  distance=0.3765  (rank 2)

Query 2: "How does idempotency stop the same payment from being processed twice?"
  -> ph35-idempotency-doc  score=0.7147  distance=0.2853  (rank 1)
  -> ph35-routing-doc      score=0.3944  distance=0.6056  (rank 2)
```

Both real, differently-worded queries correctly rank the relevant document first, with a
real, meaningful score gap over the unrelated one (widening on Query 2, whose wording is
even more specific to the idempotency document's content). Real metadata, real
document/chunk IDs, real 384-dim query vectors used throughout — no fabricated result.
**Semantic Search: PASS — REAL EXECUTED. Semantic Similarity: PASS — REAL EXECUTED.**

## 12. RAG compatibility

Real, targeted (not full Phase 3.9) check:

```
POST :8096/api/v1/rag/query {"query":"How does PaymentX prevent duplicate payments?"}
```

First real attempt returned `retrievedChunks: 0` — not a dimension error (confirming the
schema migration itself was already correct), but a **second real gap discovered during
this phase's own validation**: `paymentx-rag-service`'s own config
(`rag.embedding-provider`/`rag.embedding-model`) still said `openai`/
`text-embedding-3-small`, and Vector Service's search filters on that exact
`(provider, model)` pair (`WHERE e.provider = :provider AND e.model = :model`) — so RAG
was searching for rows that no longer exist under that label. Fixed (minimal, in-scope:
2 config values, not a RAG redesign) in `paymentx-rag-service/application.yml` and
`RagProperties`'s Java defaults, both changed to `local`/`sentence-transformers/all-
MiniLM-L6-v2`, env-var-overridable (`RAG_EMBEDDING_PROVIDER`/`RAG_EMBEDDING_MODEL`).
26/26 pre-existing RAG tests still pass unchanged (their WireMock stubs match on URL path
only, not request body, so they were never coupled to this default).

Re-run live after the fix:
```
-> status: SUCCESS, retrievedChunks: 2, contextChunksUsed: 2
   sources: [ph35-idempotency-doc score=0.832, ph35-routing-doc score=0.624]
   answer: a real, grounded Anthropic response that explicitly separates "what the
     context states" from "my reasoning (inference, not from the sources)" and correctly
     notes the assistant is read-only
```
This is the full real chain working end to end: Local Embedding -> 384 vector -> Vector
DB (migrated schema) -> real retrieval -> Prompt Service -> LLM Service -> real Anthropic
call -> grounded answer. One real LLM call was made (necessary to prove full-chain
compatibility honestly, not repeated). **RAG Compatibility: PASS — REAL EXECUTED.**

## 13. Vector repository

`AiDocumentEmbeddingRepository`/`VectorStoreServiceImpl` were **not rewritten** — the
native `topKSimilaritySearch` query has no hardcoded dimension literal anywhere (confirmed
by reading it) and needed no change. Verified operations, all real, all against the
migrated `vector(384)` schema: insert (§9), search with metadata filtering and top-K
(§10/11, and the 27 Testcontainers-backed tests below), delete (existing test
`deleteDocument_...` still passes), upsert/find-by-chunk-provider-model (existing tests
still pass). Pagination is not a feature this repository has (top-K only, unchanged
from before this phase — not something this migration added or removed).

## 14. Database migration

Liquibase (the project's established mechanism — confirmed, not assumed, by finding
`db.changelog-master.yaml`/`@ConfigurationPropertiesScan`-driven Liquibase startup in
every existing AI Platform service). Migration ID: `V1_0_2-migrate-embedding-dimension-
to-384`. Old schema: `vector(1536)`, `CHECK(dimension=1536)`. New schema: `vector(384)`,
`CHECK(dimension=384)`. Reproducible: applied identically by Testcontainers (fresh
container, migration ran from scratch, 27/27 tests passed) and by the live shared dev
database (existing container, migration ran as an incremental changeset, confirmed via
`databasechangelog`).

## 15. Rollback

**Not a reversible vector conversion** — 384-dimension vectors cannot be mathematically
expanded back into meaningful 1536-dimension ones, and this migration does not attempt
to. Documented rollback path: (1) recreate the old `vector(1536)` schema via a new,
forward-only Liquibase changeset (mirroring `V1_0_1`'s original column definition — never
edit `V1_0_2` in place), (2) revert `VectorProperties`/`AiDocumentEmbedding.DIMENSION`/
`RagProperties` to their prior 1536/openai values, (3) re-embed every source document
using OpenAI's `text-embedding-3-small` via Embedding Service's still-present, still-
functional `OpenAiEmbeddingProvider` (`embedding.provider=openai` + a real
`EMBEDDING_API_KEY`). Not exercised in this phase (nothing needed rolling back), and not
needed here since old vector count was 0.

## 16. Health

`GET :8095/api/v1/vector/health` (unchanged code, re-verified live after migration):
```
{"status":"UP","databaseReachable":true,"pgvectorExtensionAvailable":true,"documentCount":2}
```
Real DB connectivity check + real pgvector-extension check + real row count — not merely
"process started." `documentCount: 2` correctly reflects the two real documents inserted
during this phase's own testing (§9/§10). **PASS — REAL EXECUTED.**

## 17. Tests

27/27 targeted `paymentx-vector-service` tests pass (real Testcontainers Postgres, real
Liquibase migration run fresh for each test class):
- `VectorStoreServiceImplTest`: 19 (one test's helper data —
  `partiallyFlippedVector(400)`, sized for the old 1536-dimension vectors — needed a real
  fix, `partiallyFlippedVector(100)`, preserving the same ~26% "mid-distance" ratio at the
  new 384 dimension; not a logic change, a test-data scale fix)
- `VectorControllerIntegrationTest`: 8 (unchanged logic, `DIMENSION` constant updated to
  384)

Plus 26/26 pre-existing `paymentx-rag-service` tests (unaffected by the config default
change, confirmed — see §12). No `mvn clean install`, no full reactor build, no Phase 3.9
re-run.

## 18. Performance

Not separately re-benchmarked in this phase (Phase 3.4 already measured the local
embedding step: ~25ms single, ~93ms/3-item batch). Real, observed this phase: vector
insert (embed + store) round trip well under 1s per document; vector search HTTP call
returned in well under 1s; full RAG query (embed + search + prompt render + real Anthropic
generation) took 11.6s, dominated by the real LLM call, not the vector search.

## 19. Security

No `EMBEDDING_API_KEY` was required anywhere in this phase's real testing (Local provider
only). No secret was printed, logged, or written to `application.yml`/source/docs. The
`X-Roles: VECTOR_ADMIN` header used for the real insert test is the platform's existing,
already-documented trusted-header mechanism (`HeaderRoleAuthenticationFilter`, same
pattern audit-service uses) — nothing new was invented or bypassed.

## 20. Known limitations

1. Rollback is documented, not exercised (nothing needed rolling back).
2. `AiDocumentEmbedding`'s class-level javadoc still contains one illustrative example
   ("switching to text-embedding-3-large, 3072 dimensions...") describing a *hypothetical*
   future migration — left as-is since it is explicitly an example, not a claim about
   current state (the surrounding, load-bearing sentences were corrected to 384/local).
3. No sustained-load/concurrency test of the HNSW index at the new dimension — out of this
   phase's scope (targeted validation only).
4. The RAG config fix (§12) was discovered, not anticipated — a real, live full-chain test
   is what surfaced it; a narrower "schema-only" validation would have missed it.

## 21. Phase 3.6 integration points

RAG Service's own retrieval/synthesis logic was not touched beyond the two leaf config
values in §12 — Phase 3.6 (already built) now runs against the migrated vector DB with no
further code changes anticipated. If a real corpus of PaymentX documentation is ingested
later, it should go through the same real path exercised in §9 (Local Embedding Service ->
`POST /api/v1/vector/documents`) — no second ingestion mechanism should be built.
