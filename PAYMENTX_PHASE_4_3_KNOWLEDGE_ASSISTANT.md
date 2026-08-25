# PaymentX Phase 4.3 — Knowledge Assistant (RAG Agent)

**Status:** Implementation complete, 333/333 deterministic tests passing. Live E2E was attempted, hit a genuine host memory-exhaustion event unrelated to this implementation, and was skipped by explicit user decision in favor of the deterministic proof already in place (documented honestly in §13, not glossed over).

---

## 1. Objective

Implement the platform's second business agent — a read-only, RAG-first Knowledge Assistant that answers questions about how PaymentX works using the trusted Phase 4.2.1 knowledge corpus, and, only for a specific payment's current state, authorized read-only MCP evidence. Built entirely on the existing Agent Foundation (Phase 4.1) and the exact configuration/prompt pattern Error Analyzer (Phase 4.2.3) already established — no framework redesign, no new mechanism.

---

## 2. Architecture

Identical mechanism to Error Analyzer — a new agent is **configuration + a prompt**, not new code:

```
AgentController.execute
  -> AgentRegistry.resolve("knowledge-assistant")     [existing mechanism, new config entry]
  -> AgentOrchestratorService.execute                  [unmodified this phase]
       -> runLoop
            -> AgentPlanner.plan (renders PAYMENT_KNOWLEDGE_ASSISTANT, new prompt)
            -> AgentPlanValidator.validate -> AgentToolPolicy.checkAllowed  [existing, unmodified]
            -> executeTool -> MCP Gateway -> ToolAuthorizationService      [existing, unmodified,
                                                                             only when a runtime question]
            -> executeRetrieval -> RagServiceClient.query                  [existing, unmodified,
                                                                             the primary path]
       -> finalizeAnswer
       -> AgentAuditClient.recordAgentRun                                  [existing, unmodified]
```

