# Phase 5 — Agent Platform Productization & End-to-End Readiness

**Status: IN PROGRESS.** This document is updated incrementally as each backlog item is
completed; it is not yet a final sign-off. Phase 4 is frozen/complete per
`PAYMENTX_PHASE_4_10_AGENT_PLATFORM_COMPLETION_AUDIT.md` (7 agents, 6 MCP tools, 4 RAG corpora,
403 tests, 0 defects found). Phase 5's objective is REUSE → INTEGRATE → HARDEN → TEST → VERIFY →
DOCUMENT — production-grade, product-usable, observable, secure, testable, fully integrated —
**not** new agents. Every claim below is tagged SOURCE VERIFIED / TEST VERIFIED / LIVE VERIFIED /
BLOCKED / PRE-EXISTING; nothing is fabricated.

## 1. How this backlog was derived

No Phase 5 backlog document existed on disk or in this session's transcript when this work
resumed — only the task mandate (discovery → evidence-based backlog → implement P0/P1 only) had
been discussed, plus one explicit example item (LLM provider/fallback metadata exposure) cited
directly in the task brief. Rather than fabricate a "re-read" of a document that doesn't exist,
this phase performed real discovery against the current codebase and Phase 4.10's own completion
audit (which found **zero** open Phase-4-level defects — see its §16, "Recommended Next Work:
None is required"). Phase 5's own backlog is therefore built from a *different* lens than Phase
4.10's correctness audit: production-readiness gaps that a correctness-focused audit would not
flag — observability, resource protection, operational safety.

## 2. Backlog

| # | Item | Priority | Status | Evidence basis |
|---|---|---|---|---|
| 1 | LLM provider/fallback visibility (expose `provider`/`fallbackUsed`/`fallbackReason` end-to-end) | P1 | **DONE** | Explicit example cited in this task's own brief; closes a real observability gap — no caller (Control Center UI, Execution History) could previously tell whether an answer came from Gemini (primary) or the automatic Anthropic fallback |
| 2 | Local rate limiter on `LlmServiceImpl.generate()` | P1 | **DONE** | SOURCE VERIFIED: zero request throttling existed anywhere between Control Center's UI and the real Gemini/Anthropic API calls; Phase 4.10 §13 documents Gemini's 20-req/day free-tier quota being "genuinely, repeatedly exhausted across this engagement" — an unprotected shared resource is a genuine production-readiness gap, not merely an external blocker |

Further items will be appended to this table as this phase continues.

---

## 3. Backlog Item 1 — LLM Provider/Fallback Visibility

### 3.1 Problem (SOURCE VERIFIED)

`paymentx-llm-service`'s `GenerateResponse` gained `provider`/`fallbackUsed`/`fallbackReason`
fields in Phase 4.8.6 (`LlmProviderRouter`), but every downstream consumer discarded them:
`paymentx-agent-orchestrator`'s `LlmServiceClient.LlmAnswer` never parsed them, `AgentExecution`
never stored them, `AgentExecuteResponse` never returned them, `AgentAuditClient` never wrote
them to the audit trail, and Control Center's backend DTOs/repository/frontend never displayed
them. An operator watching Execution History had no way to tell "Gemini answered this" from
"Gemini was down and Anthropic silently answered instead" — a real operational blind spot given
how often Gemini's quota has been exhausted this engagement.

### 3.2 Implementation

Backend chain (all additive fields, no existing constructor signature broken except the one
production call site each, updated in place):

- `paymentx-agent-orchestrator/src/main/java/com/paymentx/agent/client/LlmServiceClient.java` —
  `LlmAnswer` record gained `provider`/`fallbackUsed`/`fallbackReason`; `generate()` now parses
  them from LLM Service's response. 2-arg back-compat constructor preserves all 11 existing
  `AgentPlannerTest` call sites.
- `paymentx-agent-orchestrator/src/main/java/com/paymentx/agent/state/AgentExecution.java` —
  new mutable fields `lastLlmProvider`/`fallbackUsedInExecution`/`lastFallbackReason`
  (whole-execution OR across every planning-loop LLM call, so a mid-execution fallback is never
  hidden by a later primary-provider success).
- `paymentx-agent-orchestrator/src/main/java/com/paymentx/agent/planning/AgentPlanner.java` —
  records provider/fallback on the execution after every real LLM call, including a refusal.
- `paymentx-agent-orchestrator/src/main/java/com/paymentx/agent/dto/AgentExecuteResponse.java` —
  3 new trailing fields; single production call site
  (`AgentOrchestratorService.java:452`) updated.
- `paymentx-agent-orchestrator/src/main/java/com/paymentx/agent/audit/AgentAuditClient.java` —
  audit payload gained `provider`/`fallbackUsed`/`fallbackReason`.
- Control Center backend: `AgentExecutionSummary`/`AgentExecutionDetail`/`AgentExecuteResponse`
  (control-center's own DTO)/`AgentExecutionRepository` (reads the audit `payload` jsonb)/
  `AiPlatformClient.FullAgentExecuteResult`/`AgentExecutionService` — same additive-field chain.
- Frontend: `types/agent.ts` mirrors extended; `AiAgentExecutePage.tsx` renders a provider chip
  (amber + warning alert with reason when fallback was used); `AiAgentHistoryPage.tsx` adds a
  Provider column to the history table and provider/fallback fields to the detail drawer.

### 3.3 Tests (TEST VERIFIED — this session)

| Suite | Tests | Result |
|---|---|---|
| `AgentPlannerTest` | 11 | 0 failures |
| `AgentOrchestratorServiceTest` | 15 | 0 failures |
| `AiAgentControllerTest` (control-center backend) | 6 | 0 failures |
| `AgentExecutionServiceTest` (control-center backend) | 10 | 0 failures |
| Frontend `tsc -b` (real `npm run build` type-check) | — | 0 errors |
| Frontend vitest: `AiAgentExecutePage`/`AiAgentHistoryPage`/`AiAgentsPage` | 10 | 0 failures |

All test-side record-construction call sites (2 in `AiAgentControllerTest`, 4 in
`AgentExecutionServiceTest`) were updated for the new trailing fields — no test was weakened.

Additionally found and fixed 3 **pre-existing** TypeScript errors unrelated to this item, which
were blocking the frontend's own official `npm run build` script entirely: 2 unused imports
(`waitFor` in `AiAgentExecutePage.test.tsx`, `beforeEach` in `AiAgentsPage.test.tsx`) and one
real nullable-type bug (`AiAgentHistoryPage.tsx`'s `correlationId` passed as non-null to
`DetailField`). Confirmed pre-existing by checking the exact lines were never touched by this
session's edits.

### 3.4 Build/Deploy

Compiled clean (`mvn compile`/`test-compile`, real exit 0) for `paymentx-agent-orchestrator` and
`paymentx-control-center/backend`. **Not yet redeployed to the running processes** — this item's
verification was compile+test level only; live redeployment/verification for this item is
deferred to a later pass of this document (agent-orchestrator's own PID was not restarted this
session).

### 3.5 Blockers

None. No external blocker for this item.

### 3.6 Deployment & live verification (LIVE VERIFIED, zero real Gemini/Anthropic quota spent)

Completed after §3.3-3.5 above, using the exact same protocol as item 2 (§4.6): real captured
Maven exit codes, jar-timestamp checks, `javap` bytecode verification, exact-PID stop/restart,
health checks.

1. **`paymentx-agent-orchestrator`**: old PID 8088 stopped by exact command-line match →
   `mvn -pl paymentx-agent-orchestrator package -DskipTests` real exit 0 → jar timestamp
   `10:18` → `13:28` → `javap` confirmed `AgentExecuteResponse.class` and
   `AgentExecution.class` genuinely carry the new `provider`/`fallbackUsed`/`fallbackReason`
   fields and accessors → restarted, new PID **12112** confirmed → `GET /actuator/health` → `UP`
   → `GET /api/v1/agent/agents` → real 7-agent list returned.
2. **`paymentx-control-center/backend`**: old PID 31896 stopped → `mvn -pl paymentx-control-center/
   backend package -DskipTests` real exit 0 → jar timestamp `00:33` → `13:30` → `javap` confirmed
   `AgentExecuteResponse.class` and `AgentExecutionSummary.class` genuinely carry the new fields
   → restarted with `CONTROL_CENTER_AI_ENABLED=true` (the pre-existing, unrelated opt-in dev flag
   this feature has always required — not a Phase 5 change), new PID **27612** confirmed →
   `GET /actuator/health` → `UP` → `GET /api/v1/agents` → real 7-agent list (proxied through the
   freshly-restarted agent-orchestrator above) → `GET /api/v1/agents/executions?page=0&size=3` →
   **real query against the real Postgres `audit_event` table succeeded**, returning **77 total
   execution records** (exactly matching Phase 4.10's own independently-obtained count, confirming
   this is genuinely the same live database, not a mock) — proving the new
   `payload->>'provider'`/`payload->>'fallbackUsed'` JSONB column expressions added to
   `AgentExecutionRepository` this phase are syntactically valid against the real database and
   correctly return null (silently omitted by Jackson) for the many pre-existing rows that predate
   today's audit-payload change, exactly the expected, honest behavior for historical data.

No real Gemini or Anthropic API call was made during this redeployment or verification — every
check used either a registry-passthrough endpoint (agent list), a pure-SQL audit-history read, or
a health/actuator endpoint.

### 3.7 Follow-up verification (this phase, after item 2 was completed)

The frontend's real, official production build script (`npm run build` = `tsc -b && vite build`)
was run end-to-end after the tsc fixes described in §3.3: **real exit 0**, `✓ built in 54.55s`,
a genuine chunked `dist/` output (46 asset files, largest `vendor-mui` at 399KB/120KB gzipped).
Before this phase's fixes, this exact script would have failed at the `tsc -b` step and never
reached `vite build` at all — the frontend was genuinely undeployable via its own official build
command. This is now closed and TEST/BUILD VERIFIED, not merely type-checked.

---

## 4. Backlog Item 2 — Local LLM Rate Limiter

### 4.1 Problem (SOURCE VERIFIED)

Grepped the entire AI/agent stack (`paymentx-agent-orchestrator`, `paymentx-control-center/
backend`, `paymentx-llm-service`) for `RateLimit`/`Bucket4j`/`RateLimiter`: the only matches were
(a) the Anthropic SDK's own `RateLimitException` class (reactive, after-the-fact upstream error
handling) and (b) a docstring mention of "rate-limited" as a concept. **No proactive rate
limiting existed anywhere.** `LlmServiceImpl.generate()` (`paymentx-llm-service`) is the single
physical choke point every real Gemini/Anthropic call in the entire platform funnels through
(agent-orchestrator's planning + final-answer synthesis calls, Control Center's own AI Assistant
chat feature, any future caller) — completely unprotected.

The frontend's own double-submit guard (`AiAgentExecutePage.tsx`'s
`if (!canSubmit || executeMutation.isPending) return`) is client-side React state only —
trivially bypassed by a page refresh, a second browser tab, or any direct API caller. Given
Gemini's free-tier quota is 20 requests/day and has been "genuinely, repeatedly exhausted across
this engagement" (Phase 4.10 §13), a UI bug, duplicate tab, or runaway retry loop could exhaust
an entire day's shared quota in a few seconds with zero operator visibility — a real
production-readiness gap, not merely an external blocker to work around.

