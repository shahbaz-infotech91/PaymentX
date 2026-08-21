# PaymentX Phase 3.4 — Local Embedding Migration

**Migration: COMPLETE** for the Embedding Service itself (real, tested, live-verified).
**Vector DB re-indexing: REQUIRED, NOT PERFORMED** (explicitly out of this migration's scope
— "Migrate ONLY the Embedding Service").

## 1. Why OpenAI embedding was replaced

Phase 3.9's real end-to-end validation found the OpenAI account behind `EMBEDDING_API_KEY`
out of quota/billing (`EMBEDDING_RATE_LIMITED`, reproduced twice, not transient) — a real,
external, account-level failure that blocked every real RAG retrieval and any new vector
ingestion. This migration removes that runtime dependency entirely: the normal path no
longer needs `EMBEDDING_API_KEY` or a network call to `api.openai.com`.

## 2. Architecture (unchanged shape, one new implementation)

```
RAG Service
   -> EmbeddingService (interface, unchanged)
        -> EmbeddingServiceImpl (unchanged contract; health() generalized — see §9)
             -> EmbeddingProvider (interface — 2 new methods: configuredModel(), isReady())
                  -> LocalEmbeddingProvider (NEW, default)  -> DJL -> ONNX Runtime -> local model
                  -> OpenAiEmbeddingProvider (unchanged, still real, opt-in via embedding.provider=openai)
```

Nothing in `EmbeddingController`, any DTO (`EmbeddingRequest`/`EmbeddingResponse`/
`BatchEmbeddingRequest`/`BatchEmbeddingResponse`), or `EmbeddingServiceImpl`'s `embed()`/
`embedBatch()` logic changed. Bean selection is a real Spring
`@ConditionalOnProperty(prefix="embedding", name="provider", ...)` on each provider:
`LocalEmbeddingProvider` has `matchIfMissing=true` (the new default);
`OpenAiEmbeddingProvider` requires `embedding.provider=openai` explicitly. Exactly one
provider bean exists on the context at a time — no runtime switching, no ambiguous-bean
risk.

## 3. Selected model

**`sentence-transformers/all-MiniLM-L6-v2`**, resolved through DJL's real HuggingFace/ONNX
Runtime model zoo (`djl://ai.djl.huggingface.onnxruntime/sentence-transformers/all-MiniLM-L6-v2`).

Why this model, against the brief's 9 criteria:
1. Runs locally — yes, ONNX Runtime, CPU, in-process.
2. No external API key — yes.
3/4/5. Suitable for English technical documentation and RAG, good semantic-search
   quality — one of the most widely deployed sentence-embedding models for exactly this
   use case; real evidence in §14 below (not just reputation).
6. Reasonable CPU/memory for a developer laptop — real model file is 87MB (ONNX weights)
   + ~13MB tokenizer/native libs; real measured latency in §15 is single-digit-to-tens of
   milliseconds per text on CPU.
7. Java/Spring Boot integration practical — Deep Java Library (DJL, Apache-2.0, AWS) is a
   real, maintained Java-native inference runtime with a purpose-built
   `TextEmbeddingTranslatorFactory` (tokenize + mean-pool + normalize) for exactly this
   model family — no hand-rolled tokenizer, no Python subprocess, no separate service.
8. License — DJL: Apache-2.0. `sentence-transformers/all-MiniLM-L6-v2`: Apache-2.0. Both
   permit this project's use.
9. Vector dimension explicitly known — **384**, fixed, real (verified in §14/§15, not
   assumed).

A larger/higher-recall model (e.g. `bge-small-en-v1.5`, also 384-dim) was considered but
not selected — `all-MiniLM-L6-v2` is the more conservative, better-established default for
a first local-embedding migration, and switching later is a one-line config change (§19).

## 4. Model runtime

Deep Java Library 0.36.0 (`ai.djl:api`, `ai.djl.onnxruntime:onnxruntime-engine`,
`ai.djl.huggingface:tokenizers`) — real Maven Central dependencies, added only to
`paymentx-embedding-service/pom.xml`. `onnxruntime-engine` pulls Microsoft's real
`onnxruntime` native binary for the host platform as its own transitive dependency (not
committed to this repo). CUDA is not available in this environment — DJL logs a warning
and falls back to CPU automatically; this is expected and was observed for real (see
`ai.djl.onnxruntime.engine.OrtEngine` warning in every real test/service run this phase).

## 5. Model storage

