# Phase 4.5.4 — Agent Platform Consolidated Checkpoint

Read-only architectural checkpoint. No source, configuration, database, or MCP changes were
made. All values below were re-verified against current repository source and, where noted,
against the live running platform (already up from Phase 4.5.3) — not taken from prior
reports on trust.

## 1. Executive Summary

Four business agents (Error Analyzer, Knowledge Assistant, Database Analysis Agent, Fraud
Detection Agent) are implemented on top of one unchanged Agent Foundation. Every agent is a
configuration entry plus a prompt row; the only production-code growth across all four phases
combined is one new MCP tool (`database.statistics`, Phase 4.4) and one new descriptive enum
value (`AgentCapability.RISK_ANALYSIS`, Phase 4.5.3). The two-layer security chain
(`AgentToolPolicy` → MCP Gateway `ToolAuthorizationService`) is identical and unmodified
across all five agents (including `default`). No write-capable MCP tool exists anywhere on
the platform — write-safety is structural, not agent-specific. Two live E2Es remain pending
purely due to host-memory constraints (not implementation gaps); Error Analyzer and Fraud
Detection Agent are both live-verified.

## 2. Current Agent Inventory

| Agent | Status | Deterministic Tests | Live E2E |
|---|---|---|---|
| `error-analyzer` | Implemented | 299/299 (cumulative total at time of that phase) | PASS |
| `knowledge-assistant` | Implemented | 333/333 (cumulative) | PENDING — host memory |
| `database-analysis-agent` | Implemented | 394/394 (cumulative) | PENDING — host memory |
| `fraud-detection-agent` | Implemented | 434/434 (cumulative, current authoritative total) | PASS — 7 scenarios |

## 3. Agent Registry Matrix

Read directly from `paymentx-agent-orchestrator/src/main/resources/application.yml`
(current, unmodified):

| Agent ID | Purpose | Enabled | Version | Capabilities | Allowed Tools | Prompt Key | Max Iter. | Timeout | Live E2E |
|---|---|---|---|---|---|---|---|---|---|
| `error-analyzer` | Evidence-first payment-failure root-cause investigation | true | 1.0 | PAYMENT_ANALYSIS, ROUTING_ANALYSIS, RECONCILIATION_ANALYSIS, KNOWLEDGE_RETRIEVAL | payment.lookup, payment.status, routing.lookup, reconciliation.status, audit.search | PAYMENT_ERROR_ANALYSIS | null → platform default (5) | null → platform default (75000ms) | PASS |
| `knowledge-assistant` | RAG-first explanation of how PaymentX works + single-payment state | true | 1.0 | KNOWLEDGE_RETRIEVAL, PAYMENT_ANALYSIS | payment.lookup, payment.status, audit.search | PAYMENT_KNOWLEDGE_ASSISTANT | null → 5 | null → 75000ms | PENDING |
| `database-analysis-agent` | Payment record/event/reconciliation/aggregate database evidence | true | 1.0 | DATABASE_ANALYSIS, PAYMENT_ANALYSIS, RECONCILIATION_ANALYSIS, KNOWLEDGE_RETRIEVAL | payment.lookup, payment.status, audit.search, reconciliation.status, database.statistics | PAYMENT_DATABASE_ANALYSIS | null → 5 | null → 75000ms | PENDING |
| `fraud-detection-agent` | Conservative, evidence-based potential-risk analysis (never fraud confirmation) | true | 1.0 | RISK_ANALYSIS, PAYMENT_ANALYSIS, RECONCILIATION_ANALYSIS, KNOWLEDGE_RETRIEVAL | payment.lookup, payment.status, audit.search, reconciliation.status | PAYMENT_FRAUD_RISK_ANALYSIS | null → 5 | null → 75000ms | PASS |

(`default` — the pre-4.1 general agent — is unchanged: all five real tools, `PAYMENTX_AGENT_ORCHESTRATOR` prompt, unaffected by any of the four business-agent additions.)

Platform defaults all four fall back to (`agent:` block): `max-iterations: 5`,
`max-tool-calls: 10`, `max-tools-per-iteration: 1`, `overall-timeout-ms: 75000`.

## 4. Capability Matrix

