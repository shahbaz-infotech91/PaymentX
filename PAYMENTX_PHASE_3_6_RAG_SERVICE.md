# PaymentX — Phase 3.6: RAG Service

**Status:** Complete
**Depends on:** Phase 3.0–3.5 (Architecture, AI Chat, Prompt Service, LLM Service, Embedding Service, Vector Service)
**Does NOT implement:** MCP Gateway, Agent Orchestrator, Multi-Agent, AI Security platform, document ingestion pipeline, payment mutation tools

---

## 1. RAG Purpose

Phase 3.6 delivers `paymentx-rag-service`, a pure orchestration layer implementing
Retrieval-Augmented Generation: it embeds a user's question, searches the PaymentX knowledge base
stored by Vector Service, applies a relevance threshold, builds a bounded and deduplicated context,
renders a grounded prompt via Prompt Service, and calls LLM Service for a real, source-cited answer.
**The Critical Principle**: if the knowledge base has nothing relevant enough, the LLM is never called at
all — the service returns a truthful `INSUFFICIENT_CONTEXT` result instead of inventing an answer.

## 2. Architecture

```
RagController → RagService (interface) → RagServiceImpl
                                                  │
                     ┌───────────────┬────────────┼────────────┬───────────────┐
                     ▼               ▼            ▼            ▼               ▼
          EmbeddingServiceClient  VectorServiceClient  ContextBuilder  PromptServiceClient  LlmServiceClient
                     │               │                              │               │
                     ▼               ▼                              ▼               ▼
          Embedding Service   Vector Service                 Prompt Service    LLM Service
              (8094)              (8095)                        (8092)           (8093)
```

`paymentx-rag-service` owns **zero database**, **zero provider-specific logic**, **zero vector-search
implementation** — every real capability is delegated to an already-built service via REST, matching the
brief's explicit "do not invent duplicate implementations." Isolated from LLM/Embedding provider code,
Vector Service's database internals, and the AI Chat frontend — no compile-time dependency on any of the
four services it calls.

## 3. Query Flow

`POST /api/v1/rag/query`:

1. Validate the query (blank/length/topK/filter-shape).
2. Embed the query via **Embedding Service**.
3. Search **Vector Service** for the raw top-K (no threshold applied there — see §7).
4. Apply the relevance threshold client-side; if nothing passes, return `INSUFFICIENT_CONTEXT` —
   **Prompt Service and LLM Service are never called on this path**.
5. Build a bounded, deduplicated, deterministically-ordered context (`ContextBuilder`).
6. Render the grounded prompt via **Prompt Service** (`PAYMENTX_KNOWLEDGE_ASSISTANT`).
7. Call **LLM Service** with the rendered prompt.
8. Return a real, source-cited `RagQueryResponse`.

## 4. Embedding Integration

`EmbeddingServiceClient` calls Embedding Service's real `POST /api/v1/embeddings` — no compile-time
dependency on `paymentx-embedding-service`'s DTOs (JsonNode parsing, matching Control Center's
`AiPlatformClient` convention). No embedding provider logic is duplicated.

## 5. Vector Search Integration

`VectorServiceClient` calls Vector Service's real `POST /api/v1/vector/search` — never touches pgvector
or Postgres directly. `provider`/`model` are always passed explicitly (from `RagProperties`, the single
source of truth also used for the Embedding Service call) so a query vector is never compared against
embeddings from an incompatible model (§9 — Model Compatibility).

## 6. Top-K

Two-layer bound, matching every prior AI Platform service's pattern: `RagQueryRequest.topK` has a fixed
`@Max(20)` at the API boundary; `RagProperties.maxTopK` (default 20, tunable) is enforced again in
`RagServiceImpl`. `defaultTopK` (5) applies when omitted. Deliberately much smaller than Vector Service's
own `maxTopK` (100, Phase 3.5) — this ceiling protects the LLM's context budget, not the database.

## 7. Relevance Threshold

`rag.min-score` (default 0.5) is applied **client-side in `RagServiceImpl`**, against the *raw* top-K
Vector Service returns — deliberately **not** delegated to Vector Service's own `minScore` search
parameter, so `RagServiceImpl` can see and count the rejected candidates
(`rag_relevance_threshold_rejections`, `RagQueryMetadata.rejectedByThreshold`). Score is the same real
cosine similarity Vector Service computes (`1 - distance`) — never confused with distance (lower is
better) or mislabeled.

## 8. Context Builder

A dedicated `ContextBuilder` component (not folded into `RagServiceImpl`):

