# PaymentX Phase 4.2.0 — Error Analyzer Agent Design & Readiness Audit

**Status:** READ-ONLY DESIGN. No source, configuration, or database was modified. No MCP tool was created or changed. No document was ingested. No service was started. No payment was created. The Error Analyzer was not implemented.

**Method:** every claim is grounded in actual source — either read directly this session (MCP tool classes, Prompt Service Liquibase seeds, Vector/RAG DTOs and service impl, `AgentOrchestratorService`/`AgentPlanner`/`AgentMetrics`/`AgentAuditClient` from the Phase 4.1 work already in this codebase) or traced by one focused research pass across `paymentx-validation-service`, `paymentx-payment-service`, `paymentx-routing-service`, `paymentx-audit-service`, `paymentx-reconciliation-service`, `paymentx-reporting-service`, `paymentx-notification-service`, cited to `file:line` throughout §2.

---

## 1. Executive Summary

PaymentX's Phase 4.1 foundation (`AgentDefinition`, `AgentRegistry`, per-agent `AgentToolPolicy`) is sufficient to run an Error Analyzer today **without any orchestrator code change** — adding one `agents.definitions` entry is structurally all that's required at the orchestration layer. The 5 existing read-only MCP tools (`payment.lookup`, `payment.status`, `routing.lookup`, `reconciliation.status`, `audit.search`) cover every piece of live evidence a payment-failure investigation needs; no new tool, read or write, is required.

Two real, non-obvious assets already exist that materially reduce the remaining work:
1. A **DRAFT** Prompt Service template, `PAYMENT_ERROR_ANALYSIS` (seeded Phase 3.2, never activated), already frames the exact task — but its variable contract (`paymentReference, status, errorCode, errorMessage`) is **incompatible** with the current agent loop's rendering contract (`availableTools, executionHistory, userQuery, iteration, maxIterations` — the only variables `AgentPlanner.plan()` ever supplies). It cannot be used as-is; it is real, relevant starting material for a new version, not a drop-in prompt.
2. A real operational runbook, `paymentx-control-center/docs/RUNBOOK.md` (217 lines), exists in the repository — but it documents running/deploying Control Center itself, not diagnosing payment failures. It is genuine content, not payment-error content.

What is genuinely missing is knowledge-corpus content: no error-code reference, no payment-scheme behavioral documentation, no ISO 20022 material, and no payment-failure-specific runbook exist anywhere in the repository, and nothing has ever been ingested into Vector Service beyond RAG Service's own validation test fixtures (a Phase 3.6 finding this audit re-confirms, not assumes). One real plumbing gap was also found: `agent-orchestrator`'s `RagServiceClient.query()` does not expose the `filters` parameter that `RagQueryRequest`/`VectorSearchRequest` already support end-to-end at the RAG/Vector layer — a one-line-signature gap between two already-real capabilities.

**Classification: B — RAG CORPUS REQUIRED FIRST.** See §21.

---

## 2. Actual Payment Error Flow

Traced from real source across all 7 services in the path. No table, enum, endpoint, or topic below is inferred — each was read directly.

### 2.1 Validation Service (`paymentx-validation-service`)
- **Error generation:** `ValidationController.validate` (`controller/ValidationController.java:48-61`) delegates to `ValidationService`. Failures throw `BusinessRuleViolationException` (`exception/BusinessRuleViolationException.java:26-37`, always `retryable=false` — e.g. blacklist/amount-limit violations) or `DuplicatePaymentException` (`exception/DuplicatePaymentException.java:17-23`, errorCode `DUPLICATE_PAYMENT_REFERENCE`, `retryable=false`).
- **Status enum:** `ValidationStatus` (`entity/ValidationStatus.java:15-19`) — `VALIDATED, REJECTED, DUPLICATE`.
- **Persistence:** `validation_log` table, entity `ValidationLog` — `payment_reference, trace_id, validation_status, rejection_reason` (free text, 512 chars), `validated_at`.
- **HTTP semantics:** `VALIDATED`/`REJECTED` → 200; `DUPLICATE` → 409 (`ValidationController.java:55-58`).
- **Kafka out:** `event/KafkaTopics.java:23-26` — `instant-payment-validated`, `card-payment-validated`, `real-time-payment-validated` (per scheme), plus `payment-rejected`. Publisher: `ValidationEventPublisher`.
- **Query endpoint:** **none** — no `GET` on `validation_log` was found; the synchronous `POST /api/v1/validations` response is the only place `rejection_reason` is ever returned.

### 2.2 Payment Service (`paymentx-payment-service`)
- **Status enum:** `PaymentStatus` (`entity/PaymentStatus.java:30-49`) — `RECEIVED, VALIDATED, PROCESSING, ROUTING, DEBITING, DEBIT_SUCCESS, DEBIT_FAILED, CREDITING, CREDIT_SUCCESS, CREDIT_FAILED, SETTLING, SETTLED, RETURNED, REVERSED, FAILED, CANCELLED, TIMEOUT, RETRYING`.
- **Error generation:** `PaymentEngineImpl.handleFailure` (`service/impl/PaymentEngineImpl.java:282-287`) sets `payment.failureReason` from `PaymentProcessor.ProcessingResult` (`service/PaymentProcessor.java:60-66`) — a record carrying a real, per-failure `retryable` boolean (a downstream timeout is retryable; a business-rule failure is not).
- **Persistence:** `payment` table (`status, failure_reason` [512 chars], `trace_id, correlation_id`). Separately, `payment_status_history` (`from_status, to_status, reason, transitioned_at` — one row per transition, e.g. written by `TimeoutScheduler.markTimedOut`, `scheduler/TimeoutScheduler.java:99-107`) and a service-local `payment_audit` table (`PaymentAudit` entity, distinct from the central Audit Service). `PaymentRetry` entity tracks `current_retry/max_retry/retry_reason/status(RetryStatus)/next_retry_time`; `RetryStatus` (`entity/RetryStatus.java:15-20`) — `SCHEDULED, IN_PROGRESS, SUCCEEDED, EXHAUSTED`.
- **Timeout detection:** `TimeoutScheduler.detectStuckPayments` (`scheduler/TimeoutScheduler.java:64-90`) scans payments stuck in `PROCESSING`, marks `TIMEOUT`, writes history + local audit rows, publishes `PaymentTimeoutEvent`.
- **Kafka:** consumes `*-validated` topics via `PaymentValidatedConsumer`; publishes `payment.processing, payment.debited, payment.credited, payment.completed, payment.failed, payment.returned, payment.reversed, payment.cancelled, payment.timeout` (`constant/KafkaTopics.java:37-45`).
- **Query endpoints** (`controller/PaymentController.java`): `GET /{reference}` (full snapshot, **includes `failureReason`**, line 67-74); `GET /{reference}/status` (lightweight — `PaymentStatusResponse` has **no `failureReason` field**, confirmed structurally, `dto/PaymentStatusResponse.java:24-29`); `GET /` (list/filter); `GET /search`. **No endpoint exposes `payment_status_history` or the local `payment_audit` table** — `PaymentHistoryResponse` DTO exists but is unreferenced anywhere in main source (dead).
- **Operational controls (not MCP-exposed, out of scope):** `POST /{ref}/retry`, `POST /{ref}/cancel`.