| Agent | Capabilities |
|---|---|
| `error-analyzer` | PAYMENT_ANALYSIS, ROUTING_ANALYSIS, RECONCILIATION_ANALYSIS, KNOWLEDGE_RETRIEVAL |
| `knowledge-assistant` | KNOWLEDGE_RETRIEVAL, PAYMENT_ANALYSIS |
| `database-analysis-agent` | DATABASE_ANALYSIS, PAYMENT_ANALYSIS, RECONCILIATION_ANALYSIS, KNOWLEDGE_RETRIEVAL |
| `fraud-detection-agent` | RISK_ANALYSIS, PAYMENT_ANALYSIS, RECONCILIATION_ANALYSIS, KNOWLEDGE_RETRIEVAL |

**`RISK_ANALYSIS` classification: additive extension, not architectural redesign.**
Evidence from `AgentCapability.java`'s own javadoc: capability values are explicitly
documented as *"purely descriptive metadata... never checked as a security control
themselves; the actual enforcement boundary is `AgentDefinition.allowedTools()`"*.
`RISK_ANALYSIS` is one more value appended to a plain Java enum with no structural role
change — no interface changed, no security path branches on capability value, no other
agent's behavior changed. This matches the same pattern `DATABASE_ANALYSIS` established one
phase earlier (Phase 4.4).

## 5. Tool Permission Matrix

Read directly from each MCP tool's `McpToolDefinition` construction
(`paymentx-mcp-gateway/.../tool/*.java`):

| MCP Tool | readWrite | requiredPermission |
|---|---|---|
| `payment.lookup` | READ_ONLY | PAYMENT_READ |
| `payment.status` | READ_ONLY | PAYMENT_READ |
| `routing.lookup` | READ_ONLY | ROUTING_READ |
| `reconciliation.status` | READ_ONLY | RECONCILIATION_READ |
| `audit.search` | READ_ONLY | AUDIT_READ |
| `database.statistics` | READ_ONLY | DATABASE_READ |

**No `ToolReadWrite.WRITE` tool exists anywhere in the MCP tool registry** — confirmed by
enumerating every file under `paymentx-mcp-gateway/.../tool/`; all six are READ_ONLY.

Per-agent allow-list (from application.yml, §3):

| Agent | Allowed | Denied (by omission) | Write capability |
|---|---|---|---|
| `error-analyzer` | payment.lookup, payment.status, routing.lookup, reconciliation.status, audit.search | database.statistics | None |
| `knowledge-assistant` | payment.lookup, payment.status, audit.search | routing.lookup, reconciliation.status, database.statistics | None |
| `database-analysis-agent` | payment.lookup, payment.status, audit.search, reconciliation.status, database.statistics | routing.lookup | None |
| `fraud-detection-agent` | payment.lookup, payment.status, audit.search, reconciliation.status | routing.lookup, database.statistics | None |

Least privilege confirmed: `knowledge-assistant` carries the smallest grant (3 tools);
`error-analyzer` and `database-analysis-agent` are the only two granted `routing.lookup`/
`database.statistics` respectively, each justified in that phase's own design (evidence
breadth for root-cause work; schema/aggregate scope for database analysis). No agent's
allow-list was read from documentation — each row above was read from the live
`application.yml`.

## 6. Security Architecture

Traced directly in source, not inferred:

```
Agent identity        AgentController.execute() resolves agentId from the trusted
                       AgentRegistry BEFORE the loop starts — never from planner output.
   ↓
AgentToolPolicy        AgentPlanValidator.validateCallTool() → toolPolicy.checkAllowed()
                       — default-deny, agent-specific allowedTools set, throws
                       AgentException.toolNotAllowed() on any miss. Checked BEFORE
                       McpToolClient is ever touched.
   ↓
AgentPlanValidator      Also independently confirms the tool exists in MCP's own
                        discovered-tool catalog (existsInRegistry check) — an agent cannot
                        even reference a tool name MCP has never advertised.
   ↓
MCP Gateway → ToolAuthorizationService.checkPermission()
                        Independent, second gate: (1) unconditionally denies
                        readWrite()==WRITE regardless of role — structural, not
                        allow-list-dependent; (2) separately checks caller's X-Roles
                        against requiredPermission().
   ↓
Tool execution          Only reached if both gates pass.
```

All five agents (`default` + 4 business agents) use this exact, unmodified chain. **No
agent-specific security exception exists anywhere in source** — `AgentToolPolicy` and
`ToolAuthorizationService` contain no `if (agentId.equals(...))` branch of any kind.

## 7. Cross-Agent Isolation

Five dedicated security test files, all real (unmocked) `AgentRegistry` +
`AgentToolPolicy` + `AgentPlanValidator`, mocked only at the LLM/MCP/RAG/Audit boundary:

