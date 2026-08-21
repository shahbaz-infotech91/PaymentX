# PaymentX — Phase 3.10 Formal Specification

**Working title:** Security + Observability + Production Hardening
**Status:** SPECIFICATION ONLY — no implementation performed under this document.
**Derived exclusively from:** `PAYMENTX_PHASE_3_10_READINESS_AUDIT.md` (2026-08-20) and the repository state it recorded. No new investigation was performed to produce this document; where a fact is needed and the audit didn't establish it, that is stated explicitly rather than invented.
**Date:** 2026-08-20.

---

## Section 1 — Objective

Phase 3.10 exists to close the gap between "the AI platform works" (proven, Phase 3.1–3.9, real evidence) and "the AI platform is operable and trustworthy without relying on a human re-running live validation sessions." Concretely, three things:

- **Security:** convert AI security properties that are today proven only by live-session evidence (prompt injection refusal, write-tool refusal, PII masking, tool authorization) into automated, repeatable, CI-enforced regression tests — and close the one real audit-coverage gap the readiness audit found (RAG Service).
- **Observability:** make the AI platform's already-real, already-collected telemetry (per-service Micrometer metrics, already scraped by Prometheus) actually visible to a human operator, the same way every other PaymentX domain's health is already visible in Control Center.
- **Production hardening:** confirm — not rebuild — that the resilience mechanisms already present (timeouts, circuit breakers, retries, rate limits, health checks, graceful degradation via the `control-center.ai.enabled` flag) are sufficient, and document them as the platform's actual hardening posture rather than treating their absence as an assumption.

**This is explicitly not a rewrite.** The readiness audit found no CRITICAL or HIGH gap anywhere in the AI platform's architecture, security posture, or core functionality. Phase 3.10 builds three additive layers on top of the existing, unmodified Phase 3.9 implementation: an audit call, a set of tests, and a visualization surface. LLM Service, Embedding Service, Vector Service, RAG Service, MCP Gateway, Agent Orchestrator, and Control Center AI remain architecturally as they are.

---

## Section 2 — Scope

Per-item classification for the three areas the task requires evaluating, plus the items Sections 7–9 require evaluating separately. Full consolidated table in §17.

| # | Item | Classification | Why |
|---|---|---|---|
| A | Control Center AI metrics visualization | **RECOMMENDED** | Real value for the "Observability" pillar and matches this codebase's existing `MetricsPage.tsx` pattern exactly (no new architectural pattern) — but not REQUIRED, because the underlying data is already collected and already queryable today via Prometheus/Grafana directly (readiness audit §13, §17). Nothing is currently invisible; it's merely not in the one UI operators already use for everything else. |
| B | RAG Service audit layer | **REQUIRED** | The readiness audit's own Gap Matrix (§18) flags this MEDIUM, and it is the one place where a security-relevant AI component (retrieval, the surface RAG poisoning/indirect injection defenses concern themselves with) has no audit trail of its own — current production coverage via Agent Orchestrator is real but incidental, not a property of RAG Service itself. Small, additive, mirrors an already-proven pattern (`McpAuditClient.java`). Directly serves the "Security" pillar of this phase's own title. |
| C | Automated AI security tests | **REQUIRED** | Zero dedicated `*SecurityTest.java`-equivalent exists for any AI module today (readiness audit §15), unlike Auth/Routing/Reconciliation Services, which each have one. A phase whose title includes "Security" cannot be considered to have delivered on that title while the platform's own strongest security evidence is "it worked when a human ran it once." Converting that into CI-enforced tests is the single most title-aligned, lowest-risk (test-only, zero production code path change required if behavior is already correct — which the audit's evidence strongly indicates) item available. |

---

## Section 3 — AI Observability

**No new metric is proposed. Every metric named below already exists, in source, today**, per the readiness audit (§6, §7, §9, §10, §11, §13):

| Service | Existing metrics (real, already implemented) |
|---|---|
| LLM Service | `llm_requests_total`, `llm_generate_success_total`, `llm_generate_failure_total`, `llm_generate_refused_total`, `llm_generate_latency`, `llm_provider_error_total`, `llm_input_tokens_total`, `llm_output_tokens_total`, `llm_not_configured_total`, `llm_health_check_total` |
| Embedding Service | `embedding_requests_total`, `embedding_success_total`, `embedding_failure_total`, `embedding_timeout_total`, `embedding_latency`, `batch_embedding_requests_total`, `provider_errors` |
| Vector Service | `vector_store_requests_total`, `vector_store_success_total`, `vector_store_failure_total`, `vector_search_total`, `vector_search_latency`, `vector_insert_latency`, `vector_update_total`, `vector_delete_total`, `vector_dimension_errors` |
| RAG Service | `rag_requests_total`, `rag_success_total`, `rag_failure_total`, `rag_insufficient_context_total`, `rag_embedding_latency`, `rag_vector_search_latency`, `rag_prompt_render_latency`, `rag_llm_latency`, `rag_total_latency`, `rag_relevance_threshold_rejections` |
| MCP Gateway | `mcp_requests_total`, per-tool `tool_calls`/`tool_success`/`tool_failure`/`tool_denied`/`tool_timeout`/`tool_rate_limited` counters, `authorization_failures`, `validation_failures`, `tool_latency` |
| Agent Orchestrator | `agent_requests_total`, `agent_success_total`, `agent_failure_total`, `agent_timeout_total`, `agent_iterations_total`, `agent_tool_calls_total`, `agent_tool_denied_total`, `agent_rag_calls_total`, `agent_llm_calls_total`, `agent_execution_latency`, `agent_tool_latency` |

