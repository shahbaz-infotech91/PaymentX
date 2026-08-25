# PaymentX Phase 4.1 — Source Change Review

**Status:** READ-ONLY REVIEW. No file was modified while producing this document. Nothing was committed or pushed.

**Method:** every file below was re-read from disk during this review (not recalled from memory of writing it), and the two claims most load-bearing for the security section — that MCP Gateway is genuinely untouched, and that no tool anywhere registers as `WRITE` — were independently re-verified via `git status` and a fresh grep, not assumed.

---

## 1. File Change Inventory

### 1.1 New production source files (5)

| File | Why | What | Phase 3 behavior changed? |
|---|---|---|---|
| `registry/AgentDefinition.java` | Section 2 of the task requires a trusted, immutable per-agent description | Java 21 record: `agentId, name, description, version, capabilities, allowedTools, promptKey, riskLevel, enabled, maxIterations, timeoutMs`. Compact constructor rejects blank `agentId`/`name`/`promptKey`, defensively copies `capabilities`/`allowedTools` to immutable sets, defaults `riskLevel` to `LOW`, rejects non-positive `maxIterations`/`timeoutMs`. | No — new type, nothing referenced it before |
| `registry/AgentCapability.java` | Section 6 requires a type-safe capability model | 6-value enum: `PAYMENT_ANALYSIS, ROUTING_ANALYSIS, RECONCILIATION_ANALYSIS, KNOWLEDGE_RETRIEVAL, LOG_ANALYSIS, DATABASE_ANALYSIS` | No |
| `registry/AgentRiskLevel.java` | `AgentDefinition.riskLevel` needs a type | 4-value enum: `LOW, MEDIUM, HIGH, CRITICAL` | No |
| `registry/AgentRegistryProperties.java` | YAML-binding shape for the `agents:` block | `@ConfigurationProperties(prefix="agents")` mutable bean: `defaultAgentId` (default `"default"`), `definitions: List<Entry>`; nested `Entry` mirrors every `AgentDefinition` field as plain `String`/`List<String>`/primitives for Spring's relaxed binding | No |
| `registry/AgentRegistry.java` | Section 3 requires register/retrieve/list/enabled/capabilities/tools + duplicate prevention | `@Component`, builds an immutable `Map<String,AgentDefinition>` at construction from `AgentRegistryProperties`, fails fast (`IllegalStateException`) on empty config, duplicate `agentId`, or an unresolvable `defaultAgentId`. Methods: `resolve(String)` (throws `AgentException.agentNotFound`/`agentDisabled`), `find(String)` (`Optional`), `isEnabled(String)`, `capabilitiesOf(String)`, `allowedToolsOf(String)`, `list()`, `getDefaultAgentId()` | No — new type |

### 1.2 Modified production source files (11)