| File | Test methods (incl. parameterized) |
|---|---|
| `AIAgentSecurityTest` (foundation/default) | 13 |
| `ErrorAnalyzerSecurityTest` | 9 |
| `KnowledgeAssistantSecurityTest` | 11 |
| `DatabaseAnalysisAgentSecurityTest` | 10 |
| `FraudDetectionAgentSecurityTest` | 10 |
| **Total** | **53** |

Direct cross-agent-escalation evidence:
- `AIAgentSecurityTest.agentCannotEscalatePermissions_sameToolAllowedForOneAgentIsDeniedForANarrowerAgent`
  — proves a tool allowed for one agent is denied for a narrower one.
- Each business agent's own `unknownAgentId_rejected...`/`disabledAgent_rejected` pair —
  identity spoofing and disabled-agent denial, per agent.
- Each business agent's own `...cannotUseAnotherAgentsToolOrAnyUnauthorizedTool`/
  `...cannotUseAnyToolOutsideItsReal*ToolAllowlist` — e.g. `fraud-detection-agent` is
  parameter-tested against `routing.lookup` and `database.statistics` specifically (tools
  granted to sibling agents), not just an invented name.
- `...modelCannotGrantItselfATool_evenIfMcpDiscoveryListsIt` — present in all four business
  agent files, proving MCP-catalog presence alone never grants access.
- `...agentIdentityIsFixedByTheCaller_planHasNoFieldCapableOfChangingIt` — present in all
  four, proving the planner LLM's JSON output has no field that can rename or reassign the
  executing agent.

## 8. Write Safety

| Agent | Writes allowed |
|---|---|
| `error-analyzer` | None |
| `knowledge-assistant` | None |
| `database-analysis-agent` | None |
| `fraud-detection-agent` | None |

Proven structurally, not assumed: §5 confirms zero `ToolReadWrite.WRITE` tools exist in the
MCP registry at all — so no allow-list configuration change, prompt change, or planner output
could grant a write capability to any agent even in error, because the capability does not
exist to be granted. `ToolAuthorizationService.checkPermission()` additionally denies any
WRITE tool unconditionally as a second, independent layer, confirmed live in Phase 4.5.3
(prompt-injection attempts to invoke `database.write` were refused by the planner itself,
before any call reached MCP Gateway; MCP Gateway's own Prometheus metrics after that run
showed only `audit.search` and `payment.lookup` were ever invoked).

## 9. RAG Architecture

```
Agent (deriveRagFilters — only paymentScheme, only from a real successful payment.lookup)
   ↓
RagServiceClient.query(ragQuery, filters, correlationId)
   ↓
RAG Service  (paymentx-rag-service, port 8096)
   ↓
Vector Service (paymentx-vector-service, port 8095)
   ↓
ai_document / ai_document_chunk / ai_document_embedding  (paymentx_ai Postgres DB)
```

**Important architectural finding, confirmed by direct query of the live vector database:
RAG retrieval is not partitioned by agent.** There is no "this document belongs to agent X"
concept anywhere in the schema or query path. Any agent's RAG query can retrieve any document
in the shared corpus; the only filter ever applied is the optional `paymentScheme` derived
from tool evidence. What differs per agent is only which documents happen to exist and be
semantically relevant to that agent's questions — not an enforced scope.

Real document-type inventory (`SELECT document_type, count(*) FROM ai_document GROUP BY
document_type`, current state):

| document_type | Count | Source |
|---|---|---|
| RISK_REFERENCE | 11 | Phase 4.5.1 fraud/risk corpus (`docs/ai/fraud-risk/`) |
| ERROR_CODE_REFERENCE | 4 | Phase 4.2.1 error-analyzer corpus (`docs/ai/error-analyzer/`) |
| OPERATIONAL_REFERENCE | 4 | Same corpus (audit, idempotency, kafka-failures, timeout-retry) |
| PAYMENT_LIFECYCLE | 1 | Same corpus |
| SCHEME_REFERENCE | 1 | Same corpus |
| `note` | 4 | Pre-existing Phase 3.5/3.6 test fixtures (`ph35-idempotency-doc`, `ph35-routing-doc`, `ph36-validation-doc`, `ph36-injection-test-doc` — the last is the known benign prompt-injection test document identified in Phase 4.5.2) — unrelated to any of the 4 business agents |

Total: 25 documents / 141 chunks / 141 embeddings, all embedded (chunk count == embedding
count, no gap).

## 10. Corpus Matrix

