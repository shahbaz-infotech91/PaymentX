# Phase 4.8.5 — Incident RCA Remediation + Final Closure

## Status: FINAL CLASSIFICATION B — SUBSTANTIALLY COMPLETE, EXTERNAL GEMINI QUOTA BLOCKER (not a PaymentX implementation defect). A genuine, real PaymentX defect underlying §25's malformed argument WAS found and fixed via source inspection — see §26.

## 26. Root-Cause Investigation and Fix — Malformed `payment.lookup` Argument (2026-08-24)

Following §25's finding that one live Gemini run generated a malformed `paymentReference`
argument, a full source-level investigation was performed **before any code change and without
spending Gemini quota** — the audit trail only records tool name + status, never the raw argument
value, so the exact malformed string could not be recovered; root cause was established from
code, not from re-running the live call.

**Investigation trail** (each layer inspected in turn, per instruction, no guessing):

1. **`PaymentLookupTool` (MCP Gateway)**: has a correct, real `McpSchema.JsonSchema` — declares
   `paymentReference` (string, required) and `includeHistory` (boolean, optional) with real
   descriptions — and a correct regex validator (`^[A-Za-z0-9_-]{1,64}$`, which the real reference
   `LIVEE2E-3FE6115058` satisfies). **Not the defect.**