### 2.3 Routing Service (`paymentx-routing-service`)
- **No dedicated "failed routing attempt" record exists.** `RoutingController` is CRUD+resolve over routing *rules*, not a per-payment routing-decision log. Read endpoints: `GET /{id}`, `GET /` (search), `GET /default?scheme=`, `GET /participant/{participantId}?scheme=` (falls back to default). Mutating endpoints require `ROUTING_ADMIN`.
- **Kafka:** `RoutingKafkaTopics.java:23-24` publishes `routing.route-resolved`, `routing.rule-changed`.
- **Implication:** a routing-caused payment failure (misroute, no active rule, participant unavailable) is only observable via `Payment.failureReason`/`payment_status_history` on the payment side, or the absence of a matching rule via `routing.lookup` — never a routing-side failure log.

### 2.4 Audit Service (`paymentx-audit-service`)
- **Event type enum:** `EventType` (`entity/EventType.java:16-30`) — `PAYMENT_CREATED, PAYMENT_UPDATED, PAYMENT_ROUTED, PAYMENT_COMPLETED, PAYMENT_FAILED, PAYMENT_CANCELLED, PAYMENT_REFUNDED, VALIDATION_COMPLETED, PARTICIPANT_UPDATED, ROUTING_RULE_CHANGED, SECURITY_EVENT, API_REQUEST, API_RESPONSE, SYSTEM_EVENT, KAFKA_EVENT`.
- **Event status enum:** `EventStatus` (`entity/EventStatus.java:15-19`) — `RECORDED, PROCESSING_FAILED, ARCHIVED`.
- **Query endpoints** (`controller/AuditController.java`): `GET /{id}`; `GET /` (search by `correlationId, paymentId, participantId, reference, status, eventType, fromDate, toDate`, paginated).
- **This is the platform's aggregation point** — `event/AuditEventConsumer.java` + `event/TopicEventTypeResolver.java` consume Kafka events centrally from every other service.

### 2.5 Reconciliation Service (`paymentx-reconciliation-service`)
- **Mismatch type enum:** `ReconciliationStatus` (`entity/ReconciliationStatus.java:19-30`) — `MATCHED, MISSING, DUPLICATE, AMOUNT_MISMATCH, CURRENCY_MISMATCH, STATUS_MISMATCH, SETTLEMENT_DELAY, LATE_SETTLEMENT, ORPHAN, UNEXPECTED_SETTLEMENT`.
- **Batch status enum:** `BatchStatus` (`entity/BatchStatus.java:15-21`) — `PENDING, RUNNING, COMPLETED, FAILED, PARTIALLY_COMPLETED`.
- **Persistence:** `mismatch_record` (`MismatchRecord`: `reconciliation_record_id, batch_id, mismatch_type, description, resolved, resolved_by, resolved_at, resolution_notes`).
- **Query endpoints:** `GET /batches/{id}` (status), `.../summary`, `GET /mismatches` (search), `.../report` (CSV). Mutating (`upload`, `start batch`, `reprocess`, `resolve mismatch`) require `RECONCILIATION_ADMIN`.
- **Parse failure:** `SettlementFileParseException` on malformed settlement file upload.

### 2.6 Reporting Service (`paymentx-reporting-service`)
- **Status enum:** `ReportStatus` (`entity/ReportStatus.java:21-27`) — `PENDING, RUNNING, COMPLETED, FAILED, CANCELLED`.

### 2.7 Notification Service (`paymentx-notification-service`)
- **Status enum:** `NotificationStatus` (`entity/NotificationStatus.java:15-22`) — `PENDING, SENDING, SENT, FAILED, RETRYING, DEAD_LETTERED`.

### 2.8 Key implications for the agent design
1. **`payment_status_history`/`payment_audit` are the richest failure-transition evidence in the platform but have no REST endpoint** — `payment.lookup`'s `failureReason` (one field, current state only) is the deepest currently-queryable failure detail Payment Service itself exposes; anything about *how* a payment got there requires `audit.search` against the central Audit Service instead.
2. **`payment.status` structurally cannot see `failureReason`** — confirmed via `PaymentStatusResponse`'s actual fields, not assumed.
3. **`retryable` is a real, first-class, per-failure signal** at both the validation layer (always `false`) and the payment-processing layer (`ProcessingResult.retryable`) — a sound, evidence-backed basis for a "recommended action" distinguishing "safe to retry" from "requires human action."
4. Every terminal status/enum across all 7 services is a small, closed, real Java enum — never free text for the *type* (only `reason`/`description` fields are free text) — a sound basis for a bounded `errorCategory` in Error Analyzer's output.

---

## 3. Existing MCP Tool Inventory

All 5 tools read directly from `paymentx-mcp-gateway/src/main/java/com/paymentx/mcp/tool/*.java` this session — exact schemas, not summarized from memory.

| Tool | Class | Backend call | R/W | Auth | Input schema | Output fields | Evidence value |
|---|---|---|---|---|---|---|---|
| `payment.lookup` | `PaymentLookupTool` | `PaymentServiceClient.getByReference` → `GET /api/v1/payments/{reference}` | READ_ONLY, `PAYMENT_READ`, 5s | Resource-ownership check post-fetch (debtor/creditor participant) | `paymentReference` (required, `^[A-Za-z0-9_-]{1,64}$`) | `found, paymentReference, status, scheme, amount, currency, debtorParticipantId, creditorParticipantId, debtorAccountMasked, creditorAccountMasked, failureReason, createdAt, updatedAt` | **Primary evidence source** — the only tool carrying `failureReason` |
| `payment.status` | `PaymentStatusTool` | `PaymentServiceClient.getStatus` → `GET /api/v1/payments/{reference}/status` | READ_ONLY, `PAYMENT_READ`, 5s | None (documented limitation — no participant identity in response) | `paymentReference` (required, same pattern) | `found, paymentReference, status, updatedAt` | Lightweight status confirmation only — **no failure detail** |
| `routing.lookup` | `RoutingLookupTool` | `RoutingServiceClient.resolveForParticipant`/`resolveDefault` → `GET /routes/participant/{id}?scheme=`/`GET /routes/default?scheme=` | READ_ONLY, `ROUTING_READ`, 5s | Resource-ownership only when a specific participant is requested | `scheme` (required, one of `INSTANT_PAYMENT/REAL_TIME_PAYMENT/CARD_PAYMENT`), `participantId` (optional) | `found, participantId, targetRoute, priority, active, isDefault, description` | Confirms whether an active route/rule exists — useful only when a routing-caused failure is suspected |
| `reconciliation.status` | `ReconciliationStatusTool` | `ReconciliationServiceClient.getBatchStatus`/`getSummary` → `GET /reconciliation/batches/{id}` [+`/summary`] | READ_ONLY, `RECONCILIATION_READ`, 5s | None (batches are platform-wide, no participant ownership) | `batchId` (required, UUID), `includeSummary` (optional boolean) | `found, batchId, batchType, status, windowFrom/To, startedAt, completedAt, totalRecords, matchedCount, mismatchCount, failureReason` [+ 10-field `summary` object] | Only relevant when a batch/settlement-related failure is suspected — requires a `batchId` the agent does not otherwise have (see §17) |
| `audit.search` | `AuditSearchTool` | `AuditServiceClient.search` → `GET /api/v1/audit-events` | READ_ONLY, `AUDIT_READ`, 8s | Force-scopes `participantId` for scoped callers | `correlationId, paymentId, participantId, reference, status(3 values), eventType(15 values), fromDate, toDate, page, size(≤50)` — all optional | `content[]` of `{id, eventType, eventStatus, sourceService, correlationId, paymentId, participantId, reference, occurredAt}`, `pageNumber, pageSize, totalElements, totalPages` | Only tool with access to the platform's cross-service aggregated event history — **never returns raw event `payload`** |