**Already true, no work needed:** all 7 services are already live Prometheus scrape targets (`infra/prometheus.yml`, confirmed by the readiness audit).

**What Phase 3.10 defines (the RECOMMENDED item, §2.A):**

- **Control Center visualization:** a fixed, named-query catalog entry per metric family above (mirroring `PrometheusMetricsService.java`'s existing pattern of pre-defined queries, not free-text PromQL exposed to the frontend).
- **Filters:** by service (LLM/Embedding/Vector/RAG/MCP/Agent), and where the metric is naturally tagged, by tool name (MCP) or provider/model (LLM/Embedding) — using tag dimensions that already exist on these metrics (e.g. `tool_calls_total{tool="payment.lookup"}`), not new tags.
- **Service-level visibility:** one panel/section per AI service, consistent with each service's own health (`/actuator/health`, already aggregated by `GET /api/v1/ai/health`).
- **LLM latency/errors:** `llm_generate_latency`, `llm_generate_failure_total`, `llm_provider_error_total` — already present, would be surfaced as-is.
- **RAG retrieval metrics:** `rag_vector_search_latency`, `rag_relevance_threshold_rejections`, `rag_insufficient_context_total` — already present.
- **MCP tool execution metrics:** per-tool `tool_calls`/`tool_success`/`tool_failure`/`tool_denied` — already present, naturally answers "which tool, how often, how it failed."
- **Agent execution metrics:** `agent_iterations_total`, `agent_tool_calls_total`, `agent_execution_latency` — already present.

No metric not already listed in the readiness audit is proposed here.

---

## Section 4 — Control Center

**Existing backend capability (unchanged, reused):** `PrometheusClient.java`, `PrometheusMetricsService.java`, `PrometheusController.java`, and the DTOs under `dto/prometheus/` — the exact mechanism `MetricsPage.tsx` already uses for every other domain's metrics. This mechanism is not being replaced or duplicated.

**New (RECOMMENDED, not yet built):** a set of new named-query entries in that same catalog (backend, additive only — no existing entry touched) feeding one new frontend page, informally "AI Monitoring," structured as:

- A per-service health strip (LLM / Embedding / Vector / RAG / MCP / Agent / Control Center AI proxy), reusing the same `UP`/`DOWN`/`NOT_CONFIGURED` vocabulary `GET /api/v1/ai/health` already returns.
- Request volume and success/failure rate per service.
- Latency (LLM generate, RAG sub-stages, MCP tool calls, agent execution) — as already-collected histograms/timers.
- LLM token usage (input/output) — already collected.
- RAG retrieval detail: relevance-threshold rejection rate, insufficient-context rate.
- MCP tool usage broken down by tool name — already tagged.
- Agent execution: iteration counts, tool-call counts, tool-denial counts.
- An error summary panel aggregating each service's own `*_failure_total`/`*_error_total` counters.

**What is explicitly NOT specified here, because it is not yet justified by evidence:** exact panel layout, chart library choices beyond what `MetricsPage.tsx` already uses (Recharts, per the readiness audit's architecture notes), specific PromQL query strings, or a refresh-interval value — these are implementation-time decisions for whoever picks up the RECOMMENDED item, not specification-time ones.

---

## Section 5 — RAG Audit

**REQUIRED (§2.B).** Minimum audit event shape, modeled directly on the pattern MCP Gateway's `McpAuditClient` already uses successfully (one real audit event per operation, written to the existing Audit Service — no new audit system):

**Captured (safe metadata only):**
- `correlationId` (already generated/propagated per-service, per the readiness audit §13)
- `requestId` (mirroring Agent Orchestrator's `AgentExecution.requestId` convention)
- Retrieval count (how many chunks were retrieved)
- Document/chunk identifiers (`ai_document_chunk.id` values — internal UUIDs, not raw content)
- Similarity scores (the numeric relevance scores already computed by the vector search, already used internally for the `rag.min-score` threshold decision)
- Latency (already measured internally via `rag_total_latency`; the audit event records the same real number, not a duplicate measurement mechanism)
- Result status (success / insufficient-context / failure — the same three states `RagMetrics.java`'s counters already distinguish)

**NEVER captured — explicit, non-negotiable:**
- API keys, passwords, JWTs, or any other secret
- The full user prompt or the full LLM-generated answer text
- Full retrieved document/chunk *content* (only its identifier)
- Any raw personal or payment data that may appear inside retrieved content

This mirrors the existing platform-wide convention already enforced elsewhere: `audit.search`'s own MCP tool description states it "never returns raw event payloads," and MCP Gateway's own audit events are already described as "never secrets, raw arguments, or chain-of-thought" (readiness audit §12).

**Architecture:** one new outbound call from RAG Service to the existing, unmodified Audit Service — the same service every other PaymentX component already writes to. **No new audit database, no new audit table, no parallel audit system.** This is explicitly the same principle the original Phase 3.0 architecture document already committed to and Phase 3.7/3.8 already followed for MCP Gateway and Agent Orchestrator.

**Failure mode requirement:** the audit call must fail open — if the Audit Service is unreachable, RAG Service must still return its real answer to its caller. Audit is a side effect, not a precondition for RAG's core function; this is consistent with how every other PaymentX service's audit-writing already behaves (audit failures are logged, not propagated as request failures) and must not become a new operational dependency that can degrade the AI platform's core "answer questions" function.

---

## Section 6 — AI Security Testing

**REQUIRED (§2.C).** Every category below already has real, live-proven behavior (per the readiness audit); this section defines converting that into automated coverage — **not** inventing new untested behavior.

| Category | What already exists (evidence) | What the automated test asserts |
|---|---|---|
| Prompt injection (direct) | Real live refusal, multiple sessions (readiness audit §12) | A crafted "ignore previous instructions" user message does not change tool-call behavior or leak system-prompt content |
| Indirect prompt injection (via RAG content) | Real, proven against the existing seeded adversarial test document (readiness audit §9, §12) | Querying a topic that organically retrieves the existing seeded injection-test document produces an answer that does not obey the embedded instruction — **reuses the existing document already in the vector store; no new malicious content is added** |
| RAG poisoning | Curated ingestion path only (`POST /api/v1/vector/documents`), no arbitrary query-time source | A test asserting no code path exists for query-time document ingestion from an untrusted source (a structural/code-level assertion, not a live poisoning attempt) |
| Tool authorization | Real, two independent layers (Agent Orchestrator allow-list + MCP Gateway `ToolAuthorizationService`) (readiness audit §10, §11) | A caller without the required role/permission is denied at both layers independently (two separate test cases, one per layer) |
| Read/write separation | No write tool exists anywhere (readiness audit §10, verified by source grep) | A structural test asserting the MCP tool catalog contains zero `ToolReadWrite.WRITE` entries — regresses immediately and loudly if anyone ever adds one |
| Secret leakage | Empty-placeholder env-var defaults everywhere, no secret value found in any inspected config (readiness audit §6, §12) | No test can prove a negative for all future code, but a log/response-inspection test asserting a known env-var-style secret placeholder never appears verbatim in a captured response or log line is achievable and mirrors this codebase's existing `@ToString.Exclude`-style conventions |
| Unsafe tool execution | N/A — no write tool exists to execute unsafely | A test asserting any attempt to invoke a non-existent/unregistered tool name is rejected, not silently ignored or defaulted |
| Malicious tool parameters | JSON Schema validation per tool already exists (e.g. `audit.search`'s `ALLOWED_STATUSES`/`ALLOWED_EVENT_TYPES` enums) | Out-of-schema or enum-violating parameters are rejected with a structured error, not passed through |
| Hallucination on unavailable data | Real, proven live — "I won't guess" behavior for undocumented topics; correct "no matching record" for a genuinely nonexistent payment reference (readiness audit §9, §14 and prior session evidence) | A query for a genuinely nonexistent payment reference (clean, well-formed, alphanumeric per the platform's own known reference-format validation quirk) produces a "not found" answer, never a fabricated status |

**Constraints, restated:** every test above is deterministic and safe — no destructive action, no new malicious data introduced (indirect-injection coverage explicitly reuses the existing seeded test document), no write MCP tool exists to accidentally exercise. The existing read-only MCP posture is preserved by these tests, not weakened by them (the read/write-separation test actively guards it).

---

## Section 7 — MCP

**Existing architecture (unchanged, documented only):** exactly 5 real tools, all `READ_ONLY`, all `LOW` risk, each independently authorized (readiness audit §10):

| Tool | Permission |
|---|---|
| `payment.lookup` | `PAYMENT_READ` |
| `payment.status` | `PAYMENT_READ` |
| `routing.lookup` | `ROUTING_READ` |
| `reconciliation.status` | `RECONCILIATION_READ` |
| `audit.search` | `AUDIT_READ` |

**Write tools are not made mandatory by this specification.** No write tool is proposed, scheduled, or implied as in-scope for Phase 3.10.

**Unavailable tools (`participant.lookup`, `payment.error.lookup`):** classified **OPTIONAL / FUTURE**. The readiness audit confirms neither has a backing business API today — building one would be new business-service scope, not an AI-platform hardening task, and nothing in the repository proves either is required for Phase 3.10's stated Security/Observability/Production-Hardening theme.

---

## Section 8 — Identity

**Current state (documented, unchanged):** no per-user AI identity exists. Agent Orchestrator and MCP Gateway both present a fixed, minimal, read-only service credential (`agent.mcp-roles: PAYMENT_READ,ROUTING_READ,RECONCILIATION_READ,AUDIT_READ`) rather than a genuine per-caller one (readiness audit §11, §12).

**Classification: FUTURE.**

**Why not REQUIRED/RECOMMENDED/OPTIONAL:** per-user AI identity is architecturally downstream of a Phase 1 gap this specification is explicitly forbidden from touching — "no frontend Auth integration" (Phase 1 known gap #3). There is no per-dashboard-user PaymentX identity anywhere in the platform for an AI-identity layer to attach to. Building AI-specific identity ahead of that foundational piece would mean inventing a parallel, AI-only identity concept — exactly the kind of architectural deviation this specification's own governing principle ("do not replace working components without evidence... prefer minimal hardening") argues against. This is future work, gated on a decision outside Phase 3.10's scope, not a Phase 3.10 deliverable.

---

## Section 9 — Conversation Persistence

**Current state:** session-only (`sessionStorage`), no server-side persistence (readiness audit §14, unchanged since Phase 3.1).

**Classification: OUT OF SCOPE.**

**Why:** conversation persistence is a functional/UX capability, not a security, observability, or production-hardening concern — it does not serve any of this phase's three named pillars. Per the explicit instruction not to add it "merely because it would be useful," and because nothing in the readiness audit connects its absence to any security or operability risk, it is excluded from Phase 3.10 entirely. (It remains a legitimate candidate for a future, differently-scoped phase.)

---

## Section 10 — Security

Phase 3.10's security requirements, explicitly reusing what already works rather than duplicating it:

| Concern | Already implemented (reuse, do not rebuild) | Phase 3.10 addition |
|---|---|---|
| Secrets | Empty-placeholder `${ENV_VAR:}` defaults across every AI service; no secret ever logged or returned (readiness audit §6, §12) | None — reuse as-is |
| PII masking | `@Mask`/`MaskingSerializer` reused in `PaymentLookupTool.java` (readiness audit §10, §12) | None — reuse as-is; the security test suite (§6) adds regression coverage for it |
| Prompt injection | System-prompt isolation, proven live twice (direct + indirect) (readiness audit §12) | Automated test coverage (§6) |
| Tool authorization | Two independent layers, real (readiness audit §10, §11) | Automated test coverage (§6) |
| RAG safety | Curated ingestion path only; no query-time arbitrary source (readiness audit §9) | Audit trail for RAG's own operations (§5) |
| Logging | No secret logging found anywhere inspected | None — reuse as-is |
| Audit | Real for MCP + Agent Orchestrator; absent for RAG Service itself (readiness audit §10, §11, §13) | RAG audit layer (§5) — the one genuine addition |
| Error handling | Structured `McpException`, `LlmException` with typed/retryable semantics (readiness audit §6, §10) | None — reuse as-is |
| Least privilege | Fixed, minimal, read-only `agent.mcp-roles` service credential (readiness audit §11) | None — reuse as-is (per-user least-privilege is §8, FUTURE) |

**Net new security surface introduced by Phase 3.10: one outbound audit call (§5) and a body of tests (§6). No new security control, credential, or trust boundary is introduced.**

---

## Section 11 — Production Hardening

Evaluated against what the readiness audit actually found already exists — not a generic checklist:

| Mechanism | Current state | Phase 3.10 action |
|---|---|---|
| Timeouts | LLM: `timeout-seconds: 60`. Agent Orchestrator: `overall-timeout-ms: 75000`, `rag-read-timeout-ms: 65000` (both raised in Phase 3.9 after a real 45.7s worst case). Frontend AI chat: 95000ms override. All already tuned against real, observed behavior. | **Document as sufficient. No change proposed** — no evidence of continued timeout insufficiency exists post-3.9. |
| Retries | Resilience4j `retry` instances on `llmProvider` and `embeddingProvider` (3 attempts, exponential backoff) (readiness audit §6, §7). | **Document as sufficient. No change proposed.** |
| Circuit breakers | Resilience4j `circuitbreaker` instances on `llmProvider` and `embeddingProvider` (count-based, 20-window, 50% threshold, 30s open-state). | **Document as sufficient. No change proposed.** |
| Rate limits | MCP Gateway: 30 tool calls per 60s (`ToolCallRateLimiter`). Embedding Service: 20 calls/second (`embeddingProvider` Resilience4j rate limiter). | **Document as sufficient. No change proposed.** |
| Resource limits | Not independently measured by the readiness audit (memory footprint explicitly not benchmarked). | **No action** — no evidence of a problem; benchmarking without a known issue is not proposed as Phase 3.10 work. |
| Failure handling | `LlmException`/typed exceptions with retryable semantics; `control-center.ai.enabled` flag reports `NOT_CONFIGURED` honestly rather than crashing when AI is off (readiness audit §3, §16). | **Document as sufficient.** RAG audit's fail-open requirement (§5) is the one new failure-handling contract this phase adds. |
| Observability | See §3/§4. | Addressed in §3/§4. |
| Health checks | `/actuator/health` on every service; `GET /api/v1/ai/health` aggregates all 6 downstream components (readiness audit §3, §16). | **Document as sufficient. No change proposed.** |
| Graceful degradation | Confirmed live: with the AI feature flag off, Control Center reports `NOT_CONFIGURED` cleanly rather than erroring; with the flag on but a downstream AI service down, the aggregated health check would presumably reflect that per-component (not independently tested in the readiness audit). | **Document as the current, adequate behavior.** No change proposed absent evidence of a real degradation failure. |

**Conclusion:** production hardening, as a checklist, is already substantially satisfied. Phase 3.10's contribution to this pillar is primarily the observability surfacing (§3/§4) and security-test formalization (§6) — not new resilience mechanisms, because no evidence indicates the existing ones are insufficient.

---

## Section 12 — Testing

| Test type | Scope for Phase 3.10 |
|---|---|
| Unit tests | New: for the RAG audit client (§5) — request construction, fail-open behavior on Audit Service unavailability. |
| Integration tests | New: RAG Service integration test confirming a real audit event is written and retrievable via the existing Audit Service for a real RAG query; must not alter RAG Service's existing 26/26 suite's outcomes. |
| Security tests | New: the full §6 catalog, one test class per AI module following this codebase's existing `*SecurityTest.java` convention (precedent: `AuthServiceSecurityTest.java`, `RoutingSecurityTest.java`, `ReconciliationSecurityTest.java`). |
| Frontend tests | New: for the AI Monitoring page (§4), if and when the RECOMMENDED item is picked up — following the existing `MetricsPage`/`AiAssistantPage.test.tsx` conventions. Not required for the two REQUIRED items (§5, §6), which are backend-only. |
| AI E2E tests | Not newly automated by this phase (no evidence a repeatable, safe, LLM-calling E2E test harness exists or is proposed) — Phase 3.10's E2E validation remains the same real, live, one-message-at-a-time pattern already established and used successfully in Phase 3.6/3.9/the prior full-platform validation session, run manually at acceptance time (§13), not added to CI. |

---

## Section 13 — Acceptance Criteria

Phase 3.10 is complete when, using this repository's actual, already-established verification capabilities:

- The RAG audit layer (§5) is live: a real RAG query produces a real, retrievable Audit Service event containing only the safe metadata fields listed in §5, and Audit Service being unreachable does not cause the RAG query itself to fail (fail-open, verified).
- The automated AI security test suite (§6) exists and passes, across whichever AI modules it was added to.
- **Existing Phase 3.9 behavior remains intact:** the historical per-module test counts (Prompt 38/38, LLM 16/16, Embedding 38/38, Vector 27/27, RAG 26/26 — plus any newly added RAG tests, MCP Gateway 46/46 — plus any newly added MCP security tests, Agent Orchestrator 45/45 — plus any newly added agent security tests) do not decrease from their pre-Phase-3.10 baseline.
- **Phase 1 payment flow remains intact:** the 255/255 automated test count from `PAYMENTX_PHASE_1_FINAL_SIGNOFF.md` does not decrease, and no Phase 1 service (Auth, Gateway, Payment, Routing, Validation, Audit, Notification, Reconciliation, Reporting) has any source file touched by Phase 3.10 work.
- **No secret is exposed** anywhere in the new audit events, new tests, new metrics queries, or new documentation produced by Phase 3.10 — consistent with every constraint already enforced throughout this session.
- **AI failures degrade safely:** the existing `control-center.ai.enabled` flag behavior (honest `NOT_CONFIGURED` rather than a crash) is unchanged, and the new RAG audit call's fail-open behavior is specifically verified (not merely assumed).
- **Frontend AI remains functional:** the existing AI Assistant page's real, already-proven capabilities (payment lookup, RAG grounding, non-hallucination, prompt-injection resistance) are re-verified unchanged after any Phase 3.10 code lands, using the same safe live-session pattern already established in this session's prior full-platform validation.
- **A real E2E AI flow is validated** at acceptance time: one real, safe chat message exercising RAG + MCP together (the same "combined RAG+MCP+LLM answer" pattern already proven in Phase 3.9, per the readiness audit §11), confirming the new RAG audit event appears alongside the existing MCP/Agent audit events for that same interaction.
- **Regression tests pass:** the full existing automated suites for every touched module (RAG Service at minimum; any module a new security test was added to) pass alongside the new tests, run via the same targeted, sequential, PostgreSQL-connection-conscious pattern already established in this session's Phase 1 sign-off task.

---

## Section 14 — Implementation Order

The task's suggested order is adjusted based on repository evidence: security tests are moved ahead of the RAG audit change itself, so that automated coverage for existing (already-correct) behavior exists *before* any production code is touched — giving every subsequent step a regression safety net rather than adding one only after the fact.

1. **Enable `control-center.ai.enabled`** in whatever environment performs this work (zero-risk config value; all downstream AI components already report `READY` per the readiness audit — this is a prerequisite for any live/E2E step below, not a Phase 3.10 deliverable in itself).
2. **Automated AI security tests** (§6, §12) — test-only, zero production code path change, validates current (already-correct, per live evidence) behavior first.
3. **RAG Service audit layer** (§5) — the one production code change, landed with the safety net from step 2 already in place.
4. **Observability UI** (§3, §4 — RECOMMENDED, not blocking) — independent of steps 2–3, can proceed in parallel or after, at the implementer's discretion, since it touches only Control Center (backend query catalog + one new frontend page) and no AI service.
5. **Optional Grafana dashboard** (OPTIONAL) — independent, purely additive provisioning file(s).
6. **Full regression** — every module touched (RAG Service, whichever modules got new security tests, Control Center if step 4 was done) plus the full existing Phase 1 and Phase 3.9 suites, confirming no count decreased (§13).
7. **Real E2E AI flow validation** — the one live, manual, safe verification step (§12, §13), performed last, against the fully-landed change set.

---

## Section 15 — Out of Scope

Explicitly excluded from Phase 3.10 unless separately, later approved:

- Replacing the LLM provider (Anthropic) — `LlmProvider` abstraction exists for this; no evidence justifies exercising it now.
- Replacing the local embedding model or switching back to OpenAI embeddings — explicitly forbidden by this task's own instructions and the platform's own documented migration rationale (local was chosen because OpenAI quota was unavailable).
- Rewriting RAG Service, MCP Gateway, or Agent Orchestrator — no CRITICAL or HIGH gap justifies this per the readiness audit.
- Adding any MCP write tool — the read-only posture is a deliberate, explicit, repeatedly-reaffirmed design decision (Phase 3.0 architecture doc, Phase 3.7, the readiness audit), not an oversight.
- Building `participant.lookup` / `payment.error.lookup` (§7) — requires new backing business APIs, which is business-service scope, not AI-platform hardening.
- Redesigning Payment Service or Routing Service — Phase 1 scope, explicitly untouchable here.
- Changing Payment Types (`INSTANT_PAYMENT`, `REAL_TIME_PAYMENT`, `CARD_PAYMENT`) or introducing real-world payment-network/provider names.
- Changing any Phase 1 architecture or Phase 3.9 architecture.
- Per-user AI identity (§8) — FUTURE, gated on a Phase 1 decision outside this phase.
- Conversation persistence (§9) — OUT OF SCOPE, orthogonal to this phase's pillars.
- Multi-turn agent memory, a second LLM/embedding provider, multi-agent orchestration, streaming responses — all pre-existing, explicitly-disclosed future items (readiness audit §11, checkpoint §20) not connected to Security/Observability/Production Hardening.
- Any unrelated frontend functionality (Payment Details page, frontend Auth integration — both Phase 1 gaps, both explicitly out of this phase).

---

## Section 16 — Risk

| Risk | Assessment | Mitigation (built into this spec) |
|---|---|---|
| Performance | RAG Service's timeout budget is already tight (raised twice in Phase 3.9 to accommodate real Anthropic latency). A new synchronous audit call inside the RAG request path could worsen this. | The audit call must be fire-and-forget/fail-open (§5) and must not block the response — same pattern MCP Gateway/Agent Orchestrator already use successfully. |
| Security | A new audit event is a new place sensitive data could accidentally leak if implemented carelessly. | §5's explicit "never capture" list; the new security test suite (§6) should itself include a test asserting the RAG audit event never contains prompt/answer/PII content. |
| PII | Retrieved RAG content could theoretically include sensitive text if a future document does. | Audit captures only chunk *identifiers*, never chunk *content* (§5) — structurally prevents this regardless of what gets ingested later. |
| Observability overhead | New Prometheus queries are read-only against already-collected, already-scraped data. | Near-zero — no new scrape target, no new metric emission, only new query definitions. |
| Database growth | New RAG audit events add to the existing Audit Service's storage, at roughly the same per-request volume MCP/Agent audit events already produce today. | No new database or table — reuses existing Audit Service capacity/retention model as-is; not independently re-assessed here (no evidence of an existing capacity problem). |
| Audit volume | Same as above — one new event per RAG request. | Monitor via existing Audit Service tooling; no new tooling proposed. |
| LLM cost | Phase 3.10 introduces zero new LLM calls (the new security tests, per §6, are structural/authorization/schema tests, not full LLM-invoking tests, except where explicitly needed for prompt-injection coverage, which reuses existing, already-budgeted test patterns). | Keep new tests minimal-LLM-call by design, consistent with this whole session's own "don't call the LLM unnecessarily" discipline. |
| Model availability | Unchanged — Anthropic remains the single configured provider; this phase does not add a dependency on model availability beyond what already exists. | N/A — out of scope (§15). |
| Failure modes | The one new failure mode introduced is "Audit Service down while RAG Service is up." | Explicitly required to fail open (§5, §13) — RAG must keep answering even if audit writing fails. |

---

## Section 17 — Final Scope Matrix

| Item | Classification | Reason |
|---|---|---|
| RAG Service audit layer | REQUIRED | Closes the one MEDIUM audit gap the readiness audit found; small, additive, mirrors a proven pattern; directly serves the "Security" pillar |
| Automated AI security tests | REQUIRED | Zero automated coverage exists today for security properties already proven live; directly serves the "Security" pillar; test-only, zero production risk |
| Control Center AI metrics visualization | RECOMMENDED | Real observability value, matches existing UI pattern, but underlying data is already accessible via Prometheus/Grafana today — not blocking |
| Pre-provisioned Grafana AI dashboard | OPTIONAL | Lower-effort parallel/alternate path to the same observability goal as the Control Center page |
| MCP unavailable tools (`participant.lookup`, `payment.error.lookup`) | OPTIONAL / FUTURE | No backing business API exists; out of AI-platform-hardening scope |
| MCP write tools | OUT OF SCOPE | Explicit, repeatedly-reaffirmed platform design decision; no evidence justifies changing it |
| Per-user AI identity | FUTURE | Architecturally blocked on a Phase 1 gap (no frontend Auth integration) that is itself out of this phase's scope |
| Conversation persistence | OUT OF SCOPE | Functional/UX capability, unconnected to Security/Observability/Production Hardening |
| LLM provider replacement | OUT OF SCOPE | No evidence of insufficiency; explicitly future work per the abstraction's own design intent |
| Embedding provider change (back to OpenAI) | OUT OF SCOPE | Explicitly forbidden by task instruction and documented migration rationale |
| Timeouts / retries / circuit breakers / rate limits | OUT OF SCOPE (no change) | Already implemented and tuned against real observed behavior; no evidence of insufficiency |
| Health checks / graceful degradation | OUT OF SCOPE (no change) | Already implemented and confirmed working live |
| RAG/MCP/Agent Orchestrator rewrite or redesign | OUT OF SCOPE | No CRITICAL/HIGH gap found anywhere; explicitly against this specification's governing principle |
| Payment Service / Routing Service / Payment Types changes | OUT OF SCOPE | Phase 1 territory, explicitly untouchable |

---

## Section 18 — Implementation Plan (REQUIRED items only)

### 18.1 — RAG Service Audit Layer

- **Goal:** every real RAG Service operation produces a real, retrievable audit event via the existing Audit Service, containing only the safe metadata defined in §5, with fail-open behavior if the Audit Service is unreachable.
- **Affected service:** `paymentx-rag-service` only.
- **Affected files/components:** a new audit-client class within `paymentx-rag-service/src/main/java/com/paymentx/rag/` (exact package/class name not specified here — not yet known/decided, per instruction not to invent unverified file names; it should structurally mirror the existing, real `paymentx-mcp-gateway/src/main/java/com/paymentx/mcp/audit/McpAuditClient.java`), plus one new call site inside RAG Service's existing request-handling flow (the specific class that currently orchestrates a RAG query was not identified by name in the readiness audit and is not asserted here).
- **Dependencies:** the existing, unmodified Audit Service (no version/API change required — same write path MCP Gateway and Agent Orchestrator already use).
- **Test strategy:** new unit test for the audit-client's request construction and fail-open behavior; new integration test confirming a real event is written and retrievable via `GET /api/v1/audit-events` for a real RAG query; full existing RAG Service suite (26/26 baseline) must still pass unmodified.
- **E2E strategy:** one real, safe RAG query (reusing existing safe test content already in the vector store — no new document required), followed by a real `GET /api/v1/audit-events` lookup confirming the new event exists with only the permitted fields.
- **Rollback strategy:** revert the one new client class and its single call site; RAG Service's core behavior is unaffected either way since the call is additive and fail-open by design.

### 18.2 — Automated AI Security Test Suite

- **Goal:** every category in §6's table has at least one automated, deterministic, safe test, added per relevant module.
- **Affected services:** `paymentx-mcp-gateway`, `paymentx-agent-orchestrator`, `paymentx-rag-service` (read/write separation, tool authorization, malicious-parameter, and prompt-injection-adjacent tests are distributed across these three based on where each behavior actually lives).
- **Affected files/components:** new test classes under each module's existing `src/test/java/...` tree, following the naming convention already established elsewhere in this codebase (`AuthServiceSecurityTest.java`, `RoutingSecurityTest.java`, `ReconciliationSecurityTest.java` are the real, existing precedents) — e.g. a new `*SecurityTest.java` per module. Exact class names are an implementation-time decision, not specified here.
- **Dependencies:** none new — these tests exercise existing, already-implemented code paths (`ToolAuthorizationService`, the MCP tool catalog, the existing seeded adversarial vector-store document).
- **Test strategy:** this item *is* the test strategy (§6, §12) — unit/integration-level tests, no new test framework, matching each module's existing convention (JUnit 5 + Mockito, per the pattern already used throughout this repository).
- **E2E strategy:** not applicable — these are the automated substitute for what was previously only E2E/live-session evidence; §13's real E2E step remains the final, separate acceptance check.
- **Rollback strategy:** delete the new test files; no production code path is touched by this item at all, so rollback carries zero behavioral risk.

---

## FINAL RECOMMENDATION

**PHASE 3.10 SCOPE:**
Close the one real audit-coverage gap (RAG Service) and convert already-proven-live AI security behavior into automated, CI-enforced tests. Optionally surface already-collected AI metrics in the Control Center UI. Everything else the original "Security + Observability + Production Hardening" title might have implied is either already implemented (per the readiness audit's evidence) or is explicitly out of scope because it doesn't serve this phase's three named pillars or is architecturally blocked on out-of-scope Phase 1 work.

**REQUIRED:**
- RAG Service audit layer (§5, §18.1)
- Automated AI security test suite (§6, §18.2)

**RECOMMENDED:**
- Control Center AI metrics visualization page (§3, §4)

**OPTIONAL:**
- Pre-provisioned Grafana AI dashboard
- `participant.lookup` / `payment.error.lookup` MCP tools (FUTURE)

**OUT OF SCOPE:**
- MCP write tools
- Per-user AI identity (FUTURE, blocked on Phase 1)
- Conversation persistence
- LLM provider replacement
- Embedding provider change (back to OpenAI)
- Timeouts/retries/circuit breakers/rate limits/health checks/graceful degradation changes (already adequate)
- Any rewrite or redesign of RAG Service, MCP Gateway, or Agent Orchestrator
- Any Phase 1 service, architecture, or Payment Type change

**ESTIMATED IMPLEMENTATION ORDER:**
1. Enable `control-center.ai.enabled` in the working environment (prerequisite, zero-risk).
2. Automated AI security test suite (test-only, establishes a regression safety net first).
3. RAG Service audit layer (the one production code change, landed with that safety net in place).
4. Observability UI (independent, RECOMMENDED, Control-Center-only).
5. Optional Grafana dashboard.
6. Full regression across every touched module plus the existing Phase 1 (255/255) and Phase 3.9 baselines.
7. Real E2E AI flow validation (one live, safe, manual check, performed last).

**REGRESSION RISKS:**
- A poorly-implemented RAG audit call could add latency to an already-timeout-tight request path if not made fail-open/non-blocking (§5, §16).
- A new audit event could leak sensitive content if the "never capture" list (§5) isn't strictly enforced — mitigated by capturing chunk identifiers only, never chunk content.
- New security tests could be flaky if any are made to depend on a real LLM call rather than structural/authorization/schema assertions — mitigated by keeping the catalog (§6) mostly LLM-call-free by design.
- Any work on the Observability UI (RECOMMENDED, not REQUIRED) must not touch `PrometheusMetricsService.java`'s existing entries, `useE2EFlow.ts`, or any Payment Flow file — purely additive new entries and a new page only.

---

## STOP

This document is the complete deliverable for this task. No source code, configuration, dependency, test, database, or Docker file was modified. No service was started, stopped, or restarted. No payment was created. No MCP tool (read or write) was executed. Phase 1 and Phase 3.9 were not touched. Phase 3.10 implementation was not started. Nothing was committed.
