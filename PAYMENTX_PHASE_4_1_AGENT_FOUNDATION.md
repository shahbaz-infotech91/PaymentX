# PaymentX Phase 4.1 — Multi-Agent Foundation

**Status:** Foundation implementation complete. No business agent (Error Analyzer, Fraud Detection, etc.) was implemented — see §20 for the explicit out-of-scope list. Not committed, not pushed — awaiting approval per the task's stop condition.

**Scope:** `paymentx-agent-orchestrator` only. MCP Gateway, RAG/LLM/Prompt/Embedding/Vector Service, Audit Service, and every payment service are unmodified.

---

## 1. Existing Architecture Before Phase 4.1

Verified directly against source (not assumed from the Phase 4.0 report) before any change was made. `AgentOrchestratorService` ran a single, hardcoded agent:

- `policy.AgentToolPolicy` held one `private static final Set<String> ALLOWED_TOOLS` (5 tools: `payment.lookup`, `payment.status`, `routing.lookup`, `reconciliation.status`, `audit.search`).
- `config.AgentOrchestratorProperties` held one flat set of platform-wide values (URLs, `agentPromptKey="PAYMENTX_AGENT_ORCHESTRATOR"`, `maxIterations=5`, `overallTimeoutMs=75000`, etc.) with no per-agent variation possible.
- `planning.AgentPlanner.plan(AgentExecution, String correlationId)` and `planning.AgentPlanValidator.validate(AgentPlan, List<ToolSummary>)` had no way to receive "which agent" — there was only ever one.
- `orchestrator.AgentOrchestratorService.execute(AgentExecuteRequest, String correlationId, String traceId)` — no identity parameter.
- `dto.AgentExecuteRequest(conversationId, userId, userQuery)` — no `agentId` field.
- `state.AgentExecution` — no `agentId` field.
- `controller.AgentController` — one dependency (`AgentOrchestratorService`), no registry.

This matches the Phase 4.0 audit's findings; nothing was found to contradict it during re-verification.

---

## 2. Foundation Design

The existing single-agent implementation is generalized in place, not replaced. One new package, `registry`, is added; every other change is an additive parameter threaded through the existing call chain:

```
AgentController.execute(request)
    -> AgentRegistry.resolve(request.agentId())          [NEW - trusted identity resolution]
    -> AgentOrchestratorService.execute(request, definition, correlationId, traceId)
         -> AgentPlanner.plan(execution, definition, correlationId)
              -> AgentToolPolicy.isAllowed(definition, tool)   [scoped, was global]
         -> AgentPlanValidator.validate(plan, discoveredTools, definition)
              -> AgentToolPolicy.checkAllowed(definition, tool)
         -> AgentToolClient / RagServiceClient                  [UNCHANGED]
         -> MCP Gateway's own ToolAuthorizationService           [UNCHANGED - second gate]
```

No new microservice, no new database, no new resilience framework — reusing exactly what the task's design principles require.

---

## 3. Agent Definition

**Chosen name: `AgentDefinition`** (package `com.paymentx.agent.registry`) — not the brief's example verbatim by coincidence, but because it is the name the existing codebase's own closest analogue already uses: MCP Gateway's `registry.McpToolDefinition` (a single callable entity's metadata: name, description, permission, risk level, enabled). `AgentDefinition` mirrors that shape one layer up, for consistency across the two services.

