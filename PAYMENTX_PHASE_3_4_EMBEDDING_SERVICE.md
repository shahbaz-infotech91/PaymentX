# PaymentX — Phase 3.4: Embedding Service

**Status:** Complete
**Depends on:** Phase 3.0 (Architecture & Readiness Audit), Phase 3.1 (AI Chat Interface), Phase 3.2 (Prompt Service), Phase 3.3 (LLM Service)
**Does NOT implement:** Vector Database, RAG, MCP, Agents/Multi-Agent, Document Ingestion, Semantic/Similarity Search (Phase 3.5+)

---

## 1. Purpose

Phase 3.4 delivers `paymentx-embedding-service`, a standalone Spring Boot microservice that converts
text into real numerical vector embeddings via a configured provider (OpenAI). It is the third real AI
Platform backend service, after Prompt Service (Phase 3.2) and LLM Service (Phase 3.3). It generates
vectors only — no storage, no search, no retrieval.

## 2. Architecture

```
EmbeddingController → EmbeddingService (interface) → EmbeddingServiceImpl
                                                              │
                                                              ▼
                                                EmbeddingProvider (interface)
                                                              │
                                                              ▼
                                                  OpenAiEmbeddingProvider (adapter)
                                                              │
                                                              ▼
                                            OpenAI REST embeddings API (via RestTemplate)
```

Matches LLM Service's `LlmService → LlmProvider → ProviderAdapter` layering exactly
(`PAYMENTX_PHASE_3_ARCHITECTURE.md` §8). OpenAI's raw JSON shape appears in **exactly one** class:
`provider/openai/OpenAiEmbeddingProvider.java`. Every other class — the controller, `EmbeddingServiceImpl`,
every DTO — only ever sees the provider-agnostic `EmbeddingProviderRequest`/`EmbeddingProviderResult`
(internal) or `EmbeddingRequest`/`EmbeddingResponse`/`BatchEmbeddingRequest`/`BatchEmbeddingResponse`
(public, HTTP-shaped) types.

## 3. Service Boundary

Deployed as its own Maven module, `paymentx-embedding-service`, registered in the root `pom.xml`,
independently deployable and independently runnable — matching Prompt Service's/LLM Service's flat
module placement (Phase 3.0 architecture §16/§17). Port **8094** (Phase 3.0's AI Platform port
allocation). No API Gateway route added — matching the already-established precedent that Prompt
Service (8092) and LLM Service (8093) also have no Gateway route yet; deferred, not forced (Step 30).

## 4. Provider Abstraction

```java
public interface EmbeddingProvider {
    String providerName();
    int dimension();
    EmbeddingProviderResult embed(EmbeddingProviderRequest request);
}
```

Exactly one implementation exists (`OpenAiEmbeddingProvider`), but every call site in the application
layer depends on this interface — adding a second embedding provider later means writing a second
implementation, not touching `EmbeddingServiceImpl`, `EmbeddingController`, or any DTO.

## 5. Provider & Model Selection

**Chosen provider: OpenAI**, model `text-embedding-3-small` (1536 dimensions), called via **Spring's
`RestTemplate`** — not a vendor SDK.

