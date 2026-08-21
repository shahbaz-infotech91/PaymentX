# PaymentX — Phase 3.10 Readiness Audit (Read-Only)

**Scope:** Read-only audit and implementation plan only. No source, configuration, database, or frontend code was modified. No service was started, stopped, or restarted. No write MCP tool was executed. No payment, user, or token was created. Nothing was committed.
**Date:** 2026-08-20.

---

## 1. Executive Summary

No dedicated "Phase 3.10 specification" document exists in this repository. The only authoritative fragment is a one-line title assignment in `PAYMENTX_PHASE_3_ARCHITECTURE.md` (§19, Implementation Order): **"Phase 3.10 — Security + Observability + Production Hardening."** No enumerated requirements list for that phase exists anywhere.

Investigating what that title would actually require, against what is already real and running, produced a significant finding: **most of the "Security" and "Observability" substance implied by that title is already implemented**, incrementally, across Phases 3.1–3.9 — not deferred to a future phase as the original Phase 3.0 design document once assumed. Specifically:

- Every one of the 7 AI services has its own real, dedicated Micrometer metrics class (`llm_*`, `embedding_*`, `vector_*`, `rag_*`, `mcp_*`, `agent_*` counters/timers) and is already a live Prometheus scrape target.
- Real prompt-injection defense, real read/write tool separation (no write tool exists anywhere, not merely disabled), real per-tool authorization, real audit trail (via the existing Audit Service, no duplicate system built), and real cross-service trace continuity (Zipkin) are all live and were proven working repeatedly in Phase 3.6/3.9 with real, not simulated, adversarial input.

The genuine remaining gaps are narrower and more concrete than "build security and observability from scratch": a Control Center UI surface for the AI metrics that already exist, a handful of already-known and already-disclosed functional/operational limitations (2 unavailable MCP tools, no conversation persistence, no per-user identity, etc.), and the fact that the Control Center's own AI feature is currently switched off in the running instance (`control-center.ai.enabled=false`) — a one-line config value, not a code gap.

**No new defect was found in this audit. No Phase 1 or Phase 3.9 capability regressed.**

**Recommendation: READY** to begin Phase 3.10 planning/implementation, conditioned on first authoring an actual detailed Phase 3.10 requirements document (see §22).

---

## 2. Phase 1 Baseline

Carried forward, unchanged, from `PAYMENTX_PHASE_1_FINAL_SIGNOFF.md` (2026-08-20) — not re-verified in this task (out of scope; this audit is Phase 3.10-focused and read-only):

- **Classification: B — PHASE 1 COMPLETE WITH DOCUMENTED NON-BLOCKING GAPS.**
- Build 10/10, automated tests 255/255, real authenticated E2E payment SETTLED, PostgreSQL healthy.
- 6 known non-blocking gaps (routing per-payment attribution, no Payment Details page, no frontend Auth integration, no centralized Auth audit, role-based 403 not fully provable, no account lockout) — none of these are treated as Phase 3.10 defects per instruction, and none were touched in this audit.

Confirmed still true by file-timestamp evidence: no `.java`/`.ts`/`.tsx`/`.yml`/`.xml` source or config file anywhere in the repository has been modified since the Phase 1 sign-off completed.

---

## 3. Phase 3.9 Baseline

Carried forward from `PAYMENTX_PHASE_3_AI_PLATFORM_CHECKPOINT.md`, the most recent and most authoritative Phase 3 status document, and independently re-confirmed live in this audit:

- **Phase 3.1 through 3.9: COMPLETE** per that checkpoint's own completion statement.
- Historical test totals (unchanged, no source modified since): Prompt 38/38, LLM 16/16, Embedding 38/38, Vector 27/27, RAG 26/26, MCP Gateway 46/46, Agent Orchestrator 45/45, Phase 3.9 incremental (agent+control-center+frontend) 62/62.
- **Re-confirmed live in this audit:** all 7 AI platform services (Prompt 8092, LLM 8093, Embedding 8094, Vector 8095, RAG 8096, MCP Gateway 8097, Agent Orchestrator 8098) are currently running and report `/actuator/health` → `UP`.
- **Re-confirmed live in this audit:** `GET /api/v1/ai/health` on Control Center reports `promptService/llmService/embeddingService/ragService/mcpGateway/agentOrchestrator` all `READY`, but `chatInterface: NOT_READY` and overall `status: NOT_CONFIGURED` — because `control-center.ai.enabled` is off on the currently-running backend instance. This is the same configuration-flag observation already made and documented in the immediately-prior Phase 1 sign-off task; it has not changed since, and is not a Phase 3.9 regression (the flag defaults to `false` by design — see checkpoint §15).

**No AI service, code, or configuration was touched to investigate this** — it was read only, per instruction not to force AI online for this audit.

---

## 4. Phase 3.10 Specification Sources

### AUTHORITATIVE SPEC:
`C:\PaymentX\PAYMENTX_PHASE_3_ARCHITECTURE.md`, §19 "Implementation Order" — **title/theme only**, not a requirements list.

### SOURCE DATE/VERSION:
Undated in-repo; written as the original Phase 3.0 architecture design, before any of Phases 3.1–3.9 were implemented (confirmed by its own §1–§18 describing a target, not-yet-built state that has since been superseded by real implementation).

### DEFINED REQUIREMENTS:
The document defines Phase 3.10's **name only**: *"Security + Observability + Production Hardening."* It does not enumerate specific Phase 3.10 tasks. However, two sections of that same document (§12 Observability, §13 AI Security) describe the **original target design** for exactly those two themes, at design time before Phase 3 began — these are the closest thing to a requirements list, and are cited below as **INFERRED** (not confirmed as Phase 3.10-specific, since they predate the phase split and much of their content has since been built into earlier phases rather than deferred).