| Agent | Knowledge Corpus | Ingested | Retrieval Verified | Known Gaps |
|---|---|---|---|---|
| `error-analyzer` | 10 docs it authored (`docs/ai/error-analyzer/`, types ERROR_CODE_REFERENCE/OPERATIONAL_REFERENCE/PAYMENT_LIFECYCLE/SCHEME_REFERENCE) | Yes | Yes (own phase) | None verified this checkpoint |
| `knowledge-assistant` | **No dedicated corpus of its own.** Draws on the same shared pool as `error-analyzer` (10 docs) purely via semantic relevance, since RAG has no agent-scoping (§9) | N/A — shared | Yes (own phase) | Relies entirely on content authored for a different agent's phase |
| `database-analysis-agent` | **No dedicated corpus of its own.** Same shared pool | N/A — shared | Not separately verified — no DATABASE_ANALYSIS-typed documents exist | No schema-specific RAG content exists; whatever database-related answers it gives via RAG come from the general `OPERATIONAL_REFERENCE`/`SCHEME_REFERENCE` docs, not purpose-built material |
| `fraud-detection-agent` | 11 docs it authored (`docs/ai/fraud-risk/`, RISK_REFERENCE) | Yes (Phase 4.5.2) | Yes — 9/10 required test queries passed live; one narrow embedding-similarity retrieval gap on query 10, reported not silently fixed (Phase 4.5.2); reconfirmed working live in Phase 4.5.3 scenario 2 (5 real sources retrieved) | The one known query-10 retrieval gap (embedding-similarity threshold, fails safe to INSUFFICIENT_CONTEXT, not a hallucination) |

## 11. Prompt Architecture

Queried live against the running `prompt-service` (`GET /api/v1/prompts/{key}`), not assumed:

| Agent | Prompt Key | Active Version | Total Versions | Variable Contract |
|---|---|---|---|---|
| `error-analyzer` | PAYMENT_ERROR_ANALYSIS | v2, ACTIVE | 2 (v1 stale/never referenced by any agent — see §18 tech debt) | availableTools, executionHistory, userQuery, iteration, maxIterations |
| `knowledge-assistant` | PAYMENT_KNOWLEDGE_ASSISTANT | v1, ACTIVE | 1 | same 5-variable contract |
| `database-analysis-agent` | PAYMENT_DATABASE_ANALYSIS | v1, ACTIVE | 1 | same 5-variable contract |
| `fraud-detection-agent` | PAYMENT_FRAUD_RISK_ANALYSIS | v1, ACTIVE | 1 | same 5-variable contract |

No DRAFT version is currently active for any of the four agents. All four use the identical
AgentPlanner variable contract — no agent has a divergent prompt-rendering shape. Nothing was
activated, created, or modified to obtain this table.

## 12. Audit Architecture

`AgentAuditClient` (unchanged, shared by all agents) posts one event per execution to
`audit-service` with `eventType: "API_REQUEST"`, `sourceService: "agent-orchestrator"`,
`actorType: "AI_AGENT"`, `actorId` = userId or `"unknown"`, `correlationId`,
`reference` = the execution's requestId (**note**: there is no literal `executionId` or
`agentId` top-level audit field — `agentId` is nested inside the JSON `payload`, and the
execution identifier surfaces as `reference`, not a field named `executionId`; reporting this
precisely rather than assuming the field names in this task's own template exist verbatim).
MCP Gateway independently audits each tool call with `authorizationResult: CHECKED`. Verified
live in Phase 4.5.3 via direct query of `audit-service` — both layers recorded real entries
with no code inspection needed to trust it. **No agent-specific audit code exists** — the
same `AgentAuditClient` call site fires identically regardless of which of the five agents
ran.

## 13. Metrics

All from `AgentMetrics.java`, shared, agent-tagged (not agent-specific classes):

