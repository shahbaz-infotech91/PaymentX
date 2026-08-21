# PaymentX — Phase 3.10.2: RAG Service Audit Layer

## 1. Objective

Implement ONLY the RAG Service audit layer the Phase 3.10 specification approved: a fail-open audit
record of every RAG retrieval operation, mirroring the existing, proven `McpAuditClient` pattern, capturing
only safe operational metadata (correlation ID, request ID, retrieval count, chunk identifiers, similarity
scores, latency, status) and never the user's prompt, the LLM's answer, or any secret/credential/full
document content.

## 2. Existing RAG Architecture (inspected before writing any code)

`RagServiceImpl.query()` (`paymentx-rag-service/src/main/java/com/paymentx/rag/service/impl/RagServiceImpl.java`)
is the entire flow: validate request → embed query (`EmbeddingServiceClient`) → vector search
(`VectorServiceClient`) → apply `rag.min-score` relevance threshold, client-side → (if nothing relevant)
return `INSUFFICIENT_CONTEXT` immediately, **never** calling Prompt/LLM Service → else build context
(`ContextBuilder`) → render prompt (`PromptServiceClient`) → generate answer (`LlmServiceClient`) → map to
`SUCCESS`/`REFUSED`. Every downstream failure is a typed `RagException`, caught once at the top level.
`correlationId` is read from `MDC` (`CorrelationIdFilter.MDC_KEY`), set by the module's own
`CorrelationIdFilter` from the incoming `X-Correlation-Id` header (or generated if absent) - RAG Service
had **no** per-query `requestId` concept of its own before this phase.

## 3. Existing MCP Audit Pattern (the mandated implementation template)

`McpAuditClient` (`paymentx-mcp-gateway/src/main/java/com/paymentx/mcp/audit/McpAuditClient.java`):

- **Construction**: `RestTemplateBuilder` + `SimpleClientHttpRequestFactory`, connect/read timeouts from
  `McpGatewayProperties` (`auditWriteConnectTimeoutMs=2000`, `auditWriteReadTimeoutMs=3000`).
- **Endpoint**: real, already-existing Audit Service `POST /api/v1/audit-events` - no new audit database.
- **DTO style**: no dedicated request record - builds a `Map<String,Object>` body matching
  `AuditEventRequest` (`eventType`, `sourceService`, `actorId`, `actorType`, `correlationId`, `traceId`,
  `paymentId`, `participantId`, `reference`, `payload`); `payload` is itself a small, redacted JSON
  *string* of safe fields only.
- **Event structure**: uses `EventType.API_REQUEST` - the closest real, existing enum value (audit-service
  has no dedicated AI-specific `EventType`; inventing one would be an out-of-scope schema change).
- **Correlation handling**: propagates the real caller correlation ID both in the body and as the
  `X-Correlation-Id` header.
- **Error handling / fail-open**: the entire POST is wrapped in `try { ... } catch (Exception e) { log.warn(...); }`
  - every possible failure is swallowed and only logged; the real operation being audited (a tool call) is
  never affected.
- **Timeout**: short, fixed, un-retried (`2000`/`3000` ms).
- **Logging**: `log.warn` with the operation's identifier and the failure reason, never a stack trace to
  the caller.

This suite reuses this pattern **verbatim** - same constructor shape, same timeout wiring, same
`try`/`catch`-and-log fail-open body, same `EventType.API_REQUEST` choice, same "no dedicated request DTO,
plain `Map` body + JSON-string payload" style.

## 4. RAG Audit Event Design

New package `com.paymentx.rag.audit`:

- **`RagAuditEvent`** (record) - the minimum DTO: `correlationId`, `requestId`, `retrievalCount`,
  `chunkIds` (`List<String>`), `similarityScores` (`List<Double>`), `latencyMs`, `status`.
- **`RagAuditClient`** - mirrors `McpAuditClient` exactly (see §3); posts `eventType=API_REQUEST`,
  `sourceService=rag-service`, `actorId=rag-service`, `actorType=AI_RETRIEVAL`, `correlationId`,
  `reference=rag.query`, and a `payload` JSON string containing only the six `RagAuditEvent` fields.
  `traceId`/`paymentId`/`participantId` are left unset - RAG Service has no real per-query trace-id or
  participant-scoping concept to honestly populate them with (inventing one would be a fabricated
  identifier, which Step 4 of this task explicitly forbids).