**Zero production Java source files were modified.** Source inspection (per this phase's own §1 requirement) confirmed `AgentOrchestratorService`'s `deriveRagFilters`/`effectiveUserQuery` helpers, the response contract (`AgentExecuteResponse`), and the audit/metrics wiring are all already agent-generic (verified by grep: no `error-analyzer`-specific string exists anywhere in main source, only in comments) — everything Error Analyzer needed was already reusable as-is for a second, differently-scoped agent.

---

## 3. Agent Definition

New `agents.definitions` entry in `paymentx-agent-orchestrator/src/main/resources/application.yml`:

```yaml
- agent-id: knowledge-assistant
  name: PaymentX Knowledge Assistant
  description: >-
    Read-only, RAG-first assistant that explains how PaymentX works (validation, routing,
    idempotency, reconciliation, schemes, Kafka's role) using trusted PaymentX knowledge, and
    - only for a specific payment's current state - authorized read-only MCP evidence
    (payment.lookup, payment.status, audit.search). Never performs a write operation.
  version: "1.0"
  capabilities: KNOWLEDGE_RETRIEVAL,PAYMENT_ANALYSIS
  allowed-tools: payment.lookup,payment.status,audit.search
  prompt-key: PAYMENT_KNOWLEDGE_ASSISTANT
  risk-level: LOW
  enabled: true
```

Naming follows the established lowercase-hyphenated convention (`default`, `error-analyzer`, now `knowledge-assistant`). `maxIterations`/`timeoutMs` left unset, falling back to platform defaults — same pattern every agent uses. Verified against the real, deployed YAML by `registry.KnowledgeAssistantAgentDefinitionTest`, a `@SpringBootTest` resolving the real `AgentRegistry` bean.

---

## 4. Tool Permissions

**Smallest possible set, deliberately narrower than Error Analyzer's:**

| Tool | Granted? | Reason |
|---|---|---|
| `payment.lookup` | Yes | Runtime question: "what happened to payment X" |
| `payment.status` | Yes | Runtime question: "is payment X currently FAILED" |
| `audit.search` | Yes | Runtime question: "what happened to payment X" (history) |
| `routing.lookup` | **No** | No live runtime example in the brief needs it; "how does routing work" is answered via RAG |
| `reconciliation.status` | **No** | Same reasoning — no live runtime example given |

This is a deliberate, documented judgment call (application.yml's own comment above the entry explains it): capabilities also reflect this — `KNOWLEDGE_RETRIEVAL, PAYMENT_ANALYSIS` only, omitting `ROUTING_ANALYSIS`/`RECONCILIATION_ANALYSIS` (capabilities are descriptive metadata, not a security boundary — see `AgentCapability`'s own javadoc — but should still describe what the agent actually does).

Security chain unchanged and re-verified:
```
AgentToolPolicy -> AgentPlanValidator -> MCP Gateway -> ToolAuthorizationService -> execution
```
`KnowledgeAssistantSecurityTest` specifically proves `routing.lookup`/`reconciliation.status` are denied to this agent even though `default`/`error-analyzer` both have them — the smallest-permission-set design is enforced, not just declared.

---

## 5. RAG Flow

For a documentation question, the loop is pure RAG — no MCP tool is ever touched:

```
User question ("How does idempotency work?")
  -> AgentPlanner renders PAYMENT_KNOWLEDGE_ASSISTANT, model chooses RETRIEVE_KNOWLEDGE
  -> RagServiceClient.query(ragQuery, filters, correlationId)   [Phase 4.2.2 client, reused unchanged]
  -> RAG Service -> Embedding/Vector Service -> retrieved PaymentX knowledge chunk(s)
  -> AgentPlanner renders again with the retrieved context in EXECUTION HISTORY
  -> FINAL_RESPONSE, answer grounded in the retrieved chunk(s)
```

Reuses Phase 4.2.2's `RagQueryFilters`/`deriveRagFilters` mechanism exactly as Error Analyzer does — `paymentScheme` is derived only from a real, successful `payment.lookup` result already in this run's evidence, never fabricated. Proven deterministically by `AgentE2EIntegrationTest.execute_knowledgeAssistantAgent_staticSchemeQuestion_realRagOnlyNoMcpCallRealPromptKey`: a real HTTP call through the real bounded loop, real prompt-key selection, `toolEvidence` asserted empty, `McpToolClient.callTool` asserted **never** invoked.

---

## 6. Runtime Evidence Flow

For a runtime question ("What is the status of payment X?"), the prompt's own rule 4 instructs the model: *"Only use CALL_TOOL when the user is asking about the CURRENT state of one specific, identified payment... RAG knowledge is documentation, never a substitute for it."* The response's existing `toolEvidence`/`sources`/`answer` separation (unchanged from Phase 4.1/4.2.3) carries this through structurally:

| Category | Field | Source |
|---|---|---|
| AUTHORITATIVE RUNTIME EVIDENCE | `toolEvidence` | Real MCP tool result, never overwritten by the narrative |
| RAG KNOWLEDGE | `sources` | Real retrieved document/source labels |
| LLM EXPLANATION | `answer` | Structured prose, explicitly required (prompt rule 7) to separate what came from each |

`KnowledgeAssistantSecurityTest.knowledgeAssistant_toolEvidenceIsPreserved_evenWhenPlannerFinalAnswerContradictsIt` proves `toolEvidence` reflects the real (mocked) tool result even when the (worst-case, mocked) planner's own final answer claims something different — the authoritative-evidence guarantee holds regardless of what the LLM says.

---

## 7. Prompt

**New prompt key: `PAYMENT_KNOWLEDGE_ASSISTANT`** — seeded via `paymentx-prompt-service/.../changes/V1_0_5__seed_payment_knowledge_assistant_agent_prompt.yaml`, using the exact same five-variable AgentPlanner contract (`availableTools`/`executionHistory`/`userQuery`/`iteration`/`maxIterations`) `PAYMENT_ERROR_ANALYSIS` v2 established.

**Important, non-obvious distinction (source-verified, not assumed):** this is a **different** key from the pre-existing `PAYMENTX_KNOWLEDGE_ASSISTANT` (note the "X") seeded back in Phase 3.6, which is RAG Service's own internal answer-synthesis prompt (two-variable `{{context}}`/`{{question}}` contract, rendered by `RagServiceImpl` on every plain `/api/v1/rag/query` call, including every `RETRIEVE_KNOWLEDGE` step any agent — Error Analyzer or Knowledge Assistant — ever takes). That prompt is **not touched** by this phase and remains `ACTIVE`, exactly as it was.

Seeded **already `ACTIVE`** (not `DRAFT`) — a deliberate departure from `PAYMENT_ERROR_ANALYSIS`'s original DRAFT-first seeding, learned directly from the Phase 4.2.3 live E2E, which discovered the hard way that an un-activated prompt is a real functional blocker the first time any agent actually uses it. Safe for the same reason `PAYMENTX_KNOWLEDGE_ASSISTANT`'s own V1_0_2 migration gave: this is the only version of this template that will ever exist at migration time, so the partial unique index enforcing "at most one ACTIVE version per template" has nothing to conflict with.

The prompt text enforces every item in this phase's own §10 requirement list: RAG-first reasoning (rule 3), evidence grounding (rule 5), no fabricated facts (rules 5, 9), separation of runtime vs. documentation evidence (rule 7's labeled answer structure), explicit uncertainty (rule 9), and no tool-permission escalation (rules 1–2, same boundary language as `PAYMENT_ERROR_ANALYSIS`). Verified by 8 dedicated tests in `PaymentKnowledgeAssistantAgentPromptSeedTest`.