**AUTHORITATIVE PHASE 3.10 SPECIFICATION NOT FOUND** as a standalone, detailed requirements document. No file named or containing a dedicated "Phase 3.10" plan, ticket, or specification exists anywhere in the repository (verified via repository-wide search for `3.10`, `Phase 3.10` across all `.md` files; the only real matches are the one architecture-doc title line above and several other documents' boilerplate stop-conditions — "Phase 3.10 was not started" — which are not specifications).

### INFERRED candidate scope (derived from existing project documentation, not authoritative):
Synthesized from `PAYMENTX_PHASE_3_ARCHITECTURE.md` §12/§13 (original design intent) cross-checked against `PAYMENTX_PHASE_3_AI_PLATFORM_CHECKPOINT.md` §20/§21 (what is actually still missing as of the end of Phase 3.9) — see the full Gap Matrix in §18.

- Surface the AI platform's already-real Prometheus metrics in a Control Center "AI Monitoring" page (§12 itself already flagged this as "future, not built in Phase 3.0").
- Address the small set of already-documented Known Limitations in checkpoint §20 that are plausibly in-scope for a "hardening" phase (2 unavailable MCP tools, `payment.status` resource-ownership gap, no per-dashboard-user AI identity).
- Any genuinely new security/observability requirement not already covered — none was found; this list is therefore a refinement/completion list, not a from-scratch build.

---

## 5. Existing Phase 3 Architecture

### Inventory

| Service | Purpose | Phase Introduced | Phase 3.9 Changes | Tests (last known) | Runtime (checked live, this audit) | Phase 3.10 Relevance |
|---|---|---|---|---|---|---|
| **LLM Service** (8093) | Real LLM provider abstraction (Anthropic) — chat/generate | 3.3 | Timeout raised (per checkpoint) | 16/16 (no surefire report currently on disk — see §15) | **UP** | Low — provider abstraction stable; observability metrics already present |
| **Embedding Service** (8094) | Text→vector embeddings, local (default) + OpenAI (opt-in, unused) | 3.4 | — | 38/38 | **UP** | Low — local-first design already the hardened choice (no external dependency/quota risk) |
| **Vector Service** (8095) | pgvector-backed store + similarity search | 3.5 | — | 27/27 | **UP** | Low — schema/index stable |
| **RAG Service** (8096) | Retrieval + grounded generation | 3.6 | — | 26/26 | **UP** | Medium — no dedicated audit layer of its own (known, disclosed) |
| **MCP Gateway** (8097) | 5 read-only PaymentX tools over MCP protocol | 3.7 | — | 46/46 | **UP** | Medium — 2 designed tools still unavailable (no backing business API) |
| **Agent Orchestrator** (8098) | Ties MCP + RAG + LLM into a bounded, policy-enforced agent | 3.8 | Timeouts raised (75s/65s) | 45/45 | **UP** | Medium — no per-dashboard-user identity; single-turn only |
| **Control Center AI** (8089, within `control-center-backend`) | Chat proxy + health aggregation for the frontend | 3.1 | Timeouts raised (90s backend / 95s frontend) | Included in Control Center's 65/65 (Phase 1 sign-off) | **UP**, AI feature `NOT_CONFIGURED` (flag off) | High — this is the one visible "off" switch blocking end-to-end use today |
| **Prompt Service** (8092) | Prompt template CRUD + versioning | 3.2 | — | 38/38 | **UP** | Low — stable, versioned |
| **AI-related frontend** (`/ai-assistant`) | Chat UI, session-only history | 3.1 | — | Included in frontend's 29/29 (Phase 1 sign-off) | Vite dev server UP; AI backend gate currently off | Medium — no citations UI, no persistence (known) |
| **AI security** | Prompt-injection defense, tool auth, PII masking, write-tool refusal | 3.6–3.8 | — | Covered by respective service suites | N/A (code-level) | Low — real, proven live repeatedly |
| **AI audit** | Real audit events via existing Audit Service | 3.7/3.8 | — | Covered by respective service suites | N/A (code-level) | Low — real, no gaps beyond RAG's own missing layer (mitigated) |
| **AI observability** | correlationId/traceId (Zipkin) + per-service Micrometer metrics | 3.1–3.9 | — | N/A | Metrics confirmed live-scraped by Prometheus (see §13) | **Medium — instrumented but not surfaced in Control Center UI** |

Not all services were assumed running before checking — every "UP" above was independently confirmed via a live, read-only `GET /actuator/health` or port-listen check in this session (§16).

---

## 6. LLM Audit

Config-only inspection; **the LLM was not called during this audit.**

- **Provider:** Anthropic (`llm.provider: anthropic`), the one configured provider — matches `PAYMENTX_PHASE_3_3_LLM_SERVICE.md`'s documented choice. `LlmProvider` interface exists as a real abstraction point; no second provider implemented (by design, left for future).
- **Model:** `${LLM_MODEL:claude-opus-5}` (checkpoint doc confirms this is the currently configured value).
- **Timeouts:** `timeout-seconds: ${LLM_TIMEOUT_SECONDS:60}` at the HTTP client level; Resilience4j `circuitbreaker`/`retry` instance named `llmProvider` (count-based sliding window, 20/10, 50% failure threshold, 30s open-state wait; retry max 3 attempts, exponential backoff).
- **Error handling:** a single `LlmException` type with a per-instance `retryable` boolean (not a class-based retry list) — a `RetryConfigCustomizer` bean supplies the actual predicate, documented inline as a deliberate design choice.
- **Prompt handling:** delegated to Prompt Service (versioned templates), not inlined in LLM Service.
- **Token limits:** `default-max-tokens: ${LLM_MAX_TOKENS:4096}`.
- **Security/secrets:** `api-key: ${LLM_API_KEY:}` — empty-placeholder default, never a real- or fake-looking value; documented as a deliberate choice so the service starts and reports `NOT_CONFIGURED` honestly rather than crashing or fabricating a response when the key is absent. No secret value is present in any config file inspected.
- **Logging:** `com.paymentx.llm: DEBUG` — no evidence of secret logging found (grep for `@ToString.Exclude` on credential fields, per checkpoint §12, not independently re-verified line-by-line in this audit but consistent with the pattern seen elsewhere).
- **Fallback behavior:** none beyond retry/circuit-breaker — no secondary provider fallback (single-provider by design, documented as a known limitation, not a defect).
- **Metrics:** `LlmMetrics.java` defines real counters/timers: `llm_requests_total`, `llm_generate_success_total`, `llm_generate_failure_total`, `llm_generate_refused_total`, `llm_generate_latency`, `llm_provider_error_total`, `llm_input_tokens_total`, `llm_output_tokens_total`, `llm_not_configured_total`, `llm_health_check_total`.

**No secret value was printed anywhere in this section.**

---

## 7. Embedding Audit

**Current provider confirmed: LOCAL, and this audit did not touch or recommend switching it.**

- **Provider:** `embedding.provider: ${EMBEDDING_PROVIDER:local}` — local is the default. The `openai.*` config block is still present and functional (opt-in only, via explicit env var override), consistent with `PAYMENTX_PHASE_3_4_LOCAL_EMBEDDING.md`'s documented migration rationale (OpenAI quota unavailable at the time).
- **Model:** `sentence-transformers/all-MiniLM-L6-v2` (`EMBEDDING_LOCAL_MODEL`).
- **Dimension:** 384 (`EMBEDDING_LOCAL_DIMENSION`) — independently re-confirmed against the live database schema (§8): `ai_document_embedding.dimension` carries a `CHECK (dimension = 384)` constraint, and the `vector(384)` column type matches exactly.
- **Model loading / cache:** `cache-dir: ${EMBEDDING_LOCAL_CACHE_DIR:}` — per the checkpoint doc, the real model cache lives at `C:\Users\Appex\.djl.ai` (~101 MB of real ONNX weights/tokenizer/native libraries), confirmed outside the repository, not re-verified by this audit (would require filesystem access outside `C:\PaymentX`, not necessary to confirm the config contract).
- **Batch behavior:** `max-batch-size: ${EMBEDDING_MAX_BATCH_SIZE:50}` — checkpoint discloses this loops per-item internally rather than true batched ONNX inference (a known, disclosed performance limitation, not investigated further here to avoid re-deriving what's already documented).
- **Timeouts:** local provider has no network timeout concept (in-process inference); the still-present `openai.*` block retains `connect-timeout-ms`/`read-timeout-ms` for the unused opt-in path.
- **Failure behavior:** `embedding_timeout_total`, `embedding_failure_total`, `provider_errors` counters exist (`EmbeddingMetrics.java`) — real failure telemetry, not silent.
- **Memory footprint:** not independently measured in this audit (would require inspecting a running JVM's heap, out of this task's minimal-footprint read-only scope); checkpoint discloses no GPU in this environment, CPU fallback confirmed working but not benchmarked.

**No paid embedding dependency was introduced or recommended by this audit**, consistent with the explicit instruction.

---

## 8. Vector DB Audit

Live, read-only inspection of `paymentx_ai` (PostgreSQL + pgvector), performed in this audit:

```
Extension: vector, version 0.8.0

Tables: ai_document, ai_document_chunk, ai_document_embedding,
        prompt_template, prompt_version
        (+ databasechangelog/lock — Liquibase-managed)

ai_document_embedding:
  embedding   vector(384)   NOT NULL
  CHECK (dimension = 384)
  UNIQUE (chunk_id, provider, model)         -- supports multiple embedding providers per chunk
  FK chunk_id -> ai_document_chunk(id) ON DELETE CASCADE

Indexes on ai_document_embedding:
  idx_ai_document_embedding_vector_hnsw   HNSW, vector_cosine_ops   <- real ANN index, not brute-force
  idx_ai_document_embedding_chunk_id      btree
  ai_document_embedding_pkey              btree

Row counts (live, at audit time): ai_document=4, ai_document_chunk=4, ai_document_embedding=4
```

- **Dimension:** 384, enforced both at the column type and a `CHECK` constraint — matches the local embedding provider exactly (§7).
- **Index:** HNSW with cosine similarity — a real approximate-nearest-neighbor index, not a full scan.
- **Document metadata:** `ai_document` has a GIN index on metadata (`idx_ai_document_metadata_gin`) plus a status index — supports filtered/faceted retrieval.
- **Migration history:** managed by Liquibase (`databasechangelog` table present).
- **Similarity search / query behavior / threshold:** not independently re-run in this audit (would require a real vector query — avoided per "do not modify data" and to keep this audit read-only-safe); RAG Service's own config (`rag.min-score: 0.5`, `rag.default-top-k: 5`, `rag.max-top-k: 20`) is the authoritative threshold, unchanged since Phase 3.6/3.9 checkpoint.
- **Content:** confirmed only 4 documents exist — consistent with checkpoint's disclosure that this is real test content (idempotency, routing, validation, one adversarial injection-test document), not a genuine documentation corpus.

**No schema was modified.**

---

## 9. RAG Audit

Not independently re-executed (would require a real LLM/embedding call chain, avoided per instruction); reported from `PAYMENTX_PHASE_3_6_RAG_SERVICE.md`, `PAYMENTX_PHASE_3_6_RAG_FINAL_VALIDATION.md`, and the checkpoint document, cross-checked for continued accuracy against the live schema (§8) and unmodified source (§3):

| Aspect | Status | Evidence source |
|---|---|---|
| Document ingestion | Real, via `POST /api/v1/vector/documents` (Vector Service) | Checkpoint §21 |
| Chunking | Implemented (real chunk table + FK relationship confirmed live, §8) | This audit + checkpoint |
| Embedding | Real, local provider (§7) | This audit |
| Vector search | Real HNSW cosine search (§8) | This audit |
| Retrieval threshold | `rag.min-score: 0.5`, top-k 5 default / 20 max | Checkpoint §15 |
| Context construction | `rag.max-context-chunks: 5`, `rag.max-context-characters: 8000` | Checkpoint §15 |
| LLM grounding | Real, proven live repeatedly (Phase 3.6, 3.9) — answers cite retrieved categories, explicitly disclose documentation gaps rather than inventing detail | Checkpoint §12, prior full-platform validation session |
| Citation/source handling | Text-based, inline in the LLM's answer — no structured citation UI component exists in the frontend (confirmed, §14) | This audit |
| Failure behavior | `rag_insufficient_context_total`, `rag_failure_total` counters real and present (`RagMetrics.java`) | This audit |
| Prompt injection handling | Real, proven twice live in an earlier session against the actual seeded adversarial test document — correctly disregarded and proactively flagged, not merely refused silently | `PAYMENTX_REAL_TRANSACTION_VALIDATION.md`, prior full-platform validation |

**Production-ready vs. Phase 3.10 candidate:** grounding, retrieval, and injection defense are production-ready as-is. The one real gap: **RAG Service has no audit layer of its own** (disclosed, unchanged since Phase 3.6) — mitigated in practice because Agent Orchestrator's own audit trail covers real production RAG usage, but a direct RAG Service caller (bypassing the agent) would go unaudited. This is a legitimate Phase 3.10 "hardening" candidate.

---

## 10. MCP Audit

**Live source inspection (read-only); no tool was executed, read or write.**

Exactly 5 real tools exist, matching the checkpoint's claim exactly:

| Tool | Class | READ/WRITE | Risk | Permission | Input | Purpose | Authorization |
|---|---|---|---|---|---|---|---|
| `payment.lookup` | `PaymentLookupTool` | **READ** | LOW | `PAYMENT_READ` | `paymentReference` (required) | Full current snapshot of a payment | `checkResourceOwnership` against debtor/creditor participant; PII masking applied (`@Mask`, confirmed via grep) |
| `payment.status` | `PaymentStatusTool` | **READ** | LOW | `PAYMENT_READ` | `paymentReference` (required) | Lightweight status-only read, polling-friendly | No resource-ownership check possible — underlying endpoint carries no participant identity (disclosed limitation) |
| `routing.lookup` | `RoutingLookupTool` | **READ** | LOW | `ROUTING_READ` | `scheme` (required, one of `INSTANT_PAYMENT`/`REAL_TIME_PAYMENT`/`CARD_PAYMENT`), `participantId` (optional) | Resolve active routing rule, participant-specific or scheme default | `checkResourceOwnership` against participantId |
| `reconciliation.status` | `ReconciliationStatusTool` | **READ** | LOW | `RECONCILIATION_READ` | `batchId` (required), `includeSummary` (optional) | Batch status + optional aggregate mismatch summary | Permission-gated only |
| `audit.search` | `AuditSearchTool` | **READ** | LOW | `AUDIT_READ` | free-text filters (correlationId, paymentId, participantId, reference, status, eventType, date range), bounded pagination | Search the audit trail, never returns raw payloads | Permission-gated only |

**No `UNKNOWN`-classified tool exists** — every tool's `ToolReadWrite` value is explicitly set to `READ_ONLY` in source.

**Write-tool verification:** `grep` for `ToolReadWrite.WRITE` across the entire MCP Gateway source tree returns exactly one match — inside `ToolAuthorizationService.checkPermission()`, a defense-in-depth denial check (`throw McpException.writeOperationNotAllowed(...)`) that exists for a tool type that is never actually registered. No write tool implementation exists anywhere.

**Error handling / timeout / idempotency:** `ToolCallRateLimiter` (30 permits/60s, per checkpoint §15) and `McpException` (structured error codes) exist; individual tool timeout values were not re-derived line-by-line in this audit (would require reading each tool's downstream HTTP client config) — flagged as a minor follow-up for whoever authors the detailed Phase 3.10 spec, not asserted as missing.

**Audit:** every tool call writes a real, redacted audit event to the existing Audit Service (checkpoint §14, cross-checked against `McpAuditClient.java`'s presence in source).

**Known, disclosed gaps (not new):** `participant.lookup` and `payment.error.lookup` tools do not exist — no backing business API exists for either (Phase 3.7, unchanged).

---

## 11. Agent Orchestrator Audit

Not independently re-executed (would require a real LLM call). Reported from `PAYMENTX_PHASE_3_8_AGENT_ORCHESTRATOR.md` and the checkpoint document, cross-checked against unmodified source and live config:

- **Tool selection / MCP integration:** real, via MCP protocol client to MCP Gateway.
- **RAG integration:** real, via direct RAG Service call.
- **LLM integration:** real, via LLM Service.
- **Prompt handling:** system prompts `PAYMENTX_AGENT_ORCHESTRATOR`/`PAYMENTX_KNOWLEDGE_ASSISTANT` sourced from Prompt Service (versioned).
- **Timeout:** `agent.overall-timeout-ms: 75000`, `agent.rag-read-timeout-ms: 65000` — both raised during Phase 3.9 incremental fixes (from 45000/20000) after a real, organically-observed worst case of 45.7s.
- **Retry:** not independently re-derived in this audit beyond what checkpoint discloses.
- **Error handling:** real, includes an organically-observed real Anthropic `529 Overloaded` event handled correctly (checkpoint §17).
- **Audit:** one real audit event per completed run, recording `ragUsed`, `toolCalls`, `iterations`, `status` — confirmed via a real example record in checkpoint §14.
- **Authorization:** `agent.mcp-roles: PAYMENT_READ,ROUTING_READ,RECONCILIATION_READ,AUDIT_READ` — a fixed, minimal, read-only service credential; **no per-dashboard-user identity** (disclosed limitation, unchanged).
- **Prompt injection defense:** two independent layers — the system prompt's own explicit instruction to treat retrieved/tool content as data, and `AgentToolPolicy`'s hardcoded allow-list — both proven live against real adversarial input.
- **Hallucination prevention:** RAG grounding + the explicit system-prompt instruction against fabricating unavailable detail (proven live: "I won't guess" behavior observed in the earlier full-platform validation session for a topic with no available documentation).
- **Read/write tool separation:** enforced at two independent layers (Agent Orchestrator's own allow-list, checked before MCP Gateway is even called; MCP Gateway's own independent re-check) — real defense-in-depth, not a single point of failure.
- **Metrics:** `AgentMetrics.java` — `agent_requests_total`, `agent_success_total`, `agent_failure_total`, `agent_timeout_total`, `agent_iterations_total`, `agent_tool_calls_total`, `agent_tool_denied_total`, `agent_rag_calls_total`, `agent_llm_calls_total`, `agent_execution_latency`, `agent_tool_latency`.

**Already implemented, not missing:** tool selection, RAG/MCP/LLM integration, timeout handling, audit, two-layer authorization, prompt-injection defense, hallucination mitigation, read/write separation.
**Genuinely missing (disclosed, not new):** multi-turn memory across requests, per-dashboard-user identity, multi-agent orchestration (explicitly out of scope), streaming responses.

---

## 12. AI Security Audit

Static/config review only; **no destructive or exploit test was performed — existing safe evidence was used.**

| Threat | Status | Evidence |
|---|---|---|
| Prompt injection (direct) | **Mitigated, proven live** | System-prompt isolation; real user queries attempting instruction override correctly refused in an earlier live session |
| Indirect prompt injection (via RAG content) | **Mitigated, proven live** | The seeded adversarial test document, retrieved organically across multiple real queries, consistently treated as inert data and proactively flagged back to the user |
| Tool authorization | **Real, two-layer** | Agent Orchestrator allow-list + MCP Gateway's independent `ToolAuthorizationService` |
| Tool allowlist | **Real, hardcoded, fixed** | 5 tools, no dynamic registration path found in source |
| Read/write separation | **Real** | No write tool implemented anywhere (§10) |
| Input validation | Present at the tool-schema level (JSON Schema per tool, e.g. `audit.search`'s `ALLOWED_STATUSES`/`ALLOWED_EVENT_TYPES` enums) | Source inspection |
| Output validation | Not independently confirmed as a dedicated structured-output-schema validation layer at LLM Service (the original §8 design proposed this) — not verified present or absent without deeper source review than this audit's scope covered | **INFERRED gap, unconfirmed** |
| Secret leakage | **None found** | Every AI service's `application.yml` uses empty-placeholder `${...:}` env-var defaults; no real/fake-looking secret value present in any file inspected |
| System prompt protection | Versioned via Prompt Service, not user-editable | Source/architecture |
| Context poisoning / RAG poisoning | Mitigated by curated ingestion path (`POST /api/v1/vector/documents`, not arbitrary user-supplied URLs at query time) | Architecture doc §9, cross-checked against live schema (§8) |
| PII handling | **Real, confirmed via grep** | `@Mask` used in `PaymentLookupTool.java` |
| Audit trail | **Real** | Every tool call and agent run produces a retrievable Audit Service record (§10, §11) |
| Tenant/participant isolation | **Partial, disclosed** | `payment.lookup`/`routing.lookup` enforce resource-ownership; `payment.status` cannot (no participant identity on the underlying endpoint) — an unscoped caller passes that one check unconditionally |
| Unsafe tool execution | **None found** | No write tool exists to execute unsafely |

**No secret was detected or would need redaction in this section.**

---

## 13. AI Observability Audit

**Correlation/tracing:** `X-Correlation-Id` generated/propagated per-service (`CorrelationIdFilter`, identical pattern across all AI services), put into MDC, echoed on responses. Real Zipkin trace-ID continuity was independently confirmed in Phase 3.6/3.9 across `rag-service` → `embedding-service` → `vector-service` → `prompt-service` → `llm-service` for one real request. **Known, disclosed gap:** the Agent Orchestrator → downstream-service hop is reported as a separate root trace rather than one continuous tree — correlation still works via `X-Correlation-Id` and the audit trail.

**Metrics — re-verified live in this audit, not merely cited from prior documents:** every one of the 7 AI services has its own dedicated Micrometer metrics class with real counters/timers (full names listed per-service in §6, §7, §9, §10, §11 above). This directly satisfies the original Phase 3.0 architecture's §12 metrics wishlist — under a different, arguably cleaner per-service naming convention (`llm_*`/`rag_*`/`mcp_*`/`agent_*`/`embedding_*`/`vector_*`) rather than the originally-sketched unified `ai_*` prefix.

**Scraping — re-verified live in this audit:** `infra/prometheus.yml` defines a real scrape job for all 7 AI service ports (8092–8098). Prometheus is running and healthy.

**The one real, concrete, verified gap:** **none of these metrics are surfaced anywhere in the Control Center UI.** `PrometheusMetricsService.java` (Control Center backend, the existing named-query catalog that feeds the existing `MetricsPage.tsx`) contains zero `llm_`/`rag_`/`mcp_`/`agent_`/`embedding_`/`vector_`-prefixed queries. No AI-specific Grafana dashboard exists either (`infra/grafana-provisioning` contains only a datasource, no dashboards directory). The data exists and is being collected; it is simply not yet visualized anywhere. This exact gap was explicitly anticipated and flagged as deferred in the original architecture document itself (§12: *"Control Center surfacing (future, not built in Phase 3.0)"*).

**Audit events:** real, per §10/§11/§14 above — MCP tool calls and Agent Orchestrator runs both produce structured Audit Service records; RAG Service itself does not (disclosed).

---

## 14. Frontend AI Audit

Not modified; behavior re-confirmed from this session's prior real, live browser validation (`PAYMENTX_FRONTEND_FULL_PLATFORM_VALIDATION.md`) plus fresh source inspection (grep, no execution) in this audit:

- **Authentication integration:** none — the Control Center has no login flow at all (Phase 1 known gap); AI chat calls carry no per-user identity, consistent with Agent Orchestrator's own fixed-service-credential model (§11).
- **AI request lifecycle:** real — `useAiChat.ts`/`aiService.ts` wrap a real HTTP call; loading ("PaymentX AI is thinking…") and disabled-Send states are real, proven in the Phase 1 sign-off era's frontend test suite (`AiAssistantPage.test.tsx`, part of the Control Center's 29/29).
- **Timeouts:** frontend AI chat call overrides the global `VITE_API_TIMEOUT_MS` (15000) to 95000ms specifically (Phase 3.9 incremental fix) — confirmed as a documented, deliberate override, not independently re-read line-by-line in this audit.
- **Error handling:** real, classified (`classifyAiError`), tested against the real `AI_SERVICE_NOT_READY`/`AI_NOT_CONFIGURED` shape the backend actually returns.
- **Payment lookup:** real, proven live in an earlier session — a real payment status/timestamp returned, byte-for-byte matching the database.
- **MCP interaction:** real, via the backend proxy → Agent Orchestrator → MCP Gateway chain (not called directly by the frontend).
- **RAG interaction:** real, proven live with correct grounding and honest "gap" disclosure (earlier session).
- **Conversation/history:** session-only (`sessionStorage`, `aiConversationStore.ts` — confirmed via grep in this audit), no server-side persistence (known, disclosed limitation).
- **Citations:** no dedicated citation UI component found in `AiAssistantPage.tsx`'s file inventory — any source attribution is inline text in the LLM's own answer, not a structured, clickable citation.
- **Unknown-result behavior:** real, proven live — a genuinely nonexistent payment reference correctly produces "no matching record found," not a fabricated status.

**Current live state:** the AI Assistant page itself is reachable (Vite dev server serving), but the backend AI feature gate reports `NOT_CONFIGURED` right now (§3) — a real user opening `/ai-assistant` at this exact moment would see the same "Not Configured" state observed in the Phase 1 sign-off task, not a functional chat. This is a live runtime observation, not a code defect.

---

## 15. Test Coverage

Source/test file inventory (counted, not executed, in this audit) plus the most recent available test-result evidence per module:

| Module | Production `.java` files | Test `.java` files | Last known automated result | Result freshness |
|---|---|---|---|---|
| Prompt Service | 28 | 4 | 38/38 | Surefire report on disk, dated 2026-08-19 |
| LLM Service | 20 | 3 | 16/16 | Cited from checkpoint doc only — **no surefire report currently on disk for this module** (not re-run in this audit) |
| Embedding Service | 23 | 4 | 38/38 | Surefire report on disk, dated 2026-08-19 |
| Vector Service | 26 | 2 | 27/27 | Surefire report on disk, dated 2026-08-19 |
| RAG Service | 24 | 3 | 26/26 | Surefire report on disk, dated 2026-08-19 |
| MCP Gateway | 33 | 8 | 46/46 | Surefire report on disk, dated 2026-08-19 |
| Agent Orchestrator | 32 | 6 | 45/45 | Surefire report on disk, dated 2026-08-19 |
| Control Center (backend, incl. AI proxy) | — | — | Included in Phase 1's 65/65 | Re-run 2026-08-20 (Phase 1 sign-off task) |
| Control Center (frontend, incl. AI Assistant) | — | — | Included in Phase 1's 29/29 | Re-run 2026-08-20 (Phase 1 sign-off task) |

**Security tests:** no dedicated, separately-named "security test" suite was found per AI module (e.g. no `*SecurityTest.java` under any AI service, unlike Auth/Routing/Reconciliation services which do have one) — prompt-injection and write-refusal coverage instead lives inside each service's regular integration tests and, more substantially, in the real live-execution evidence recorded in Phase 3.6/3.8/3.9's own validation documents. This is a genuine, if minor, **test-coverage gap**: the strongest evidence for AI security properties is live-session evidence, not a repeatable, CI-friendly automated test.

**No massive suite was run for this audit** — all counts above are either pre-existing surefire XML reports already on disk or a source-file inventory (`find ... | wc -l`).

---

## 16. Runtime Status

Checked live, read-only, via `/actuator/health` (GET only) or port-listen checks. **Nothing was started, stopped, or restarted.**

| Component | Status |
|---|---|
| LLM Service (8093) | **UP** |
| Embedding Service (8094) | **UP** |
| Vector Service (8095) | **UP** |
| RAG Service (8096) | **UP** |
| MCP Gateway (8097) | **UP** |
| Agent Orchestrator (8098) | **UP** |
| Prompt Service (8092) | **UP** |
| Control Center AI feature (via `/api/v1/ai/health`) | **NOT_CONFIGURED** (all downstream components report `READY`; only the Control Center's own `chatInterface` gate is off, due to `control-center.ai.enabled=false` on the current instance) |

No status above was inferred from source code — every line is a live, timestamped check result from this session.

---

## 17. Infrastructure

Read-only inspection; nothing modified.

- **PostgreSQL/pgvector:** `pgvector/pgvector:0.8.0-pg16`, container `paymentx-postgres`, healthy (per `docker ps`, not re-verified again in this audit beyond the schema check in §8). `paymentx_ai` database confirmed real and populated (§8).
- **Redis:** `redis:7-alpine`, container `paymentx-redis`, healthy — used by MCP Gateway's rate limiter and (per Phase 1 audit) the E2E test API-key mechanism; not independently re-inspected for AI-specific keys in this audit.
- **Kafka:** running, healthy — not used directly by any AI service per the architecture design (AI services call each other over HTTP/MCP, not Kafka); consistent with Risk #3 in the original architecture doc still being an open, unexercised risk.
- **RabbitMQ:** running, healthy — not used by the AI platform (Notification Service's own concern).
- **Docker configuration:** `infra/docker-compose.yml` defines only infrastructure containers (Postgres, Redis, Kafka+UI, RabbitMQ, pgAdmin, Zipkin, Prometheus, Grafana, MailHog, Redis Insight) — **no AI service itself runs in Docker**; all 7 AI services + Control Center run as host Java/Node processes, matching every other PaymentX service's convention in this environment.
- **Prometheus scrape config:** confirmed real scrape jobs for all 7 AI service ports (§13).
- **Model cache:** per checkpoint, `C:\Users\Appex\.djl.ai`, ~101 MB, confirmed outside the repository by design — not independently re-verified in this audit (outside `C:\PaymentX`, out of this audit's inspection scope).

---

## 18. Phase 3.10 Gap Matrix

| Requirement | Current State | Evidence | Gap | Priority |
|---|---|---|---|---|
| AI-specific Prometheus metrics exist | **Already implemented** — every AI service has a real metrics class, real scrape config | §13, §6/§7/§9/§10/§11 | None | — |
| AI metrics surfaced in Control Center | Not built | `PrometheusMetricsService.java` has zero AI-prefixed queries; no `AiMonitoringPage` exists | **Real gap** — data exists, not visualized | MEDIUM |
| Pre-built AI Grafana dashboard | Not built | `infra/grafana-provisioning` has only a datasource, no dashboards | Real gap, but Grafana ad-hoc querying already works against the real scraped metrics | LOW |
| Control Center AI feature reachable end-to-end | Currently off (`control-center.ai.enabled=false`) on the running instance | §3, §16 | **Operational, not a code gap** — a one-line config/env-var flip | INFO |
| Prompt injection defense | Real, proven live repeatedly | §12 | None | — |
| Read/write tool separation | Real — no write tool exists anywhere | §10 | None | — |
| Tool authorization (2-layer) | Real | §10, §11, §12 | None | — |
| PII masking on sensitive tool output | Real, confirmed via source | §12 | None | — |
| Audit trail for AI actions | Real (MCP + Agent Orchestrator) | §10, §11 | RAG Service itself has no audit layer of its own | MEDIUM |
| Cross-service tracing | Real, proven live | §13 | Agent Orchestrator → downstream hop is a separate root trace, not one tree | LOW |
| Dedicated AI security test suite (automated, not live-session evidence) | Not found per AI module | §15 | Real gap — security properties currently proven by live sessions, not repeatable CI tests | MEDIUM |
| Output schema validation at LLM Service | Originally designed (§13, architecture doc) | Not confirmed present or absent — not deeply re-verified | **Unconfirmed — needs a closer look, not asserted as missing** | LOW/INFO |
| `participant.lookup` / `payment.error.lookup` MCP tools | Not implemented | §10, checkpoint §20 | Real, pre-existing, disclosed gap — no backing business API exists | LOW (pre-existing, not new) |
| `payment.status` resource-ownership enforcement | Cannot be enforced (no participant identity on underlying endpoint) | §10 | Real, pre-existing, disclosed gap | LOW (pre-existing, not new) |
| Per-dashboard-user AI identity | Not implemented — fixed service credential only | §11, §12 | Real, pre-existing, disclosed gap; tied to Phase 1's own "no frontend Auth integration" gap | LOW (pre-existing, not new; explicitly not a Phase 3.10 defect per instruction unless required) |
| Multi-turn agent memory | Not implemented | §11, checkpoint §20 | Pre-existing, disclosed, explicitly out-of-scope-for-Phase-3 item | INFO |
| Second LLM/embedding provider | Not implemented (abstraction exists) | §6, §7 | Pre-existing, explicitly future work | INFO |

**Nothing above was labeled a gap merely because Phase 3.9 didn't build it if it's explicitly disclosed as future/out-of-scope work in the checkpoint document itself — those are marked INFO, not counted against readiness.**

**No CRITICAL or HIGH gap was found.**

---

## 19. Target Architecture

**CURRENT:** Exactly as described in §5/§17 — 7 AI services + Control Center AI proxy, all real, all tested, all currently running, communicating over real HTTP/MCP calls, instrumented with real per-service metrics, audited via the existing Audit Service, traced via the existing Zipkin instance. No dedicated vector database service (pgvector extension inside the existing Postgres). No AI service containerized in Docker (host processes, matching platform convention).

**TARGET (Phase 3.10, inferred only):** The same architecture, unchanged at the component level — no redesign is justified by any evidence gathered in this audit. The only architectural *additions* implied by the gap matrix:

1. A new, thin Control Center backend layer of AI-prefixed Prometheus named-queries (mirroring `PrometheusMetricsService.java`'s existing pattern exactly) feeding a new `AiMonitoringPage.tsx` (mirroring `MetricsPage.tsx`'s existing pattern exactly) — **no new architectural pattern, just new named queries and a new page**, exactly as the original architecture document itself already predicted.
2. Optionally, a small number of pre-provisioned Grafana dashboard JSON files under `infra/grafana-provisioning` (a directory that does not yet exist) — purely additive, zero risk to any running service.
3. A decision (not a code change by default) on whether/how to give RAG Service its own audit call to the existing Audit Service, mirroring MCP Gateway's `McpAuditClient` pattern.

**No component should be replaced.** This audit found no evidence that any existing AI service, provider abstraction, database schema, or security control is broken, insufficient, or in need of a different design.

---

## 20. Implementation Plan

Incremental, ordered to touch the lowest-risk, most additive items first and never require changing a currently-passing test or a currently-working request path.

### Item 1 — Author the actual Phase 3.10 requirements document
- **Goal:** Turn the one-line architecture-doc title into a real, enumerated specification, informed by this audit's Gap Matrix (§18).
- **Files/services affected:** none (documentation only).
- **Dependencies:** none.
- **Risk:** none.
- **Tests required:** none.
- **E2E validation:** none.
- **Rollback:** trivial (delete the document).

### Item 2 — Turn on `control-center.ai.enabled` for whatever environment Phase 3.10 work happens in
- **Goal:** Make the AI Assistant reachable end-to-end for development/testing.
- **Files/services affected:** none (env var / deployment config only, no source change).
- **Dependencies:** none — all downstream AI services already report `READY`.
- **Risk:** none — this flag exists specifically to gate the feature safely; flipping it does not touch any other Phase 1 or Phase 3.9 code path.
- **Tests required:** re-run existing Control Center AI tests to confirm the flag flip doesn't itself regress anything (it shouldn't — it's read at request time, not compile time).
- **E2E validation:** one real, safe chat message through the real UI (already-established safe pattern from prior validation sessions).
- **Rollback:** flip the flag back.

### Item 3 — AI-specific Control Center named-query catalog + AI Monitoring page
- **Goal:** Surface the already-real `llm_*`/`rag_*`/`mcp_*`/`agent_*`/`embedding_*`/`vector_*` metrics.
- **Files/services affected:** `paymentx-control-center/backend` (`PrometheusMetricsService.java` — additive new entries only, existing entries untouched), `paymentx-control-center/frontend` (one new page, mirroring `MetricsPage.tsx`).
- **Dependencies:** none new — Prometheus already scrapes these targets (§13).
- **Risk:** LOW — purely additive, read-only queries against an already-running Prometheus instance.
- **Tests required:** new, focused tests for the new query entries + new page (matching this codebase's existing test conventions — see `PostgresDataServiceTest.java`/`AiAssistantPage.test.tsx` as the established pattern).
- **E2E validation:** load the new page against the real, running Prometheus instance.
- **Rollback:** remove the new page/route; backend query additions are inert if unused.

### Item 4 — Pre-provisioned Grafana AI dashboard(s)
- **Goal:** Give operators (not just Control Center users) a real-time view without waiting on Item 3.
- **Files/services affected:** `infra/grafana-provisioning` (new `dashboards/` subdirectory + JSON files only).
- **Dependencies:** none.
- **Risk:** LOW — Grafana provisioning is purely additive and read-only against existing datasources.
- **Tests required:** none applicable (JSON config, not code) — manual visual verification.
- **E2E validation:** open Grafana, confirm panels render against real data.
- **Rollback:** delete the new files.

### Item 5 — RAG Service audit call
- **Goal:** Close RAG Service's one disclosed audit gap by having it call the existing Audit Service directly (mirroring `McpAuditClient`), independent of whether Agent Orchestrator is the caller.
- **Files/services affected:** `paymentx-rag-service` only (new client class + one call site).
- **Dependencies:** existing Audit Service (unmodified).
- **Risk:** MEDIUM — first actual behavior change in this list; must not alter RAG Service's real response contract or timing budget (already tight per checkpoint's timeout history).
- **Tests required:** new unit test for the audit call; full existing 26/26 RAG suite must still pass unmodified.
- **E2E validation:** one real RAG query, confirm a new real audit event appears via `GET /api/v1/audit-events`.
- **Rollback:** revert the one new client + call site.

### Item 6 — Dedicated AI security regression tests
- **Goal:** Convert the currently live-session-only evidence (prompt injection refusal, write-tool refusal, PII masking) into repeatable, CI-runnable automated tests per AI module.
- **Files/services affected:** test-only additions across `paymentx-mcp-gateway`, `paymentx-agent-orchestrator`, `paymentx-rag-service` (mirroring the `*SecurityTest.java` pattern already established in Auth/Routing/Reconciliation services).
- **Dependencies:** none new.
- **Risk:** LOW — test-only, no production code path changes required if the behavior is truly already correct (which this audit's evidence strongly suggests).
- **Tests required:** this item *is* the tests.
- **E2E validation:** N/A (this replaces ad-hoc live validation with automated coverage).
- **Rollback:** delete the new test files.

**Explicitly not planned, per this audit's findings:** rebuilding any AI service, switching embedding providers, adding a new observability platform, or redesigning any working component — no evidence supports any of that.

---

## 21. Regression Safety

Must remain unchanged throughout any Phase 3.10 work:

- **Phase 1 payment flow** (Gateway → Authentication → Validation → Kafka → Payment → Routing → Settlement → Audit → Notification → Reporting) — none of the above implementation items touch any Phase 1 service.
- **Authentication / JWT** — Auth Service, API Gateway — untouched by every item above.
- **Routing, Validation, Kafka, Audit (business), Notification, Reconciliation, Reporting** — untouched.
- **Frontend payment flow** (the two recently-fixed Payment Flow defects) — Item 3 adds a new page; it must not touch `useE2EFlow.ts`, `PaymentFlowPage.tsx`, or `E2EPage.tsx`.
- **RAG, MCP, Agent Orchestrator's existing real behavior** — Item 5 is the only item that changes a running AI service's behavior at all, and only additively (one new outbound audit call); every other item is either documentation, config, or a wholly new, unused-until-wired page/dashboard.
- **The existing 255/255 Phase 1 test count and the historical Phase 3.9 test counts** (§15) — every implementation item above ships with its own new/updated tests and must not reduce any existing count.

---

## 22. Entry Criteria

Objective, derived from actual repository evidence gathered in this audit (not assumed):

| Criterion | Status |
|---|---|
| Phase 3.9 baseline healthy | **MET** — checkpoint doc's own completion statement, re-confirmed live (all 7 AI services UP) |
| No critical Phase 1 regressions | **MET** — Phase 1 sign-off is current, unmodified since |
| AI services compile | **Not re-verified in this audit** (would require a build, out of read-only scope) — last known state (Phase 3.9) was clean; no source changed since |
| Existing AI tests pass | **Partially re-verified** — 6 of 7 AI modules have surefire reports on disk confirming their last-known-passing state; LLM Service's report is not currently on disk (last known 16/16 per checkpoint, not independently re-confirmed here) |
| Known limitations documented | **MET** — checkpoint §20 is thorough and current |
| Database healthy | **MET** — pgvector schema inspected live, consistent and correct (§8) |
| Secrets configured safely | **MET** — every AI service's config uses empty-placeholder env-var defaults, no real secret value found anywhere inspected |
| **Additional criterion this audit surfaced:** an actual Phase 3.10 requirements document exists | **NOT MET** — this is the one true blocker to *starting implementation* (not to readiness of the underlying platform) |

---

## 23. Risks

1. **No detailed Phase 3.10 spec exists** — starting implementation without one risks building the wrong thing or re-discovering scope mid-flight, as happened organically with the observability metrics (already built, just not under the originally-planned name) — see Item 1.
2. **RAG Service's timeout budget is already tight** (raised repeatedly during Phase 3.9 to accommodate real Anthropic latency) — Item 5's new audit call must be measured against this, not added blindly.
3. **`control-center.ai.enabled` being off by default** means any Phase 3.10 work that assumes a live, reachable Control Center AI Assistant must explicitly account for this flag in whatever environment it runs in (Item 2).
4. **The original architecture document (§12/§13) is now a partially-superseded design artifact** — treating it as a literal current-state requirements list (rather than this audit's Gap Matrix) risks re-implementing things that already exist (e.g. re-proposing an `ai_*` metric prefix when real, working `llm_*`/`rag_*`/etc. metrics already exist and are already scraped).
5. **No dedicated AI security test suite exists** — until Item 6 lands, confidence in AI security properties rests on live-session evidence, which does not regress-protect automatically the way a CI-run test suite does.

---

## 24. Final Recommendation

1. **Is Phase 3.10 specification present?** No — only a one-line title (`PAYMENTX_PHASE_3_ARCHITECTURE.md` §19). No enumerated requirements document exists.
2. **What is Phase 3.10 actually supposed to deliver?** Per its title: security, observability, and production hardening for the AI platform. Per this audit's evidence, the large majority of that substance is already real and working; what remains is narrower — primarily surfacing already-real telemetry, closing a few pre-existing disclosed gaps, and converting live-session security evidence into automated tests.
3. **What is already complete?** LLM/Embedding/Vector/RAG/MCP/Agent Orchestrator/Control Center AI proxy — all real, tested, currently running. Prompt-injection defense, read/write tool separation, two-layer tool authorization, PII masking, audit trail (except RAG's own), per-service Prometheus metrics, and real cross-service tracing are all already implemented and were proven with real, not simulated, evidence across Phases 3.1–3.9.
4. **What is missing?** An AI-metrics UI surface in Control Center (MEDIUM); a RAG Service audit call (MEDIUM); dedicated automated AI security tests (MEDIUM); a handful of small, pre-existing, already-disclosed functional gaps (2 unavailable MCP tools, no per-user AI identity, no conversation persistence — all LOW/INFO, none new). No CRITICAL or HIGH gap was found.
5. **What should NOT be changed?** Every Phase 1 business service, the Auth/JWT/Gateway chain, the two already-fixed frontend Payment Flow defects, and the core working shape of every AI service (provider abstractions, local embedding choice, pgvector schema, the 5-tool MCP catalog, Agent Orchestrator's policy model) — none of these should be redesigned; no evidence in this audit justifies it.
6. **What should be implemented first?** Item 1 (write the actual spec) and Item 2 (flip the config flag for whichever environment does the work) — both zero-risk, both prerequisites for everything else. Then Item 3 (AI Monitoring surfacing) as the highest-value, lowest-risk substantive item.
7. **What are the risks?** Building without a real spec risks re-deriving already-built work (as this audit itself had to do); the RAG timeout budget is already tight; the AI feature flag defaults off; the original architecture doc is partially stale and should not be read as current-state truth; AI security currently rests on live evidence, not automated regression tests.
8. **Is the repository READY to start Phase 3.10 implementation?**

## READY

**Why:** Phase 1 is stable and fully regression-tested (B classification, unchanged). Phase 3.9 is complete, and every AI service is currently built, tested (historically, with no source drift since), and live-confirmed running and healthy in this exact audit. No critical or high-priority gap was found anywhere in the AI platform's security, observability, or core functionality — what remains is genuinely incremental hardening work (metrics surfacing, one audit-layer completion, test-suite formalization), not foundational repair. The only blocker to *starting* is procedural, not technical: no one has yet written down a detailed Phase 3.10 requirements document — which this audit's Gap Matrix (§18) is offered as the evidentiary basis for.

---

# FINAL OUTPUT

**PHASE 3.10 SPECIFICATION:**
NOT FOUND (only a one-line title exists; full Gap Matrix in §18 is this audit's own INFERRED substitute)

**PHASE 3.10 STATUS:**
READY

**PHASE 3.9 REGRESSION BASELINE:**
Phase 3.1–3.9 COMPLETE per `PAYMENTX_PHASE_3_AI_PLATFORM_CHECKPOINT.md`; all 7 AI services live-confirmed UP and healthy in this audit; no source file in any AI module has changed since Phase 3.9's own validation; historical test totals (Prompt 38/38, LLM 16/16, Embedding 38/38, Vector 27/27, RAG 26/26, MCP Gateway 46/46, Agent Orchestrator 45/45) remain the last-known, unregressed state.

**PHASE 3.10 REQUIREMENTS:**
Title only ("Security + Observability + Production Hardening"); no enumerated list exists in the repository. See §18 Gap Matrix for this audit's evidence-based INFERRED candidate scope.

**CRITICAL GAPS:**
None found.

**HIGH GAPS:**
None found.

**MEDIUM GAPS:**
- AI-specific Prometheus metrics not surfaced in Control Center UI (data already exists and is already scraped).
- RAG Service has no audit layer of its own (mitigated in practice via Agent Orchestrator's audit trail).
- No dedicated, automated AI security test suite (security properties currently proven via live-session evidence only).

**RECOMMENDED IMPLEMENTATION ORDER:**
1. Author an actual Phase 3.10 requirements document (using this audit's Gap Matrix as evidence).
2. Enable `control-center.ai.enabled` in whatever environment does the work.
3. Surface existing AI metrics in a new Control Center "AI Monitoring" page.
4. (Optional, parallel) Pre-provision a Grafana AI dashboard.
5. Add a RAG Service audit call.
6. Add dedicated, automated AI security regression tests.

**FILES/SERVICES EXPECTED TO CHANGE:**
`paymentx-control-center/backend` (`PrometheusMetricsService.java`, additive), `paymentx-control-center/frontend` (one new page), `infra/grafana-provisioning` (new dashboards directory), `paymentx-rag-service` (one new audit client + call site), test directories under `paymentx-mcp-gateway`/`paymentx-agent-orchestrator`/`paymentx-rag-service`.

**FILES/SERVICES THAT MUST REMAIN UNCHANGED:**
`paymentx-auth-service`, `paymentx-api-gateway`, `paymentx-payment-service`, `paymentx-routing-service`, `paymentx-validation-service`, `paymentx-audit-service`, `paymentx-notification-service`, `paymentx-reconciliation-service`, `paymentx-reporting-service`, the two already-fixed Control Center frontend Payment Flow files (`useE2EFlow.ts`, `PaymentFlowPage.tsx`/`E2EPage.tsx`), and the core working design of every existing AI service (LLM/Embedding/Vector/RAG/MCP/Agent provider abstractions, the 5-tool MCP catalog, the local-embedding default).

---

## STRICT STOP

This document is the complete deliverable for this task. No source code, configuration, dependency, test, database, or frontend file was modified. No service was started, stopped, or restarted. No write MCP tool was executed. No payment, user, or token was created. Phase 3.10 implementation was not started. Nothing was committed.