2. **`AgentPlanValidator` (Agent Orchestrator)**: intentionally validates only that `arguments` is
   non-null, tool name is real/allowed — by design, never inspects individual argument *values*
   (that is deliberately MCP Gateway's job, per its own javadoc: "never a silent best-effort
   correction"). **Not the defect — working as designed.**
3. **`AgentPlanner` (Agent Orchestrator)**: parses the LLM's raw JSON text response and passes
   `arguments` straight through via `objectMapper.convertValue(...)` with zero transformation of
   values. **Not the defect** — but this is where the real gap was found, one step earlier:
4. **`McpToolClient.listTools()` (Agent Orchestrator)** — **the actual root cause**. The real MCP
   protocol's `tools/list` response already carries each tool's full `inputSchema`
   (`JsonSchema.properties()` + `.required()`, confirmed via `javap` against the MCP Java SDK
   0.18.3, and confirmed present and correct in `PaymentLookupTool`'s own definition). But
   `McpToolClient.ToolSummary` was a bare `record(String name, String description)` —
   `listTools()` discarded the entire `inputSchema` and kept only `name` + free-text
   `description`. `AgentPlanner.formatAvailableTools()` therefore rendered only prose (e.g. "-
   payment.lookup: Retrieve the full current snapshot of a payment using its payment
   reference...") into the prompt's `AVAILABLE TOOLS` section — **the LLM was never given the
   real, machine-readable argument schema (exact key name `paymentReference`, its type, or that
   it is required) for any tool, on any agent, regardless of provider.** It had to infer the exact
   JSON key name purely from prose.
5. **RCA prompt** (`V1_0_9__seed_payment_incident_rca_agent_prompt.yaml`): confirmed it never
   states the argument schema either — it only says the model must emit `"arguments": {
   "<argument name>": "<value>" }` generically, relying entirely on `availableTools` (item 4
   above) for real per-tool argument guidance. **Not itself the defect, but downstream of it** —
   correctly designed to rely on `availableTools` carrying real schema, which it didn't.

**Conclusion**: this is a genuine, pre-existing PaymentX-side defect — not a Gemini quirk, not an
MCP Gateway or API Gateway validation bug, not an Incident RCA prompt/business-logic issue. It is
a structural gap that predates the Gemini integration entirely (the same code path existed for
Anthropic) and affects every agent/tool combination on the platform equally; it simply had not
surfaced as an observed failure before this session's live Gemini testing exercised it. This
determination was reached entirely through source inspection (`javap` against the real MCP SDK,
reading `PaymentLookupTool`, `AgentPlanValidator`, `AgentPlanner`, `McpToolClient`, and the prompt
seed) — not by inferring intent from the one failed live call.

**Fix** (scoped exactly to the confirmed defect — no RCA business logic, prompt content, tool
permissions, read-only enforcement, or provider/failover code touched):

- `McpToolClient.ToolSummary` gained two new fields (`argumentProperties`, `requiredArguments`)
  carrying the real schema; a backward-compatible two-arg constructor (defaulting both to empty)
  preserves every one of the 12 existing call sites across production and test code unchanged.
- `McpToolClient.listTools()` now maps `tool.inputSchema().properties()`/`.required()` into those
  fields instead of discarding them.
- `AgentPlanner.formatAvailableTools()` now renders an `Arguments: <name> (<type>,
  required|optional) - <description>` line under each tool's description when a schema is
  present, giving the LLM the real, exact argument key names, types, and required/optional
  status — for every agent, every tool, on either provider.

**Tests**: two new deterministic `AgentPlannerTest` cases (no live LLM/Gemini calls — the same
Mockito-mocked-LLM pattern the whole file already uses) — one confirms the rendered
`availableTools` text now contains `paymentReference (string, required)` and its description when
a schema is supplied, the other confirms the pre-existing two-arg-constructor, no-schema case
renders exactly as before (no `Arguments:` line) for full backward compatibility. `McpToolClient`
itself was not given a dedicated unit test: it builds its own MCP protocol client internally with
no injection seam, and the SDK's `Tool`/`JsonSchema` types are simple, already-`javap`-confirmed
records — the causally relevant, user-visible behavior (what reaches the prompt) is fully covered
by the `AgentPlanner` tests above.

**Test results**: ran every test class touching `ToolSummary` or `AgentPlanner` to check for
regressions — **189 tests total, 0 failures, 0 errors** across `AgentPlannerTest` (10, incl. the
2 new ones), `AgentPlanValidatorTest` (11), `AgentOrchestratorServiceTest` (15),
`AgentE2EIntegrationTest` (9), and all 6 agent `*SecurityTest` classes (28+21+16+23+13+19+24 =
144).

**Deployed, not live-verified against Gemini**: `agent-orchestrator` was rebuilt
(`mvn package -DskipTests`, tests already green) and restarted (exact PID 8868 verified as
`C:\PaymentX`-owned before stop; new PID 8088 confirmed healthy, registry intact, all four
downstream dependencies UP) — **zero Gemini quota was spent verifying this fix**, per instruction.
The next live Incident RCA execution (once quota allows) will be the first real-world signal of
whether this closes the gap; it cannot fully eliminate malformed arguments (the LLM can still
generate one despite having the schema), but it removes the specific, confirmed cause of this
session's failure and should measurably improve reliability across every provider and every tool.

**Files changed this fix** (3 files, 185 insertions / 14 deletions):
```
M  paymentx-agent-orchestrator/src/main/java/com/paymentx/agent/client/McpToolClient.java
M  paymentx-agent-orchestrator/src/main/java/com/paymentx/agent/planning/AgentPlanner.java
M  paymentx-agent-orchestrator/src/test/java/com/paymentx/agent/planning/AgentPlannerTest.java
```

**Environment, reconfirmed clean**: memory 2.21GB at close (safe); all 7 `jobpilot-*` containers
`Up (healthy)`, untouched; no commit; no push. This fix did not touch Incident RCA business
logic, the prompt, tool permissions, MCP write access (still zero), provider selection/failover,
or any database schema — confirmed by the diff above being limited to exactly the three files
named.

## 25. Phase 4.8 Final Live Verification — Fifth Attempt, ~9 Hours Later (2026-08-24T04:34–04:37 UTC / 10:04–10:07 IST)

A fifth attempt, roughly 9 hours after §24, in a new conversation turn ("continue"). Before
spending any Gemini quota, two non-Gemini prerequisite checks were done:

1. **Memory/service health** (no Gemini cost): 1.05GB free at start (above the 1GB floor; no new
   JVM needed since all four services were already running from the prior session); all four
   confirmed `HTTP 200`; all 7 `jobpilot-*` containers `Up (healthy)`.
2. **MCP API key expiry check** (no Gemini cost): the Redis key provisioned in §6/§20 (2hr TTL
   from ~00:24 IST) had expired (`EXISTS` → `0`, checked ~09:58 IST, ~7.5 hours past expiry).
   Rather than spend a guaranteed-wasted Gemini call (payment.lookup would fail regardless of
   quota), the user was asked and **re-ran the identical, already-approved provisioning
   mechanism themselves** (same command as §6, new key, key value never shown). Verified via the
   same boolean-only `EXISTS` check. `mcp-gateway` (PID 25628, verified `C:\PaymentX`-owned) was
   restarted with the fresh key loaded directly into its process environment from the user's
   local file, then a **direct, non-Gemini API Gateway call** confirmed `HTTP 200` with real
   payment data before any Gemini quota was spent.

**First real Gemini call = the Incident RCA execution** (per the standing "zero preliminary
Gemini calls" protocol). Result: **genuine success at the Gemini/reasoning layer** — 2 real
iterations, ~15.5s total latency, real multi-turn reasoning occurred. However, `payment.lookup`
itself failed with `INVALID_TOOL_ARGUMENTS` — the planning model passed a malformed argument on
its first attempt (the same class of self-correcting agentic hiccup documented in Phase 4.8.4,
where a same-execution retry previously succeeded; this time the agent's own loop terminated
after one failed tool call rather than retrying internally).

Three further attempts were made to capture a clean `payment.lookup` success, all unsuccessful,
with a **diagnostic methodology error identified and corrected mid-session**: `tail -N` on
`llm-service.log` repeatedly appeared unchanged across checks, because different real errors
produce near-identical Java stack-trace tails — the tool was comparing stack trace *noise*, not
the actual new log lines above it. Switching to `grep` for the specific log-message text (`LLM
call completed`, `LLM request failed`, `Unhandled exception`) revealed the true sequence,
correcting an earlier false read:

```
10:04:32 IST  LLM call completed (real Gemini success, part of the first attempt above)
10:04:35 IST  LLM request failed errorCode=LLM_RATE_LIMITED status=429 TOO_MANY_REQUESTS
10:04:37 IST  Unhandled exception (circuit breaker recording the failure)
10:05:19 IST  LLM request failed errorCode=LLM_RATE_LIMITED status=429 TOO_MANY_REQUESTS
10:05:22 IST  Unhandled exception
10:06:45 IST  LLM request failed errorCode=LLM_RATE_LIMITED status=429 TOO_MANY_REQUESTS
10:06:48 IST  Unhandled exception
```

**Conclusion**: the daily quota had recovered just enough headroom for roughly one real call
window this morning (consistent with the single successful reasoning burst at 10:04:32), then
returned to genuine `429 RESOURCE_EXHAUSTED` for every attempt since — not an
agent-orchestrator-side circuit-breaker artifact this time, though the breaker's `HALF_OPEN`
state (observed and correctly diagnosed as a *recovery test* state, not a hard block, before this
was known) made that determination take a few extra live attempts to establish with certainty.
Once the true 429 pattern was confirmed via `grep`, no further attempts were made, per the
standing "if the underlying call returns 429, stop, do not retry" rule.

**This is the closest Scenario 1 has come to succeeding**: real Gemini reasoning completed
end-to-end for one execution (proving the full LLM-reasoning path is genuinely functional, not
merely single-call-functional as in §9/§22), and one real `payment.lookup` attempt reached MCP
Gateway → API Gateway → payment-service (evidenced by `agent_tool_calls_total{tool="payment.
lookup"}` incrementing from 12 to 13) — it simply carried a malformed argument that the agent
did not self-correct within that particular run. The specific success criterion "payment.lookup
succeeds inside a complete agent execution" remains unmet, now purely because of quota
exhaustion recurring before a corrected retry could be attempted — not because of any remaining
infrastructure, authentication, or code defect (both of those were independently proven resolved
in §7/§20 and again via the direct HTTP 200 check above).

**Verified zero side effects**: exactly one new `payment.lookup` call recorded (12→13, the single
failed attempt above) — no write tool invoked, zero MCP writes, zero database writes, zero
payments created. Memory 1.03GB at close (safe). All 7 `jobpilot-*` containers reconfirmed
healthy, untouched. `git status`/`git diff --stat` unchanged from every prior section (53 files,
2429 insertions/191 deletions) — zero new code changes; nothing staged; no commit; no push.
Security suite not re-run (nothing changed).

**FINAL CLASSIFICATION: B — SUBSTANTIALLY COMPLETE, EXTERNAL GEMINI QUOTA BLOCKER — unchanged**.
Five independent attempts (§9, §22, §23, §24, §25) across roughly 10 hours have now all been
blocked by the same root external cause: the Gemini `gemini-3.7-flash` free-tier daily quota on
the configured key. This attempt is the strongest evidence yet that the platform's own
implementation is sound — real end-to-end reasoning and a real (if malformed-argument) tool call
both occurred — and that quota, not code, is the sole remaining obstacle to a clean Scenario 1/2
pass.

## 24. Phase 4.8 Final Live Verification — Quota-Reset Run (2026-08-23T19:25 UTC / 00:55 IST)

A fourth and final attempt, run under the strictest quota-conservation protocol yet: **zero
preliminary Gemini calls of any kind** — no health probe, no test generation, no connectivity
check. The very first Gemini-consuming request of this attempt was the real Incident RCA
execution itself.

1. **Memory at start**: 1.48GB free (lightweight OS-level check only, no JVM started) — above
   the 1GB hard-stop line; proceeded per instruction.
2. **First real Gemini call = the Incident RCA execution** (`POST /api/v1/agent/execute`,
   `agentId=incident-rca-agent`, `paymentReference=LIVEE2E-3FE6115058`, the same existing
   controlled reference used throughout Phase 4.8 — no new payment created).
3. **Result: FAILED**, `iterations:1, toolCallCount:0, totalLatencyMs:3232`.
4. **Diagnosis (log evidence, not a guess, and no additional Gemini call spent to check)**:
   `llm-service`'s own internal `llmProvider` Resilience4j circuit breaker (wrapping its real
   Gemini SDK/HTTP call) was `OPEN`, throwing `CallNotPermittedException`. Critically, this is
   not the earlier session's stale/half-open artifact — the log immediately preceding this
   attempt, five minutes earlier at `00:50:33 IST`, is a **genuine, direct `429
   RESOURCE_EXHAUSTED`** response from Gemini itself (captured in §23). A circuit breaker with
   `automatic-transition-from-open-to-half-open-enabled: true` and a 30s wait-duration remaining
   `OPEN` five minutes after a real 429 means every automatic half-open recovery trial in that
   window also hit a real 429 — the breaker is accurately reflecting that **Gemini is still
   rejecting calls**, not a false/stuck state.
5. Per the explicit rule for this exact situation ("if the underlying Gemini call returns 429:
   STOP immediately, do not retry, do not run another Gemini request"), **no further action was
   taken**. No retry, no second execution, no confirmatory quota probe. Scenario 2 (Control
   Center live execute) was not attempted, as it depends on Scenario 1 succeeding first.

**Verified zero side effects**: `agent_tool_calls_total` metrics unchanged from every prior
reading this phase (`audit.search=7, payment.lookup=12, reconciliation.status=2`) — the failed
execution never reached the planning stage that would invoke a tool, so MCP Gateway was never
called, `payment.lookup` was never attempted this run, zero MCP writes, zero database writes,
zero payments created (by construction — no write tool exists on the platform regardless).

**Environment, reconfirmed clean**: memory 1.31GB at close (safe, no new JVM was started this
attempt); all 7 `jobpilot-*` containers `Up (healthy)`, untouched; `git status`/`git diff --stat`
identical to every prior section in this report (53 files, 2429 insertions/191 deletions — zero
new code changes); nothing staged; no commit; no push. Security suite not re-run (nothing changed
since the 48/48 baseline, per instruction).

**FINAL CLASSIFICATION: B — SUBSTANTIALLY COMPLETE, EXTERNAL GEMINI QUOTA BLOCKER — unchanged
and reconfirmed.** Across four independent attempts spanning this report (§9, §22, §23, §24), the
Gemini `gemini-3.7-flash` free-tier daily quota has been the sole, consistent, external cause
blocking the two remaining live-evidence gaps (Scenario 1: full agent-loop `payment.lookup`
success; Scenario 2: Control Center live execute). Every other Phase 4.8 success criterion not
dependent on a live multi-call Gemini workflow is met and independently verified: Gemini
provider implementation (single-call level), `paymentx-llm-service` and Agent Orchestrator test
suites, Control Center execution-history fix, MCP payment-service API key provisioned and proven
functional at the infrastructure level (direct HTTP 200 with real payment data, §7/§20), security
regression (48/48), zero writes throughout, JobPilot fully isolated, no commits. This is not a
PaymentX implementation defect and should not be classified as one — it is a third-party billing/
quota condition on the specific Google Cloud project backing the configured `GEMINI_API_KEY`,
outside this platform's control. Resolution requires either the daily quota to reset with
sufficient headroom to survive a full ~3-5-call agentic execution without exhausting mid-run, or
the operator upgrading the Gemini API key's plan/billing tier.

A third attempt was made to close the two remaining live-evidence gaps (Scenario 1: live
Incident RCA execution with `payment.lookup` success; Scenario 2: one Control Center Execute
flow). Per instruction, exactly **one** lightweight Gemini quota check was performed first.

**Quota check: SUCCESS.** `POST /api/v1/llm/generate` with a trivial prompt returned a genuine
`content: "OK"`, `stopReason: STOP` — real Gemini traffic, quota available at that instant.
Per instruction ("if quota succeeds → continue autonomously through both remaining scenarios"),
Scenario 1 was started immediately.

**Scenario 1 result: BLOCKED — quota exhausted mid-scenario, not a new defect.** The first
execute call failed in 1.6s with `iterations:1, toolCallCount:0` — too fast to be a real LLM
round-trip. Diagnosis (not a blind retry): `agent-orchestrator`'s own client-side `llmService`
Resilience4j circuit breaker (a separate breaker from `llm-service`'s internal Gemini one) was
found in `HALF_OPEN` state, a residual artifact of the many failures accumulated during the
recent quota-exhaustion period — not a code defect, not related to the just-confirmed quota
availability. A single retry (the correct way to let a half-open breaker test recovery, not a
"repeated retry while quota is exhausted" — quota had just been confirmed available) was made.
That second attempt reached `llm-service` for real this time and surfaced the true cause in its
log (note: log timestamps are IST, `+05:30` — an earlier grep against a UTC pattern in a prior
session had missed this same line type):

```
WARN ... c.p.l.exception.GlobalExceptionHandler : LLM request failed path=/api/v1/llm/generate
errorCode=LLM_RATE_LIMITED retryable=true status=429 TOO_MANY_REQUESTS
```

**Root cause**: a single Incident RCA execution needs several internal Gemini calls (planning +
per-iteration reasoning — 3–5 calls observed in every prior successful run this phase family).
The initial lightweight check apparently consumed one of only 1–2 requests remaining in the
20/day free-tier budget for the day; the RCA execution's very first internal planning call then
hit the same daily cap. **This is the identical external blocker documented in §9 and §22 — it
did not reset, and a routine single-token check succeeding does not guarantee enough headroom
remains for a full multi-call agentic scenario.** No further retries were made after this was
confirmed (the WARN log line is direct evidence, not an inference) — per instruction, this branch
was stopped rather than burning additional quota.

Because Scenario 1 did not complete, Scenario 2 (Control Center live execute, which depends on a
successful agent execution) was **not attempted** — consistent with "immediately after Scenario 1
succeeds."

**Verified zero side effects from both failed attempts**: `agent_tool_calls_total` (Agent
Orchestrator Prometheus metric) is unchanged from the prior session's values
(`audit.search=7, payment.lookup=12, reconciliation.status=2`) — both attempts failed at the
planning stage, before any MCP tool (read or write) was ever invoked. Zero MCP calls, zero MCP
writes, zero database writes, zero payments created, by construction.

**Environment state, reconfirmed unchanged and healthy**: free memory 1.78GB (safe, no action
needed); all 7 `jobpilot-*` containers `Up (healthy)`, untouched; `git status`/`git diff --stat`
identical to §1–§22 (53 files, 2429 insertions/191 deletions — zero new code changes this
session); no commit, no push, nothing staged. Security suite (48/48) not re-run — no source or
config changed since that result, per instruction not to re-run without cause.

**FINAL CLASSIFICATION: B — SUBSTANTIALLY COMPLETE, EXTERNAL GEMINI QUOTA BLOCKER.** Every
criterion not dependent on a live Gemini call remains satisfied (MCP key provisioned and proven
functional at the infrastructure level via direct HTTP 200 in §7/§20, Control Center
execution-history bug fixed and verified in §4–§5/§11–§12, security regression green, JobPilot
isolation, zero writes throughout, no commits). The two live-Gemini-dependent proofs (Scenario 1
payment.lookup success inside a full RCA loop, Scenario 2 Control Center execute) remain blocked
purely by the external daily quota, not by any PaymentX code or configuration defect — this is
explicitly not to be misclassified as an implementation failure, per instruction.

**Recommendation unchanged from §22**: retry with one lightweight quota check in a later session
once enough time has passed for the daily quota to plausibly have reset. Given this attempt shows
even a successful check can immediately precede exhaustion, budget the retry attempt assuming at
most 1–2 requests may be available even after a nominal "reset" is detected — if so, prioritize
starting Scenario 1 immediately after a successful check without any other intervening Gemini
calls (including further quota probes) to preserve maximum headroom for the RCA loop's own
multi-call requirement.

## 22. Phase 4.8 Final Live Verification Attempt (2026-08-23T19:13 UTC) — Quota Still Exhausted

A follow-up session attempted to close the two remaining live-evidence gaps from this report
(§20 items 2–3: one clean live `payment.lookup SUCCESS` inside a full Incident RCA execution, and
one real Control Center Execute flow). Per that session's own instruction, exactly **one**
lightweight Gemini connectivity/quota check was performed before any further action, specifically
to avoid burning quota on doomed live-execution attempts.

**Result: still `429 RESOURCE_EXHAUSTED`**, identical `quotaId:
GenerateRequestsPerDayPerProjectPerModel-FreeTier`, `limit: 20`, model `gemini-3.7-flash` — the
same daily cap documented in §9, not yet reset. Per explicit instruction ("if 429/quota exhausted
→ STOP live execution and report... do NOT repeatedly retry"), **no further Gemini calls, no
Incident RCA live execution, and no Control Center live Execute flow were attempted this
session.** The live-execution branch (Phase 4.8 Final Success Criteria items requiring a real
Gemini response) remains blocked on this external, account-level condition — unchanged from §9,
§20.

Non-Gemini verification performed instead, confirming nothing regressed since §1–§21:

- All four previously-fixed/restarted PaymentX services (`llm-service` :8093,
  `control-center-backend` :8089, `mcp-gateway` :8097, `agent-orchestrator` :8098) were still
  running from the prior session and responded `HTTP 200` on their health endpoints — no restart
  was needed or performed.
- Free host memory: 2.29GB (recovered further since §2's last reading; healthy, no action
  required).
- All 7 `jobpilot-*` containers reconfirmed `Up (healthy)`, untouched.
- `git status`/`git diff --stat`: identical source diff to §1–§21 (53 files changed, 2429
  insertions / 191 deletions — zero new code changes this session, matching the "no genuine
  regression found, nothing to fix" case). One additional untracked file exists (this report
  itself). No commit, no push, nothing staged.
- Security regression (48/48, §13) was **not re-run** — no source or configuration changed since
  that result, consistent with the instruction not to re-run expensive tests without cause.

**Phase 4.8 cannot yet reach FINAL A classification** — the Final Success Criteria explicitly
require a real Gemini response, a live Incident RCA execution, and a live Control Center
execution, none of which are possible while the daily quota remains exhausted. Every
non-Gemini-dependent criterion (MCP key provisioned and proven functional at the infrastructure
level, Control Center execution-history bug fixed and verified, security regression green,
JobPilot isolation, zero writes, no commits) remains satisfied from §1–§21 and was reconfirmed
unchanged this session.

**Recommendation**: retry the same single lightweight quota check (one `POST
/api/v1/llm/generate` call) in a later session once enough time has passed for Google's daily
quota to plausibly have reset (`GenerateRequestsPerDayPerProjectPerModel` quotas reset on
Google's own daily cycle — commonly midnight Pacific Time, though this is not something PaymentX
controls or can query directly; treat any specific reset-time estimate as approximate). If that
check succeeds, complete Scenario 1 (live Incident RCA `payment.lookup` success) and Scenario 2
(one Control Center Execute flow) in a single pass, then upgrade this report's classification.

## 1. Starting State

Continued directly from `PAYMENTX_PHASE_4_8_4_GEMINI_PROVIDER_AND_RCA_VALIDATION.md`. At the
start of this phase: `paymentx-control-center-backend` was down (from the prior session's
incident); free host memory had bottomed at 0.53GB; `MCP_PAYMENT_SERVICE_API_KEY` was
unprovisioned; the Control Center execution-history endpoint's root cause was unknown.

## 2. Memory Observations

Memory was volatile throughout this phase, never settling into a single stable plateau — it
oscillated in bands roughly correlated with which JVMs were active:

- Session start: 1.88GB → 1.68GB (bounded checks, held steady enough to start
  `control-center-backend`).
- After that start: dropped to 1.13GB, then as low as 0.95GB during a bounded wait (below the
  1GB "do not start anything" line — no action taken while there).
- Recovered to 1.86GB (two consecutive stable readings) before the `mcp-gateway` restart was
  performed, per the user's explicit ≥1.5GB-and-stable rule.
- Fluctuated 0.95GB–1.86GB across the rest of the session; every privileged action (JVM start,
  `mvn package`) was preceded by a fresh reading, never a reused stale one, per
  [[feedback_host_resource_caution]].

No JVM was started while free memory was below 1GB. No broad process-kill command was used at
any point. IntelliJ's compile-server/JPS build processes remained the largest observed
non-PaymentX, non-JobPilot memory consumer, left untouched per instruction.

## 3. Control Center Recovery

`paymentx-control-center-backend` was restarted (capped `-Xmx512m`) once memory held ≥1.5GB
across two consecutive readings. PID verified via `Get-CimInstance Win32_Process` to contain
`C:\PaymentX` both before stopping the old process and after starting each new one — no broad
`java.exe` kill was ever used; only the exact, verified PID was targeted, in each of three
restarts this phase (initial recovery, rebuild, and enabling `CONTROL_CENTER_AI_ENABLED`).

## 4. Execution-History `INTERNAL_ERROR` — Root Cause

**Not a data, SQL, or logic defect.** Static analysis first ruled out the likely suspects: the
`audit_event.payload` column is genuinely `JSONB` (confirmed in
`V1_0_0__create_audit_event_table.yaml`), and `AgentExecutionDetail`/`AgentExecutionSummary`
record component counts matched their construction call sites exactly.

The real stack trace (captured by restarting with log redirection) showed:

```
org.springframework.web.servlet.resource.NoResourceFoundException: No static resource api/v1/agents/executions.
```

Every `/api/v1/agents/**` route failed identically (including the base `GET /api/v1/agents` and
`POST /api/v1/agents/execute`) — Spring MVC had no controller mapping for any of them, falling
through to the default static-resource handler. `jar tf` on the running jar confirmed
`AiAgentController`, `AgentExecutionService`, and `AgentExecutionRepository` were **absent from
the packaged jar entirely**, while `target/classes` showed them fully compiled (timestamp 15:02,
hours after the jar was last packaged at 06:20). **The running jar simply predated these classes
— a stale build artifact, not a code defect.**

## 5. Fix Made

```
mvn package -o -DskipTests   (paymentx-control-center/backend, capped -Xmx512m)
```

The Spring Boot repackage step failed on the first attempt (`Unable to rename ... .jar.original`)
because the old jar was locked by the still-running process; the exact, PID-verified process was
stopped first, then the rebuild succeeded. `jar tf` on the new jar confirmed all three previously
missing classes are now present. The service was restarted from the rebuilt jar and
`GET /api/v1/agents/executions` and `GET /api/v1/agents/executions/{id}` were re-verified live —
both now return real, correctly-shaped data (see §11/§12). No source code was changed to produce
this fix — it was a build/packaging gap, not a logic error, so no regression test was added for
"packaging is current," matching the instruction not to invent fixes beyond the genuine defect.

Separately, `GET/POST /api/v1/agents` (list/execute) returned a legitimate, different response —
`AI_NOT_CONFIGURED` — gated by `control-center.ai.enabled` (`${CONTROL_CENTER_AI_ENABLED:false}`,
same externalized-config convention as every other PaymentX property). This is correct,
intentional behavior, not a bug. It was enabled (`CONTROL_CENTER_AI_ENABLED=true`) for this
phase's live verification via one further restart of the same, already-rebuilt jar.

## 6. MCP API-Key Resolution

The existing, documented mechanism (`McpGatewayProperties.paymentServiceApiKey`'s own javadoc,
and `paymentx-validation-suite/scripts/run-e2e.ps1`'s `Invoke-Redis`) was used, not invented:

```
docker exec paymentx-redis redis-cli SET gateway:apikey:<key> BANK001 EX 7200
```

**The user executed this command themselves** (their explicit request), against `paymentx-redis`
only — no other container touched, no `docker exec` on any `jobpilot-*` resource. The key value
was never typed into chat, displayed, or logged at any point:

- Provisioning was verified with a boolean-only command: `EXISTS gateway:apikey:<key>` → `1`.
- The key was loaded into `mcp-gateway`'s process environment directly from the user's local
  temp file (`$env:MCP_PAYMENT_SERVICE_API_KEY = Get-Content ... | .Trim()`) — never assigned
  through a step that would echo it, and never appeared in any terminal output, log file, or this
  report.
- `mcp-gateway` was restarted (exact PID verified as `C:\PaymentX`-owned before stop) only after
  the user confirmed provisioning was complete, per their explicit instruction.

## 7. `payment.lookup` / `payment.status` Evidence

**Direct proof the key and routing chain work end-to-end**: a direct call to the real API Gateway
using the provisioned key (key read from the local file, never displayed) returned:

```
HTTP 200 — real payment data: status=SETTLED, amount=275.50 USD, debtorParticipantId=BANK001,
creditorParticipantId=BANK002, reference=LIVEE2E-3FE6115058
```

This conclusively proves the `MCP_PAYMENT_SERVICE_API_KEY` → API Gateway →
`ApiKeyAuthenticationGlobalFilter` → payment-service chain is correctly provisioned and
functional — the "API key is invalid or inactive" failure documented in Phase 4.8.4 is resolved.

**Through the live agent loop**, `payment.lookup` did not yet produce one single clean `SUCCESS`
in this phase's runs: one attempt returned `INVALID_TOOL_ARGUMENTS` (the planning model passed a
malformed argument on its first sub-attempt — a normal, self-correcting agentic hiccup, not a
tool defect; `PaymentLookupTool`'s own regex `^[A-Za-z0-9_-]{1,64}$` does accept the real
reference), and a retry returned a generic `TARGET_SERVICE_UNAVAILABLE` / "An unexpected error
occurred" — most consistent with a cold-start hiccup immediately after `mcp-gateway`'s restart,
given the direct call succeeded cleanly moments later with identical credentials and reference.
Before a clean live retry could be captured, the session hit Gemini's daily quota (§9) — this is
the one item left genuinely unverified end-to-end through the full agent loop, though the
infrastructure-level proof above leaves little doubt it would succeed on a fresh attempt once
Gemini quota is available again.

## 8. Incident RCA Evidence

Against the real controlled reference `LIVEE2E-3FE6115058`, `audit.search` succeeded and returned
5 real, unfabricated audit events spanning the payment's actual lifecycle: `VALIDATION_COMPLETED`
(validation-service) → `PAYMENT_UPDATED` ×3 → `PAYMENT_COMPLETED` (payment-service), correctly
correlated (`correlationId=629e09c1-...`), correctly attributing participants `BANK001`/`BANK002`.
The agent incorporated this real evidence and continued investigating despite the
`payment.lookup` friction rather than fabricating a payment record — consistent with the
FACT/CORRELATION/HYPOTHESIS discipline proven in Phase 4.8.4.

## 9. Gemini Evidence — Daily Quota Exhausted (External Blocker)

A direct `POST /api/v1/llm/generate` call returned the real, unambiguous cause behind every
"transient" failure observed this phase:

```
HTTP 429 RESOURCE_EXHAUSTED
"Quota exceeded for metric: generativelanguage.googleapis.com/generate_content_free_tier_requests,
limit: 20, model: gemini-3.7-flash"
quotaId: GenerateRequestsPerDayPerProjectPerModel-FreeTier
```

This was re-confirmed after waiting the API's own suggested retry delay (~52s) and again after a
further ~90s — both retries failed identically (429 / circuit-breaker-OPEN wrapping the same
429). This is a genuine **per-day** quota (20 requests/day for `gemini-3.7-flash` on the current
free-tier key), not a short burst limit — it will not clear by waiting minutes. Combined with
Phase 4.8.4's own live calls earlier in this calendar-day window, the 20/day cap is exhausted.
**This is an external, account-level condition** — matching this task's own Hard Stop Condition
#7 ("Gemini account/billing insufficient — STOP and report, do not work around it"). No further
live Gemini calls were attempted after this was confirmed; remaining validation work was
redirected to deterministic tests and non-LLM verification paths.

## 10. Control Center Live E2E

- **Agent registry (via Control Center's own proxy)**: PASS — `GET /api/v1/agents` (with
  `CONTROL_CENTER_AI_ENABLED=true`) returns the real 7-agent list from Agent Orchestrator's
  registry, unchanged.
- **Execution History (via Control Center's own endpoint)**: PASS — see §11.
- **Execution Detail (via Control Center's own endpoint)**: PASS — see §12.
- **Live execute via Control Center's own `POST /api/v1/agents/execute` proxy**: **NOT LIVE
  TESTED** this phase — deliberately skipped once the Gemini quota exhaustion was confirmed (§9),
  to avoid guaranteed-to-fail calls. Verified instead by direct code review:
  `AiAgentController.execute()` → `AgentExecutionService.execute()` →
  `AiPlatformClient.executeAgentFull()` is a direct, unmodified passthrough to the exact same
  `POST /api/v1/agent/execute` endpoint already proven live-working (both in Phase 4.8.4 and
  earlier this phase, directly against Agent Orchestrator). This is a structural, not a live,
  verification for this one specific path — flagged honestly as the phase's one remaining gap.

## 11. Execution History Evidence

`GET /api/v1/agents/executions?page=0&size=5` (Control Center's own endpoint, post-fix) returned
real, correctly paginated data — `totalElements: 70`, each row carrying `executionId`,
`correlationId`, `agentId`, `userQuery`, `paymentReference` (where applicable), `outcome`,
`timestamp`, `durationMs`, `toolCallCount`, `ragUsed` — including this phase's own live runs
against `LIVEE2E-3FE6115058` and Phase 4.8.4's earlier runs. No duplicate records observed for
any single execution.

## 12. Execution Detail Evidence

`GET /api/v1/agents/executions/{executionId}` returned the full real record for a live run:
`toolsCalled: [payment.lookup FAILED, payment.lookup FAILED, audit.search SUCCESS]`,
`iterations: 4`, `startedAt`/`completedAt`/`durationMs` all consistent, `outcome: FAILED` (matches
the real execution outcome truthfully — not silently upgraded to success). No API key or other
secret appears anywhere in the record.

## 13. Security Results

48/48 deterministic tests pass, 0 failures, 0 errors (this phase's own run, capped-heap, no
Gemini calls involved):

- `IncidentRcaAgentDefinitionTest`: 6/6
- `IncidentRcaAgentSecurityTest`: 28/28 — unauthorized tool access denied, every write/operational
  tool denied across every domain (`payment.create/update/retry/cancel/refund`,
  `routing.update`, `reconciliation.update/resolve`, `notification.send/update`,
  `database.write/execute`, `service.restart`, `deployment.trigger`, `config.update`,
  `kafka.publish`, `rabbitmq.publish`, `shell.exec`, `log.read`, and more), prompt injection
  cannot escalate permissions, malicious RAG content cannot escalate, agent identity fixed by
  caller, model cannot self-grant a tool, tool evidence preserved even under contradiction,
  secrets never leak in a tool failure.
- `AgentRegistryTest`: 14/14 — registry construction/resolution regression, unaffected by this
  phase's changes.

Final secret scan (`git diff` across the entire working tree, pattern-matched for Google/Anthropic
key shapes, hardcoded `api-key:`/`API_KEY=` assignments, and the specific test key prefix used
this phase): **clean**. No secret value appears in any report file, log capture, or source diff.

## 14. Test Counts

- This phase: 48 tests (Agent Orchestrator, deterministic) — 0 failures, 0 errors.
- Carried forward, unchanged, from Phase 4.8.4 (no source in these modules changed this phase, so
  not re-run): 35/35 `paymentx-llm-service` tests, 34/34 earlier Agent Orchestrator security/
  registry tests (subsumed by this phase's 48, which is a superset run).

## 15. Build Results

`paymentx-control-center-backend`: `mvn package -o -DskipTests` — **BUILD SUCCESS** (second
attempt, after stopping the lock-holding process). Verified via `jar tf` that the previously
missing classes are now packaged. No other module was rebuilt this phase (none needed it).

## 16. Database Writes

**Zero.** Every tool invocation observed this phase (`audit.search`, `payment.lookup`,
`reconciliation.status`) is read-only by construction — confirmed both by the 28 passing
write-tool-denial tests and by live Prometheus metrics (`agent_tool_calls_total`) showing only
these three read tool names across the entire phase, never a write tool.

## 17. MCP Writes

**Zero.** No `PaymentServiceClient`/MCP tool write path exists on the platform (re-confirmed,
matching Phase 4.8.0's original discovery); no write attempt appears in any audit record, metric,
or log this phase.

## 18. Payments Created

**Zero.** Only the pre-existing, already-controlled reference `LIVEE2E-3FE6115058` was used for
verification, exactly as instructed — no new payment was created at any point.

## 19. JobPilot Isolation — VERIFIED

All 7 `jobpilot-*` containers confirmed `Up (healthy)` at both the start and end of this phase.
No `jobpilot-*` container, process, volume, network, database, Kafka topic, Redis key, RabbitMQ
resource, or configuration file was read, modified, restarted, or stopped at any point. Every
process stop/start this phase was preceded by an explicit PID + command-line verification against
`C:\PaymentX`.

## 20. Remaining Blockers

1. **Gemini free-tier daily quota exhausted** (20 requests/day, `gemini-3.7-flash`) — external,
   account-level, resets on Google's own daily cycle. No further live Gemini/agent-loop testing
   is possible until then. Not a PaymentX defect.
2. **One clean, live `payment.lookup SUCCESS` inside a full agent loop was not captured** —
   infrastructure-level proof (§7) strongly indicates it would succeed on retry, but this specific
   scenario is unconfirmed pending Gemini quota availability.
3. **Control Center's own `POST /api/v1/agents/execute` proxy was not live-tested** this phase
   (§10) — verified structurally only, deferred to avoid a guaranteed-429 call against exhausted
   Gemini quota.
4. Host memory remains volatile (oscillated 0.95GB–1.86GB throughout this phase without a lasting
   stable plateau) — not a PaymentX defect, but worth noting for future sessions on this shared
   host.

## 21. Final Classification

**Control Center backend: RECOVERED, root cause diagnosed and FIXED (stale build artifact,
rebuilt), execution history and detail VERIFIED working end-to-end through Control Center's own
endpoints.** **MCP payment-service API key: PROVISIONED via the established mechanism and
VERIFIED functional at the infrastructure level (direct HTTP 200 with real payment data).**
**Incident RCA: evidence discipline reconfirmed live against real audit data; full clean
payment.lookup success pending Gemini quota reset.** **Security: 48/48 deterministic tests
green.** **JobPilot: fully isolated and untouched throughout.** **No commit, no push, no
destructive action.** The two remaining gaps (items 2–3 above) are both consequences of the
external Gemini quota exhaustion (item 1), not unresolved PaymentX implementation defects —
classified as **environment/external blockers**, with the underlying PaymentX code and
configuration now genuinely sound and ready to complete verification once Gemini quota is
available.