| Metric | Tags | Purpose | Verified? | Known gap |
|---|---|---|---|---|
| `agent_requests_total` | agent | Execution count | Live (4.5.3) | None |
| `agent_success_total` | agent | Successful completions | Live (4.5.3) | None |
| `agent_failure_total` | agent, status | Failures | Not exercised live this window | None |
| `agent_timeout_total` | agent | Timeouts | Not exercised | None |
| `agent_iterations_total` | — | Loop iteration count | Live (4.5.3, via iterations field) | None |
| `agent_tool_calls_total` | agent, tool | Per-tool invocation count | Live (4.5.3 — confirmed only allow-listed tools ever appear) | None |
| `agent_tool_denied_total` | agent, tool | Denied tool attempts | Not directly scraped this window (denials happened at planner level, before reaching a metric-emitting call site, per live scenario 4/6 responses) | Worth a future live check |
| `agent_rag_calls_total` | — | RAG invocation count | Live (4.5.3, ragUsed:true scenario) | None |
| `agent_llm_calls_total` | — | LLM invocation count | Not directly scraped | None |
| `agent_execution_latency` | agent | Latency histogram | Live (totalLatencyMs observed per response) | None |
| `agent_tool_latency` | tool | Per-tool latency histogram | Live (durationMs seen in audit payload) | None |
| `agent_context_size` | — | RAG context character count | Not directly scraped | None |
| `agent_insufficient_context_total` | agent | INSUFFICIENT_CONTEXT outcomes | Not directly scraped (though 4/7 live fraud scenarios returned INSUFFICIENT_CONTEXT at the answer level — whether that specific counter increments for a planner-level refusal like scenarios 4/6, vs. only a genuine evidence-insufficiency case, was not traced in source this checkpoint) | Flagging as unverified, not claiming pass |
| `mcp_tool_calls_total` (MCP Gateway) | tool | Per-tool call count, gateway side | Live (4.5.3 — `audit.search`:1, `payment.lookup`:4) | None |

No metric is agent-specific in implementation — all are generic counters/timers tagged with
`agentId`, reused identically by all five agents.

## 14. Test Matrix

**Explicit accounting note, per this task's own instruction:** the four "known values"
(299, 333, 394, 434) are **cumulative running totals across the same 5-module regression
suite** (`agent-orchestrator` + `prompt-service` + `mcp-gateway` + `rag-service` +
`llm-service`) at the point each agent was added — not four independent, summable counts.
Adding them would count the shared foundation tests four times over. The correct reading is
a monotonic sequence:

```
... → 299 (after Error Analyzer) → 333 (after Knowledge Assistant, +34)
    → 394 (after Database Analysis Agent, +61) → 434 (after Fraud Detection Agent, +40)
```

**434/434 is the current, single authoritative total** — confirmed by a fresh full run in
Phase 4.5.3 this same session, re-verified by source inspection this checkpoint (no file
changed since).

| Agent | Unit tests | Security tests | Integration/E2E tests | RAG tests | Live E2E | Status |
|---|---|---|---|---|---|---|
| `error-analyzer` | Registry/definition tests + prompt-seed tests | 9 (`ErrorAnalyzerSecurityTest`) | `AgentE2EIntegrationTest` scenario(s) | Shared RAG suite (43/43 in `rag-service`, unmodified) | PASS | Complete |
| `knowledge-assistant` | Registry/definition tests + prompt-seed tests | 11 (`KnowledgeAssistantSecurityTest`) | `AgentE2EIntegrationTest` scenario(s) | Shared RAG suite | PENDING | Deterministic complete, live pending |
| `database-analysis-agent` | Registry/definition tests + prompt-seed tests + `DatabaseStatisticsToolTest`/`ControlCenterClientTest` (mcp-gateway) | 10 (`DatabaseAnalysisAgentSecurityTest`) | `AgentE2EIntegrationTest` scenario(s) | Shared RAG suite | PENDING | Deterministic complete, live pending |
| `fraud-detection-agent` | `FraudDetectionAgentDefinitionTest` (6) + `PaymentFraudRiskAnalysisAgentPromptSeedTest` (10) | 10 (`FraudDetectionAgentSecurityTest`) | `AgentE2EIntegrationTest` new scenario | Corpus-specific retrieval verification (Phase 4.5.2, 9/10 queries) | PASS — 7 scenarios | Complete, only agent with both deterministic AND live security+RAG+E2E all PASS |

Module totals as of this checkpoint (unchanged since Phase 4.5.3's own run, re-confirmed by
reading current source, not re-run — re-running was avoided per this task's own
"do not start services unless required" and to avoid an unnecessary repeat of a resource-
intensive full build):
```
paymentx-agent-orchestrator: 208/208
paymentx-prompt-service:      71/71
paymentx-mcp-gateway:         96/96
paymentx-rag-service:         43/43
paymentx-llm-service:         16/16
TOTAL: 434/434
```

## 15. Live E2E Matrix

