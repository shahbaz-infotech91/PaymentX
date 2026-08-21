# PaymentX — Phase 3.10.3: Control Center AI Metrics UI — Implementation

## 1. Objective

Implement the AI Metrics page recommended by `PAYMENTX_PHASE_3_10_3_AI_METRICS_UI_DESIGN.md`: product-native
operational visibility over the ~60 already-existing AI Platform Micrometer metrics, reusing the existing
Prometheus scraping and the existing Control Center backend's safe, allowlisted query architecture. No new
metric instrumentation, no new Prometheus deployment, no arbitrary PromQL from the browser.

## 2. Existing Architecture Reused

Per the design doc's own audit, and re-verified before writing any code:

- **Prometheus**: already scrapes all 7 AI services (`infra/prometheus.yml`, unmodified).
- **`PrometheusClient`**: real HTTP client for Prometheus's instant/range query APIs — unmodified.
- **`PrometheusMetricQuery`**: the fixed, server-defined PromQL allow-list enum — **extended**, never
  restructured.
- **`PrometheusMetricsService`**: catalog/`runAll()`/`runQuery()`/`runRangeQuery()` — **extended** with one
  new method, existing methods untouched.
- **`PrometheusController`**: `/metrics`, `/metrics/all`, `/metrics/{slug}`, `/metrics/{slug}/range` —
  **extended** with one new route, existing routes untouched.
- **`DashboardAuthFilter`**: already gates every `/api/**` route — the new route inherits this automatically,
  no new security code was needed or written.
- **`useAiHealth()`**: already fetches the full 7-component AI health breakdown — reused completely as-is,
  zero backend changes; only a new frontend component renders more of what it already returns.
- **Frontend conventions**: `PageContainer`/`PageHeader`/`LoadingState`/`ErrorState`/`HealthIndicator`,
  React Query 30s polling, MUI `Grid2`, the `'AI'` route group — all reused verbatim.

## 3. Metric Catalog