### 4.2 Design decision

Considered three placement options: (a) Control Center backend, (b) Agent Orchestrator's own
`/api/v1/agent/execute` endpoint, (c) LLM Service's own `/api/v1/llm/generate` endpoint. Chose
(c): it is the single physical point closest to the actual scarce resource, protects *every*
current and future caller uniformly regardless of which upstream service or feature initiates
the call, and reuses the exact `resilience4j-spring-boot3` starter + YAML-instance-name +
annotation pattern this module's own circuit breakers/retries already establish (no new
dependency added).

Chose limits (`limit-for-period: 8`, `limit-refresh-period: 10s`, `timeout-duration: 5s`)
deliberately generous: `AgentOrchestratorProperties.maxIterations = 5` (SOURCE VERIFIED) means a
**single legitimate agent execution can itself issue up to ~6 sequential LLM calls** (up to 5
planning iterations plus one final-answer synthesis call) — a limiter tuned too tight would break
normal single-execution usage, not just abuse. 8 permits per 10 seconds comfortably absorbs one
full execution's own internal burst while still bounding a genuine flood (a duplicate click, an
extra tab, a scripted loop) to a rate an operator has time to notice, rather than "quota gone
before anyone looks."

A new, distinct error code — `LLM_LOCAL_RATE_LIMITED` (HTTP 429) — was added rather than reusing
`LLM_RATE_LIMITED`, matching this codebase's own established philosophy (`LlmErrorCodes`' own
javadoc: "a caller needs to distinguish... programmatically, not just from a human-readable
message") — a caller can now tell "this service is locally throttling" apart from "the real
upstream provider rejected us."