| Agent | Live E2E | Scenarios | Result | Blocker |
|---|---|---|---|---|
| `error-analyzer` | PASS | (Phase 4.2.3's own run) | PASS | None |
| `knowledge-assistant` | PENDING | Not run | N/A | Host-memory constraint, blocked 3 consecutive attempts (Phases 4.3–4.4.5); explicitly deferred by user instruction, not retried here |
| `database-analysis-agent` | PENDING | Not run | N/A | Same host-memory constraint |
| `fraud-detection-agent` | PASS | 7 (general risk analysis, pure-knowledge RAG question, not-found handling, write/fraud-confirmed injection, participant-lookup-limitation question, unauthorized-tool injection, reconciliation batch-scoping question) | PASS — all 7, zero forbidden phrases, zero unauthorized/write tool calls | None |

No pending live E2E was retried or attempted this checkpoint, per explicit instruction.

## 16. Agent Limitations

**Fraud Detection Agent** (Phase 4.5.3, reconfirmed by source, not re-invented):
- No ML fraud model, no fraud scoring engine, no anomaly detection engine, no historical
  fraud labels anywhere in PaymentX.
- No `participant.lookup` MCP tool exists — cannot verify blacklist membership or
  participant standing directly (confirmed live, scenario 5).
- No persisted IP, device, or geolocation data anywhere in the platform.
- No failed-login history — `AuthController.login()` throws an identical generic exception
  for every failure type, so no signal is derivable from auth failures.
- `SECURITY_EVENT` `EventType` value is defined but never emitted in production code (only in
  test fixtures) — dormant, not usable as a real signal.
- Newly confirmed this checkpoint (from live scenario 7, source-verified against
  `ReconciliationStatusTool`): `reconciliation.status` is **batch-scoped**
  (`arguments.get("batchId")`), with no MCP-tool-mediated way to discover a batch ID from a
  payment reference — so this agent's fourth allow-listed tool is, in practice, rarely
  directly usable for a specific-payment question unless a batch ID is already known.

**Database Analysis Agent:**
- The same `reconciliation.status` batch-scoping limitation applies identically — it is
  allow-listed for this agent too, and the tool's argument contract has not changed. **Not
  fixed** — confirmed by reading the current `ReconciliationStatusTool.java`, same
  `batchId`-only argument this checkpoint as in Phase 4.5.3.
- No dedicated RAG corpus (§10) — database-specific questions are answered from
  `database.statistics` tool evidence plus whatever generic OPERATIONAL_REFERENCE/
  SCHEME_REFERENCE content happens to be relevant, not purpose-built schema documentation.

**Knowledge Assistant:**
- Live E2E pending purely due to host-memory constraint across three consecutive prior
  attempts — **not an implementation failure**; 333/333 deterministic tests, including its
  own dedicated security suite (11 tests), pass.
- No dedicated RAG corpus (§10) — shares the error-analyzer corpus.

**Error Analyzer:**
- The `MCP_PAYMENT_SERVICE_API_KEY` auth-configuration gap (Phase 3.9, re-encountered and
  worked around in Phase 4.5.3's own live E2E) applies to this agent's `payment.lookup`/
  `payment.status` calls too, since it is a property of `PaymentServiceClient` shared by
  every agent, not something specific to Error Analyzer. It is an operational/environment
  configuration concern (unset env var on service start), not a code defect — confirmed by
  reading `McpGatewayProperties`/`PaymentServiceClient`, which already support setting the
  key correctly when an operator provides it.
- No confidence-scoring limitation beyond what its own prompt already documents (evidence-
  grounded only, never permitted to override tool evidence) — no further gap found this
  checkpoint.

## 17. Shared Infrastructure

| Component | Shared across all agents? | Agent-specific pieces |
|---|---|---|
| Agent Registry / `AgentDefinition` / `AgentRegistry` | Fully shared code | Per-agent YAML entry only |
| `AgentToolPolicy` | Fully shared code | Per-agent `allowedTools` set (data, not code) |
| `AgentPlanValidator` | Fully shared, no branching by agent | None |
| `AgentPlanner` | Fully shared | None (prompt content differs, code path identical) |
| `AgentOrchestratorService` | Fully shared bounded loop | None |
| MCP Gateway + `ToolAuthorizationService` | Fully shared | One new tool (`database.statistics`, Phase 4.4) added to the shared catalog, not agent-specific code |
| RAG Service / Vector Service | Fully shared, no agent-scoping (§9) | Corpus content only (data, not code) |
| Prompt Service | Fully shared mechanism | One new prompt-key row per agent (data, not code) |
| LLM Service | Fully shared | None |
| Audit | Fully shared client and endpoint | None |
| Metrics | Fully shared class, per-agent tag | None |

**Adding another agent requires:** one YAML entry (`agents.definitions`), one prompt-key row
seeded ACTIVE, and — only if genuinely new evidence is needed — a new MCP tool following the
exact `database.statistics` precedent (new `Tool` class + permission constant +, if calling
an existing service through a new path, a new client). It does **not** require: a new
microservice, a new database, a new agent framework, or a new MCP Gateway. This conclusion is
based on the fact that all four business agents to date fit this exact pattern with zero
framework-level change beyond the one narrow enum addition in Phase 4.5.3.

## 18. Technical Debt

Only verified items — nothing speculative:

1. **Knowledge Assistant / Database Analysis Agent live E2E pending** — host-memory
   constraint, three consecutive blocked attempts, explicitly deferred by user instruction.
2. **`MCP_PAYMENT_SERVICE_API_KEY` unset-by-default gap** (Phase 3.9) — affects every agent
   equally; requires an operator to seed a Redis API key and set the env var before
   `payment.lookup`/`payment.status` calls succeed through the API Gateway. Encountered fresh
   in Phase 4.5.3's own live E2E, worked around per-run, not a permanent fix (by design — no
   static key is meant to be baked in).
3. **`reconciliation.status` batch-scoping** — no MCP-tool-mediated path from a payment
   reference to a reconciliation batch ID; affects both `database-analysis-agent` and
   `fraud-detection-agent`, which both hold this tool in their allow-list but can rarely use
   it for a specific-payment question without an already-known batch ID.
4. **No `participant.lookup` MCP tool** — affects Fraud Detection Agent most, but is a
   platform-wide gap (confirmed absent since Phase 3.7).
5. **No dedicated RAG corpus for Knowledge Assistant or Database Analysis Agent** — both rely
   entirely on the error-analyzer corpus via unscoped semantic retrieval; functional today,
   but a content gap if either agent's question set diverges further from error-analysis
   topics.
6. **Stale `PAYMENT_ERROR_ANALYSIS` v1 / template-level description text** — the prompt
   template's own `description` field still reads "development seed, DRAFT until explicitly
   activated," which no longer matches reality (v2 is the real ACTIVE version, seeded and
   used since Phase 4.2.2); cosmetic, does not affect behavior since the DTO returns
   `activeVersion` correctly, but is a stale/misleading label worth a future cleanup.