No tool anywhere reads `payment_status_history`, the local `payment_audit` table, `validation_log`, or `mismatch_record` directly — those live behind endpoints that don't exist (§2), so no MCP tool could expose them without a new backend API, which is explicitly out of scope.

---

## 4. Error Analyzer Tool Allowlist

**Recommended `AgentDefinition.allowedTools` for `error-analyzer`:**

```
payment.lookup, payment.status, audit.search, routing.lookup, reconciliation.status
```

All 5 existing tools, no new tool, no write tool. Justification per tool:

- **`payment.lookup`** — required, primary. The only tool carrying `failureReason`, the single most direct evidence field for any RCA.
- **`payment.status`** — required, secondary. Cheap confirmation of current state; useful when the LLM only needs to confirm a payment is still `PROCESSING`/not yet terminal before investigating further, without the heavier full-lookup call.
- **`audit.search`** — required, primary. The only source of historical/cross-service context (`PAYMENT_ROUTED`, `VALIDATION_COMPLETED`, etc.) — needed whenever `payment.lookup`'s single `failureReason` field is insufficient to explain *how* the payment reached its current state.
- **`routing.lookup`** — required, conditional-use. Only useful when `audit.search`/`failureReason` suggests a routing-caused failure (misroute, unavailable participant); the planning LLM decides per-case whether to call it, same as today's general agent.
- **`reconciliation.status`** — required, conditional-use, but see §17's limitation: it requires a `batchId` the agent has no tool to discover from a payment reference alone (no `payment→batch` lookup tool/field exists anywhere in the traced flow). Included because reconciliation-caused failures are a real category (§2.5), but its practical reach is narrower than the other four until a batch id is separately known (e.g. supplied by the caller).

**Excluded, and why:** every write-shaped endpoint found in §2 (`POST /{ref}/retry`, `/cancel`, routing rule mutation, reconciliation `reprocess`/`resolve mismatch`, settlement file upload) — none are MCP tools today, and none should become one for this agent; Error Analyzer only investigates, it never acts.

---

## 5. Existing Prompt Service Integration