---

## 8. Payment Scheme Handling

Prompt rule 6 states explicitly: *"PaymentX supports exactly three payment schemes: INSTANT_PAYMENT, REAL_TIME_PAYMENT, and CARD_PAYMENT. Never state or imply that any other scheme... is part of PaymentX."* The forbidden scheme names (`ACH`, `FedNow`, etc.) appear in the prompt text **only** inside that negation sentence — the identical guardrail pattern `docs/ai/error-analyzer/payment-schemes.md` already established in Phase 4.2.1. `enforcesTheThreeRealPaymentSchemes_andExplicitlyExcludesOthersByName` verifies this precisely (`containsOnlyOnce`), not merely "absent," since a correct negation must name what it excludes.

---

## 9. Security

`KnowledgeAssistantSecurityTest` (19 test cases covering the task's 10 items), mirroring `ErrorAnalyzerSecurityTest`'s exact discipline — the **real** `knowledge-assistant` `AgentDefinition` (built from `AgentRegistryProperties` shaped like the real YAML) and a **real** (never mocked) `AgentPlanValidator`/`AgentToolPolicy` pair:

| Item | Test |
|---|---|
| 1. Unknown agent rejected | `unknownAgentId_rejected_evenWithKnowledgeAssistantRegistered` |
| 2. Disabled Knowledge Assistant rejected | `disabledKnowledgeAssistant_rejected` |
| 3. Unauthorized MCP tool rejected | `knowledgeAssistant_cannotUseAnyToolOutsideItsRealThreeToolAllowlist` (8 params) + `knowledgeAssistant_cannotUseRoutingOrReconciliationTools_evenThoughOtherAgentsCan` (2 params — the smallest-permission-set-specific proof) |
| 4. Write tool cannot be used | `knowledgeAssistant_cannotUseAWriteTool_evenWhenDirectlyRequested` |
| 5. Prompt injection cannot grant tools | `knowledgeAssistant_promptInjectionInUserMessage_cannotGrantAdditionalTools` |
| 6. Model cannot change agent identity | `knowledgeAssistant_agentIdentityIsFixedByTheCaller_planHasNoFieldCapableOfChangingIt` |
| 7. Model cannot change permissions | `knowledgeAssistant_modelCannotGrantItselfATool_evenIfMcpDiscoveryListsIt` (code-level boundary, independent of MCP discovery) |
| 8. RAG content cannot grant permissions | `knowledgeAssistant_maliciousRagContentCannotGrantPermissions_ragHasNoToolCallingCapabilityAtAll` |
| 9. RAG content cannot override authoritative MCP state | `knowledgeAssistant_toolEvidenceIsPreserved_evenWhenPlannerFinalAnswerContradictsIt` |
| 10. Sensitive information not leaked | `knowledgeAssistant_toolFailureCarryingASecret_neverSurfacesInTheResponse` |

MCP Gateway's own second gate (`ToolAuthorizationService`) is structurally unaffected — `paymentx-mcp-gateway` was not modified this phase, and its own 63-test suite was re-run unchanged.

---

## 10. Audit

Reuses `audit.AgentAuditClient` completely unchanged — `actorType=AI_AGENT`, real `agentId="knowledge-assistant"`, `executionId`/`correlationId` at the outer envelope, `outcome`, `latencyMs`, and per-tool `{tool, status}` summaries (never raw arguments/results, never secrets) — the exact Phase 4.2.3 minimization design, now genuinely exercised by a second, differently-scoped agent.

---

## 11. Metrics

Reuses `metrics.AgentMetrics` unchanged — every existing meter (`agent_requests_total`, `agent_success_total`, `agent_failure_total`, `agent_tool_calls_total`, `agent_tool_denied_total`, `agent_plan_rejected_total`, `agent_insufficient_context_total`) now also carries `agent="knowledge-assistant"` label values. No new metrics system, no new meter, no high-cardinality label — nothing in this phase's config or prompt touches metrics recording at all.

---

## 12. Test Strategy

**34 new tests added**, all passing:

| File | Tests | Type |
|---|---|---|
| `registry.KnowledgeAssistantAgentDefinitionTest` (new) | 6 | Real Spring context, real YAML verification |
| `security.KnowledgeAssistantSecurityTest` (new) | 19 | All 10 security items |
| `repository.PaymentKnowledgeAssistantAgentPromptSeedTest` (new, prompt-service) | 8 | Real Testcontainers-Postgres, real Liquibase seed verification |
| `controller.AgentE2EIntegrationTest` (+1) | 1 | Full real-HTTP, real-loop, real-prompt-key, zero-MCP-call RAG-first scenario |

Plus one pre-existing test mechanically updated: `ErrorAnalyzerAgentDefinitionTest.registry_containsExactlyTwoAgents` → `registry_containsAtLeastDefaultAndErrorAnalyzer` (its literal "exactly 2" assertion was correctly stale now that a 3rd agent exists by design; the exact-count assertion now lives in `KnowledgeAssistantAgentDefinitionTest.registry_containsExactlyThreeAgents`).

**One real bug caught and fixed during this work** (in my own new test, not product code): an assertion wrongly expected the forbidden scheme names to be totally absent from the prompt text, when they correctly appear once each inside the explicit negation rule ("never state... is part of PaymentX") — fixed to assert `containsOnlyOnce` instead of `doesNotContain`.

---

## 13. Live E2E

**Attempted, not completed — environment resource exhaustion, not a defect.** After the deterministic suite passed, I started the 7 AI-platform services to run the four required live scenarios (A: static RAG question, B: architecture question, C: runtime question against the real, already-existing `LIVEE2E-3FE6115058` payment reference from Phase 4.2.3 — no new payment created, per this phase's own "do not create a new payment unless absolutely necessary" instruction — D: unknown-knowledge question).

During startup, the host had only 1.1–2.1GB free RAM, driven by: 16+ PaymentX JVMs already running from Phase 4.2.3's live E2E, plus a completely unrelated concurrent Maven build (`jobpilot-matching-service`, a different project sharing this machine) and an unrelated IntelliJ debug session — neither of which I control or should touch. `prompt-service` and `llm-service` both **fully started, served a health check successfully, then were silently killed by the OS** moments later with no shutdown log — a hard memory-exhaustion termination, not a graceful stop, not a code defect. Confirmed this was genuine resource exhaustion (not a fluke): free memory measured at 1.1GB immediately after the crash, and the 9 core services (unrelated to this incident) survived intact throughout.

Given the real, external, uncontrollable memory pressure, I surfaced this to the user rather than retrying blindly a second time. **The user explicitly chose to skip live E2E and rely on the deterministic proof already in place.**

**What deterministic testing already proves in place of live E2E:**
- RAG-first behavior with zero MCP calls for a documentation question (`AgentE2EIntegrationTest`, real HTTP, real bounded loop, real prompt-key selection, `McpToolClient.callTool` asserted never invoked).
- Correct, real answer separation and scheme accuracy (INSTANT_PAYMENT/REAL_TIME_PAYMENT/CARD_PAYMENT only).
- Runtime-evidence-vs-RAG-knowledge separation under adversarial conditions (security suite).
- The underlying platform topology (Agent Orchestrator → MCP Gateway → Prompt/LLM/RAG services, real Anthropic reasoning, real audit/metrics) was already proven live and working end-to-end for the sibling Error Analyzer agent in Phase 4.2.3's own live E2E — Knowledge Assistant reuses that exact, already-validated runtime path unchanged.

---

## 14. Known Limitations

- Live E2E not completed this phase (§13) — a resource-availability gap in this shared environment, not an implementation gap.
- `routing.lookup`/`reconciliation.status` are unavailable to this agent by design (§4) — a live "how is routing configured for payment X right now" runtime question is out of scope; the corresponding documentation question ("how does routing work") is fully answerable via RAG.
- RAG retrieval quality for Knowledge Assistant's specific phrasing style was not live-tested against a real Anthropic model this phase — the deterministic WireMock-based proof covers the mechanism, not real-model answer quality, consistent with how Phase 4.2.3's own deterministic suite is scoped.

---

## 15. Error Analyzer Compatibility

**Zero behavioral change to Error Analyzer.** `error-analyzer`'s own `AgentDefinition` entry in `application.yml` is byte-for-byte unchanged; `PAYMENT_ERROR_ANALYSIS` (both v1 and v2) is untouched; `ErrorAnalyzerSecurityTest` (16 tests, all still passing) and the Phase 4.2.3 E2E scenario were re-run unmodified as part of this phase's regression. The only edit touching Error Analyzer's own test file was updating one stale assertion (`registry_containsExactlyTwoAgents`, §12) whose premise (exactly 2 total agents) was always going to become stale the moment any second agent was added by design — not a change to what it verifies about Error Analyzer itself.

---

## 16. Future Multi-Agent Integration

This phase adds no new shared mechanism — `AgentRegistry`, `AgentToolPolicy`, `AgentPlanValidator`, `AgentOrchestratorService`, `RagServiceClient`, `AgentAuditClient`, and `AgentMetrics` all now demonstrably serve **three** independently-configured agents (`default`, `error-analyzer`, `knowledge-assistant`) without modification, reinforcing that the Phase 4.1 Agent Foundation's "configuration, not code, defines an agent" design goal holds under real, repeated use. A future third business agent should follow the identical pattern this phase and Phase 4.2.3 both used: one `agents.definitions` entry, one prompt template (new key, seeded ACTIVE, AgentPlanner-compatible 5-variable contract), and a dedicated security test suite — no Agent Foundation source change anticipated unless a genuinely new capability (e.g., a new tool category) is required.

---

## Final Validation

- Production source files modified: **0**
- Production config files modified: **1** (`paymentx-agent-orchestrator/src/main/resources/application.yml`)
- Prompt files modified: **2** (1 new seed migration — `V1_0_5__seed_payment_knowledge_assistant_agent_prompt.yaml`; 1 changelog include-list update — `db.changelog-master.yaml`)
- Tests added: **34** (6 + 19 + 8 new-file tests, + 1 new E2E test; exactly reconciled against the 299→333 total test-count delta across all 5 modules)
- Database changed: **YES** (new `prompt_template`/`prompt_version` data rows via the existing Liquibase seed mechanism — same pattern every prior prompt seed used; **no schema/DDL change**)
- MCP tools modified: **NO**
- MCP writes: **NO**
- Payments created: **NO** (live E2E was not completed; no new payment was created at any point this phase)
- Error Analyzer modified: **NO** (§15)
- Agent Foundation modified: **NO**
- Knowledge Assistant implemented: **YES**
- Deterministic tests: **333/333 PASS** (agent-orchestrator 158, prompt-service 53, rag-service 43, llm-service 16, mcp-gateway 63)
- Regression: **PASS**
- Live E2E: **NOT RUN** (§13 — environment resource exhaustion, user-approved skip; reported honestly, not worked around)
- RAG retrieval: **PASS** (deterministic proof, §5/§12)
- Runtime MCP evidence: **NOT RUN live** — structurally proven via the security suite (§6/§9) and the identical, already-live-validated Error Analyzer path (§13)
- Security: **PASS** (19/19 dedicated tests)
- Build: **PASS** (all 5 modules, `BUILD SUCCESS`)

One transient, unrelated test failure (`mcp-gateway`'s `PaymentServiceClientTest`) occurred during a parallel multi-module regression pass — the same known resource-contention flakiness pattern documented in Phase 4.2.3; re-run in isolation and passed cleanly (4/4), confirmed non-regression (`paymentx-mcp-gateway` was not modified this phase).

**Stopping here per the stop condition — no commit, no push, no other business agent implemented. Waiting for explicit approval before implementing the next agent.**