7. **`agent_tool_denied_total` / `agent_insufficient_context_total` semantics not fully
   traced live this checkpoint** — not a defect, just an unverified-this-session gap, flagged
   rather than assumed passing.

Nothing in this list was invented — each item traces to a specific file/behavior read or
observed above.

## 19. Production Readiness

| Agent | Classification | Basis |
|---|---|---|
| `error-analyzer` | **A — Live verified** | Live E2E PASS (Phase 4.2.3), deterministic + security tests pass |
| `knowledge-assistant` | **B — Implementation verified, live validation pending** | 333/333 + dedicated security suite pass; live E2E blocked by environment, not code |
| `database-analysis-agent` | **B — Implementation verified, live validation pending** | 394/394 + dedicated security suite pass; live E2E blocked by environment, not code |
| `fraud-detection-agent` | **A — Live verified** | 434/434, dedicated security suite, RAG verification, and 7-scenario live E2E all pass |

**Overall Agent Platform: A — Live verified at the foundation and security level; 2 of 4
business agents additionally live-verified end-to-end, 2 pending purely on host resources.**

## 20. Next-Agent Readiness

| Layer | Ready? | Evidence |
|---|---|---|
| Agent Foundation | Yes | Unchanged since Phase 4.1; 4 successive agents added with zero framework change |
| Security | Yes | Same two-layer chain, zero agent-specific exceptions, 53 cross-agent isolation tests |
| MCP | Yes | 6 tools, all READ_ONLY, adding a 7th follows an established precedent (`database.statistics`) |
| RAG | Yes, with a caveat | Mechanism proven end-to-end; a genuinely new agent domain will need its own corpus authored and ingested (no agent gets one "for free") |
| Prompt | Yes | Established seed-ACTIVE-immediately pattern (lesson from Phase 4.2.3), consistent 5-variable contract |
| LLM | Yes | Unchanged, shared, no per-agent config |
| Audit | Yes | Unchanged, shared, verified live |
| Metrics | Yes | Unchanged, shared, agent-tagged |
| Testing framework | Yes | Registry/definition + prompt-seed + security-suite + E2E-scenario pattern is now proven 4 times over |