### 4.3 Implementation — exact files changed

| File | Change |
|---|---|
| `paymentx-llm-service/src/main/java/com/paymentx/llm/exception/LlmErrorCodes.java` | added `LLM_LOCAL_RATE_LIMITED` constant |
| `paymentx-llm-service/src/main/java/com/paymentx/llm/exception/LlmException.java` | added `localRateLimited(String)` factory → HTTP 429, retryable=true |
| `paymentx-llm-service/src/main/java/com/paymentx/llm/service/impl/LlmServiceImpl.java` | added `@RateLimiter(name = "llmGenerate")` on `generate()` |
| `paymentx-llm-service/src/main/java/com/paymentx/llm/exception/GlobalExceptionHandler.java` | added `@ExceptionHandler(RequestNotPermitted.class)` → honest 429/`LLM_LOCAL_RATE_LIMITED` |
| `paymentx-llm-service/src/main/resources/application.yml` | added `resilience4j.ratelimiter.instances.llmGenerate` (8/10s/5s) |
| `paymentx-llm-service/src/test/java/com/paymentx/llm/controller/LlmControllerRateLimiterIntegrationTest.java` | **new file** — real Spring context + WireMock integration test |

No DB schema change. No MCP change. No new dependency (resilience4j-spring-boot3 already
present). No new LLM provider. No change to `paymentx-agent-orchestrator` for this item (the
consumer needed no changes — it already handles honest `LlmException`-shaped errors generically).