The model is **never** committed to `C:\PaymentX`. DJL downloads the real ONNX weights +
tokenizer files on first use into its own default cache directory —
**`C:\Users\<user>\.djl.ai`** (confirmed by real inspection this phase: 101MB total,
`model.onnx` = 87MB, `tokenizer.json` = 696KB, `vocab.txt` = 228KB, plus native tokenizer
libraries). `embedding.local.cache-dir` (env `EMBEDDING_LOCAL_CACHE_DIR`) can override this
via DJL's own real `DJL_CACHE_DIR` system property — not a new convention invented here.
A developer obtains the model simply by starting the service with network access once;
DJL caches it and every subsequent start reuses the cache with no re-download.

## 6. Configuration

`paymentx-embedding-service/src/main/resources/application.yml`:

```yaml
embedding:
  provider: ${EMBEDDING_PROVIDER:local}      # was hardcoded "openai"; local is now the default
  local:
    model: ${EMBEDDING_LOCAL_MODEL:sentence-transformers/all-MiniLM-L6-v2}
    dimension: ${EMBEDDING_LOCAL_DIMENSION:384}
    max-sequence-length: ${EMBEDDING_LOCAL_MAX_SEQ_LENGTH:256}   # real tokenizer truncation length
    cache-dir: ${EMBEDDING_LOCAL_CACHE_DIR:}                     # blank = DJL's own default
  openai:                                     # unchanged, still real, opt-in only
    api-key: ${EMBEDDING_API_KEY:}
    ...
```

No new configuration convention was invented — `embedding.local.*` matches the existing
`embedding.openai.*` nested-properties pattern exactly (`EmbeddingProperties.Local`, same
shape as `EmbeddingProperties.OpenAi`).

## 7. EmbeddingService contract

**Unchanged.** `EmbeddingService.embed()`/`embedBatch()`/`health()` and every DTO keep
their exact existing shape. `EmbeddingController`'s three endpoints
(`POST /api/v1/embeddings`, `POST /api/v1/embeddings/batch`, `GET
/api/v1/embeddings/health`) are untouched.

## 8. Local provider implementation

`LocalEmbeddingProvider` (`paymentx-embedding-service/.../provider/local/`):
- Loads the model once in `@PostConstruct init()`; deliberately does **not** throw on
  failure (no internet, corrupted cache, etc.) — `ready` stays `false` and `isReady()`/
  `health()` report it honestly instead of crashing service startup.
- `embed()`: real inference via `Predictor.predict()`, one call per input text (batch
  support = looping, preserving order/count — not internally-batched ONNX inference; see
  Limitations). Validates output dimension (384) and rejects non-finite values, mirroring
  `OpenAiEmbeddingProvider`'s own defensive checks exactly.
- Thread-safety: DJL's `Predictor` is not safe for concurrent `predict()` calls from
  multiple threads — every real call is serialized through one `synchronized` block. Fine
  for this migration's real scope (a single developer/CI machine); a predictor pool is a
  real, identified follow-up if concurrent throughput matters later (see §17).
- No fake/random vectors anywhere — every vector returned is a real DJL/ONNX Runtime
  inference result, proven in §14.

## 9. Vector DB compatibility — **RE-INDEX REQUIRED**

Inspected the real schema
(`paymentx-vector-service/.../V1_0_3__create_ai_document_embedding-table.yaml`):

```sql
embedding vector(1536) NOT NULL
CHECK (dimension = 1536)
CREATE INDEX ... USING hnsw (embedding vector_cosine_ops)
```

pgvector's `vector(N)` column type is a **fixed** dimension. A real local embedding
(384-dim) cannot be stored in this column — confirmed live this phase:

```
POST :8096/api/v1/rag/query
-> HTTP 503 {"errorCode":"VECTOR_SERVICE_UNAVAILABLE",
    "message":"Vector Service call failed: queryEmbedding has 384 dimensions but this
    service is configured for 1536."}
```

Vector Service correctly and honestly rejected the mismatched dimension rather than
corrupting data or silently truncating/padding — this is Vector Service's own real
validation working exactly as designed. **This migration does NOT alter Vector Service's
schema** — that is explicitly out of scope ("Migrate ONLY the Embedding Service"). A real
Phase 3.5 follow-up must: add a Liquibase changeset altering `embedding` to `vector(384)`
(or a new column/table if multiple dimensions must coexist), update the `CHECK
(dimension = 1536)` constraint, and rebuild the HNSW index for the new dimension/ops
class.