**Recommendation: the platform is ready for another business agent.** The two pending live
E2Es are an environment constraint on this host, not a platform readiness gap — they do not
block starting new agent work, since the security/foundation layer they'd also depend on is
independently and repeatedly proven (most recently and most thoroughly by Fraud Detection
Agent's own live run).

## 21. Candidate Comparison

| Candidate | Foundation readiness | Data readiness | MCP readiness | RAG readiness | Security complexity | Implementation complexity | New capability | Priority |
|---|---|---|---|---|---|---|---|---|
| **Reconciliation Agent** | Ready | Strong — `ReconciliationStatus` 10-value taxonomy already modeled, `reconciliation.status` tool already exists and is already allow-listed to 2 agents | Ready — reuses `reconciliation.status`; the batch-scoping gap (§18) is the one real thing to design around, likely needing a small new read-only lookup (e.g. batch-by-payment) | Would benefit from its own corpus, similar effort to fraud-risk's Phase 4.5.1 | Low — read-only, same pattern | Low–Medium — mostly the batch-ID-discovery gap to solve cleanly | RECONCILIATION_ANALYSIS already exists as a capability; no new enum needed | **High** — closes the one concrete tool-usability gap surfaced twice this checkpoint |
| **Log Analysis Agent** | Ready | Weak — no centralized log-aggregation store surfaced in any prior discovery; would likely need a new MCP tool over existing logging infra, unverified feasibility | Unready — no log-query tool exists today | Unready — no log corpus exists | Medium — depends entirely on what log source is chosen | Medium–High — likely needs real new tool + client, similar in scope to Phase 4.4's `database.statistics` work | LOG_ANALYSIS capability already reserved in the enum, unused since Phase 4.1 | Medium |
| **Incident RCA** | Ready | Overlaps heavily with Error Analyzer's existing evidence set; unclear net-new value without a distinct incident/postmortem data source | Ready (reuses existing 5-tool set) | Would reuse error-analyzer's corpus largely as-is | Low | Low — closest to a "prompt + config only" agent of any candidate | No new capability value needed | Medium — risk of overlapping Error Analyzer's scope rather than adding distinct value |
| **Payment Routing Optimizer** | Ready | Weak — `routing.lookup` is read-only status, not optimization data; no historical routing-performance dataset surfaced in any prior discovery | Partial — `routing.lookup` exists but an "optimizer" implies a write/recommendation capability that would need careful scoping to stay read-only | Would need new corpus on routing rules/scheme selection | Medium-High — "optimizer" framing risks scope creep past read-only advisory | Medium-High | ROUTING_ANALYSIS already exists as a capability | Low — highest risk of scope mismatch with the platform's read-only mandate |
| **Notification Agent** | Ready | Weak — no notification-history MCP tool exists; `paymentx-notification-service` was never discovered as having a read-only lookup tool built | Unready — no MCP tool | Unready — no corpus | Low if kept strictly read-only/informational | Medium — needs a new tool (notification-status lookup) | No dedicated capability value exists yet (would need a new one, same additive pattern as RISK_ANALYSIS) | Low |
| **Report Generation Agent** | Ready | Ambiguous — "report generation" implies producing new artifacts, which sits oddly against the platform's strict read-only/no-write agent mandate established since Phase 3.8 | Unclear what MCP surface it would even call beyond existing read tools | Could reuse existing corpus | Medium — needs careful definition of what a "read-only report" agent outputs (text summary vs. an artifact) to avoid implying a write capability | Medium | No dedicated capability value exists yet | Low — needs scope clarification before implementation readiness can even be assessed |

## 22. Final Classification

**A — AGENT PLATFORM READY FOR NEXT AGENT.**

Basis: the Agent Foundation, security chain, MCP Gateway, RAG mechanism, Prompt Service
integration, audit, and metrics are all unchanged, fully shared, and have now been exercised
by four independent business-agent implementations with zero framework modification beyond
one additive enum value. Cross-agent isolation is proven by 53 dedicated test methods across
5 files. Write-safety is structural (zero WRITE tools exist platform-wide), not a per-agent
promise. The two pending live E2Es are a host-resource constraint, not a platform defect —
Error Analyzer and Fraud Detection Agent's own live runs independently prove the same shared
path both pending agents also depend on. Recommended next candidate, based on §21: the
**Reconciliation Agent**, since it directly resolves the one concrete, twice-surfaced
tool-usability gap (`reconciliation.status` batch-scoping) rather than opening a new,
unverified data-readiness question.