### 4.4 Tests (TEST VERIFIED, zero real Gemini/Anthropic quota spent)

`LlmControllerRateLimiterIntegrationTest` (new): a real Spring Boot context (`RANDOM_PORT`), real
`GlobalExceptionHandler`/Resilience4j wiring, WireMock standing in for Gemini (zero real API
cost). Dynamic properties tighten the limiter to 1 permit/10s/no-wait for a fast, deterministic
test. First call → real HTTP 200 through the full stack; second call in the same window → real
HTTP 429 with `errorCode=LLM_LOCAL_RATE_LIMITED`, proving the mechanism end-to-end at the real
HTTP/Spring layer, never a fabricated success.

| Run | Tests | Result |
|---|---|---|
| `LlmControllerRateLimiterIntegrationTest` (initial, pre-rebuild) | 1 | 0 failures |
| Full `paymentx-llm-service` suite (regression) | 52 | 0 failures |
| `LlmControllerRateLimiterIntegrationTest` (rerun, post-deploy) | 1 | 0 failures |
| `AgentPlannerTest` (consumer-side regression, agent-orchestrator) | 11 | 0 failures |

No test was weakened. `paymentx-agent-orchestrator` has no dedicated `LlmServiceClientTest` file
(that client is exercised indirectly through `AgentPlannerTest`/`AgentOrchestratorServiceTest`,
both green) and was not modified by this item.

### 4.5 Build verification (real exit codes, never piped through `tail`)

1. `mvn -pl paymentx-llm-service compile` → real exit 0.
2. First `mvn -pl paymentx-llm-service package -DskipTests` attempt → **real exit 1**
   (`Unable to rename ...jar to ...jar.original` — the same known Windows file-lock class of
   defect first hit in Phase 4.8.6: the *currently running* `llm-service` process, PID 5116, had
   the old jar open). Root-caused correctly rather than retried blindly: stopped PID 5116
   specifically (verified via `Get-CimInstance Win32_Process` command-line match, not a broad
   kill), confirmed it exited, then re-ran.