An immutable Java 21 record (the project's established style for this kind of DTO — see `state.AgentPlan`, `dto.AgentExecuteRequest`):

```java
public record AgentDefinition(
        String agentId, String name, String description, String version,
        Set<AgentCapability> capabilities, Set<String> allowedTools, String promptKey,
        AgentRiskLevel riskLevel, boolean enabled,
        Integer maxIterations, Long timeoutMs)
```

A compact constructor validates `agentId`/`name`/`promptKey` are non-blank and defensively copies `capabilities`/`allowedTools` into immutable sets. `maxIterations`/`timeoutMs` are nullable — `null` means "fall back to the platform default in `AgentOrchestratorProperties`" (only fields genuinely needed; no speculative fields were added).

---

## 4. Registry

**`registry.AgentRegistry`** — a single `@Component`, no interface (matching this module's own existing style: `AgentOrchestratorService`, `AgentPlanner`, etc. are all concrete classes, not interface+impl). Built once at startup from `AgentRegistryProperties` (a `@ConfigurationProperties(prefix="agents")` YAML-bound list, sibling to the existing singular `agent:` block), converted into immutable `AgentDefinition`s, and held in an immutable `Map`.

- **Configuration-based, in-memory** — per Phase 4.0's own recommendation. No database table was added. Justification: nothing today needs a human to enable/disable/reconfigure an agent at runtime without a deploy, and this codebase's own established pattern keeps security-relevant surfaces (a tool allow-list) in reviewed configuration, not a runtime-mutable table — exactly matching `AgentToolPolicy`'s own pre-Phase-4.1 precedent, just made configurable instead of hardcoded.
- **Fails fast** on a duplicate `agentId` or a missing default — mirrors MCP Gateway's `ToolRegistry`'s own "collect at startup, fail fast on collision" discipline.
- Responsibilities: `resolve(agentId)` (throws on unknown/disabled), `find(agentId)` (non-throwing `Optional`), `isEnabled(agentId)`, `capabilitiesOf(agentId)`, `allowedToolsOf(agentId)`, `list()`.

Deterministic tests: `AgentRegistryTest` (14 tests) — registration, duplicate rejection, missing-default rejection, resolve (blank→default, known, unknown, disabled), find, isEnabled, list, capability/tool lookup.

---

## 5. Identity

`agentId` is a plain `String`, resolved exclusively by `AgentRegistry.resolve(...)` from the request's optional `agentId` field (or the configured default if absent/blank) — **before** the orchestrator loop starts. It is carried through `AgentExecution.agentId` and `AgentAuditClient`'s payload for traceability.

Explicitly distinct from:
- **Authenticated human identity** — `AgentExecuteRequest.userId` remains a separate, optional field, unchanged since Phase 3.8 (still no real platform-wide identity system — this phase does not claim to solve that).
- **`participantId`** — never referenced by `AgentDefinition` at all; MCP Gateway's own resource-ownership check (`X-Participant-Id`) is untouched and independent.
- **`paymentId`** — never referenced by `AgentDefinition`.

The LLM's own output (`state.AgentPlan`) has no field capable of naming or influencing agent identity — verified structurally (the record's 6 fields are `action, reasoning, tool, arguments, ragQuery, answer`) and by a new deterministic test (`AIAgentSecurityTest.agentIdentityCannotBeOverriddenByModelOutput_...`).

---

## 6. Execution Context

**No new type was introduced.** `state.AgentExecution` already was the platform's execution context (identity/correlation fields, `currentStep`/`iteration`/`toolCallCount` bounded state, `retrievedContext`/`toolCalls` evidence, terminal `status`/`finalAnswer`) — it was generalized in place with one additive field, `agentId`, via a new 6-arg constructor plus a 5-arg backward-compatible overload. Introducing a separate `AgentContext` type would have duplicated this for no benefit — Design Principle #2 ("reuse existing code") argued directly against it.

No secrets, no raw prompts, no full LLM answers, no unnecessary PII were added — the existing minimization discipline (only `AgentPlan.reasoning()`, a short label, is ever captured) is unchanged.

---

## 7. Capability Model

**`registry.AgentCapability`** — a 6-value enum: `PAYMENT_ANALYSIS`, `ROUTING_ANALYSIS`, `RECONCILIATION_ANALYSIS`, `KNOWLEDGE_RETRIEVAL`, `LOG_ANALYSIS`, `DATABASE_ANALYSIS`. Chosen as the minimum concrete set matching what PaymentX's existing 5 MCP tools and RAG retrieval already support — not a speculative taxonomy of all 20 future agents. Purely descriptive metadata (for a future Control Center "AI Agents" listing); it is never itself a security boundary — `allowedTools` is.

---

## 8. Tool Scoping

**The central change.** `policy.AgentToolPolicy` no longer owns a class-wide hardcoded set. `isAllowed(AgentDefinition, String toolName)` / `checkAllowed(AgentDefinition, String toolName)` now check the **resolved agent's own** `allowedTools`. The default agent's configured allow-list is byte-for-byte the same 5 tools Phase 3.8 hardcoded, so single-agent behavior is unchanged.

The two-gate design required by the task is fully preserved and was not touched at the second gate:

```
AgentToolPolicy (per-agent, this phase's change)
        v
MCP Gateway (untouched)
        v
ToolAuthorizationService (untouched, independent, per-call)
        v
Tool execution
```

Cross-agent isolation is proven by new tests at three layers: `AgentToolPolicyTest` (same tool allowed for one definition, denied for a narrower one), `AgentPlanValidatorTest` (a plan cannot be validated against a different, more permissive definition than the one resolved), `AIAgentSecurityTest.agentCannotEscalatePermissions_...` (full orchestrator-level proof).

---

## 9. Planner Integration

`AgentPlanner.plan(AgentExecution, AgentDefinition, String correlationId)` now renders `definition.promptKey()` (not the platform-default prompt key) and filters `formatAvailableTools()` through `toolPolicy.isAllowed(definition, ...)`. `effectiveMaxIterations(definition)` falls back to `AgentOrchestratorProperties` only when the definition leaves it unset.

The planner cannot change agent identity, allowed tools, security policy, or execution limits — those are method **parameters** passed in by the trusted caller (`AgentOrchestratorService`, which received them from `AgentController`, which received them from `AgentRegistry`), never derived from the LLM's own output. New test: `AgentPlannerTest.plan_availableToolsVariable_isScopedToThisAgentsAllowListEvenWhenMcpKnowsMoreTools`.

---

## 10. Plan Validation

`AgentPlanValidator.validate(AgentPlan, List<ToolSummary>, AgentDefinition)` — the tool-existence check (against MCP-discovered tools) and the policy check (`toolPolicy.checkAllowed(definition, tool)`) both remain two independent, mandatory conditions, now both scoped to the resolved definition. A plan can never expand its own permissions: the `AgentDefinition` parameter is supplied by the caller on every single call, never read from anything the plan itself carries.

---

## 11. Execution Boundaries

All preserved, verified unchanged by the full regression suite:
- **Max iterations** — `effectiveMaxIterations(definition)`, still hard-checked every loop pass.
- **Timeout** — `effectiveTimeoutMs(definition)`, still the single `CompletableFuture.get(...)` bound around the whole loop.
- **Recursion/depth protection** — unchanged; no agent-to-agent capability exists (see §19 in the audit / task item 12), so no new recursion risk was introduced.
- **Cancellation** — unchanged (was, and remains, MISSING — not part of this phase's scope; the audit already flagged this as a pre-existing gap, not something Phase 4.1 was asked to close).
- **Failure handling** — the 7-outcome honest status model is unchanged; `applyPlanningFailure` now also records `agentId` on every failure.
- **Deterministic termination** — unchanged; still a bounded `while(true)` with two independent exit checks every pass.

---

## 12. Audit

Reused `audit.AgentAuditClient` and the existing Audit Service — no second audit system, no schema change. One field was added to the existing JSON payload: `agentId` (defaults to `"default"` if somehow absent). Everything else (`status`, `iterations`, `toolCallCount`, `ragUsed`, per-tool-call `{tool, status}` only) is unchanged; still fire-and-forget, still `eventType=API_REQUEST`/`actorType=AI_AGENT` (the existing generic reuse — a dedicated `AGENT_RUN` event type was **not** added in this phase, since it would require an Audit Service change and this task's scope is `paymentx-agent-orchestrator` only; the Phase 4.0 audit's own recommendation to consider it applies once a second agent is genuinely live, which this phase does not add).

Never persisted: secrets, raw credentials, unnecessary PII, raw model internals, full prompts, full answers — unchanged.

---

## 13. Metrics

Extended `metrics.AgentMetrics` (Micrometer, existing hand-registered pattern — no `@Timed`/`@Counted` introduced) rather than building a second system:

- `agent` tag added to: `agent_requests_total`, `agent_success_total`, `agent_failure_total`, `agent_timeout_total`, `agent_tool_calls_total`, `agent_tool_denied_total`, `agent_execution_latency`.
- **New:** `agent_plan_rejected_total{agent, reason}` — fired when `AgentPlanValidator` rejects a plan (`PLAN_INVALID`/`TOOL_NOT_ALLOWED`), closing a gap the Phase 4.0 audit explicitly identified.
- **Wired in:** `recordToolDenied` — the audit found this method existed but was never called anywhere; it is now invoked from `applyPlanningFailure` whenever a plan is rejected specifically for `TOOL_NOT_ALLOWED`.
- `agentId` always comes from `AgentRegistry` — a small, bounded, configured set of values, never raw `userQuery` text — so tag cardinality stays safe (Design Principle: "Do NOT use unbounded user-generated values as metric labels").
- `recordIteration`, `recordRagCall`, `recordLlmCall`, `recordContextSize`, `stopToolTimer` were **not** changed — out of this phase's explicit scope, kept minimal.

---

## 14. Resilience

**Untouched.** All 4 existing Resilience4j circuit breaker/retry instances (`ragService`, `mcpGateway`, `promptService`, `llmService`) are unchanged in `application.yml` and `config.ResilienceConfig`. No new instance was added, no retry was multiplied across layers. The per-agent `timeoutMs` override (when set) only changes the **outer** `CompletableFuture` bound in `AgentOrchestratorService` — it can never be looser than a downstream client's own connect/read timeout, since those remain independently configured and unrelated to this value; an agent cannot use a longer `timeoutMs` to bypass a downstream service's own timeout, only to bound its own overall run more tightly or loosely within what downstream calls can still complete in.

---

## 15. Security Model

See the Phase 4.0 audit (§8) for the pre-existing model; this phase's changes and their verification:

| Item (task §18) | Covered by |
|---|---|
| 1. Unknown agent rejected | `AgentRegistryTest.resolve_unknownAgentId_throwsAgentNotFound`, `AIAgentSecurityTest.unknownAgentId_...` |
| 2. Disabled agent rejected | `AgentRegistryTest.resolve_disabledAgentId_throwsAgentDisabled`, `AIAgentSecurityTest.disabledAgentId_...` |
| 3. Duplicate agent rejected | `AgentRegistryTest.construction_duplicateAgentId_failsFast` |
| 4. Agent cannot use unauthorized tool | `AgentToolPolicyTest`, `AgentPlanValidatorTest` (updated, same scenarios) |
| 5. Agent cannot escalate permissions | `AgentToolPolicyTest.isAllowed_toolGrantedToOneAgent_isNotGrantedToAnotherAgent...`, `AgentPlanValidatorTest.validate_callToolAllowedForOneAgentButNotAnother_...`, `AIAgentSecurityTest.agentCannotEscalatePermissions_...` |
| 6. Agent cannot request MCP write tool | Existing 9-write-tool-name parameterized test (unchanged), plus new `AIAgentSecurityTest.agentCannotRequestMcpWriteTool_seededDefaultDefinitionContainsNoWriteShapedToolNames` (config-safety guard) |
| 7. Invalid tool rejected | `AgentPlanValidatorTest.validate_callToolNotInMcpRegistry_...` (unchanged scenario) |
| 8. Invalid parameters rejected | `AgentPlanValidatorTest` (unchanged scenarios) |
| 9. Max iterations enforced | `AgentOrchestratorServiceTest.execute_plannerNeverFinishes_stopsAtMaxIterations` (unchanged, now via `DEFINITION` with `maxIterations=null` falling back to `properties`) |
| 10. Timeout enforced | `AgentOrchestratorServiceTest.execute_plannerNeverReturns_hitsOverallTimeout` (unchanged, same reasoning) |
| 11. Agent identity cannot be overridden by model output | `AIAgentSecurityTest.agentIdentityCannotBeOverriddenByModelOutput_...` (new) |
| 12. Prompt cannot grant additional tools | `AgentPlannerTest.plan_availableToolsVariable_isScopedToThisAgentsAllowListEvenWhenMcpKnowsMoreTools` (new) |
| 13. MCP second authorization gate remains active | MCP Gateway module untouched; full `McpSecurityTest`/`ToolAuthorizationServiceTest` suite re-run, 24 tests, all pass |
| 14. Existing prompt injection protections remain active | `AIAgentSecurityTest`'s original direct-injection tests (unchanged assertions, adapted signatures), `RagSecurityTest` (untouched module, re-run, 3 tests pass) |

No existing security test's assertions were weakened, removed, or loosened — every one keeps its original scenario and expected outcome; only the mechanical plumbing (new `AgentDefinition` parameter) changed.

---

## 16. Backward Compatibility

- `POST /api/v1/agent/execute` — unchanged path/method. `AgentExecuteRequest` gained one optional field, `agentId`; a `null`/absent value resolves to the configured default agent (byte-for-byte the original Phase 3.8 tool set/prompt/limits), so a pre-Phase-4.1 JSON body works identically. `AgentExecuteResponse`'s shape is completely unchanged.
- A 3-arg secondary constructor (`AgentExecuteRequest(conversationId, userId, userQuery)`) was kept alongside the new 4-arg canonical one specifically so existing Java call sites (test code) continue to compile without modification wherever that was possible.
- Control Center was **not modified** — its `AiPlatformClient`/`AiChatService` continue to call `POST /api/v1/agent/execute` exactly as before, with no `agentId`, and get the same default-agent behavior as before Phase 4.1.
- All pre-existing Phase 3.8/3.9/3.10 test scenarios pass unchanged (see §17) — none were deleted; 5 existing test files were mechanically adapted to the new method signatures (documented per-file in §17), never weakened.

---

## 17. Tests

**New test files:** `registry.AgentRegistryTest` (14 tests).

**Modified test files** (signature adaptation only — every original scenario/assertion preserved):
`policy.AgentToolPolicyTest` (+3 new tests), `planning.AgentPlanValidatorTest` (+1 new test), `planning.AgentPlannerTest` (+2 new tests), `orchestrator.AgentOrchestratorServiceTest` (no new tests, signatures only), `security.AIAgentSecurityTest` (+5 new tests: unknown agent, disabled agent, escalation, identity override, write-tool config guard).

**Full results — actually executed, not assumed:**

| Suite | Result |
|---|---|
| `paymentx-agent-orchestrator` (all 9 test classes, incl. real end-to-end `AgentE2EIntegrationTest` over WireMock) | **85/85 passed** |
| `paymentx-mcp-gateway` (all 9 test classes, incl. real-protocol `McpProtocolIntegrationTest`, `McpSecurityTest`) | **63/63 passed** |
| `paymentx-rag-service` (incl. `RagSecurityTest`) | **43/43 passed** |
| `paymentx-llm-service` | **16/16 passed** |
| `paymentx-prompt-service` | **38/38 passed** |

Total: **245/245 tests passed**, 0 failures, 0 errors, 0 skipped, across 2 full Maven runs (`mvn ... test`), both `BUILD SUCCESS`.

**Not run:** `paymentx-control-center`'s own test suite — Control Center was not modified in this phase (§16), so its tests are unaffected; not re-run in the interest of scope discipline, stated honestly here per the task's "do not claim success if a test was not executed" instruction rather than silently omitted.

One real bug was caught and fixed during this work: a newly-written test (`agentIdentityCannotBeOverriddenByModelOutput_...`) initially left `mcpToolClient.callTool(...)` unstubbed for its "permissive agent" path, causing a `NullPointerException` inside `executeTool` (an unstubbed Mockito mock returning `null` for a non-primitive return type) that surfaced as a genuine `FAILED` status — not a bug in production code, a bug in the new test itself, found and corrected before this report.

---

## 18. Files Changed

**Production source — 16 files** (11 modified, 5 new):
- Modified: `audit/AgentAuditClient.java`, `controller/AgentController.java`, `dto/AgentExecuteRequest.java`, `exception/AgentErrorCodes.java`, `exception/AgentException.java`, `metrics/AgentMetrics.java`, `orchestrator/AgentOrchestratorService.java`, `planning/AgentPlanValidator.java`, `planning/AgentPlanner.java`, `policy/AgentToolPolicy.java`, `state/AgentExecution.java`
- New: `registry/AgentCapability.java`, `registry/AgentDefinition.java`, `registry/AgentRegistry.java`, `registry/AgentRegistryProperties.java`, `registry/AgentRiskLevel.java`

**Production configuration — 1 file:** `src/main/resources/application.yml` (new `agents:` block, additive; existing `agent:` block unchanged)

**Test source — 6 files** (5 modified, 1 new): see §17.

**Documentation — 1 file:** this document.

No file outside `paymentx-agent-orchestrator/` was touched.

---

## 19. Future Agent Implementation Strategy

Adding a real business agent (e.g. a future Error Analyzer) in a later phase requires, per this foundation:

1. A new entry under `agents.definitions` in `application.yml` (or an environment-specific override) — `agentId`, `name`, `promptKey` (a **new** Prompt Service key, created via the existing `POST /api/v1/prompts` admin flow, never hardcoded), `allowedTools` (a subset of MCP Gateway's real registered tools), `capabilities`.
2. If that agent needs a tool that doesn't yet exist as an MCP tool, that tool must be built and registered in `paymentx-mcp-gateway` first (unaffected by this phase) — `AgentDefinition.allowedTools` can only ever narrow what MCP Gateway already exposes, never widen it.
3. No `paymentx-agent-orchestrator` code change should be required for a straightforward new agent using only existing tool types and the existing loop shape — this is the entire point of the generalization done in this phase.
4. If Control Center should let an operator choose an agent (rather than always using the default), `AiChatRequest`/`AiPlatformClient.executeAgent(...)` would need a new optional `agentId` passthrough field, and a UI control — explicitly **not** built in this phase (§16's "leave the frontend untouched" instruction, since the current single-agent chat experience needs no change to keep working).

---

## 20. Explicit Out-of-Scope List

Not implemented in this phase, per the task's explicit instruction:

Error Analyzer, Fraud Detection, Payment Routing Optimizer, Incident RCA, Reconciliation, Knowledge Assistant, Log Analysis, Notification, Report Generation, API Integration, SDK Generator, Test Case Generation, Code Review, Performance Optimization, Compliance, Database Analysis, Kafka Monitoring, Redis Health, Deployment Assistant.

Also explicitly not built in this phase (correctly, per task scope): any new MCP tool (read or write), any Control Center UI/API change, any database/persistence layer for the registry, agent-to-agent communication, a human-approval workflow, conversation memory, and a dedicated `AGENT_RUN` audit event type.