23 new `PrometheusMetricQuery` entries were added, all slug-prefixed `ai-` (mirroring the existing
`business-` prefix convention), grouped exactly as the design doc's Step 5 structure requires. Every metric
name and label was re-verified directly against each service's `*Metrics.java` source (not assumed) before
being written into a PromQL string — including confirming, per timer, whether `.publishPercentileHistogram()`
is actually called (only one "headline" latency timer per service is, so only those are used for
`histogram_quantile`; RAG's four per-step timers and Agent's per-tool timer are *not* histogram-backed and
are therefore not queried this way), and confirming the real Micrometer tag key is `tool` (not `toolName`,
which the design doc's conceptual layout had used loosely) for both MCP and Agent tool-scoped counters.

| Group | New slugs | Real metric(s) queried |
|---|---|---|
| LLM | `ai-llm-request-rate`, `ai-llm-success-rate`, `ai-llm-failure-rate`, `ai-llm-latency-p95` | `llm_requests_total`, `llm_generate_success_total`, `llm_generate_failure_total`, `llm_generate_latency_seconds_bucket` |
| RAG | `ai-rag-query-rate`, `ai-rag-success-rate`, `ai-rag-insufficient-context-rate`, `ai-rag-failure-rate`, `ai-rag-latency-p95` | `rag_requests_total`, `rag_success_total`, `rag_insufficient_context_total`, `rag_failure_total`, `rag_total_latency_seconds_bucket` |
| MCP | `ai-mcp-tool-call-rate`, `ai-mcp-tool-success-rate`, `ai-mcp-tool-failure-rate`, `ai-mcp-tool-denied-rate`, `ai-mcp-tool-latency-p95` | `mcp_tool_calls_total`, `mcp_tool_success_total`, `mcp_tool_failure_total`, `mcp_tool_denied_total`, `mcp_tool_latency_seconds_bucket` (all `by (tool)` except latency) |
| Agent | `ai-agent-execution-rate`, `ai-agent-success-rate`, `ai-agent-failure-rate`, `ai-agent-latency-p95` | `agent_requests_total`, `agent_success_total`, `agent_failure_total` (`by (status)`), `agent_execution_latency_seconds_bucket` |
| Embedding | `ai-embedding-request-rate`, `ai-embedding-latency-p95` | `embedding_requests_total` (`by (provider)`), `embedding_latency_seconds_bucket` |
| Vector | `ai-vector-search-rate`, `ai-vector-latency-p95` | `vector_search_total`, `vector_search_latency_seconds_bucket` |
| Prompt | `ai-prompt-request-rate`, `ai-prompt-failure-rate` | `prompt_requests_total` (`by (operation)`), `prompt_render_failure_total` |

This is deliberately the operational subset (requests/success/failure/latency per service) the design doc
prioritized — not all ~60 raw meters that exist (e.g. token counts, context-size distributions, and
per-step RAG timers were intentionally left out of the catalog for this phase, matching the design's own
"do not add every metric blindly" guidance).

**No new metric instrumentation was added anywhere.** Every PromQL string above references a metric that
already existed, in a service whose Java code was not touched by this phase.

## 4. Backend Changes

- **`PrometheusMetricQuery.java`**: 23 new enum constants added (see §3), inserted after the existing
  business-metric block. No existing constant's slug or PromQL text was changed.
- **`PrometheusMetricsService.java`**: one new method, `runAi()` — filters `PrometheusMetricQuery.values()`
  to the `ai-`-prefixed subset and queries only those, via the same unmodified `PrometheusClient`. Kept
  deliberately separate from `runAll()` (which would otherwise fan out to the full, now ~37-entry catalog
  on every AI Metrics page load — see the design doc's own §10 performance note).
- **`PrometheusController.java`**: one new route, `GET /api/v1/prometheus/metrics/ai`, delegating to
  `runAi()` — coexists with the pre-existing `/metrics/all` and `/metrics/{slug}` routes on the same
  controller (Spring's literal-path-over-path-variable precedence, already proven by `/metrics/all`
  coexisting with `/metrics/{slug}` before this phase).

No `ControlCenterProperties`, no Prometheus config, no database schema, and no other backend class was
touched.

## 5. Frontend Changes

- **`services/prometheusService.ts`**: one new function, `fetchAiPrometheusMetrics()`, calling the new
  endpoint — reuses the existing `PrometheusQueryResult` type verbatim (no new DTO).
- **`hooks/usePrometheusMetrics.ts`**: one new hook, `useAiPrometheusMetrics()` — same 30s
  `refetchInterval` as every other Prometheus-backed hook in this app.
- **`components/ai/AiPlatformHealth.tsx`** (new): renders the full 7-component health breakdown
  `useAiHealth()` already fetches, as a small card grid, reusing the existing `HealthIndicator` component
  and `theme.palette.status.*` tokens (`READY`→`UP`, `NOT_READY`→`DOWN`, `NOT_IMPLEMENTED`→`UNKNOWN`) — no
  second health implementation, no new backend call.
- **`pages/AiMetricsPage.tsx`** (new): the `/ai-metrics` page — one `useAiPrometheusMetrics()` call drives
  every card, grouped into LLM/RAG/MCP/Agent/Embedding/Vector/Prompt sections exactly matching the design's
  Step 5 structure, each card independently loading/error/empty-safe (see §8). Cards, not charts — per
  this task's own Step 6 preference and the extra Prometheus round-trips a chart-per-metric page would add.
- **`utils/routes.ts`**: one new `RouteDefinition` in the existing `'AI'` group (alongside AI Assistant) —
  the Sidebar picks it up automatically, no `Sidebar.tsx` change needed.
- **`app/router.tsx`**: one new lazy-loaded route entry.

No new npm dependency was added — `recharts` was evaluated but not used (cards were preferred per Step 6);
MUI, React Query, and every reused component were already present.

## 6. Security

- **No raw PromQL from the browser**: the frontend has no code path that sends a query string anywhere —
  `fetchAiPrometheusMetrics()` calls a fixed URL with no query-string parameter carrying PromQL; the
  backend's `PrometheusController.ai()` takes no request body/param at all, it simply invokes
  `PrometheusMetricsService.runAi()`, which iterates the fixed enum. There is no code path, new or old, by
  which a browser-supplied string could reach `PrometheusClient.query()`.
- **Allowlist preserved**: every new query is a compile-time `PrometheusMetricQuery` enum constant; nothing
  changed about how `fromSlug()` rejects unrecognized input (proven by the unmodified, still-passing
  `PrometheusMetricQueryTest.fromSlugRejectsArbitraryPromQlInjectedAsASlug`, plus a new AI-specific
  equivalent in `PrometheusMetricQueryAiTest`).
- **No secrets/credentials/environment variables/infrastructure tokens** appear in any new PromQL string,
  any new DTO, or any new frontend type — every value returned is a numeric Prometheus sample (rate/count/
  latency), matching every metric's real, non-string Micrometer type.
- **`DashboardAuthFilter`** already covers `/api/v1/prometheus/metrics/ai` automatically (path-prefix
  match on `/api/`) — no security code was written or needed for this endpoint specifically.

## 7. Tests

**Backend (11 new, 0 modified in existing files):**

| Class | Tests | Covers (Step 9 items) |
|---|---|---|
| `PrometheusMetricQueryAiTest` | 5 | AI metric catalog (1), allowlisted access / unknown slug rejection (2, 4), histogram-backed-timer-only validation |
| `PrometheusMetricsServiceAiTest` | 4 | valid AI metric query (3), partial metric failure (7), missing metric / honest empty samples (6) |
| `PrometheusControllerAiTest` | 2 | endpoint wiring, non-delegation to `runAll()`/`runQuery()` |

Prometheus-unavailable (5) and AI health aggregation (8) are already covered by pre-existing,
unmodified tests (`PrometheusClient`'s `ResourceAccessException`/`RestClientException` handling is
exercised by `PrometheusMetricsServiceAiTest`'s failure-result construction; `AiChatServiceTest`/
`AiControllerTest` already cover health aggregation, untouched by this phase since `useAiHealth()`'s
backend was not modified).

**Frontend (10 new, 0 modified in existing files):**

| File | Tests | Covers (Step 10 items) |
|---|---|---|
| `AiPlatformHealth.test.tsx` | 4 | Overall AI health renders (2), loading state (10), error state |
| `AiMetricsPage.test.tsx` | 6 | page renders + LLM/RAG/MCP/Agent/Embedding/Vector/Prompt sections render (1, 3-9), loading state (10), Prometheus-unavailable state (11), missing-metric state (12), partial-failure state (13) |

All backend tests use Mockito against the existing `PrometheusClient`/`PrometheusMetricsService`
interfaces (no live Prometheus required). All frontend tests mock `useAiPrometheusMetrics`/`useAiHealth`
directly (no live backend required), matching this app's existing `AIStatus.test.tsx` convention exactly.

## 8. Error Handling (verified by test, not just designed)

Each `AiMetricCard` independently renders one of four honest states — never a fabricated zero:

1. **Missing** (backend didn't return this slug) → "Unavailable".
2. **Query failed** (`success:false`) → "Unavailable" (red), with the real Prometheus error message in a
   tooltip.
3. **Empty** (`success:true`, zero samples) → "No data".
4. **Real data** → the actual aggregated value, plus a per-label breakdown when more than one series exists
   (e.g. per-provider, per-tool).

One card's failure or absence never affects any other card, or the page's own top-level `isLoading`/
`isError` state — proven directly by `AiMetricsPage.test.tsx`'s partial-failure test (one RAG metric fails
while its sibling RAG metric still renders real data on the same page load).

## 9. Live Validation

Re-checked immediately before finishing this phase: ports 8080/8092-8098 (API Gateway + all 7 AI services)
and 9090 (Prometheus) are all unreachable; `docker ps` fails ("failed to connect to the docker API ... the
system cannot find the file specified" — Docker Desktop is not running). **No live services or Prometheus
instance exist to browser-validate against.** Per this task's explicit instruction, nothing was started
solely to force a pass. Live browser validation is reported **NOT EXECUTED**, honestly, below.

## 10. Regression

- **Frontend**: full `vitest run` — **8 test files, 39/39 passing** (29 pre-existing + 10 new; zero
  pre-existing test was modified). This includes `AiAssistantPage.test.tsx` (5/5) and the router's own
  `router.test.ts` (3/3, which structurally verifies every `ROUTES` entry has a matching `PAGE_COMPONENTS`
  loader — confirming the new `/ai-metrics` entry is wired correctly).
- **Backend**: full `mvn test` on `paymentx-control-center-backend` — **79 tests, 78 passing, 1
  pre-existing environmental failure** (`ControlCenterApplicationTests.contextLoads`, which requires a
  real Postgres on `localhost:5433` — confirmed by full stack trace to be a `HikariPool`/`PSQLException:
  Connection refused`, entirely about datasource connectivity, unrelated to any file this phase touched;
  no live Postgres is running in this environment, matching §9's findings). This is not a regression this
  phase introduced.
- **Not touched, and not re-run in full** (no code in these modules was modified, and this task's own
  instructions favor avoiding unnecessary parallel test execution): Phase 1 services, Auth, RAG Service,
  RAG's audit layer, MCP Gateway, Agent Orchestrator, LLM/Embedding/Vector Service. The Phase 3.10.1 AI
  security test suite and Phase 3.10.2 RAG audit layer live entirely in other modules this phase never
  opened a file in.

## 11. Files Changed

**Backend production files modified (3):**
- `paymentx-control-center/backend/.../dto/prometheus/PrometheusMetricQuery.java`
- `paymentx-control-center/backend/.../service/PrometheusMetricsService.java`
- `paymentx-control-center/backend/.../controller/PrometheusController.java`

**Backend test files added (3):**
- `.../dto/prometheus/PrometheusMetricQueryAiTest.java`
- `.../service/PrometheusMetricsServiceAiTest.java`
- `.../controller/PrometheusControllerAiTest.java`

**Frontend production files added (2):**
- `paymentx-control-center/frontend/src/components/ai/AiPlatformHealth.tsx`
- `paymentx-control-center/frontend/src/pages/AiMetricsPage.tsx`

**Frontend production files modified (4):**
- `paymentx-control-center/frontend/src/services/prometheusService.ts` (+1 function)
- `paymentx-control-center/frontend/src/hooks/usePrometheusMetrics.ts` (+1 hook)
- `paymentx-control-center/frontend/src/utils/routes.ts` (+1 route entry)
- `paymentx-control-center/frontend/src/app/router.tsx` (+1 lazy import)

**Frontend test files added (2):**
- `paymentx-control-center/frontend/src/components/ai/AiPlatformHealth.test.tsx`
- `paymentx-control-center/frontend/src/pages/AiMetricsPage.test.tsx`

**Files deleted:** none.
**Configuration changed:** none (no `application.yml`, no `prometheus.yml`, no `docker-compose.yml`).
**Dependencies changed:** none (no `pom.xml`, no `package.json` change).
**Database changed:** none.

## 12. Known Limitations

1. **Live browser validation was not performed** — no AI service, Prometheus, or Docker instance was
   running (§9). The page has not been visually confirmed in a real browser against real scraped data;
   only its rendering logic against realistic mocked data shapes has been (§7/§8).
2. **`ControlCenterApplicationTests.contextLoads` remains red in this environment** — pre-existing,
   requires a live Postgres this environment doesn't have running; unrelated to this phase (§10). A real
   CI/dev environment with Postgres available would need to confirm this passes there.
3. **Charts were deliberately not implemented** for this phase (cards only) — per this task's own Step 6
   preference for "simple operational cards/tables" over charts, and to keep the AI Metrics page to exactly
   one Prometheus round-trip. A future phase could add `RangeChart`s for the 6 `-latency-p95` metrics using
   the pre-existing, unmodified `/metrics/{slug}/range` endpoint (no backend change would be needed - the
   new AI slugs already work with it), but that is out of this phase's scope.
4. **MCP/Agent/LLM/Embedding per-label breakdown is a flat inline string**, not a dedicated table
   component — sufficient for this phase's "operational cards" scope (§6 of the parent task explicitly
   allows this), a richer per-tool table remains a documented, not-yet-built enhancement (matches the
   design doc's own §16 "RECOMMENDED, not required" note).
5. **Token-count, context-size, and per-step RAG/MCP-adjacent distribution metrics were intentionally
   excluded** from the catalog (§3) — the design doc's own "do not add every metric blindly" guidance was
   followed; these ~37 remaining AI meters are still real and still scraped, just not yet surfaced in this
   UI.

## 13. Phase 3.10.3 Status

**COMPLETE**