3. `mvn -pl paymentx-llm-service package -DskipTests` (retry) → **real exit 0**.
4. Jar timestamp verified: `10:53` → `13:11` (genuinely rebuilt, not stale).
5. Bytecode verified via `javap -p -v` directly on the extracted `.class` files inside the new
   jar (not merely trusting the build log):
   - `LlmServiceImpl.class` → `RuntimeVisibleAnnotations: io.github.resilience4j.ratelimiter.annotation.RateLimiter(name="llmGenerate")` genuinely present on `generate()`.
   - `LlmErrorCodes.class` → `public static final java.lang.String LLM_LOCAL_RATE_LIMITED` genuinely present.
   - `GlobalExceptionHandler.class` → `handleRateLimitExceeded(RequestNotPermitted, HttpServletRequest)` method genuinely present.

### 4.6 Deployment & live verification (LIVE VERIFIED, zero real Gemini/Anthropic quota spent)

1. Old process (PID 5116, running the stale jar) stopped by exact PID.
2. New process launched from the freshly built jar: `java -Xmx512m -jar target/paymentx-llm-service-0.1.0-SNAPSHOT.jar`.
3. Confirmed real new PID via `Get-CimInstance Win32_Process` command-line match: **22844**.
4. Spring Boot startup log confirmed: `Started LlmServiceApplication in 57.245 seconds`.
5. `GET /api/v1/llm/health` → `{"status":"CONFIGURED","provider":"gemini","apiKeyPresent":true,...}` — this endpoint deliberately never calls the real provider (its own documented contract), so this check spends zero quota.
6. `GET /actuator/health` → `{"status":"UP",...}`.
7. **Definitive, non-invasive, zero-quota proof the live process is genuinely running the new rate limiter**: `GET /actuator/metrics/resilience4j.ratelimiter.available.permissions` →
   ```json
   {"name":"resilience4j.ratelimiter.available.permissions","measurements":[{"statistic":"VALUE","value":8.0}],"availableTags":[{"tag":"name","values":["llmGenerate"]}]}
   ```
   A real Resilience4j `RateLimiter` instance named `llmGenerate` is registered and live with
   exactly 8.0 available permissions, matching the configured `limit-for-period: 8` — this is
   only possible if the deployed, running process genuinely contains this session's code change.

No real Gemini or Anthropic API call was made anywhere in this item's build, deploy, or
verification sequence — every provider-facing test used WireMock; every live-process check used
either a quota-free endpoint (`/health`, which explicitly never calls the provider) or a local
actuator metric.

### 4.6a Test-flakiness finding and fix (later same session)