- No Phase 3.0/3.3 precedent selected an embedding provider or mandated an SDK for it (unlike LLM
  Service's Anthropic choice, which the `claude-api` skill mandated the official SDK for). OpenAI has no
  equivalent skill for this platform, so the correct choice is this platform's own dominant outbound-HTTP
  convention (Control Center's `HttpClientConfig`, notification-service's `WebClientConfig`) rather than
  adding a third-party SDK dependency for a single REST endpoint.
  `text-embedding-3-small` was chosen over `text-embedding-3-large` (3072-dim, higher cost/latency) as
  the more conservative, cost-appropriate default for a fintech platform's initial embedding use case;
  fully overridable via config.
- A second embedding provider can be added later behind `EmbeddingProvider` without rewriting business
  logic (Step 2/3).

## 6. Vector Dimension

`embedding.openai.dimension` (default **1536**, matching `text-embedding-3-small`'s real, fixed output
length) is the single source of truth the service validates every response against — **never
hardcoded** in business logic, only in configuration, and configurable independently of the model string
so an operator changing models must also update this value consciously (documented in
`EmbeddingProperties`' javadoc). Similarity strategy: **not applicable** — Phase 3.4 does not perform
similarity search; that is explicitly Phase 3.5 scope.

## 7. Request Contract

`POST /api/v1/embeddings`:
```json
{"text": "Duplicate payment detected", "model": null}
```

`POST /api/v1/embeddings/batch`:
```json
{"texts": ["Payment failed", "Duplicate payment detected"], "model": null}
```

`model` is an optional per-request override of the configured default — `null` means "use
`embedding.openai.model`," never a silently-different hardcoded value.

## 8. Response Contract

```json
{
  "embedding": [0.021, -0.123, 0.456],
  "dimension": 1536,
  "model": "text-embedding-3-small",
  "provider": "openai"
}
```

Batch:
```json
{
  "embeddings": [{"index": 0, "embedding": [...], "errorCode": null, "errorMessage": null}],
  "dimension": 1536,
  "model": "text-embedding-3-small",
  "provider": "openai"
}
```

Vectors are `List<Float>`, never `float[]` — a primitive array inside a Java `record` silently falls back
to reference equality for the generated `equals()`/`hashCode()`, a real footgun this contract avoids.
Vectors are never fabricated, padded, or truncated — see §11.

## 9. Validation

Enforced in `EmbeddingServiceImpl`, **before** the `EmbeddingProvider` collaborator is touched at all
(not even a metadata call) — proven by `EmbeddingServiceImplTest`'s `verifyNoInteractions` assertions:

| Rule | Enforcement |
|---|---|
| Null/blank text (after trim) | `EMBEDDING_INVALID_REQUEST` (422) |
| Text exceeds `embedding.max-input-length` (default 8000 chars) | `EMBEDDING_INVALID_REQUEST` (422) |
| Batch empty | `EMBEDDING_INVALID_REQUEST` (422) |
| Batch exceeds `embedding.max-batch-size` (default 50, hard DTO ceiling also 50) | `EMBEDDING_INVALID_REQUEST` (422) |
| Any batch item blank/oversized | `EMBEDDING_INVALID_REQUEST` (422), whole request rejected |
| `embedding.enabled=false` | `EMBEDDING_NOT_CONFIGURED` (503) |
| Unsupported model | No separate allowlist — OpenAI itself rejects an unrecognized model (→ `EMBEDDING_INVALID_REQUEST`), and a recognized-but-wrong-dimension model is caught by dimension validation (§11) — both real, specific, honest rejections without a redundant hardcoded list |

## 10. Timeout & Normalization

`embedding.openai.connect-timeout-ms` (5000) / `read-timeout-ms` (30000) bound every provider call — no
request can hang indefinitely. Normalization is **trim-only** (leading/trailing whitespace stripped,
nothing else) — internal whitespace, line breaks, and formatting are preserved untouched, since a
document chunk (Phase 3.5's future consumer) may depend on them for semantic meaning. "Do NOT
aggressively modify user/document text" per the brief.

## 11. Retry, Circuit Breaker & Rate Limiting

Reuses LLM Service's exact `resilience4j-spring-boot3` pattern, one more instance named
`embeddingProvider`:

- **Circuit breaker**: count-based sliding window (20), min 10 calls, 50% failure threshold, 30s open wait.
- **Retry**: 3 attempts, 500ms base wait, exponential backoff ×2. Which failures retry is decided by
  `ResilienceConfig`'s `RetryConfigCustomizer` predicate (`EmbeddingException.isRetryable()`) — YAML's
  class-based lists can't express per-instance retryability for one exception type (same reasoning as
  LLM Service's `ResilienceConfig`).
- **Rate limiter** (new to this phase, Step 15): 20 calls/second refresh period, 2s permit-acquisition
  timeout — protects the provider from unbounded concurrency without introducing a new framework, reusing
  the same dependency already on the classpath.

**Dimension validation** (Step 12) happens inside `OpenAiEmbeddingProvider`: the response's `data` array
length must equal the requested text count; each item's `embedding` array length must equal
`embedding.openai.dimension` exactly (mismatch → `EMBEDDING_DIMENSION_MISMATCH`, never silently
truncated/padded); every value is checked non-NaN/non-infinite. A syntactically-unparseable response body
is also caught and mapped to a truthful `EMBEDDING_RESPONSE_INVALID`, never an unhandled 500.

## 12. Batch Processing

Bounded at 50 items (`embedding.max-batch-size`, tunable down; hard DTO ceiling also 50). Item ordering
is preserved end-to-end — each item's `index` in the response matches its position in the request's
`texts` list, verified even when the provider's own response array arrives in a different order
(`OpenAiEmbeddingProviderTest.embed_batchResponse_preservesOrderRegardlessOfArrayOrder`). Per-item error
fields exist in `BatchEmbeddingItem` for forward compatibility, though OpenAI's real batch endpoint fails
or succeeds as one HTTP call today (no native per-item failure mode) — see that DTO's javadoc.

## 13. Security

Same trust boundary as every other PaymentX service: JWT verification happens at API Gateway; this
service is not internet-facing. No `@PreAuthorize`/admin endpoints exist, so no
`HeaderRoleAuthenticationFilter` was added (would be dead code) — matches LLM Service's identical
reasoning. `embedding.openai.api-key` is `@ToString.Exclude`d and has **no** hardcoded default anywhere
(source, YAML, tests, Docker, frontend) — verified by repository-wide grep.

## 14. Observability

9 hand-registered Micrometer meters (`EmbeddingMetrics`): `embedding_requests_total`,
`embedding_success_total`, `embedding_failure_total`, `embedding_timeout_total`, `embedding_latency`,
`embedding_input_size`, `embedding_dimension`, `batch_embedding_requests_total`, `provider_errors` — only
character *lengths* are recorded, never text content (Step 20). `CorrelationIdFilter` propagates
`X-Correlation-Id` into every log line. Exposed at `/actuator/prometheus`.

## 15. Health

`GET /api/v1/embeddings/health` never makes a real, billed provider call. `status` is `CONFIGURED`
(enabled + API key present — not proof of validity) or `NOT_CONFIGURED` (disabled or no key) — the same
two-state precedent LLM Service established in Phase 3.3, applied here. The response body's `note` field
spells out that this is not a reachability check. (Step 22 lists four labels to "differentiate" —
`SERVICE_UP` is implicit in getting an HTTP 200 from this endpoint at all; `PROVIDER_CONFIGURED` is
synonymous with `CONFIGURED` here — a third/fourth enum value would be redundant given a real HTTP
response already proves service liveness.)

## 16. Testing

**28/28 tests passing:**

| Test class | Kind | Coverage |
|---|---|---|
| `OpenAiEmbeddingProviderTest` | WireMock, real wire-level (10 tests) | Success, batch order-preservation, dimension mismatch, 429/401/400/500, malformed JSON, missing `data` array, not-configured short-circuit |
| `EmbeddingServiceImplTest` | Mockito, mocked `EmbeddingProvider` (12 tests) | Success mapping, disabled/blank/oversized/batch-limit rejection *before* touching the provider, trim-only normalization, batch ordering, error propagation, health (3 states) |
| `EmbeddingControllerIntegrationTest` | Real Spring context + WireMock (6 tests) | Validation → 400, real success → 200 (single + batch), oversized batch → 400, dimension mismatch → 502, health |

A real, deliberate bug was found and fixed during this phase: `EmbeddingServiceImpl` originally called
`EmbeddingProvider.providerName()` for metrics *before* running input validation, contradicting its own
"reject before touching the provider" design and NPE-ing on unstubbed Mockito mocks in tests — fixed by
reordering validation ahead of any provider interaction.

A second real issue was found and fixed: WireMock's embedded Jetty negotiates HTTP/2 cleartext with
Spring Boot's auto-detected `java.net.http.HttpClient`-based `RestTemplate` factory, causing intermittent
connection resets in tests. Fixed by pinning `SimpleClientHttpRequestFactory` (HTTP/1.1) in
`OpenAiEmbeddingProvider`'s constructor — applied in production too (not just tests), since REST APIs gain
nothing from HTTP/2 multiplexing for this service's call shape, and it removes an entire class of
environment-dependent negotiation risk at zero real cost.

## 17. Configuration

| Property | Env var | Default |
|---|---|---|
| `embedding.enabled` | `EMBEDDING_ENABLED` | `true` |
| `embedding.openai.api-key` | `EMBEDDING_API_KEY` | *(empty — no default, no fake key)* |
| `embedding.openai.model` | `EMBEDDING_MODEL` | `text-embedding-3-small` |
| `embedding.openai.base-url` | `EMBEDDING_BASE_URL` | `https://api.openai.com/v1` |
| `embedding.openai.dimension` | `EMBEDDING_DIMENSION` | `1536` |
| `embedding.max-input-length` | `EMBEDDING_MAX_INPUT_LENGTH` | `8000` |
| `embedding.max-batch-size` | `EMBEDDING_MAX_BATCH_SIZE` | `50` |
| `embedding.openai.connect-timeout-ms` | `EMBEDDING_CONNECT_TIMEOUT_MS` | `5000` |
| `embedding.openai.read-timeout-ms` | `EMBEDDING_READ_TIMEOUT_MS` | `30000` |

No test, YAML file, Dockerfile, or frontend file contains a real or fake OpenAI API key.

## 18. Known Limitations

- Single provider (OpenAI) — `EmbeddingProvider` exists for a future second provider, no selection
  strategy implemented.
- No caching — deliberately deferred (Step 24): no demonstrated usage pattern yet to size a cache
  against; adding Redis complexity now would be speculative. Revisit once Phase 3.5's real ingestion
  volume is known.
- No embedding persistence, no vector storage, no similarity search (§19).
- Batch per-item error fields exist but are not independently populated today — OpenAI's real batch
  endpoint has no native per-item failure mode (see §12).
- Frontend: **none built, intentionally** (Step 26) — no Embedding UI, no Control Center page, no vector
  exposure to end users.

## 19. Phase 3.5 Integration Points

- `EmbeddingResponse`/`BatchEmbeddingResponse` are the exact contract a future Vector Database ingestion
  path will consume — `embedding`, `dimension`, `model`, `provider` are already the fields a vector store
  insert needs.
- `POST /api/v1/embeddings/batch`'s bounded, ordered batch shape is designed for Phase 3.5's document-chunk
  ingestion use case without requiring a contract change.
- LLM Service (Phase 3.3) is **not** a consumer of this service yet — the future RAG flow
  (`Prompt/Document → Embedding Service → Vector → Vector Database`, then retrieval feeding LLM Service)
  is Phase 3.5+ scope; LLM Service was not modified in this phase.

---

## Explicitly NOT Implemented in Phase 3.4

- **Vector Database** — NOT IMPLEMENTED (no pgvector tables, no vector indexes, no vector repository).
- **RAG (Retrieval-Augmented Generation)** — NOT IMPLEMENTED.
- **MCP Gateway** — NOT IMPLEMENTED.
- **Agent Orchestrator / Multi-Agent** — NOT IMPLEMENTED.
- **Document ingestion pipeline** (PDF/DOCX/CSV parsing, chunking, crawling) — NOT IMPLEMENTED.
- **Semantic / similarity search** — NOT IMPLEMENTED.
- **AI Security platform** — NOT IMPLEMENTED.

These are reserved for Phase 3.5 and later, per `PAYMENTX_PHASE_3_ARCHITECTURE.md`.