| File | Why changed | What changed (exact) | Phase 3 behavior changed? |
|---|---|---|---|
| `controller/AgentController.java` | Section 16 — controller must resolve identity before execution | Added `private final AgentRegistry agentRegistry` field (Lombok `@RequiredArgsConstructor` picks it up automatically). `execute(...)` now calls `agentRegistry.resolve(request.agentId())` first and passes the resulting `AgentDefinition` into `agentOrchestratorService.execute(request, definition, correlationId, traceId)`. `health()` unchanged. | **No, for existing callers** — an absent `agentId` resolves to the same default agent/behavior as before. Method signature of `execute` internals changed; the HTTP contract did not. |
| `dto/AgentExecuteRequest.java` | Section 17 requires an optional `agentId` without breaking existing requests | Record gained a 4th field, `agentId` (no validation annotation — genuinely optional). A new 3-arg constructor `AgentExecuteRequest(conversationId, userId, userQuery)` delegates to the canonical 4-arg one with `agentId=null`. | No — the 3-arg constructor and JSON deserialization of a body without `agentId` both produce identical behavior to pre-Phase-4.1 |
| `exception/AgentErrorCodes.java` | New failure modes need normalized codes | Added `AGENT_NOT_FOUND`, `AGENT_DISABLED` constants | No — additive only |
| `exception/AgentException.java` | Factories for the two new codes | Added `agentNotFound(message)` → HTTP 404, not retryable; `agentDisabled(message)` → HTTP 403, not retryable | No — additive only |
| `state/AgentExecution.java` | Section 5 — execution context needs an explicit agent identity field | Added `private final String agentId`. New 6-arg constructor; the original 5-arg constructor is now a delegating overload (`agentId=null`) | No — the 5-arg constructor still exists and behaves identically |
| `policy/AgentToolPolicy.java` | Section 7 — tool scoping must become per-agent | Removed the `private static final Set<String> ALLOWED_TOOLS` class-wide constant. `isAllowed`/`checkAllowed` now take `(AgentDefinition definition, String toolName)` and check `definition.allowedTools().contains(toolName)` | **Behaviorally no** for the default agent (its configured `allowedTools` is byte-for-byte the same 5 tools) — but the **method signatures changed**, a real, deliberate breaking change to internal callers (all updated in this same phase) |
| `planning/AgentPlanValidator.java` | Must pass the resolved definition through to the policy check | `validate(plan, discoveredTools)` → `validate(plan, discoveredTools, AgentDefinition definition)`; `validateCallTool` likewise gained a `definition` parameter, passed straight to `toolPolicy.checkAllowed(definition, plan.tool())` | Behaviorally no for default agent; signature changed |
| `planning/AgentPlanner.java` | Prompt key and tool filtering must be per-agent | `plan(execution, correlationId)` → `plan(execution, AgentDefinition definition, correlationId)`. `promptServiceClient.render(...)` now renders `definition.promptKey()` instead of `properties.getAgentPromptKey()`. New private `effectiveMaxIterations(definition)` falls back to `properties.getMaxIterations()` when `definition.maxIterations()` is null. `formatAvailableTools()` now takes `definition` and filters via `toolPolicy.isAllowed(definition, tool.name())` | Behaviorally no for default agent (same prompt key, same 5 tools); signature changed |
| `orchestrator/AgentOrchestratorService.java` | The loop must carry the resolved definition through every call | `execute(request, correlationId, traceId)` → `execute(request, AgentDefinition definition, correlationId, traceId)`. New `effectiveMaxIterations(definition)`/`effectiveTimeoutMs(definition)`. `runLoop`, `applyPlanningFailure`, `executeTool`, `finalizeAnswer` all gained a `definition` parameter. `applyPlanningFailure` now also calls `metrics.recordPlanRejected(...)` and `metrics.recordToolDenied(...)` (new) when the rejection reason is `PLAN_INVALID`/`TOOL_NOT_ALLOWED`. `AgentPlan plan` in `runLoop` is now initialized to `null` (was implicitly definitely-unassigned) so a nullable reference can be passed to `applyPlanningFailure` even when `agentPlanner.plan(...)` itself threw before producing one | Behaviorally no for default agent, verified by the full existing regression suite passing unchanged; constructor **unchanged** (still 8 args — `AgentDefinition` is a per-call parameter, not a new constructor dependency) |
| `audit/AgentAuditClient.java` | Audit trail should distinguish which agent ran | `recordAgentRun` payload gained one field: `agentId` (from `execution.getAgentId()`, defaulting to `"default"`) | No — additive field on an existing fire-and-forget payload; nothing about the write path, event type, or failure handling changed |
| `metrics/AgentMetrics.java` | Section 14 — metrics must be agent-taggable | `recordRequest`, `recordSuccess`, `recordFailure`, `recordTimeout`, `recordToolCall`, `recordToolDenied`, `stopExecutionTimer` all gained an `agentId` parameter and now tag `"agent"`. New `recordPlanRejected(agentId, reason)` counter (`agent_plan_rejected_total`). `recordIteration`, `recordRagCall`, `recordLlmCall`, `recordContextSize`, `stopToolTimer` were **not** changed (kept minimal per the task's scope discipline) | No behavioral change to values recorded, only added tag dimensions and one new counter; signatures of 7 methods changed (all call sites updated in this same phase) |

### 1.3 Modified configuration file (1)

`src/main/resources/application.yml` — added a new top-level `agents:` block (25 lines: `default-agent-id: default` + one `definitions` entry reproducing the original hardcoded values). The pre-existing `agent:` (singular) block, `management:`, `resilience4j:` blocks are byte-for-byte unchanged (confirmed via `git diff` — the only hunk in this file is the new insertion).

---

## 2. Agent Foundation Review

Verified against the actual re-read source above, mapped to the task's checklist:

- **Agent Definition** — `registry.AgentDefinition` (record), §1.1.
- **Agent Registry** — `registry.AgentRegistry`, constructor-time build + fail-fast, `resolve/find/isEnabled/capabilitiesOf/allowedToolsOf/list`.
- **Agent Identity** — `AgentDefinition.agentId()` (String), resolved once by `AgentRegistry.resolve` inside `AgentController.execute`, carried into `AgentExecution.agentId` via the constructor call in `AgentOrchestratorService.execute` (`new AgentExecution(requestId, correlationId, traceId, request.userId(), request.userQuery(), definition.agentId())`).
- **Agent Execution Context** — `state.AgentExecution`, generalized in place (no new type), now carries `agentId`.
- **Agent Capabilities** — `registry.AgentCapability` enum, stored on `AgentDefinition.capabilities()`, descriptive only.
- **Agent-specific tool scoping** — `AgentToolPolicy.isAllowed(AgentDefinition, String)` / `checkAllowed(AgentDefinition, String)`, called from `AgentPlanValidator.validateCallTool` and `AgentPlanner.formatAvailableTools`.
- **Agent-specific prompt selection** — `AgentPlanner.plan(...)` calls `promptServiceClient.render(definition.promptKey(), ...)` — verified this is the **only** place a prompt key is chosen; `properties.getAgentPromptKey()` is no longer read anywhere in `AgentPlanner` (confirmed by re-reading the full file — it appears nowhere in the current source).
- **Agent Planner integration** — `AgentPlanner.plan(AgentExecution, AgentDefinition, String)`, called from `AgentOrchestratorService.runLoop` at `plan = agentPlanner.plan(execution, definition, execution.getCorrelationId())`.
- **Agent Plan Validation** — `AgentPlanValidator.validate(AgentPlan, List<ToolSummary>, AgentDefinition)`, called immediately after, same line-adjacent pattern as before Phase 4.1.
- **Execution boundaries** — `AgentOrchestratorService.effectiveMaxIterations(definition)` / `effectiveTimeoutMs(definition)`, both falling back to `AgentOrchestratorProperties` when a definition leaves the value `null`; checked every `runLoop` pass exactly as before.
- **Agent Audit** — `AgentAuditClient.recordAgentRun(AgentExecution execution)`, unchanged call site (`AgentOrchestratorService.execute`'s `finally` block), one new payload field.
- **Agent Metrics** — `AgentMetrics`, 7 methods gained an `agentId` tag parameter, 1 new counter (`recordPlanRejected`).
- **Resilience** — `config.ResilienceConfig`, `config.AgentOrchestratorProperties`'s resilience-related fields, and every `resilience4j:` YAML entry — confirmed **zero diff** in this review (not part of the file list in §1 at all).

All 13 items are present and traced to exact class/method names, as required.

---

## 3. Security Review

**Chain confirmed intact, read from actual code, not test names:**

```
AgentPlanValidator.validateCallTool(plan, discoveredTools, definition)
    -> toolPolicy.checkAllowed(definition, plan.tool())              [Agent-level policy - policy/AgentToolPolicy.java:94]
        -> throws AgentException.toolNotAllowed(...) if not in definition.allowedTools()
    -> (if allowed) orchestrator proceeds to McpToolClient.callTool(...)
        -> MCP Gateway: ToolInvoker.invoke(...) [unmodified]
            -> ToolAuthorizationService.checkPermission(context, definition)  [security/ToolAuthorizationService.java:94]
                -> definition.readWrite()==WRITE -> ALWAYS throws writeOperationNotAllowed, regardless of role
                -> empty/null roles -> throws unauthorized
                -> role present but wrong -> throws forbidden
        -> tool.execute(...)
```

Point-by-point, from the actual re-read source:

- **Model cannot grant itself tools.** `state.AgentPlan` (unread/unmodified this phase — re-confirmed via `git status` showing no diff on this file) has exactly 6 fields: `action, reasoning, tool, arguments, ragQuery, answer`. No field can name a definition, an allow-list, or a permission. `AgentPlanValidator.validate(plan, discoveredTools, definition)` — `definition` is a **method parameter supplied by the caller**, never read from `plan`.
- **Model cannot change agent identity.** `AgentDefinition definition` is resolved once, in `AgentController.execute`, from `agentRegistry.resolve(request.agentId())` — `request` is the deserialized HTTP body, not anything the LLM produces mid-run. The same `definition` reference is threaded unchanged through every downstream call in one `execute(...)` invocation; nothing in `AgentOrchestratorService`, `AgentPlanner`, or `AgentPlanValidator` ever re-resolves or reassigns it based on plan content.
- **Unauthorized tool rejected.** `AgentToolPolicy.checkAllowed` (line 94-100) throws `AgentException.toolNotAllowed` when `!definition.allowedTools().contains(toolName)`. Re-verified live in the actual test run's console output during Phase 4.1 (`WARN ... Agent tool policy denied agentId=default toolName=payment.refund`).
- **Cross-agent tool escalation rejected.** Confirmed structurally: `AgentToolPolicy.isAllowed`/`checkAllowed` take the **specific resolved** `AgentDefinition` as a parameter — there is no shared/global state anywhere in this class (no field at all besides the injected logger). Two different `AgentDefinition` instances with different `allowedTools()` sets necessarily produce different `isAllowed` results for the same tool name; there is no code path that could let one agent's grant apply to another.
- **Disabled agent rejected.** `AgentRegistry.resolve` line 102-105: `if (!definition.enabled()) { ... throw AgentException.agentDisabled(...); }`.
- **Unknown agent rejected.** `AgentRegistry.resolve` line 97-101: `if (definition == null) { ... throw AgentException.agentNotFound(...); }`.
- **MCP write tools remain unavailable.** Re-verified this review, not assumed: `grep -rn "ToolReadWrite.WRITE" paymentx-mcp-gateway/src/main/java/com/paymentx/mcp/tool/*.java` returns **zero matches** — no `PaymentXTool` bean registers as `WRITE`. `ToolAuthorizationService.checkPermission` line 95-98 would deny one unconditionally even if one existed. Neither this phase nor any file it touched adds a tool.
- **Existing MCP authorization remains active.** `git status --short paymentx-mcp-gateway/` returns **no output** — the module is byte-for-byte unmodified by this phase. Its own test suite (`McpSecurityTest`, `ToolAuthorizationServiceTest`, `McpProtocolIntegrationTest`, and 6 others) was re-run this phase: 63/63 passed.
- **Prompt injection protections remain intact.** `AIAgentSecurityTest`'s original direct-injection scenarios (`directPromptInjection_userTriesToForceRefundTool_...`, the 9-tool-name parameterized test, `authorizationBoundary_agentCannotBypassPolicyEvenWhenPlannerAsksDirectly`) keep their exact original mocked-plan input and expected `DENIED` assertion — only the method call's plumbing (an added `DEFAULT_DEFINITION` parameter) changed, confirmed by re-reading the current file. `rag-service`'s `RagSecurityTest` (indirect/poisoned-document injection, a module this phase never touched) was re-run: 3/3 passed.

No weakening found anywhere in this chain.

---

## 4. Backward Compatibility

`POST /api/v1/agent/execute` — path, method, and `ApiResponse<AgentExecuteResponse>` response envelope are unchanged (`AgentExecuteResponse`'s own file has no diff in this phase). Verified two ways:
1. **Statically** — `AgentExecuteRequest`'s new `agentId` field carries no `@NotBlank`/`@NotNull` (only `userQuery` is annotated required), and the 3-arg legacy constructor still exists.
2. **Dynamically** — `AgentE2EIntegrationTest` (a real HTTP test, unmodified this phase, still constructing requests via the 3-arg constructor: `new AgentExecuteRequest("conv-1", "test-user", "Why did PMT-123 fail?")`) was re-run and passed both its scenarios (2/2) against the real, now-Phase-4.1 `AgentController`.

`agentId` is **optional**, by explicit design (§16 of the implementation): a blank/absent value resolves to `AgentRegistryProperties.defaultAgentId` (`"default"`), whose configured `allowedTools`/`promptKey`/limits are the exact same values Phase 3.8 hardcoded — this is what makes omitting it safe rather than merely "not rejected."

No unintended Phase 3 behavior change was found. The one intentional, in-scope behavior addition is that `AgentAuditClient`'s payload now carries an `agentId` field it did not before (§1.2) — additive, not breaking.

---

## 5. Configuration Review

The full `agents:` block, as it exists in `application.yml` right now (lines 160-174):

| Field | Value |
|---|---|
| `default-agent-id` | `default` |
| `agent-id` | `default` |
| `name` | `PaymentX General Assistant` |
| `description` | *(multi-line, describes it as the preserved Phase 3.8 general agent)* |
| `version` | `"1.0"` |
| `capabilities` | `PAYMENT_ANALYSIS, ROUTING_ANALYSIS, RECONCILIATION_ANALYSIS, KNOWLEDGE_RETRIEVAL` |
| `allowed-tools` | `payment.lookup, payment.status, routing.lookup, reconciliation.status, audit.search` |
| `prompt-key` | `PAYMENTX_AGENT_ORCHESTRATOR` |
| `risk-level` | `LOW` |
| `enabled` | `true` |
| `max-iterations` | *(unset — falls back to `agent.max-iterations: 5`)* |
| `timeout-ms` | *(unset — falls back to `agent.overall-timeout-ms: 75000`)* |

**Exactly one agent is defined.** No business agent (Error Analyzer or any other) appears anywhere in this file — confirmed by reading the entire `agents:` block, which is 15 lines total.

**Secret scan:** no `password`, `secret`, `key`, `token`, or credential-shaped value appears anywhere in the added block — every field is a name, description, capability tag, tool identifier, prompt key, or risk/enabled flag. The block introduces no new environment variable, no new credential reference, and no change to any existing secret-bearing field elsewhere in the file (`agent.audit-service-url` etc. are pre-existing and untouched).

---

## 6. Test Coverage Review

**85/85 tests in `paymentx-agent-orchestrator`**, mapped by actual file (counts from the real `mvn test` surefire run, not estimated):

| File | Count | Maps to |
|---|---|---|
| `registry.AgentRegistryTest` | 14 (new) | registry (registration, duplicate, missing-default, resolve blank/known/unknown/disabled, find, isEnabled, list, capabilitiesOf, allowedToolsOf) |
| `policy.AgentToolPolicyTest` | 19 (16 orig scenarios adapted + 3 new) | authorization, escalation (`isAllowed_toolGrantedToOneAgent_isNotGrantedToAnotherAgent...`) |
| `planning.AgentPlanValidatorTest` | 11 (10 orig + 1 new) | plan validation, escalation (`validate_callToolAllowedForOneAgentButNotAnother_...`) |
| `planning.AgentPlannerTest` | 8 (6 orig + 2 new) | plan validation (prompt-key selection, tool-list scoping) |
| `orchestrator.AgentOrchestratorServiceTest` | 6 (unchanged scenarios, signatures adapted) | bounded execution (max iterations, timeout), backward compatibility (default-agent behavior) |
| `security.AIAgentSecurityTest` | 21 (16 orig adapted + 5 new) | authorization, escalation, identity, no-write guarantee, registry (unknown/disabled agent) |
| `controller.AgentE2EIntegrationTest` | 2 (unmodified) | backward compatibility (real HTTP, 3-arg legacy request) |
| `client.RagServiceClientTest` | 4 (unmodified, unrelated to Phase 4.1) | — |

**Coverage gaps identified (per task instruction — not fixed, only reported):**
1. **No test exercises `AgentController.execute` directly with a real `AgentRegistry` bean and a non-default `agentId`** over HTTP — `AgentE2EIntegrationTest` only ever exercises the default agent (matching its own pre-Phase-4.1 scenarios); the unknown/disabled-agent security tests construct `AgentRegistry` directly in `AIAgentSecurityTest`/`AgentRegistryTest`, not through a real Spring `MockMvc`/`TestRestTemplate` call to `/api/v1/agent/execute`. The unit-level coverage is real and passing, but there is no true end-to-end proof that an HTTP request with `{"agentId":"does-not-exist"}` produces a 404 through the full Spring stack (`GlobalExceptionHandler` included).
2. **No test asserts the exact HTTP status code** (404 for unknown, 403 for disabled) reaches the client — the existing tests assert on `AgentException.getErrorCode()` at the unit level, which is correct but one layer short of confirming `GlobalExceptionHandler.handleAgentException`'s `ex.getHttpStatus()` mapping is actually exercised for these two new codes specifically.
3. **No `AgentDefinition` compact-constructor validation test exists** (blank agentId/name/promptKey, non-positive maxIterations/timeoutMs) — the record's own defensive validation is unexercised by any test in this phase.
4. **No test for `AgentMetrics.recordPlanRejected`/`recordToolDenied` actually being invoked with the correct arguments** — `AgentOrchestratorServiceTest` uses a real `AgentMetrics` (SimpleMeterRegistry-backed) but never asserts on emitted meter values; the wiring is visually correct (confirmed by source read in §1.2) but not test-verified.

None of these are security holes — the actual enforcement (denial itself) is thoroughly tested at the unit level in §3. They are coverage gaps in the "prove it end-to-end / prove the metric fired" sense.

---

## 7. Phase 3 Regression Review

**Actually executed this phase** (two full `mvn ... test` runs, both `BUILD SUCCESS`, transcripts inspected for this review):

| Module | Tests | Result |
|---|---|---|
| `paymentx-agent-orchestrator` | 85 | PASS |
| `paymentx-mcp-gateway` | 63 | PASS |
| `paymentx-rag-service` | 43 | PASS |
| `paymentx-llm-service` | 16 | PASS |
| `paymentx-prompt-service` | 38 | PASS |
| **Total** | **245** | **PASS** |

**Not tested:**
- `paymentx-control-center` — not modified this phase (§1 shows zero Control Center files touched), so its suite was **not executed**. The implementation report already stated this; this review confirms it is still accurate — no Control Center change has occurred since.
- `paymentx-embedding-service`, `paymentx-vector-service` — not modified, not executed this phase (they sit underneath `rag-service`, whose suite exercises them only through `RagServiceClientTest`/`RagQueryIntegrationTest`'s WireMock stand-ins, not their own real test suites).
- Every non-AI payment/routing/audit/reconciliation/etc. service — untouched, not executed, not claimed.

---

## 8. Architectural Review

1. **Is the foundation reusable for 20 agents?** Yes, structurally — each new agent is one `AgentRegistryProperties.Entry` in YAML (`agent-id`, `name`, `promptKey`, `allowedTools`, `capabilities`) plus, if it needs a tool that doesn't exist yet, a new MCP tool registered in `paymentx-mcp-gateway` (a separate, out-of-scope-for-this-phase step). No `AgentOrchestratorService`/`AgentPlanner`/`AgentPlanValidator` code change is required to add an agent that only uses existing tool types.
2. **New microservice?** No — confirmed by §1, every change is inside `paymentx-agent-orchestrator`.
3. **New database?** No — `AgentRegistry` is in-memory, built once from YAML at startup; no entity, no repository, no migration was added.
4. **Preserves existing MCP security?** Yes — confirmed in §3; `paymentx-mcp-gateway` has zero diff.
5. **Preserves existing RAG/LLM/Prompt architecture?** Yes — `paymentx-rag-service`, `paymentx-llm-service`, `paymentx-prompt-service` all have zero diff (confirmed via `git status`); `AgentPlanner`'s calls to `PromptServiceClient.render`/`LlmServiceClient.generate` use the exact same method signatures as before, only the `promptKey`/`variables` values passed in changed (per-agent instead of hardcoded).
6. **Is agent-specific tool isolation correctly enforced?** Yes — see §3's cross-agent-escalation analysis; each `AgentToolPolicy` call receives the specific resolved `AgentDefinition`, no shared/global allow-list state remains anywhere in the class.
7. **Is the orchestrator still the trusted coordinator?** Yes — `AgentOrchestratorService` is still the only class driving the loop; `AgentDefinition` resolution happens one layer up (`AgentController`), before the orchestrator is ever invoked, and the orchestrator treats it as an opaque, trusted input, never re-deriving or questioning it.
8. **Any unnecessary abstractions?** None found. `AgentRegistry` is a single concrete class (no interface, matching this module's own no-interface style). `AgentExecution` was generalized in place rather than wrapped in a new `AgentContext` type (§6 of the implementation doc explains why). `AgentResult`/`AgentEvidence`-style new types (raised as options in the Phase 4.0 audit) were correctly **not** built — `AgentExecuteResponse`/`ToolEvidence`/`SourceEvidence` already cover that need.
9. **Any Phase 4.2 requirements accidentally implemented?** No business agent, no new MCP tool, no Control Center change, no agent-to-agent communication, no conversation memory, no human-approval workflow, no dedicated `AGENT_RUN` audit event type — all correctly deferred (confirmed by re-reading §1's complete file list; none of these appear).
10. **Architectural risks before Error Analyzer?** Two worth naming, neither blocking: (a) the coverage gaps in §6 (no full-HTTP-stack test of the new failure codes' status codes); (b) `AgentDefinition.maxIterations`/`timeoutMs` are read but MCP Gateway itself has no per-agent awareness at all — a future higher-risk agent's tighter/looser limits are enforced only at the orchestrator layer, never communicated to or enforced by MCP Gateway, which is fine for read-only tools today but worth remembering if a future phase ever revisits write-tool policy.

---

## 9. Error Analyzer Readiness

Without implementing anything, checking what a future Error Analyzer definition would need against what exists **today, in the codebase, right now**:

| Requirement | Status |
|---|---|
| Payment lookup access | Exists — `payment.lookup` MCP tool, already in the default agent's `allowedTools`; a new agent's own entry would just list it too |
| Payment status access | Exists — `payment.status`, same |
| Audit search | Exists — `audit.search`, same |
| Routing lookup | Exists — `routing.lookup`, same |
| Reconciliation access | Exists — `reconciliation.status`, same |
| RAG access | Exists — `RagServiceClient`/`RETRIEVE_KNOWLEDGE` action, agent-agnostic, unchanged this phase |
| LLM access | Exists — `LlmServiceClient`, agent-agnostic, unchanged this phase |
| Prompt access | Exists — Prompt Service's `POST /api/v1/prompts` admin flow can create a new `PAYMENTX_ERROR_ANALYZER` key today; `AgentPlanner` already renders whatever `promptKey` a definition supplies |
| Agent-specific tool policy | Exists — `AgentDefinition.allowedTools` + `AgentToolPolicy`, this phase's core deliverable |
| Evidence tracking | Exists, unchanged — `SourceEvidence`/`ToolEvidence` |
| Audit | Exists, extended — now carries `agentId` |
| Metrics | Exists, extended — now carries `agent` tag + `agent_plan_rejected_total` |

**Genuinely missing** (not a foundation gap — a content/config gap, consistent with the Phase 4.0 audit's own finding): a real RAG knowledge corpus (error codes, runbooks) was never ingested; and no `agents.definitions` entry for an Error Analyzer exists in `application.yml` yet (correctly, since implementing it is explicitly out of this phase's scope). Adding one, plus authoring/ingesting real knowledge content, is the entire remaining gap — no orchestrator/registry/policy code change is needed first.

---

## 10. Final Classification

## **A — APPROVED FOR PHASE 4.2**

**Why not B:** the coverage gaps in §6 are real but narrow (missing end-to-end HTTP-status assertions and a metric-emission assertion for behavior that IS correctly implemented and IS unit-tested) — they do not indicate a defect in the foundation itself, only a thinner-than-ideal test layer at the very top of the stack. Every actual security/behavioral property the task asked to verify was independently re-confirmed against source in this review, not just accepted from test names.

**Why not C:** nothing found in this review requires rework. The two-gate security model is intact and independently verified; backward compatibility is real and proven by an unmodified, passing end-to-end test; no unnecessary abstraction exists; no scope creep into Phase 4.2 content occurred; 245/245 relevant regression tests pass.

**Recommendation, not a blocker:** if a future phase has budget before Error Analyzer, closing gap #1/#2 in §6 (one real HTTP-level test for an unknown/disabled `agentId`) would be cheap and would close the one place this review found "unit-proven but not end-to-end-proven."

---

## 11. Compliance

- Source files modified during review: **0**
- Configuration modified: **0**
- Database changed: **NO**
- Services restarted: **NO**
- Payment created: **NO**
- MCP write operation: **NO**
- Business agent implemented: **NO**

Confirmed via `git status --short` immediately before writing this document and again after — identical to the state left by the Phase 4.1 implementation report, plus this one new file.