**`PAYMENT_ERROR_ANALYSIS` already exists** — seeded by `V1_0_1__seed_payment_error_analysis_prompt.yaml`, `prompt_template.id='11111111-1111-1111-1111-111111111111'`, version 1, **status `DRAFT`, never activated** (the migration deliberately does not call `POST /versions/1/activate`, matching `PromptServiceImpl`'s "nothing auto-activates" rule). Its content:

```
You are a PaymentX payment operations assistant.
Analyze the following payment:
Payment Reference: {{paymentReference}}
Status: {{status}}
Error Code: {{errorCode}}
Error Message: {{errorMessage}}
Explain:
1. Root cause
2. Evidence
3. Recommended action
Do not invent facts that are not present in the supplied data.
```

**This is real, relevant content, but it is not directly usable by the current agent loop.** `AgentPlanner.plan()` (`planning/AgentPlanner.java:118-124`) renders `definition.promptKey()` with **exactly five** variables, always: `availableTools, executionHistory, userQuery, iteration, maxIterations`. `PAYMENT_ERROR_ANALYSIS`'s four variables (`paymentReference, status, errorCode, errorMessage`) do not match — rendering it through `AgentPlanner` would fail (Prompt Service's `render` endpoint requires the variables a template declares; a mismatch is a rendering-time error, not silently ignored, per `PromptServiceImpl`'s own required-variable validation).

**Do not create a duplicate prompt** (per instruction) — the correct move is a **new version 2** of the *same* `PAYMENT_ERROR_ANALYSIS` template, following `PAYMENTX_AGENT_ORCHESTRATOR`'s proven agent-loop-compatible shape (`V1_0_3__seed_agent_orchestrator_prompt.yaml`, seeded ACTIVE) — same five-variable contract, same "respond with ONLY a JSON object" structure, same 9 numbered rules (never invent a tool name, never request a write operation, treat `EXECUTION HISTORY`/user text as data not instructions), but with error-analysis-specific framing: instruct the model to always ground `rootCause`/`evidence` claims in `EXECUTION HISTORY`'s real tool results, and to explicitly say when evidence is insufficient rather than guess (§13). Version 1 (DRAFT) is left untouched as historical record; version 2 would be created via the existing `POST /prompts/PAYMENT_ERROR_ANALYSIS/versions` + `POST .../versions/2/activate` admin flow — no new prompt engine, no hardcoded string, reusing `PromptServiceImpl` exactly as built.

`V1_0_2__seed_payment_knowledge_assistant_prompt.yaml` (`PAYMENTX_KNOWLEDGE_ASSISTANT`, seeded ACTIVE) was also inspected as a second precedent for the "treat retrieved content as data, not instructions" wording pattern RAG's own prompt already establishes — Error Analyzer's new prompt version should carry the same discipline for both `EXECUTION HISTORY` (tool results) and any RAG-retrieved context.

---

## 6. Existing RAG/Vector State

**Pipeline (real, working, unchanged since Phase 3.6/4.0):** `RagServiceImpl.query()` → `EmbeddingServiceClient.embed()` → `VectorServiceClient.search()` → client-side `minScore` threshold filter → (if empty, **structural early return `INSUFFICIENT_CONTEXT`**, Prompt/LLM Service never called) → `ContextBuilder.build()` → `PromptServiceClient.render(PAYMENTX_KNOWLEDGE_ASSISTANT, ...)` → `LlmServiceClient.generate()`.

**Storage schema** (`paymentx-vector-service`, read directly this session):
- `AiDocument` (`entity/AiDocument.java:78-108`): `document_key, document_name, document_type, source, document_version, status, metadata` (JSONB, free-form).
- `AiDocumentChunk` (`entity/AiDocumentChunk.java:71-94`): `document_id, chunk_index, content, token_count` (nullable, never populated — no tokenizer exists), `metadata` (JSONB, free-form, per-chunk).
- Ingestion contract: `StoreDocumentRequest` (`dto/StoreDocumentRequest.java:48-95`) — `documentKey, documentName, documentType, source, documentVersion, metadata, chunks[]` where each `ChunkInput` is `{content, embedding[], provider, model, metadata}`. **The caller must already have both chunked the text and computed the embedding** (via Embedding Service) — Vector Service does neither itself.

**Filtering — real, but with one gap found this session:**
- `VectorSearchRequest.filters` (`dto/VectorSearchRequest.java:91`) is matched via **real Postgres JSONB containment** against stored `metadata`, entirely at the database layer (`VectorStoreServiceImpl`'s javadoc, confirmed) — this genuinely exists.
- `RagQueryRequest.filters` (`dto/RagQueryRequest.java:64`) exists and `RagServiceImpl.query()` (line 178, `validateFilters`) **does forward it** to `VectorServiceClient.search()` (line 188) — confirmed by direct read, capped at 10 filters.
- **Gap:** `agent-orchestrator`'s own `RagServiceClient.query(String query, String correlationId)` (`client/RagServiceClient.java:91`) has **no `filters` parameter at all** — the plumbing is real and complete at the RAG/Vector layer but the Agent Orchestrator client method never exposes it. Today, an agent (any agent, not just Error Analyzer) cannot request "only error-code documents" or "only ISO 20022 material" even if such content existed and were tagged with `documentType` metadata — every RAG call is an unfiltered top-K search. See §18.A.

**Content — distinguished exactly, per instruction, not conflated:**
1. **Plumbing/infrastructure:** EXISTS, fully real and tested (embed → search → threshold → generate; audit trail via `RagAuditClient`).
2. **Existing real documents:** **none found in the ingestion path.** No seed migration, no ingestion script, anywhere in `paymentx-vector-service` or `paymentx-rag-service` loads real content — re-confirmed this session via a repo-wide search (§7).
3. **Existing test/ad-hoc documents:** `PAYMENTX_PHASE_3_6_RAG_FINAL_VALIDATION.md` records that Phase 3.6 validation inserted a small number of manually-created test documents through the real `StoreDocumentRequest` path (one deliberately a prompt-injection probe) — these are synthetic validation fixtures, not a knowledge corpus, and there is no evidence they persist in any long-lived environment today.
4. **Missing payment-error knowledge corpus:** confirmed — see §7's category-by-category breakdown.

---

## 7. RAG Knowledge Requirements

| Category | Classification | Evidence |
|---|---|---|
| PaymentX architecture | **PARTIAL** | Real, substantial source material exists (`PAYMENTX_PHASE_3_ARCHITECTURE.md`, `docs/adr/0001-0004`, this session's own Phase 4.0/4.1 documents) — **not ingested** into Vector Service |
| Payment lifecycle | **PARTIAL** | Real, authoritative source exists as code (`PaymentStatus`'s 18 real values, §2.2) and is describable from it — no compiled lifecycle document exists, nothing ingested |
| Payment error codes | **PARTIAL** | Real, authoritative values exist as code across 5 services' enums (`ValidationStatus`, `PaymentStatus`, `ReconciliationStatus`, `NotificationStatus`, `ReportStatus`) — no compiled human-readable error-code reference document exists anywhere in the repo |
| Validation errors | **PARTIAL** | `BusinessRuleViolationException`/`DuplicatePaymentException` are real, specific classes (§2.1) — undocumented externally |
| Routing errors | **MISSING** | No dedicated routing-error taxonomy exists in code or docs (§2.3's finding — routing failures surface only via `payment.failureReason`) |
| Kafka errors | **PARTIAL** | Real topic names exist (`payment.timeout`, `payment.failed`, etc., §2.2) — no operational "what a Kafka-layer failure looks like" document exists |
| Timeout/retry errors | **PARTIAL** | `TimeoutScheduler`, `RetryStatus` (4 real values) exist in code — no compiled document |
| Idempotency errors | **PARTIAL** | `DuplicatePaymentException`/`DUPLICATE` (`ValidationStatus`) are real — no compiled document |
| Reconciliation errors | **PARTIAL** | `ReconciliationStatus`'s 10 real mismatch-type values exist — no compiled document |
| ISO 20022 | **MISSING** | Confirmed by repo-wide search this session: zero matches for `iso.?20022` anywhere in the codebase |
| Payment scheme documentation | **PARTIAL** | 3 real scheme values exist (`INSTANT_PAYMENT, REAL_TIME_PAYMENT, CARD_PAYMENT`, from `RoutingLookupTool`'s own validation) — no scheme-behavior document compiled |
| Operational runbooks | **PARTIAL — real document exists, wrong subject** | `paymentx-control-center/docs/RUNBOOK.md` (217 lines) is real, substantial, and genuinely a runbook — but its content is "how to run/deploy/troubleshoot Control Center," not "how to investigate a payment failure." It is not payment-error content and should not be represented as such. |

**Conclusion, stated precisely per instruction:** the RAG corpus for payment-error investigation is not "empty" in the sense of "nothing real exists anywhere" — real architecture docs, a real runbook, and real authoritative error-taxonomy code all exist. But none of it has been (a) ingested into Vector Service, or (b) compiled into the error-code/scheme/runbook-shaped documents this specific agent needs. This is a content-authoring-and-ingestion gap, not a "start from nothing" gap — but it is a real, unclosed gap.

---

## 8. RAG Document/Metadata Design

Built directly on the existing schema (§6) — **no schema change required**, both `AiDocument.metadata` and `AiDocumentChunk.metadata` are already free-form JSONB.

**Recommended `AiDocument.metadata` keys** (document-level):

| Key | Example | Purpose |
|---|---|---|
| `documentType` | `ERROR_CODE_REFERENCE`, `RUNBOOK`, `ARCHITECTURE`, `SCHEME_REFERENCE` | Coarse category filter |
| `source` | `payment-service`, `validation-service`, `platform` | Which service's errors this document covers (also maps to `AiDocument.source` column itself) |
| `paymentScheme` | `INSTANT_PAYMENT` | Filter by scheme when relevant |
| `errorCode` | `DUPLICATE_PAYMENT_REFERENCE` | Exact-match filter for a single error-code entry |
| `severity` | `HIGH`, `MEDIUM`, `LOW` | Operational triage hint |
| `effectiveDate` | `2026-08-22` | For content that can go stale (scheme rule changes) |

**Recommended `AiDocumentChunk.metadata` keys:** inherit `documentType`/`errorCode` from the parent document (denormalized onto the chunk, since `VectorSearchRequest.filters` matches against whichever metadata the search actually queries — confirming this against `VectorStoreServiceImpl`'s exact join point is a §18.C implementation detail, not a design blocker).

**Filterable today, once `agent-orchestrator`'s `RagServiceClient` gap (§6) is closed:** `{"documentType": "ERROR_CODE_REFERENCE"}` or `{"errorCode": "DUPLICATE_PAYMENT_REFERENCE"}` passed as `RagQueryRequest.filters` would already work end-to-end at the RAG/Vector layer — this is real, existing capability, not a new feature, modulo §18.A's one-parameter client gap.

Document `documentKey` naming convention recommended: `error-code/{errorCode}` or `runbook/{topic}` (mirrors the existing `documentKey+documentVersion` compound-identity pattern, `AiDocument.java:60-67`, which is preserved unchanged).

---

## 9. Error Analyzer Input Contract

**No new DTO recommended.** Reuse `AgentExecuteRequest` (`conversationId, userId, userQuery, agentId`) exactly as Phase 4.1 built it — this matches Phase 4.1's own §5 finding that a new `AgentRequest` type was "not separately justified" once `agentId` was added, and there is no new requirement here that changes that conclusion.

- **`userQuery`** carries the investigative question in natural language, including whatever identifier the caller has: `"Why did PMT-123 fail?"`, `"What happened to payment reference ABC-999?"`. This is not hypothetical — `AgentE2EIntegrationTest.execute_duplicatePaymentQuestion_realToolResultThenRealRagRetrievalThenFinalAnswer` (unmodified, still passing) already proves this exact pattern end-to-end: a natural-language question containing a payment reference correctly drives a `CALL_TOOL payment.lookup` with the reference extracted by the planning LLM.
- **`agentId`**: `"error-analyzer"` — resolved by `AgentRegistry.resolve(...)` exactly as any other agent.
- **`correlationId`**: not a request field at all today (nor should it become one) — already handled platform-wide via `CorrelationIdFilter`/MDC, propagated automatically to every downstream call (`McpToolClient.setCorrelationId`, confirmed in `AgentOrchestratorService.execute`).
- **Error code as direct input**: deliberately **not** added as a structured field — `payment.lookup`'s own `failureReason` output is the authoritative error signal; asking the caller to supply one risks the caller's guess overriding real evidence, which conflicts with the evidence-first requirement (§13).

This is the **minimum useful input**: one natural-language field, already supported, already proven working end-to-end, requiring zero contract change.

---

## 10. Error Analyzer Output Contract

**Recommendation: no new response type. Reuse `AgentExecuteResponse` (`answer, status, sources, toolEvidence, executionMetadata`) unchanged.**

The task's example structure (`rootCause, errorCategory, affectedService, evidence[], confidence, impact, recommendedAction`) is a real, sound shape for the **content of `answer`**, not a justification for new top-level response fields — matching Phase 4.1's own precedent of declining to add `AgentResult` when `AgentExecuteResponse` already sufficed. Concretely:

- **`answer`** (existing field): the Error Analyzer's *prompt* (§5, version 2) instructs the model to structure its prose under explicit labels — `Root Cause:`, `Error Classification:`, `Affected Component:`, `Confidence:`, `Impact:`, `Recommended Action:` — a labeled-prose convention, not a new JSON contract, costing zero schema change and immediately renderable by Control Center's existing chat UI.
- **`toolEvidence`** (existing field, unchanged): each real `payment.lookup`/`audit.search`/etc. result — this is the authoritative evidence. Confirmed structurally unchangeable by the LLM's prose: `AgentOrchestratorService.buildResponse()` builds `toolEvidence` from `ToolCallRecord.result()` (the real, caught tool output) independently of `plan.answer()` — the two can never overwrite each other because they come from different objects entirely (Phase 4.1's already-proven guarantee, `AIAgentSecurityTest.toolResultTrust_...`).
- **`sources`** (existing field, unchanged): RAG source labels, once real content exists to retrieve.
- **`status`** (existing field): `SUCCESS` / `INSUFFICIENT_CONTEXT` / `DENIED` / etc. — the honest, evidence-driven signal that a caller should check *before* trusting `answer`'s prose (§13).

**Deferred, not designed here:** a machine-parseable structured field (e.g. a real `errorCategory` enum value Control Center could render as a colored badge) would require either (a) a new, small additive DTO field plus server-side parsing of the LLM's own labeled output, or (b) asking the LLM to also emit a small JSON block. Both are real, buildable options for an implementation phase, but neither is justified as part of *this* design — Control Center has no current UI surface for it (§18.E), and inventing structured fields nothing consumes yet would repeat exactly the "unnecessary abstraction" Phase 4.1's own review found none of.

---

## 11. Exact Agent Execution Flow

Using only classes that exist today (Phase 4.1), with `error-analyzer` substituted for the current single `default` agent:

```
POST /api/v1/agent/execute  { userQuery: "Why did PMT-123 fail?", agentId: "error-analyzer" }
 -> AgentController.execute
     -> AgentRegistry.resolve("error-analyzer")               [NEW config entry only, §18.A]
     -> AgentOrchestratorService.execute(request, definition, correlationId, traceId)
         -> runLoop(execution, definition)
             per iteration:
               McpToolClient.listTools()                       [unchanged - real MCP tools/list]
               AgentPlanner.plan(execution, definition, correlationId)
                   -> PromptServiceClient.render("PAYMENT_ERROR_ANALYSIS", {availableTools, executionHistory, userQuery, iteration, maxIterations}, correlationId)
                       [renders version 2 of the existing template, §5 - NOT version 1]
                   -> LlmServiceClient.generate(renderedPrompt, correlationId)
                   -> parsePlan(...) -> AgentPlan{action, reasoning, tool, arguments, ragQuery, answer}
               AgentPlanValidator.validate(plan, discoveredTools, definition)
                   -> AgentToolPolicy.checkAllowed(definition, plan.tool())
                       [definition.allowedTools() = the 5 tools in §4 - denies everything else]
               dispatch:
                 CALL_TOOL          -> McpToolClient.callTool(tool, args)
                                         -> MCP Gateway: ToolInvoker -> ToolAuthorizationService [unchanged, independent 2nd gate]
                                            -> PaymentLookupTool / PaymentStatusTool / RoutingLookupTool / ReconciliationStatusTool / AuditSearchTool
                 RETRIEVE_KNOWLEDGE -> RagServiceClient.query(ragQuery, correlationId)
                                         -> RAG Service (unchanged pipeline, §6) - returns INSUFFICIENT_CONTEXT
                                            today since no real corpus exists yet (§7)
                 FINAL_RESPONSE     -> loop ends; plan.answer() becomes execution.finalAnswer
             AgentAuditClient.recordAgentRun(execution)         [unchanged, now carries agentId="error-analyzer", Phase 4.1]
         <- AgentExecuteResponse{answer, status, sources, toolEvidence, executionMetadata}
 <- unchanged Control Center AiChatService mapping (no Control Center change needed, §18.E)
```

Every class named above already exists; the only new artifacts anywhere in this flow are one `agents.definitions` entry (§18.A) and one new Prompt Service version (§18.B) — no orchestrator/policy/validator/planner code is touched.

---

## 12. Evidence Collection Strategy

Ordering left to the planning LLM (the platform's established pattern — no hardcoded tool sequence exists for the current default agent either), but the *prompt* (§5 v2) should bias it toward:

1. **`payment.lookup` first**, always, when a payment reference is present — the only tool with `failureReason`, the cheapest way to confirm the payment exists at all (`found: false` is itself evidence, not an error).
2. **`audit.search` second**, scoped by `paymentId`/`reference`/`correlationId` from step 1's result — for historical context beyond the single current `failureReason` (e.g. was it `PAYMENT_ROUTED` before failing, confirming routing succeeded and the failure is downstream of it).
3. **`routing.lookup`** only if `failureReason` or audit events suggest a routing cause.
4. **`reconciliation.status`** only if a `batchId` is known (from audit events referencing settlement, or supplied by the caller) — §17 documents this tool's practical reach limitation.
5. **`RETRIEVE_KNOWLEDGE`** in parallel/either order, for grounding the *explanation* of whatever error code/category was found — not for finding the error itself (that's always tool evidence, never RAG).

No new tool-orchestration mechanism is proposed — this is guidance for the prompt's wording (§5), not new code.

---

## 13. Hallucination / Evidence-Grounding Strategy

Reuses real, already-implemented mechanisms — nothing new invented beyond what's justified:

- **No evidence found** (`payment.lookup` returns `found:false`): the existing `AgentOrchestratorService.finalizeAnswer` logic already handles this — `anySucceeded` is computed from real `ToolCallRecord.status()`/`RagRetrievalRecord.status()` values; if nothing succeeded, `status = INSUFFICIENT_CONTEXT`, not a false `SUCCESS` (unchanged Phase 3.8 behavior, re-verified this session).
- **Conflicting evidence** (e.g. `payment.lookup` shows `FAILED` but `audit.search` shows a later `PAYMENT_COMPLETED` event): no code-level reconciliation exists or is proposed — the prompt (§5 v2) should explicitly instruct the model to surface the conflict in `answer` rather than silently pick one, and `toolEvidence` (unchanged) preserves both raw results regardless of what the prose says.
- **Incomplete evidence** (e.g. `routing.lookup` needed but denied/unavailable): same `INSUFFICIENT_CONTEXT`/honest-failure path — `toolEvidence` still records the failed call (`ToolCallRecord` with `status="FAILED"`, unchanged mechanism).
- **Unknown error** (a `failureReason` value the prompt has no specific guidance for): the prompt instructs the model to report the raw evidence verbatim and explicitly decline to classify beyond what the evidence supports, rather than inventing a category.
- **Stale RAG information**: not addressable by this design — no corpus exists yet (§7); once one does, `AiDocument.metadata.effectiveDate` (§8) is the mechanism a future phase could use for staleness review, out of scope here.
- **LLM unsupported claims**: the pre-existing, already-tested guarantee (`AIAgentSecurityTest.toolResultTrust_notFoundEvidenceIsPreservedEvenWhenPlannerFinalAnswerClaimsOtherwise`) already proves `toolEvidence` is never overwritten by the LLM's prose — this is the platform's actual enforcement mechanism, reused unchanged, not something Error Analyzer must build itself.

**Confidence — deliberately not an invented numeric/ML mechanism.** A simple, rule-based, evidence-completeness-derived label is recommended instead of anything the LLM invents freely:
- **HIGH**: `payment.lookup` returned `found:true` with a non-null `failureReason`, and at least one corroborating `audit.search` event.
- **MEDIUM**: `payment.lookup` returned a `failureReason`, but no corroborating audit event was found or retrieved.
- **LOW / INSUFFICIENT**: `payment.lookup` returned `found:false`, or no tool call succeeded at all — maps directly to `status=INSUFFICIENT_CONTEXT`.

This is prompt guidance (§5 v2), not a new scoring service — it asks the LLM to *report* a label consistent with what it was actually given, which is checkable against `toolEvidence` by a human reader exactly as the platform's evidence-first design already intends. No new "confidence engine" is proposed, per instruction.

---

## 14. Security Model

Reuses the Phase 3.10 + Phase 4.1 chain unchanged and in full:

```
AgentPlanValidator.validateCallTool(plan, discoveredTools, definition)
    -> AgentToolPolicy.checkAllowed(definition, plan.tool())      [definition = "error-analyzer"'s own 5-tool allow-list]
    -> MCP Gateway: ToolInvoker -> ToolAuthorizationService        [unmodified, independent second gate]
```

- **Prompt injection / indirect injection**: no change to `AgentPlanValidator`/`AgentToolPolicy` mechanics — the existing `AIAgentSecurityTest` suite's write-tool-denial tests apply identically to any `AgentDefinition`, including a new `error-analyzer` one (proven generically by Phase 4.1's own `agentCannotEscalatePermissions_...` test).
- **Tool authorization / agent-specific tool policy**: `error-analyzer`'s `allowedTools` (§4) is a strict subset of the platform's known tools — no escalation path exists (Phase 4.1's structural guarantee: `AgentToolPolicy.isAllowed` only ever consults the specific resolved `AgentDefinition`, never a shared/global set).
- **Malicious parameters**: unchanged — every tool's own input validation (`PAYMENT_REFERENCE_PATTERN`, scheme allow-list, UUID parsing, `ALLOWED_STATUSES`/`ALLOWED_EVENT_TYPES`) applies identically regardless of which agent calls it.
- **Secret leakage / read/write separation / tool-result trust / failure safety**: all unchanged, all already proven by the existing `AIAgentSecurityTest`/`McpSecurityTest` suites, which do not need new scenarios specific to Error Analyzer's *tool* usage since it uses no new tool.
- **No permission escalation, no model-controlled permission changes, no MCP write operation**: structurally guaranteed exactly as Phase 4.1 designed — `definition` is resolved once by `AgentController` from the trusted registry, never re-derived from anything the LLM outputs.

**Error-Analyzer-specific risk, genuinely new to this agent (not present for the general-purpose default agent in the same way):** the prose-hallucination gap already flagged in the Phase 4.0 audit (evidence fields are guaranteed accurate; the LLM's natural-language *summary* of them is not independently verified) is **more consequential here**, because root-cause explanation *is* this agent's entire value proposition, not an incidental chat answer. No new code-level mitigation is proposed in this design (none is justified yet) — the existing recommendation stands: `toolEvidence` must remain the record a human/downstream system trusts, `answer` is advisory prose, and this distinction should be visible wherever the response is surfaced (a UI concern, §18.E, not a backend one).

---

## 15. Observability / Metrics

Reuses `AgentMetrics` (Phase 4.1) with `agentId="error-analyzer"` as the tag value on every existing per-agent meter — **no new metrics infrastructure**:

| Requirement | Existing meter |
|---|---|
| Executions | `agent_requests_total{agent="error-analyzer"}` |
| Success/failure | `agent_success_total`/`agent_failure_total{agent, status}` |
| Latency | `agent_execution_latency{agent}` |
| MCP calls | `agent_tool_calls_total{agent, tool}` |
| RAG calls | `agent_rag_calls_total` (not yet agent-tagged, Phase 4.1 scope decision — unchanged here) |
| LLM calls | `agent_llm_calls_total` (same) |
| Denied tools | `agent_tool_denied_total{agent, tool}` |
| RCA classification | Not a metric — `errorCategory`/`confidence` are prose content (§10/§13), not currently machine-extracted; would require the deferred structured-output work (§10) before it could be a tag |

**One real, specific gap found this session, relevant precisely because "insufficient evidence" is an explicit requirement here:** `AgentOrchestratorService.finalizeAnswer` only calls `metrics.recordSuccess(agentId)` when `status==COMPLETED` — the `INSUFFICIENT_CONTEXT` branch (the exact "agent explicitly said evidence was insufficient" outcome §13 designs for) **records no metric at all today**. This is a small, precise, justified addition for the implementation phase (§18.A), not a design flaw in what's being proposed — it's a pre-existing gap in the Phase 4.1 foundation that Error Analyzer's own requirements newly make visible.

---

## 16. Audit Design

Reuses `AgentAuditClient.recordAgentRun` (Phase 4.1) unchanged in mechanism — fire-and-forget `POST /api/v1/audit-events`, `eventType=API_REQUEST`, `actorType=AI_AGENT`, redacted payload. Current payload: `agentId, status, iterations, toolCallCount, ragUsed, toolCalls[].{tool, status}`.

**Recommended additive field: `paymentReference`**, when one was involved. Justification: task explicitly asks for "payment reference if safe" — this is genuinely safe by the platform's own existing precedent: `AuditSearchTool`'s own output already includes a `reference` field pulled from real audit events (§3), so a payment reference is not treated as sensitive anywhere else in this codebase; it is not PII (an internal payment identifier, not a person), and it is exactly the join key an investigator would need to correlate an Error Analyzer run with the underlying audit trail. Implementation would extract it from the first successful `payment.lookup`/`payment.status` tool call's arguments — a small, additive, `AgentAuditClient` change (§18.A).

**Never persist** (unchanged, already-correct discipline, re-verified this session): raw secrets, unnecessary PII, full prompts, full LLM answer text, raw tool arguments/results beyond `{tool, status}`. This applies to Error Analyzer with no exception — the RCA's full text is never audited, only that a run happened, with what outcome, using which tools, and now which payment reference.

---

## 17. Error Scenario Matrix

Evidence and RCA columns state exactly what the traced flow (§2) supports — **no scenario below claims a confidence level the evidence cannot back**.

| Scenario | Expected evidence | Expected RCA | Required MCP tools | Required RAG knowledge |
|---|---|---|---|---|
| Validation failure (business rule) | `payment.lookup.failureReason` (if payment record was even created — validation may reject before a `Payment` row exists, see §2.1's "no `validation_log` query endpoint" finding, so this may only be visible via `audit.search` `VALIDATION_COMPLETED`/`PAYMENT_REJECTED`-shaped events) | HIGH confidence if `failureReason` present; MEDIUM if only inferred from audit events | `payment.lookup`, `audit.search` | Validation-rule reference (MISSING, §7) |
| Duplicate payment | `payment.lookup.failureReason` (real `DuplicatePaymentException`/`DUPLICATE_PAYMENT_REFERENCE`, §2.1) | HIGH — this is a real, specific, code-backed error code | `payment.lookup`, `audit.search` | Idempotency doc (MISSING, §7) |
| Routing unavailable | `payment.lookup.failureReason` text (no dedicated routing-error record exists, §2.3) + `routing.lookup` returning `found:false` | MEDIUM at best — inferential, not a confirmed routing-side log | `payment.lookup`, `routing.lookup`, `audit.search` | Routing error taxonomy (MISSING, §7) |
| Routing timeout | Same as above — no distinct "timeout" signal separate from "unavailable" was found in Routing Service | LOW/INSUFFICIENT unless `payment.failureReason`'s free text happens to say "timeout" explicitly | Same | Same |
| Participant unavailable | `routing.lookup` for that participant returning `found:false`, or `payment.lookup.failureReason` | MEDIUM | `payment.lookup`, `routing.lookup` | MISSING |
| Downstream timeout | `payment_status_history`/`payment_audit`'s `PAYMENT_TIMEOUT_DETECTED` (§2.2's `TimeoutScheduler`) — **not queryable via any tool** (§2.8 finding #1); only `payment.lookup.status=TIMEOUT` + `failureReason` is visible | MEDIUM — the *fact* of timeout is visible (`status=TIMEOUT`), the *detail* (which downstream call, how long) is not | `payment.lookup`, `audit.search` | Timeout/retry doc (MISSING, §7) |
| Kafka failure | Real topic names exist (§2.2/§2.1) but no tool queries Kafka consumer lag/DLQ state (Control Center's own `KafkaController` exists per the Phase 4.0 audit but is **not** an MCP tool — out of scope here) | INSUFFICIENT — cannot be confirmed from evidence available to this agent's tool set | None available | MISSING |
| Reconciliation mismatch | `reconciliation.status` (real, 10-value `ReconciliationStatus` taxonomy, §2.5) — **requires a `batchId`** this agent has no tool to discover from a payment reference alone | HIGH if a `batchId` is supplied/known; INSUFFICIENT otherwise (§4's noted limitation) | `reconciliation.status`, `audit.search` | Reconciliation doc (MISSING, §7) |
| Authentication failure | `audit.search` for `SECURITY_EVENT`/`API_REQUEST` events — payment-level auth failures were not found as a distinct `PaymentStatus`/`ValidationStatus` value (auth-service is a separate, gateway-layer concern per prior phases' architecture) | MEDIUM at best, likely INSUFFICIENT for a payment-specific RCA | `audit.search` | MISSING |
| Authorization failure | Same as above | Same | `audit.search` | MISSING |
| Unknown error | Whatever `payment.lookup.failureReason` literally says, with no classification the prompt has specific guidance for | The prompt (§13) instructs: report the raw evidence, decline to invent a category | `payment.lookup`, `audit.search` | N/A — this is exactly the honest-uncertainty case the design requires |

---

## 18. Required Changes for Implementation

### A. Agent Orchestrator
- One new entry under `agents.definitions` in `application.yml`: `agent-id: error-analyzer`, `allowed-tools:` the 5 tools in §4, `prompt-key: PAYMENT_ERROR_ANALYSIS`. **No Java code change required** — `AgentRegistry`, `AgentToolPolicy`, `AgentPlanner`, `AgentPlanValidator`, `AgentOrchestratorService` all already accept an `AgentDefinition` generically (Phase 4.1).
- Small, justified additions (not required to reach "working," but required to meet §13/§15/§16's own stated needs): (1) add `RagServiceClient.query(..., Map<String,Object> filters)` overload to close the §6 filter-passthrough gap; (2) call `metrics.recordFailure`/a dedicated counter for `INSUFFICIENT_CONTEXT` in `finalizeAnswer` (§15's gap); (3) extract and pass a `paymentReference` into `AgentAuditClient`'s payload (§16).

### B. Prompt Service
- **New version 2** of the existing `PAYMENT_ERROR_ANALYSIS` template (§5) — created via the existing `POST /prompts/{key}/versions` + `POST .../versions/2/activate` admin endpoints. No schema change, no new prompt key, no code change to `PromptServiceImpl`.

### C. RAG / Embedding / Vector
- **NO CHANGE REQUIRED to any of the three services' code or schema.** `StoreDocumentRequest`/`VectorSearchRequest`'s `filters` already support everything §8 designs. What's required is **content**, not code: authoring and ingesting real error-code/scheme/runbook documents (out of scope for this design phase, per instruction).

### D. MCP Gateway / MCP tools
- **NO CHANGE REQUIRED.** All 5 needed tools already exist, already read-only, already correctly scoped (§3/§4). No new tool, no modification to `ToolAuthorizationService`, `ToolRegistry`, or any existing `PaymentXTool`.

### E. Control Center
- **NO CHANGE REQUIRED** for Error Analyzer to be reachable — the existing `POST /api/v1/ai/chat` → `AiChatService` → `AgentOrchestratorService` path is agent-agnostic already (a caller would need to pass `agentId`, which today's `AiChatRequest`/`AiPlatformClient.executeAgent` does not forward — this is the one real, small gap: `AiPlatformClient` would need an optional `agentId` passthrough to let a human operator explicitly choose Error Analyzer rather than always hitting `default`). A dedicated "Error Analyzer" UI page is explicitly out of scope (matches Phase 4.1's own "leave the frontend untouched" precedent).

### F. Tests
- New: an `AgentRegistryTest`-style config test proving `error-analyzer`'s definition resolves correctly and its allow-list matches §4 exactly.
- New: an `AIAgentSecurityTest`-style scenario proving `error-analyzer` still denies every write-shaped tool name (reusing the existing 9-name parameterized pattern against the new definition).
- New: an `AgentE2EIntegrationTest`-style scenario exercising the new prompt version 2's variable contract end-to-end over WireMock (mirroring the existing duplicate-payment scenario, §9).

### G. Metrics
- Additive only (§15/§18.A) — no new metrics system, one new counter path for `INSUFFICIENT_CONTEXT`.

### H. Audit
- Additive only (§16/§18.A) — one new field (`paymentReference`) on the existing payload shape.

---

## 19. Real E2E Validation Plan (Designed, Not Executed)

1. **Create a controlled test payment** via the existing, real `POST /api/v1/payments` (validation-service/payment-service's normal path) using an already-known failure-inducing input — e.g. submit the *same* `paymentReference` twice in immediate succession to deterministically trigger `DuplicatePaymentException`/`ValidationStatus.DUPLICATE` (§2.1, §17's "Duplicate payment" row — the highest-confidence, most evidence-rich scenario, chosen deliberately as the safest first real test).
2. **Confirm the known failure condition** via the existing, already-real `GET /api/v1/payments/{reference}` — assert `failureReason` is populated as expected, before ever invoking the agent (isolates "did the platform behave as expected" from "did the agent report it correctly").
3. **Invoke Error Analyzer** via `POST /api/v1/agent/execute` with `{userQuery: "Why did payment <reference> fail?", agentId: "error-analyzer"}`.
4. **Verify MCP evidence**: assert `AgentExecuteResponse.toolEvidence` contains a `payment.lookup` entry whose `result.failureReason` matches step 2's real value exactly (not paraphrased).
5. **Verify RAG retrieval**: assert `AgentExecuteResponse.sources`/`status` — expected `INSUFFICIENT_CONTEXT` for the RAG portion specifically (or `sources` empty) **until** real corpus content exists (§7); this is the expected, honest outcome today, not a test failure.
6. **Verify RCA**: assert `answer` references the real `failureReason`/error code from step 4, and that the response's `status` is `SUCCESS` (evidence was sufficient — the tool evidence alone, even without RAG, should be enough for this specific scenario).
7. **Verify audit**: query `audit-service`'s `GET /api/v1/audit-events` (via `audit.search` or directly) for an `actorType=AI_AGENT` event correlated by the run's `correlationId`, confirm `agentId="error-analyzer"` appears in the payload (§16).
8. **Verify metrics**: scrape `/actuator/prometheus` on Agent Orchestrator, confirm `agent_requests_total{agent="error-analyzer"}` and `agent_tool_calls_total{agent="error-analyzer",tool="payment.lookup"}` incremented by exactly 1.

**Not executed in this design phase** — this plan requires the §18.A configuration entry and §18.B prompt version to exist first, both explicitly out of scope for this read-only audit.

---

## 20. Risks and Limitations

- **RAG will legitimately return `INSUFFICIENT_CONTEXT` for most real queries** until corpus content exists (§7) — this is honest, correct behavior per the platform's own design, not a defect, but it means Error Analyzer's *explanations* will initially rest on tool evidence alone (which is sufficient for several §17 scenarios, but not all).
- **Reconciliation-caused failures are only shallowly reachable** — no tool discovers a `batchId` from a payment reference (§4, §17).
- **Several failure categories (Kafka, timeout detail, auth/authz) are not evidence-supported at all** by the current tool set + backend API surface — the honest design response (§13) is for the agent to say so, not to be given new capability in this phase.
- **Prose-hallucination gap is inherited, not solved** (§14) — root cause *text* is not independently verified against evidence the way `toolEvidence` itself is.
- **The `RagServiceClient` filters gap (§6/§18.A)** means even once corpus content exists, until that one client method is extended, RAG retrieval for Error Analyzer would be unfiltered top-K search across whatever corpus exists platform-wide, not scoped to error-code-shaped content specifically — a real, if minor, precision limitation.

---

## 21. Final Classification

## **B — RAG CORPUS REQUIRED FIRST**

**Why not A:** the orchestration foundation, MCP tooling, and security model are genuinely ready (§4, §11, §14 show zero required new capability at those layers) — but §7's category-by-category audit shows every payment-error-specific knowledge category is at best PARTIAL (real underlying facts exist as code, nothing compiled or ingested) or MISSING (routing errors, ISO 20022). An agent whose explicit mandate includes RAG-grounded explanation (§8/§13) cannot be called fully ready while its knowledge layer is empty of the content it's meant to retrieve, even though several scenarios (§17, e.g. duplicate payment) are already well-served by tool evidence alone.

**Why not C:** MCP capability is **not** the blocker — all 5 required tools exist, are correctly scoped, and need no modification (§3/§4/§18.D). Framing this as an MCP gap would misstate the actual finding.

**Why not D:** nothing found constitutes an architectural blocker — every required change (§18) is additive configuration/content, not a redesign; the Phase 4.1 foundation was built precisely to make this kind of addition low-risk, and this audit confirms it does.

**What "first" means concretely:** §18.A/B (one config entry, one prompt version) are trivial and could technically ship alongside minimal RAG content; the *load-bearing* prerequisite is authoring and ingesting at minimum an error-code reference document (compiled from the real enums in §2/§7 — genuine, accurate content, not invented) before Error Analyzer's RAG-grounded explanations are meaningfully better than tool-evidence-only answers already possible today.

---

## Final Compliance

- Source files modified: **0**
- Configuration modified: **0**
- Database changed: **NO**
- Services restarted: **NO**
- MCP write operation: **NO**
- Payment created: **NO**
- Error Analyzer implemented: **NO**