No duplicate identifier was created: `chunkId`/`score` reuse `RetrievedChunk`'s own existing fields
verbatim (never recalculated); `status` reuses this service's own SUCCESS/INSUFFICIENT_CONTEXT vocabulary
in spirit (see §5); `requestId` reuses the exact "one UUID per operation, distinct from `correlationId`"
convention `AgentOrchestratorService`/`AgentExecution` already established elsewhere in this platform.

## 5. Safe Metadata

The `payload` JSON sent to Audit Service contains **only**:

```
{ "requestId": "...", "retrievalCount": 2, "chunkIds": ["chunk-001","chunk-002"],
  "similarityScores": [0.91, 0.42], "latencyMs": 37, "status": "SUCCESS" }
```

`status` is one of three constants defined on `RagAuditClient`:

| Constant | Meaning | Reuses |
|---|---|---|
| `STATUS_SUCCESS` | at least one chunk passed `rag.min-score` | maps to `RagQueryStatus.SUCCESS`'s "found something" case |
| `STATUS_NO_RESULTS` | retrieval ran but nothing passed the threshold | maps to `RagQueryStatus.INSUFFICIENT_CONTEXT`'s retrieval-only meaning |
| `STATUS_FAILURE` | retrieval itself failed (validation/embedding/vector-search) | matches `ToolInvoker`/`McpAuditClient`'s own "FAILED" convention |

No larger taxonomy was invented, and `RagQueryStatus.REFUSED` (an LLM-level outcome, not a retrieval
outcome) is deliberately never reported here - see §6/§13's "retrieval operation, not the entire LLM
conversation" scoping.

**Structurally excluded** (there is no field for any of these anywhere in `RagAuditEvent`, so none of them
can ever reach the payload, by construction, not by convention): the user's `query` text, the LLM's
`answer`, any chunk's `content`, any full document, any password/JWT/API key/secret.

## 6. Correlation Propagation

`correlationId` is read once, from the same `MDC.get(CorrelationIdFilter.MDC_KEY)` call
`RagServiceImpl.query()` already used before this phase (the same value forwarded to all four existing
downstream clients) - never a second, independently-generated ID. `requestId` is a **new**, distinct
concept this phase adds (RAG Service had none before): a fresh `UUID.randomUUID()` generated once per
`query()` call, kept separate from `correlationId` for the same reason `AgentExecution` keeps its own
`requestId` distinct from `correlationId` - one identifies this one retrieval operation specifically, the
other identifies the whole cross-service request chain it is part of.

## 7. Latency Definition