**No existing data is at risk.** Phase 3.9 confirmed `documentCount: 0` in Vector
Service's real database — there is nothing to re-embed or lose. Any embedding generated
under the old OpenAI/1536 configuration would need re-embedding under the new local/384
model regardless, since a vector's dimension is tied to the exact model that produced it —
this is stated here for completeness, not because real data exists to migrate.

## 10. RAG compatibility

`paymentx-rag-service` has **zero hardcoded dimension assumptions** (confirmed by
source-code inspection — no `1536` literal anywhere in its real logic) — it is fully
pass-through, consuming whatever `EmbeddingResponse`/`VectorServiceClient` contracts
report. Live-verified this phase: RAG Service successfully called Embedding Service and
received a real local embedding (the failure that used to happen there —
`EMBEDDING_SERVICE_UNAVAILABLE` from the OpenAI quota outage — no longer happens); the
request then correctly failed one layer downstream, at Vector Service, for the real
dimension-mismatch reason in §9. **RAG -> Embedding: PASS (REAL EXECUTED). RAG -> Vector
DB retrieval: NOT EXECUTED (blocked by §9's real, documented, out-of-scope schema
mismatch), not a RAG defect.**

## 11. Testing

38/38 targeted `paymentx-embedding-service` tests pass, 0 regressions:
- `EmbeddingServiceImplTest`: 14 (5 new/rewritten health tests covering both providers'
  real status strings; 9 pre-existing embed/validation tests unchanged and still passing)
- `OpenAiEmbeddingProviderTest`: 10 (unchanged, still passing — this provider is untouched
  behaviorally)
- `EmbeddingControllerIntegrationTest`: 6 (unchanged, now explicitly opts into
  `embedding.provider=openai` since that's what this WireMock-based suite tests — see its
  updated `@DynamicPropertySource`)
- `LocalEmbeddingProviderTest` (**new**, 8 tests, all against a real loaded model): model
  loads and reports ready; a real single embed returns a real, non-zero, finite 384-dim
  vector; the same real input is deterministic across two calls; real batch embedding
  preserves count/order and produces genuinely different vectors per input; a very long
  (~2400-word) real input is truncated, not rejected; **real semantic similarity** — two
  differently-worded but related real queries score a real cosine similarity > 0.5 and
  higher than an unrelated real query (Step 13, done directly on real vectors since Vector
  DB search is blocked per §9); an uninitialized provider reports `isReady()=false` and
  throws a real, honest `EMBEDDING_NOT_CONFIGURED` rather than a fake vector; a deliberately
  invalid model id fails to load without crashing the provider/service.

No `mvn clean install` / full reactor build; only `paymentx-embedding-service` was built
and tested.

## 12. Real local embedding test (Step 12)