1. **Deterministic ordering** — score descending, then `chunkId` as a stable tie-break.
2. **Exact deduplication** — by `chunkId` and by trim+lowercase+whitespace-collapsed content (no
   semantic/embedding-based dedup — that would be a second vector search this phase doesn't need).
3. **Chunk-count ceiling** — `rag.max-context-chunks` (default 5).
4. **Character-length ceiling** — `rag.max-context-characters` (default 8000), character-based because
   no real tokenizer exists anywhere in this platform (never fabricates token counts). Hitting the limit
   truncates gracefully (keeps what fits); `CONTEXT_TOO_LARGE` is reserved for the one genuine edge case
   where a single chunk alone exceeds the configured maximum.

## 9. Prompt Service Integration

`PromptServiceClient` calls Prompt Service's real `POST /api/v1/prompts/{key}/render` — the RAG system
prompt text is **never hardcoded in RAG Service**. A new Liquibase changeset
(`paymentx-prompt-service/.../V1_0_2__seed_payment_knowledge_assistant_prompt.yaml`) seeds
`PAYMENTX_KNOWLEDGE_ASSISTANT` as an **ACTIVE** template (unlike Phase 3.2's DRAFT-only seed — RAG
Service's core function depends on it being renderable from the moment this phase ships). Verified live:
Prompt Service's full Testcontainers suite (38/38) applies this migration successfully.

## 10. LLM Service Integration

`LlmServiceClient` calls LLM Service's real `POST /api/v1/llm/generate` — never a provider SDK directly.
`refused` is read straight from LLM Service's real response and mapped to `RagQueryStatus.REFUSED`, a
state distinct from `INSUFFICIENT_CONTEXT` (a refusal only happens *after* real context was found and
supplied — an operator needs to distinguish "no knowledge" from "model declined").

## 11. Source / Evidence

`RagSource(documentId, chunkId, source, score)` — built **only** from the chunks `ContextBuilder`
actually included in the final prompt, never the raw pre-threshold/pre-dedup search results (a source
list including a chunk the LLM never saw would misrepresent the answer's real evidentiary basis). Never
includes raw embedding vectors, database internals, or credentials.

## 12. Hallucination Control

Structural, not a comment asking future maintainers to remember: when the filtered result set is empty,
the method returns before `ContextBuilder`, `PromptServiceClient`, or `LlmServiceClient` are ever touched
— proven by `verifyNoInteractions` in `RagServiceImplTest` and by real WireMock request-count assertions
(`llmService.verify(0, ...)`) in `RagQueryIntegrationTest`.

## 13. Prompt Injection Boundary

The seeded system prompt establishes SYSTEM INSTRUCTIONS > RETRIEVED DATA > USER CONTENT explicitly in
its own wording: retrieved context is described to the model as reference data, never as instructions,
and the model is told to ignore any instruction-like text appearing inside the CONTEXT block. This is a
prompt-level boundary only — full prompt-injection defense (input sanitization, output filtering) is
explicitly deferred to the future AI Security phase.

## 14. Security

Same JWT-at-API-Gateway trust boundary as every AI Platform service. `RagController` has no
mutating/admin endpoint (`query`/`health` only), so no `@PreAuthorize`/role filter was added — matches
LLM/Embedding Service's identical reasoning. Never exposes the database, pgvector, embedding provider, or
LLM provider to a caller. Accepts no arbitrary SQL or arbitrary vector query — every downstream call is
one of four fixed, parameterized requests.

## 15. Authorization

No new authorization system introduced. `filters` values are restricted to simple scalars
(String/Number/Boolean) to prevent a caller smuggling a nested object into Vector Service's JSONB
containment filter. RAG context is scoped to what the query and filters legitimately request — RAG is not
a bypass around any existing PaymentX authorization boundary (it has no access to payment data beyond
whatever a caller's own filters/knowledge base scope would already permit).

## 16. Observability

11 Micrometer metrics (`RagMetrics`): `rag_requests_total`, `rag_success_total`, `rag_failure_total`,
`rag_insufficient_context_total`, `rag_embedding_latency`, `rag_vector_search_latency`,
`rag_prompt_render_latency`, `rag_llm_latency`, `rag_total_latency`, `rag_context_chunks`,
`rag_context_size`, `rag_relevance_threshold_rejections`. Never logs query text, retrieved content, or the
answer itself.

## 17. Distributed Tracing

`CorrelationIdFilter` reads/generates `X-Correlation-Id` and `RagServiceImpl` forwards the same ID to all
four downstream clients — the real mechanism behind Control Center → AI Chat → RAG Service →
Embedding/Vector/Prompt/LLM Service all sharing one correlation ID end to end, reusing existing Zipkin/MDC
infrastructure, no new tracing system.

## 18. Error Handling

`RagErrorCodes`: `INVALID_QUERY`, `EMBEDDING_SERVICE_UNAVAILABLE`, `VECTOR_SERVICE_UNAVAILABLE`,
`PROMPT_SERVICE_UNAVAILABLE`, `LLM_SERVICE_UNAVAILABLE`, `LLM_TIMEOUT`, `CONTEXT_TOO_LARGE`,
`INTERNAL_ERROR`. `INSUFFICIENT_CONTEXT` is deliberately **not** one of these — it is a normal HTTP 200
`RagQueryResponse.status` value, not an exception (see §12). Standard `ApiResponse`/`ErrorResponse`
envelope, no stack traces exposed.

## 19. Timeout

Each of the four downstream clients has its own bounded connect/read timeout (`RagProperties`) — no
single dependency can hang the whole request indefinitely. The overall request's bound is the sum of four
individually-bounded calls, a simple and honest guarantee rather than a separate deadline-tracking
mechanism.

## 20. Retry

Four independent Resilience4j instances (`embeddingService`/`vectorService`/`promptService`/`llmService`),
each with its own `RetryConfigCustomizer` predicate reading `RagException.isRetryable()` — never retries
`INVALID_QUERY` or any 4xx-derived failure. **A real, platform-wide bug was found and fixed during this
phase**: `@CircuitBreaker`/`@Retry` require `spring-boot-starter-aop` (for AspectJ pointcut-expression
parsing) to actually intercept a method call — its absence is silent (no startup error), and none of
LLM Service, Embedding Service, or RAG Service originally declared it. This meant `@Retry`/`@CircuitBreaker`
had been **silent no-ops since Phase 3.3** in every AI Platform service that used them, never actually
retrying anything, despite being correctly annotated and configured. Discovered via this phase's own real
WireMock retry test (a scenario-based stub proving zero retry attempts occurred), fixed by adding
`spring-boot-starter-aop` to `paymentx-rag-service`, `paymentx-llm-service`, and
`paymentx-embedding-service` (Vector Service is unaffected — it has no external HTTP calls to retry). All
three services' full test suites re-verified green after the fix (RAG Service 26/26, LLM Service 16/16,
Embedding Service 28/28).

## 21. Caching Decision

**Deferred, deliberately.** PaymentX knowledge can change (a document can be re-ingested/updated via
Vector Service's upsert), so a query/result cache risks serving stale grounded answers — a correctness
risk for a fintech knowledge assistant that outweighs the latency benefit at this phase's expected usage
volume. No caching infrastructure (Redis or otherwise) was added. Revisit once real usage patterns and a
real staleness tolerance are known.

## 22. Testing

**26/26 tests passing:**

| Test class | Kind | Coverage |
|---|---|---|
| `ContextBuilderTest` | Pure unit, no mocks (8 tests) | Ordering, dedup, chunk/character limits, `CONTEXT_TOO_LARGE` edge case |
| `RagServiceImplTest` | Mockito, 4 mocked clients (11 tests) | Validation, structural `INSUFFICIENT_CONTEXT` short-circuit, per-dependency failure propagation, refusal mapping, threshold rejection counting |
| `RagQueryIntegrationTest` | Real Spring context + 4 real WireMock servers (7 tests) | Step 38's exact E2E scenario, validation, real retry (post-AOP-fix), correlation-ID propagation, health |

## 23. End-to-End Flow (Step 38's Scenario)

Question: *"Why was a payment rejected because of a duplicate request?"* — a stubbed Vector Service
response containing the real chunk *"Duplicate payment requests are rejected when the same idempotency
key is reused."* is retrieved, genuinely reaches the rendered prompt sent to (stubbed) LLM Service
(verified via real WireMock request assertions, not an assumption), and the final response is a real,
non-empty, source-cited answer (`sources[0].source == "payment-validation-runbook"`). Real chain, real
HTTP, zero Mockito in this specific test class — see `RagQueryIntegrationTest`'s own javadoc for why
WireMock stand-ins (not a second live `paymentx-vector-service` Spring context) were used, matching this
platform's established testing convention from every prior phase.

## 24. Known Limitations

- Chunk-level metadata is stored by Vector Service but not used as a RAG-level filter dimension (only
  document-level metadata is filterable, matching Vector Service's own Phase 3.5 scope).
- No token-accurate context budgeting — character-based limits only (documented, not fabricated).
- No caching (§21, deliberately deferred).
- The `@Retry`/`@CircuitBreaker` AOP fix (§20) was applied to LLM Service and Embedding Service as a
  minimal, additive dependency change; their own resilience *behavior* was not otherwise re-verified
  beyond their existing test suites passing unchanged.
- No document ingestion pipeline — RAG Service assumes Vector Service already contains real knowledge
  (seeded via Phase 3.5's own store API, exercised by this phase's own test fixtures, never by a file
  upload).

---

## Explicitly NOT Implemented in Phase 3.6

- **MCP Gateway** — NOT IMPLEMENTED.
- **Agent Orchestrator** — NOT IMPLEMENTED.
- **Multi-Agent** — NOT IMPLEMENTED.
- **AI Security platform** — NOT IMPLEMENTED.
- **Payment mutation tools** (retry/cancel/refund) — NOT IMPLEMENTED.
- **Document ingestion pipeline** (PDF/DOCX/CSV parsing, chunking, crawling) — NOT IMPLEMENTED.

## 25. Phase 3.7 Integration Points

- `POST /api/v1/rag/query` is the real, stable contract a future MCP Gateway or Agent Orchestrator can
  call as one read-only "consult PaymentX knowledge" tool.
- Control Center's AI Assistant now calls RAG Service (Step 35) — a future phase could surface
  `sources`/`metadata` in the chat UI (deliberately not done this phase, keeping the existing
  `AiChatResponse` contract unchanged).
- The seeded `PAYMENTX_KNOWLEDGE_ASSISTANT` prompt and `ContextBuilder`'s citation format are ready for a
  future document-ingestion phase to populate Vector Service with real PaymentX documentation at scale.