`latencyMs` in the audit payload is **not** the same number as `RagQueryMetadata.totalLatencyMs`. It is
measured from `query()`'s own `startTime` up to immediately after the vector-search/threshold step
completes - i.e. **validation + embedding + vector search only**, deliberately excluding context
construction, prompt rendering, and LLM generation. This matches Step 1's explicit instruction ("the audit
should represent the retrieval operation, not the entire LLM conversation") and Step 7's "do not include
unrelated ... latency." Measurement uses `System.currentTimeMillis()` deltas - the same convention every
other latency figure in this codebase already uses (`RagQueryMetadata`, `ToolInvoker`,
`AgentOrchestratorService`) - chosen over `System.nanoTime()` specifically to match existing repository
convention (Step 0's "reuse existing patterns" takes precedence here).

## 8. Fail-Open Behavior

`RagAuditClient.recordRetrieval` catches `Exception` broadly and only logs (`log.warn`) - a downed, slow,
5xx-returning, or malformed-response Audit Service can never make a real RAG query fail. `RagServiceImpl`
itself adds **no** second, defensive `try`/`catch` around the audit call - it trusts `RagAuditClient`'s own
fail-open contract completely, the exact same single-point-of-responsibility precedent
`ToolInvoker`→`McpAuditClient` already establishes (verified by inspection: `ToolInvoker.invoke`'s `finally`
block calls `auditClient.recordToolInvocation(...)` with no wrapping `try`/`catch` of its own either).

Only the real RAG retrieval failure itself (a genuine `RagException` from embedding/vector service) is ever
allowed to propagate to the caller - and on that path, the failure occurs either before the retrieval-audit
event was sent (a validation/embedding/vector-search failure - the `catch` block then sends one honest
`STATUS_FAILURE` event) or after it (a later prompt/LLM failure - the earlier, accurate `STATUS_SUCCESS`/
`STATUS_NO_RESULTS` event was already sent and is never overwritten or duplicated; see the
`retrievalAuditEmitted` guard in `RagServiceImpl.query()`).

## 9. Timeout Behavior

`auditWriteConnectTimeoutMs=2000` / `auditWriteReadTimeoutMs=3000` - identical default values to
`McpGatewayProperties`'s own audit-write timeouts, bound via a new, dedicated `RagProperties`
`rag.audit-write-connect-timeout-ms`/`rag.audit-write-read-timeout-ms` pair (matching every other
`rag.*-connect-timeout-ms`/`rag.*-read-timeout-ms` naming convention already in that file). No retry is
configured (matching `McpAuditClient`, which also has none) - retrying a fire-and-forget audit write would
only add latency risk to the real request for no real benefit, since the write is already best-effort.

## 10. Security Guarantees

- `RagAuditEvent` has no field capable of carrying a prompt, an answer, or full chunk/document content -
  this is enforced by the type itself, not by a runtime check.
- `RagAuditClientTest.recordRetrieval_payload_containsOnlyApprovedFieldsAndNoSentinel` asserts the
  serialized outgoing payload's key set is *exactly* the six approved fields, and that the full request
  body never contains the literal substrings `"prompt"`, `"answer"`, or `"content"`.
- `RagServiceAuditTest.query_sentinelInQueryAndChunkContentAndAnswer_neverReachesTheAuditEvent` drives a
  real query whose query text, retrieved chunk content, and LLM answer are all deliberately poisoned with
  the synthetic sentinel `TEST_SECRET_SHOULD_NEVER_APPEAR_12345`, and asserts the captured `RagAuditEvent`
  never contains it anywhere.
- No real secret, credential, or API key is used anywhere in this phase's code or tests.

## 11. Implementation

New files:

- `paymentx-rag-service/src/main/java/com/paymentx/rag/audit/RagAuditEvent.java`
- `paymentx-rag-service/src/main/java/com/paymentx/rag/audit/RagAuditClient.java`

Modified files:

- `RagProperties.java` - added `auditServiceUrl`/`auditWriteConnectTimeoutMs`/`auditWriteReadTimeoutMs`.
- `application.yml` - added the corresponding `rag.audit-*` keys (`RAG_AUDIT_SERVICE_URL` env override,
  matching the existing `RAG_EMBEDDING_PROVIDER`/`RAG_EMBEDDING_MODEL` override convention).
- `RagServiceImpl.java` - injected `RagAuditClient`; generates `requestId`; emits exactly one
  `RagAuditEvent` per `query()` call, immediately after the retrieval/threshold step (before context
  construction/prompt render/LLM generation - see §7/§13); the failure `catch` block emits one
  `STATUS_FAILURE` event, but only if the retrieval-scoped event was not already sent.

**Nothing else changed.** Embedding, vector search, relevance threshold, ranking, chunking, context
construction, the LLM prompt/system-prompt content, and response generation are byte-for-byte identical to
before this phase - the audit layer is purely observational, added at exactly two points in one method.

## 12. Tests

| Class | New/Existing | Focus |
|---|---|---|
| `RagAuditClientTest` (new, `com.paymentx.rag.audit`) | New | Real wire-level: correct body/payload, payload allow-list + no-sentinel, fail-open on 500/timeout/unreachable/malformed response |
| `RagServiceAuditTest` (new, `com.paymentx.rag.service`) | New | `RagServiceImpl` integration: correct correlationId/requestId/retrievalCount/chunkIds/similarityScores/latencyMs/status for SUCCESS, NO_RESULTS, and FAILURE paths; the post-retrieval-failure no-double-report guard; sentinel-never-reaches-the-event |
| `RagQueryIntegrationTest` (existing, modified) | +2 new tests | Real Spring context + real HTTP: Audit Service 500 and Audit Service unavailable both leave the RAG query itself successful |
| `RagServiceImplTest` (existing, modified) | Unchanged tests, new constructor arg only | Confirms the audit-client dependency didn't change any existing orchestration/validation behavior |
| `RagSecurityTest` (existing, modified) | Unchanged tests, new constructor arg only | Confirms Phase 3.10.1's prompt-injection/RAG-poisoning/secret-leakage coverage is unaffected |

Test-matrix coverage against Step 14's 17-item list: items 1-10 → `RagServiceAuditTest` (+ 2 fail-open
items also in `RagQueryIntegrationTest`); items 11-13 (5xx/timeout/connection-failure fail-open) →
`RagAuditClientTest` + `RagQueryIntegrationTest`; items 14-17 (no prompt/answer/secret/full content) →
`RagAuditClientTest.recordRetrieval_payload_containsOnlyApprovedFieldsAndNoSentinel` +
`RagServiceAuditTest.query_sentinelInQueryAndChunkContentAndAnswer_neverReachesTheAuditEvent`.