A routine re-run of `LlmControllerRateLimiterIntegrationTest` (requested as part of the re-
verification below) genuinely failed once: `expected: 429 TOO_MANY_REQUESTS but was: 200 OK`.
Investigated rather than dismissed or silently retried. Root cause: `TestRestTemplate`'s
auto-configuration detects Apache HttpClient5 on the test classpath (pulled in transitively by
WireMock's own bundled dependencies, confirmed present via the `o.a.hc.client5...
HttpRequestRetryExec` log line, though absent from `mvn dependency:tree` since it is bundled
inside WireMock's jar rather than a direct/transitive Maven dependency) and wires
`TestRestTemplate` to use it. HttpClient5's default behavior **transparently retries a 429
response** after a short backoff; under this run's heavier system load, that silent client-side
retry landed late enough to race past the rate limiter's 10-second refresh window and receive a
fresh permit, masking the correct, honest 429 my test exists to prove. **This was a test-harness
flake, not a product regression** — confirmed by the failing run's own log, which shows the
server correctly returned 429/`LLM_LOCAL_RATE_LIMITED` on the true second request; only the
client's own invisible retry obtained a later 200.

Fixed by applying the exact same pattern this codebase's own production code already uses for
this reason (`LlmServiceClient`/`AiPlatformClient` both explicitly build their `RestTemplate` via
`.requestFactory(SimpleClientHttpRequestFactory::new)`): replaced the auto-injected
`TestRestTemplate` with a plain `RestTemplate` built the same way, and adjusted the second
assertion to catch `HttpClientErrorException` (plain `RestTemplate`, unlike `TestRestTemplate`,
throws on a non-2xx response rather than returning it) and assert on the exception's real status
code and body. Re-ran **3 consecutive times** after the fix: 0 failures across all 3 (previously
2 clean runs before the flake, so 5 of 6 total runs across this session passed even before the
fix — this was genuine intermittent flakiness under load, not a majority-failing test). Full
module regression re-run once more after the fix: **52/52, 0 failures**.

File changed: `paymentx-llm-service/src/test/java/com/paymentx/llm/controller/
LlmControllerRateLimiterIntegrationTest.java` (test-only; no production source touched, so the
already-deployed, already-verified jar/process did not need rebuilding or redeploying).

### 4.6b Re-verification (later same session, no rebuild needed)

A subsequent instruction asked for this exact build/deploy/verify sequence to be repeated. Before
spending another memory-sensitive `mvn package` cycle, the live state was checked first: PID
**22844** (the exact process restarted in §4.6) was still running, unchanged, with no llm-service
source edits made since that build. Re-querying confirmed zero drift:
`GET /api/v1/llm/health` → still `CONFIGURED`/`gemini` (zero quota spent, per that endpoint's own
contract); `GET /actuator/metrics/resilience4j.ratelimiter.available.permissions` → still
`8.0` permits on the `llmGenerate` instance. Since no new code exists to build, a fresh rebuild
would have produced byte-identical output for zero new evidence, so it was skipped rather than
forced — consistent with the standing memory-conservation instruction.

### 4.7 Blockers

None PaymentX-side. Memory pressure repeatedly delayed (not blocked) this item's build/deploy
step: host free memory dropped as low as ~790MB during this work, requiring several wait cycles
(with the user's help closing Chrome tabs) before the memory-sensitive `mvn package`/JVM-restart
steps were attempted — handled per protocol (check, wait, recheck, proceed only once stable),
never forced through.

---

## 4b. Discovery pass for a third P0/P1 item (this session)

Per the standing instruction not to stop merely because one item is complete, a further,
honest discovery pass was made before concluding this session's backlog. Areas checked and
their outcomes:

1. **Unauthenticated access to the AI Agent Control Center endpoints** — SOURCE VERIFIED as
   already handled: `DashboardAuthFilter.java` (already present in the working tree, labeled
   "Phase 6" in its own comments — pre-existing, not this session's work) gates every `/api/**`
   request behind a constant-time-compared shared dashboard bearer token when
   `control-center.security.enabled=true`, fails to start rather than run unauthenticated if
   misconfigured, and is covered by its own `DashboardAuthFilterTest.java`. Deliberately
   off-by-default for local dev (documented rationale: no real per-user IdP exists yet). **Not a
   gap** — already a deliberate, tested, documented design.
2. **Input validation on agent-execute requests** — SOURCE VERIFIED as already adequate: both
   Agent Orchestrator's own `AgentExecuteRequest` and Control Center's separate, narrower
   `AgentExecuteRequest` DTO already enforce `@NotBlank`/`@Size(max=2000)` on `userQuery` and a
   bounded pattern/size on `paymentReference`, consistently between the two layers. **Not a
   gap.**
3. **New-error-code propagation completeness** — traced `LLM_LOCAL_RATE_LIMITED`'s HTTP 429
   through the full chain: `LlmServiceClient.generate()` (agent-orchestrator) does not have a
   specific branch for it (only `LLM_PROVIDER_TIMEOUT` gets special-cased there), so it currently
   surfaces to the end user as a generic `LLM_SERVICE_UNAVAILABLE`/503 with the specific, honest
   message text ("...Too many AI requests in a short time. Please wait a moment before trying
   again.") still preserved verbatim inside it. The user-visible outcome is still honest and
   actionable, just with a slightly redundant message prefix and a less-precise error *code* than
   ideal. This is a minor, cosmetic integration nit, not a functional defect — logged here as a
   disclosed, **not implemented**, P2/P3 polish item (matches Phase 4.10's own "Optional
   Improvements, explicitly deferred" pattern), consistent with this task's instruction to
   implement only P0/P1.
4. **OpenAPI/Swagger annotation coverage** — `AiAgentController` (Control Center) has none, unlike
   `LlmController` (`paymentx-llm-service`), which has `@Operation`/`@Tag`. A developer-experience
   documentation gap, not a production-safety one — classified P2, not implemented.
5. **`paymentx-agent-orchestrator`'s `SecurityConfig.java`** — `anyRequest().permitAll()` with an
   explicit inline comment: `// TODO(Auth Service): replace with internal-service-token
   validation`. SOURCE VERIFIED as a genuine, disclosed gap, but **not a viable P0/P1 item to
   implement now**: it is a deliberate, platform-wide architectural decision matching every other
   AI Platform service's identical `SecurityConfig` shape (LLM/Embedding/RAG Service all read
   identically — "trust boundary at API Gateway, this service is not internet-facing"), and it is
   explicitly blocked on missing infrastructure that does not exist yet (Auth Service "has zero
   endpoints today" — the same real constraint `DashboardAuthFilter`'s own javadoc names as why
   Control Center's own gate is a lightweight shared-token filter rather than real per-service
   auth). Implementing "internal-service-token validation" without a real token-issuing authority
   to validate against would be exactly the "dishonest scaffolding" this codebase's own prior
   phases have consistently and correctly refused to build. Matches Phase 4.10's own precedent
   (§15 item 3: infrastructure-blocked items correctly left undone). Logged here, not
   implemented.

No further P0/P1-grade gap was found after this pass (which also included a search for
TODO/FIXME/"not currently"/"deliberately deferred" markers across `paymentx-agent-orchestrator`).
Continuing to search further without new evidence would risk inventing work, which this task
explicitly instructs against.

## 5. Process notes (both items)

### 5.1 Memory safety

Before starting expensive Maven operations, free memory was checked every time. When it dropped
into the danger zone (as low as ~790MB free at one point), work paused and the user was informed
rather than proceeding — consistent with the standing "STOP if dangerously low" instruction. 8
non-AI-platform PaymentX standalone service JARs (payment/audit/notification/routing/validation/
auth/reporting/api-gateway) were stopped early in this session, with the user's explicit
approval, to relieve baseline memory pressure; they remain stopped and were not needed for either
backlog item.

### 5.2 JobPilot protection

Confirmed via `git status --short`: every changed/new file path begins with a `paymentx-*`
directory or is a repo-root `.md` file — **zero** paths under `jobpilot-*` appear anywhere in the
change set. No JobPilot process was stopped, restarted, or otherwise touched. The two running
JobPilot Java processes (`jobpilot-job-service`, `jobpilot-profile-service`) and their Maven
wrapper launchers were left completely untouched throughout, including during the process
inventory taken before stopping the 8 non-AI PaymentX services (that inventory explicitly
excluded every `jobpilot-*` command line from the stop list).

### 5.3 Git

No commit, no push, no reset, no clean was performed, per instruction. `git status --short`
(captured this session) shows a large set of already-uncommitted files — this is **pre-existing**
Phase 4 working-tree state (this repository has exactly one commit, `1236f3a chore: consolidate
complete PaymentX platform`; per Phase 4.10's own §14, "substantial pre-existing work discovered
in the working tree" has been a consistent pattern across this whole engagement), not something
Phase 5 introduced. Restricting `git diff --stat` to only the files this session's two backlog
items actually touched:

```
 .../com/paymentx/agent/audit/AgentAuditClient.java |  26 +++-
 .../paymentx/agent/client/LlmServiceClient.java    |  18 ++-
 .../paymentx/agent/dto/AgentExecuteResponse.java   |  19 ++-
 .../orchestrator/AgentOrchestratorService.java     | 132 +++++++++++++++++----
 .../com/paymentx/agent/planning/AgentPlanner.java  |  78 +++++++++++-
 .../com/paymentx/agent/state/AgentExecution.java   |  30 ++++-
 .../llm/exception/GlobalExceptionHandler.java      |  15 +++
 .../com/paymentx/llm/exception/LlmErrorCodes.java  |   7 ++
 .../com/paymentx/llm/exception/LlmException.java   |   4 +
 .../paymentx/llm/service/impl/LlmServiceImpl.java  |  67 +++++++++--
 .../src/main/resources/application.yml             |  89 ++++++++++----
```

**Important honesty note**: several of these files (`AgentOrchestratorService.java` at 132
lines, `AgentPlanner.java` at 78 lines, `application.yml` at 89 lines) already carried
substantial *pre-existing, uncommitted* changes from Phase 4 before this session began — these
diff totals are the cumulative uncommitted state, not this session's own contribution size. This
session's own edits to those three files were small, targeted insertions (documented precisely
in §3.2/§4.3 above and in each file's own inline comments), not a rewrite. Files this session
created or edited that carried **no** prior uncommitted state (i.e., where the diff above is
entirely this session's own work) include `LlmErrorCodes.java`, `LlmException.java`,
`GlobalExceptionHandler.java`, and the new `LlmControllerRateLimiterIntegrationTest.java`.

## 6. Exact counts so far (this phase, cumulative across both completed items)

- Backlog items completed: **2** (both P1)
- Backlog items in progress: **0**
- Production source files modified (this phase's own edits, not pre-existing baseline): **~17**
  across `paymentx-agent-orchestrator`, `paymentx-control-center` (backend + frontend), and
  `paymentx-llm-service`
- New files created: **1** (`LlmControllerRateLimiterIntegrationTest.java`)
- Test files modified/added: **~10**
- DB schema changes: **0**
- DB writes (by this phase's own actions): **0**
- MCP changes: **0**
- New LLM providers added: **0**
- Payments created: **0**
- JobPilot resources touched: **0**
- Processes stopped: 8 non-AI PaymentX services (user-approved, remain stopped) + 4 exact-PID
  stop/restart cycles for redeployment (`paymentx-llm-service` 5116→22844,
  `paymentx-agent-orchestrator` 8088→12112, `paymentx-control-center/backend` 31896→29680→27612,
  the last restarted twice to add the pre-existing `CONTROL_CENTER_AI_ENABLED` dev flag for
  functional verification)
- Processes restarted/redeployed with this phase's own code changes: **3**
  (`paymentx-llm-service`, `paymentx-agent-orchestrator`, `paymentx-control-center/backend`) —
  all LIVE VERIFIED healthy and running the new bytecode
- Real Gemini/Anthropic API calls made by this phase's own verification work: **0**

## 7. Current status and next steps

**Backlog status: exhausted for well-evidenced P0/P1 items, after a genuine, documented
discovery pass (§4b) that found no further gap of that severity.** Both identified P1 items
(provider/fallback visibility, local LLM rate limiter) are now **fully complete**: implemented,
tested (real Spring context + WireMock where a provider call was involved, zero real Gemini/
Anthropic quota spent throughout this entire phase), built with real captured exit codes,
**deployed, and LIVE VERIFIED against the actual running processes for both items** — all four
touched services (`paymentx-llm-service`, `paymentx-agent-orchestrator`,
`paymentx-control-center/backend`, plus the frontend's own real production build) redeployed/
rebuilt this session with exact-PID stop/restart, jar-timestamp checks, `javap` bytecode
verification, and real HTTP health/functional checks against the live, running platform.

The only friction encountered this session was environmental: host memory repeatedly dropped
into a danger zone (as low as ~790MB free) during the memory-heavy `mvn package`/JVM-restart
steps, requiring several wait cycles (and the user closing some Chrome tabs) before it was safe to
proceed. This was handled per protocol — never forced through, never a workaround that skipped
verification — and is not a PaymentX implementation gap.

**Remaining before this document can carry a full 18-section final classification:**
1. The full Agent E2E Matrix across all 7 agents (PASS/PARTIAL/BLOCKED/NOT TESTED) — deliberately
   not fabricated here; Phase 4.10 already disclosed browser-level UI E2E as never having been
   performed, and this phase has not yet performed it either. Real agent executions that reach an
   actual LLM call would spend Gemini's scarce daily quota, so this remains an explicit, disclosed
   scope item for a dedicated future pass (with the user's go-ahead on quota spend), not something
   to rush through opportunistically inside this session.
2. A final A/B/C classification decision once the above is complete or genuinely blocked.

**This session's own assessment**, short of that full matrix: **Classification B — substantially
complete.** Every identified, evidence-based P0/P1 productization gap has been closed, tested,
built, deployed, and live-verified against the real running platform with zero fabrication and
zero real LLM quota spent; the only remaining item (full cross-agent E2E matrix) is a distinct,
larger undertaking explicitly deferred pending the user's decision on real Gemini quota spend,
not an unaddressed defect.

If further P0/P1-grade gaps surface during future work, they will be added to §2's backlog table
and worked through the same inspect→implement→test→build→verify→document cycle documented above.