Both as an automated test (§11) and live against the actually-running service (restarted
with this phase's code):

```
GET  :8094/api/v1/embeddings/health
-> {"status":"LOCAL_EMBEDDING_READY","provider":"local",
    "configuredModel":"sentence-transformers/all-MiniLM-L6-v2","dimension":384}

POST :8094/api/v1/embeddings {"text":"Duplicate payment detection prevents the same
    payment from being processed more than once."}
-> HTTP 200, 25ms, {"dimension":384,"model":"sentence-transformers/all-MiniLM-L6-v2",
    "provider":"local","embedding":[-0.079065934,-0.017633015,0.02316809, ...]}
```

Real, non-zero, finite values; 25ms latency (vs. hundreds-of-ms-to-seconds for the real
OpenAI network calls observed in Phase 3.9) is itself evidence this is genuine in-process
CPU inference, not a disguised network call. No `api.openai.com` DNS/connection occurs on
this path — confirmed both by code inspection (`LocalEmbeddingProvider` contains no HTTP
client at all) and by the observed latency profile. **PASS — REAL EXECUTED.**

## 13. Semantic similarity (Step 13)

Vector DB-backed search could not be used (§9's real, documented schema block). Instead,
real cosine similarity was computed directly on real vectors from the live model
(`LocalEmbeddingProviderTest.embed_semanticallyRelatedQueries_moreSimilarThanUnrelatedQuery`):
`"How does PaymentX prevent duplicate payments?"` vs. `"How does idempotency stop the same
payment from being processed twice?"` scored a real cosine similarity **> 0.5** and higher
than either scored against an unrelated PaymentX question (dashboard theme color). **PASS
— AUTOMATED TEST on real vectors, not a Vector-DB-backed search** (that part: NOT
EXECUTED, see §9).

## 14. Performance (Step 16)

Real, measured, not benchmarked at length:
- Single embed via the live HTTP endpoint: **25ms** (includes HTTP + JSON + real
  inference).
- Batch of 3 texts via the live HTTP endpoint: **93ms** total (~31ms/text).
- Model load time (`@PostConstruct init()`, cold JVM, model already cached from a prior
  run): **~0.7s** in the live service log (`OrtModel`/`Platform` init through "loaded and
  ready").
- Model + native library disk footprint: **101MB** (`~/.djl.ai`), entirely outside
  `C:\PaymentX`.

No sustained-throughput/concurrency benchmark was run — out of this migration's scope
("do not perform a long benchmark... establish practical developer-machine performance").

## 15. Security (Step 17)

- No `EMBEDDING_API_KEY` (or any other secret) is required, read, logged, or referenced by
  `LocalEmbeddingProvider` — it has no HTTP client and no credential field at all.
- `embedding.local.cache-dir` and `embedding.local.model` are plain, non-secret config
  strings; nothing new was written to `.env`/`application.yml`/source that looks like a
  credential.
- The embedding model is a fixed-function numeric encoder — it has no tool-calling,
  code-execution, or agentic capability of any kind, so "prompt injection executing a
  tool" does not apply to it; input text is only ever treated as data to be numerically
  encoded, never interpreted as an instruction, by construction of what a sentence
  embedding model does.
- No shell command is executed by or through the model.

## 16. Limitations

1. **Vector DB schema migration not performed** (§9) — explicitly out of this phase's
   scope. RAG retrieval against real stored documents remains blocked until that real,
   separate schema change happens.
2. **Batch embedding is a loop, not internally-batched ONNX inference** — correct and
   order-preserving, but not maximally throughput-optimized; acceptable at this migration's
   real measured scale (§14) but a real, identified optimization opportunity.
3. **Single shared `Predictor`, `synchronized`** — real concurrent embedding requests are
   serialized, not parallelized. Fine for a developer/CI machine; a predictor pool is the
   real follow-up for higher real concurrency.
4. **No GPU support in this environment** — DJL correctly falls back to CPU (real,
   observed `OrtEngine` warning); GPU would only matter for far higher throughput than
   this migration's real, measured scope needs.
5. **Model quality is not independently benchmarked against MTEB/BEIR here** — relies on
   `all-MiniLM-L6-v2`'s well-established general reputation plus this phase's own real
   semantic-similarity evidence (§13), not a full retrieval-quality evaluation suite.

## 17. How to run locally

1. Ensure network access at least once (to let DJL download the model into
   `~/.djl.ai`) — no manual download step exists or is needed.
2. `mvn -pl paymentx-embedding-service spring-boot:run` (no `EMBEDDING_API_KEY` needed).
3. `GET http://localhost:8094/api/v1/embeddings/health` should report
   `LOCAL_EMBEDDING_READY` once the model finishes loading (real, ~1s after startup).
4. `POST http://localhost:8094/api/v1/embeddings` with `{"text": "..."}` returns a real
   384-dimension vector.

## 18. How to switch provider later

Set `embedding.provider=openai` (env `EMBEDDING_PROVIDER=openai`) plus a real
`EMBEDDING_API_KEY` — `OpenAiEmbeddingProvider` is unchanged and fully functional, it is
simply no longer the default bean. No code change is required to switch back. A third
provider (e.g. Voyage) would follow the exact same pattern: implement `EmbeddingProvider`,
add a `@ConditionalOnProperty(..., havingValue = "voyage")` gate, add its own
`EmbeddingProperties` nested config section — nothing in `EmbeddingServiceImpl`,
`EmbeddingController`, or any DTO would need to change (Step 5's provider-independence
goal, real and demonstrated by this migration itself being exactly that kind of addition).

## 19. Impact on Phase 3.9

Phase 3.9's real, live E2E chain is directly affected in exactly the way its own "Known
limitations" section predicted would need resolving: the Embedding leg (previously FAIL —
real OpenAI quota exhaustion) is now real and working (§12). RAG's embedding call now
succeeds where it previously failed (§10). The full RAG retrieval chain remains blocked,
now for a **different, documented, real reason** (§9's dimension mismatch) rather than the
prior external billing failure — this is real, measurable progress, not a full resolution.
Per this migration's explicit instructions, Phase 3.9's full validation suite was **not**
re-run; only the targeted compatibility checks in §10 were performed.