## 13. Real Validation

RAG Service, Audit Service, and every other AI Platform dependency (Embedding/Vector/Prompt/LLM Service)
were checked and found **not running** on this machine at the time of this phase (all of ports
8085/8092/8093/8094/8095/8096 unreachable; Docker daemon also not running, so no containerized instances
either). Per Step 18's explicit instruction, services were **not** started solely to force a real-E2E PASS.
Fail-open and full-flow behavior were instead proven through the automated test suite in §12, which
exercises the real HTTP client code (`RagAuditClient`, `RestTemplate`, real timeouts) against real WireMock
servers - the only thing not exercised for real is the actual Audit Service/Postgres persistence path
itself. Real RAG E2E and live audit-event verification are reported **NOT EXECUTED** below, honestly, not
forced to PASS.

## 14. Regression

- `paymentx-rag-service` full module suite: **43/43** passing (31 pre-existing + 12 new).
- `paymentx-agent-orchestrator`'s `RagServiceClientTest`/`AgentE2EIntegrationTest` (which call RAG Service
  over its unchanged HTTP contract): **6/6** passing - confirms Agent Orchestrator's RAG integration is
  unaffected.
- No embedding model, LLM provider, relevance threshold, or ranking logic was changed anywhere.
- MCP Gateway, Agent Orchestrator, Control Center production code: not touched in this phase.

## 15. Files Changed

**Production files added (2):**
- `paymentx-rag-service/src/main/java/com/paymentx/rag/audit/RagAuditEvent.java`
- `paymentx-rag-service/src/main/java/com/paymentx/rag/audit/RagAuditClient.java`

**Production files modified (2):**
- `paymentx-rag-service/src/main/java/com/paymentx/rag/config/RagProperties.java`
- `paymentx-rag-service/src/main/java/com/paymentx/rag/service/impl/RagServiceImpl.java`

**Configuration modified (1):**
- `paymentx-rag-service/src/main/resources/application.yml`

**Test files added (2):**
- `paymentx-rag-service/src/test/java/com/paymentx/rag/audit/RagAuditClientTest.java`
- `paymentx-rag-service/src/test/java/com/paymentx/rag/service/RagServiceAuditTest.java`

**Test files modified (3, constructor-argument update only, no assertions changed):**
- `paymentx-rag-service/src/test/java/com/paymentx/rag/service/RagServiceImplTest.java`
- `paymentx-rag-service/src/test/java/com/paymentx/rag/security/RagSecurityTest.java`
- `paymentx-rag-service/src/test/java/com/paymentx/rag/controller/RagQueryIntegrationTest.java` (+2 new
  fail-open test methods, +1 WireMock server)

**Database:** not touched. No schema change was needed - the existing `AuditEventRequest`/`AuditEvent`
contract already accepts everything this phase needed (`payload` as a free-form JSON string).

## 16. Known Limitations

1. **`traceId`/`paymentId`/`participantId` are left unset** on the outgoing `AuditEventRequest` - RAG
   Service has no real per-query trace-id concept of its own (unlike MCP Gateway, which receives a real
   MCP-protocol `traceId` per tool call) and no participant-scoping concept at all (a RAG query is not
   scoped to one participant's data). Inventing values for either would be a fabricated identifier, which
   this task's Step 4 explicitly forbids.
2. **No real Audit Service persistence was verified** in this phase (see §13) - the audit *client's*
   correctness (wire format, fail-open) is thoroughly proven; that Audit Service actually stores and
   returns the event correctly is Audit Service's own, already-existing, already-tested responsibility
   (out of this phase's scope per the strict-scope instructions), and was not re-verified live because no
   instance was running.
3. **`latencyMs` intentionally does not cover the full RAG request** (see §7) - an operator reading only
   the audit trail would need `RagQueryMetadata.totalLatencyMs` (already returned in the real API response)
   for end-to-end latency; the audit event's number is retrieval-scoped by design.
4. **A post-retrieval LLM refusal (`RagQueryStatus.REFUSED`) is never distinguished from `SUCCESS` in the
   audit trail** - by design (see §5): `REFUSED` is an LLM-level outcome, and the audit event is emitted
   before the LLM is ever called, describing the retrieval step alone, honestly.

## 17. Phase 3.10.2 Status

**COMPLETE**
