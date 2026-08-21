# PaymentX — Phase 3.10.3: Control Center AI Metrics UI — Readiness + Design Audit

**This document is READ-ONLY AUDIT + DESIGN.** No source code, configuration, database, Docker, or service
state was modified to produce it. No dependency was added. No test was added. Nothing was started, stopped,
or restarted. Every fact below was confirmed by reading the actual repository; nothing was assumed or
invented.

---

## 1. Existing Metric Inventory (Step 1/2)

Every AI service registers its meters by hand via `MeterRegistry` (`Counter.builder`/`Timer.builder`/
`DistributionSummary.builder`), never `@Timed`/`@Counted` — a deliberate, consistent platform-wide choice
(see `LlmMetrics`'s own javadoc: annotations can't distinguish success/failure/refusal at the call site the
way an explicit call can). All 7 AI services expose `management.endpoints.web.exposure.include:
health,info,metrics,prometheus`, i.e. a real `/actuator/prometheus` endpoint, matching every other PaymentX
service.

| Service | Metric | Type | Meaning | Labels | Current Source |
|---|---|---|---|---|---|
| **Prompt** | `prompt_requests_total` | Counter | Requests received | `operation` | `PromptMetrics.recordRequest` |
| Prompt | `prompt_render_success_total` | Counter | Successful renders | `promptKey` | `recordRenderSuccess` |
| Prompt | `prompt_render_failure_total` | Counter | Failed renders | `promptKey`, `errorCode` | `recordRenderFailure` |
| Prompt | `prompt_render_latency` | Timer | Render duration | `promptKey` | `startRenderTimer`/`stopRenderTimer` |
| Prompt | `active_prompt_lookup_latency` | Timer | Active-version lookup duration | `promptKey` | `startActiveLookupTimer`/`stopActiveLookupTimer` |
| Prompt | `validation_failure_count` | Counter | Request validation failures | `errorCode` | `recordValidationFailure` |
| **LLM** | `llm_requests_total` | Counter | Generate calls received | `provider` | `LlmMetrics.recordRequest` |
| LLM | `llm_generate_success_total` | Counter | Successful generations | `provider`, `model` | `recordSuccess` |
| LLM | `llm_generate_failure_total` | Counter | Failed generations | `provider`, `errorCode` | `recordFailure` |
| LLM | `llm_generate_refused_total` | Counter | Model-level refusals | `provider`, `model` | `recordRefused` |
| LLM | `llm_generate_latency` | Timer (+histogram) | Generation duration | `provider` | `startGenerateTimer`/`stopGenerateTimer` |
| LLM | `llm_provider_error_total` | Counter | Raw provider-side errors | `provider`, `errorCode` | `recordProviderError` |
| LLM | `llm_input_tokens_total` | Counter | Input tokens consumed | `provider`, `model` | `recordTokens` |
| LLM | `llm_output_tokens_total` | Counter | Output tokens produced | `provider`, `model` | `recordTokens` |
| LLM | `llm_not_configured_total` | Counter | Calls rejected — provider not configured | — | `recordNotConfigured` |
| LLM | `llm_health_check_total` | Counter | Health probe outcomes | `status` | `recordHealthCheck` |
| **Embedding** | `embedding_requests_total` | Counter | Embed calls received | `provider` | `EmbeddingMetrics.recordRequest` |
| Embedding | `embedding_success_total` | Counter | Successful embeds | `provider`, `model` | `recordSuccess` |
| Embedding | `embedding_failure_total` | Counter | Failed embeds | `provider`, `errorCode` | `recordFailure` |
| Embedding | `embedding_timeout_total` | Counter | Provider timeouts | `provider` | `recordTimeout` |
| Embedding | `embedding_latency` | Timer (+histogram) | Embed duration | `provider` | `startLatencyTimer`/`stopLatencyTimer` |
| Embedding | `embedding_input_size` | DistributionSummary | Input character length | `provider` | `recordInputSize` |
| Embedding | `embedding_dimension` | DistributionSummary | Output vector dimension | `provider`, `model` | `recordDimension` |
| Embedding | `batch_embedding_requests_total` | Counter | Batch embed calls | `provider` | `recordBatchRequest` |
| Embedding | `batch_embedding_size` | DistributionSummary | Items per batch | `provider` | `recordBatchRequest` |
| Embedding | `provider_errors` | Counter | Provider-side errors | `provider`, `errorCode` | `recordProviderError` |
| **Vector** | `vector_store_requests_total` | Counter | Store calls received | — | `VectorMetrics.recordStoreRequest` |
| Vector | `vector_store_success_total` | Counter | Successful stores | `outcome` (created/updated) | `recordStoreSuccess` |
| Vector | `vector_insert_total` / `vector_update_total` | Counter | Insert vs. update outcome | — | `recordStoreSuccess` |
| Vector | `vector_store_failure_total` | Counter | Failed stores | `errorCode` | `recordStoreFailure` |
| Vector | `vector_dimension_errors` | Counter | Dimension/value validation errors | — | `recordStoreFailure` |
| Vector | `vector_delete_total` | Counter | Deletes | — | `recordDelete` |
| Vector | `vector_search_total` | Counter | Searches | — | `recordSearch` |
| Vector | `vector_search_results` | DistributionSummary | Result-count distribution | — | `recordSearch` |
| Vector | `vector_search_latency` | Timer (+histogram) | Search duration | — | `startSearchTimer`/`stopSearchTimer` |
| Vector | `vector_insert_latency` | Timer (+histogram) | Insert duration | — | `startInsertTimer`/`stopInsertTimer` |
| **RAG** | `rag_requests_total` | Counter | Queries received | — | `RagMetrics.recordRequest` |
| RAG | `rag_success_total` | Counter | Grounded answers produced | — | `recordSuccess` |
| RAG | `rag_failure_total` | Counter | Failed queries | `errorCode` | `recordFailure` |
| RAG | `rag_insufficient_context_total` | Counter | No relevant chunks found | — | `recordInsufficientContext` |
| RAG | `rag_relevance_threshold_rejections` | Counter | Chunks rejected by `min-score` | — | `recordThresholdRejections` |
| RAG | `rag_embedding_latency` / `rag_vector_search_latency` / `rag_prompt_render_latency` / `rag_llm_latency` | Timer | Per-step duration | — | `stop*Timer` |
| RAG | `rag_total_latency` | Timer (+histogram) | Full query duration | — | `stopTotalTimer` |
| RAG | `rag_context_chunks` / `rag_context_size` | DistributionSummary | Chunks used / characters used | — | `recordContext` |
| **MCP** | `mcp_requests_total` | Counter | Requests received | — | `McpMetrics.recordRequest` |
| MCP | `mcp_tool_calls_total` | Counter | Tool invocations | `toolName` | `recordToolCall` |
| MCP | `mcp_tool_success_total` / `mcp_tool_failure_total` | Counter | Outcome | `toolName`(+`errorCode`) | `recordSuccess`/`recordFailure` |
| MCP | `mcp_tool_denied_total` | Counter | Authorization denials | `toolName`, `errorCode` | `recordDenied` |
| MCP | `mcp_tool_timeout_total` | Counter | Tool timeouts | `toolName` | `recordTimeout` |
| MCP | `mcp_tool_rate_limited_total` | Counter | Rate-limit rejections | `toolName` | `recordRateLimited` |
| MCP | `mcp_authorization_failures` / `mcp_validation_failures` | Counter | Auth/validation failures | `toolName` | `recordAuthorizationFailure`/`recordValidationFailure` |
| MCP | `mcp_tool_latency` | Timer (+histogram) | Per-tool-call duration | `toolName` | `startTimer`/`stopTimer` |
| **Agent** | `agent_requests_total` / `agent_success_total` / `agent_failure_total` / `agent_timeout_total` | Counter | Run outcomes | (`status` on failure) | `AgentMetrics.record*` |
| Agent | `agent_iterations_total` | Counter | Planning loop iterations | — | `recordIteration` |
| Agent | `agent_tool_calls_total` / `agent_tool_denied_total` | Counter | Tool-call attempts/denials | `toolName` | `recordToolCall`/`recordToolDenied` |
| Agent | `agent_rag_calls_total` / `agent_llm_calls_total` | Counter | RAG/LLM sub-calls | — | `recordRagCall`/`recordLlmCall` |
| Agent | `agent_execution_latency` | Timer (+histogram) | Full run duration | — | `startTimer`/`stopExecutionTimer` |
| Agent | `agent_tool_latency` | Timer | Per-tool-call duration | `toolName` | `stopToolTimer` |
| Agent | `agent_context_size` | DistributionSummary | Execution-history size | — | `recordContextSize` |

**Control Center's own backend has no Micrometer meters of its own for the AI chat path** (confirmed: no
`MeterRegistry`/`Counter`/`Timer` reference anywhere under
`paymentx-control-center/backend/src/main/java`). It relies entirely on the 7 services above for AI
observability, plus its own `GET /api/v1/ai/health` (`AiChatService.health()`) for a real per-component
READY/NOT_READY rollup (see §4).

No metric name above was assumed — every one was read directly from its `*Metrics.java` source file.

## 2. Prometheus Architecture (Step 3)

`infra/prometheus.yml`: `global.scrape_interval: 15s`, no retention override (Prometheus's own default
applies — 15 days, not visible/configurable from this file). All 9 business services **and all 7 AI
services** are already scraped — `paymentx-prompt-service` (8092), `paymentx-llm-service` (8093),
`paymentx-embedding-service` (8094), `paymentx-vector-service` (8095), `paymentx-rag-service` (8096),
`paymentx-mcp-gateway` (8097), `paymentx-agent-orchestrator` (8098) — each `metrics_path:
/actuator/prometheus`, target `host.docker.internal:<port>` (Prometheus runs in Docker; every PaymentX
service runs as a host process — see that file's own comment for why `host.docker.internal`, not a
compose service name, is required). `infra/docker-compose.yml` runs `prom/prometheus:latest` on host port
`9090`, config-mounted from that same file, no extra `command:` flags beyond `--config.file`. A `grafana`
container is also declared in that same compose file (pre-existing, unrelated to this phase, and this
phase must not touch it per the strict-scope instructions).

**AI services already scraped: YES, all 7.** No Prometheus configuration change is needed for AI metrics
visibility — the data is already being collected.

## 3. Control Center Architecture (Step 4)

The backend already has the complete, safe integration pattern the task recommends:

- **`PrometheusClient`** (`client/PrometheusClient.java`) — real HTTP client for Prometheus's
  `/api/v1/query` (instant) and `/api/v1/query_range` (range) APIs. Takes only a `PrometheusMetricQuery`
  enum value, never a raw string; turns a Prometheus-side error or connection failure into a
  `success=false` result object rather than an exception.
- **`PrometheusMetricQuery`** (`dto/prometheus/PrometheusMetricQuery.java`) — the fixed, server-defined
  allow-list of PromQL queries (currently 14: request rate/latency/error-rate, CPU/memory/GC/threads,
  Kafka lag, Redis latency, 3 routing/reconciliation business metrics). **This enum is the literal "no raw
  PromQL from the browser" boundary** — every controller endpoint takes a slug bound from a path segment,
  never a query string.
- **`PrometheusMetricsService`** (`service/PrometheusMetricsService.java`) — lists the catalog, runs one
  query or all of them (`runAll()` — currently **sequential**, `.stream().map(client::query)`, not
  parallel), and runs range queries over one of exactly 6 allowed windows (5/15/30/60/360/1440 minutes),
  computing `step` from a `TARGET_POINT_COUNT=120` so each chart gets a sensible point density regardless
  of window size.
- **`PrometheusController`** (`controller/PrometheusController.java`) — `GET /api/v1/prometheus/metrics`
  (catalog, no execution), `GET /api/v1/prometheus/metrics/all`, `GET /api/v1/prometheus/metrics/{slug}`,
  `GET /api/v1/prometheus/metrics/{slug}/range?rangeMinutes=`. No endpoint accepts a raw query string.
- **`ControlCenterProperties.Prometheus`** — `baseUrl` (default `http://localhost:9090`),
  `connectTimeoutMs=3000`, `readTimeoutMs=8000`.

The exact `Control Center Backend → Prometheus → Control Center API → Frontend` pattern Step 4 recommends
**already exists and is already proven in production use** by the existing `/metrics` page. Building the
AI Metrics page means **adding to this pattern**, not building it.

## 4. Existing AI Health Rollup (relevant to Step 6's "Overall Health")

`GET /api/v1/ai/health` (`AiController` → `AiChatService.health()`) already returns a real, per-component
`AiHealthResponse { status, components: Map<String, AiComponentStatus>, checkedAt }` covering
`chatInterface`, `promptService`, `llmService`, `embeddingService`, `ragService`, `mcpGateway`,
`agentOrchestrator` — each probed via a real `/actuator/health` call (`AiPlatformClient.componentHealth`).
The frontend already has `useAiHealth()` (React Query, 30s poll) and renders it — but only as a single
rolled-up dot (`AIStatus.tsx`, used in the header). **The per-component `components` map is already fetched
by the existing hook but never displayed anywhere in the UI today** — this is a real, ready-to-use data
source for the new page's "Overall Health" section, requiring no new backend endpoint at all, only a new
presentational component that renders the map the hook already returns.

## 5. Security (Step 5/18 — audited, not implemented)

- Prometheus (port 9090) is **not** reachable from the browser today — the frontend has no code anywhere
  that references `localhost:9090` or any Prometheus URL; `prometheusService.ts`'s own javadoc states this
  explicitly: "the frontend must never call Prometheus directly."
- **No arbitrary PromQL is possible from the browser** — confirmed structurally: every backend
  endpoint's `slug` parameter is resolved through `PrometheusMetricQuery.fromSlug()`, which throws
  `IllegalArgumentException` (→ HTTP 400, presumably — not independently re-verified in this read-only
  audit) for anything not in the fixed enum.
  Extending AI metrics coverage means adding new enum constants, not opening the query surface.
- `DashboardAuthFilter` already gates every `/api/**` route (when `control-center.security.enabled=true`)
  behind a single shared bearer token, compared in constant time — a new
  `/api/v1/prometheus/...`-family AI metrics endpoint would inherit this automatically, no new security
  code needed.
- No secret, credential, environment variable, or infrastructure token is present in any Prometheus sample
  value inspected in this audit — Micrometer meter values are numeric (counts/timers/summaries) by
  construction; there is no metric anywhere in the 7 services' `*Metrics.java` files that carries free text
  content (unlike, say, a log line).

**Conclusion: the existing architecture already safely supports exactly the pattern Step 5/18 require.**
No new security work is a prerequisite for this feature.

## 6. Proposed AI Metrics Page — Conceptual Design (Step 6/7)

A new page, `/ai-metrics`, in the **already-existing `'AI'` route group** (`utils/routes.ts` — currently
holds only `/ai-assistant`; this would be its second entry, no new group needed).

```
AI PLATFORM
│
├── Overall Health          (reuses useAiHealth() as-is — 7-component grid, not just the rolled-up dot)
│
├── LLM                     MetricCards: requests, success/failure/refused rate, input+output tokens
│   └── chart: p99 generate latency (range)
│
├── RAG                     MetricCards: queries, success/insufficient-context rate, threshold rejections
│   └── chart: total latency + per-step latency breakdown (embed/search/render/llm) (range)
│
├── MCP                     MetricCards: tool calls, success/denied/timeout/rate-limited counts
│   └── table: per-tool breakdown (toolName label already exists on every MCP counter)
│
├── Agent                   MetricCards: runs, success/failure/timeout rate, avg iterations
│   └── chart: execution latency (range)
│
├── Prompt                  MetricCards: renders, success/failure rate
│
└── Embedding / Vector      MetricCards: embed requests, search requests, latency, errors
    └── chart: embedding + vector search latency (range)
```

This groups the catalog from §1 exactly along the 7 real services, matching the task's suggested layout
with two small, evidence-based adjustments: Prompt Service gets its own small section (it is a distinct
scraped AI service with its own real metrics, not folded into another), and Embedding/Vector are combined
per the original suggestion since both are small, symmetric, low-cardinality (no `toolName`-style label)
services.

**Do NOT create unnecessary charts** (Step 7): most sections need only `MetricCard`s (a labelled number),
matching this repository's existing philosophy of preferring instant-query cards over charts. Only
genuinely time-varying, comparison-worthy figures (RAG latency breakdown, Agent execution latency, LLM
latency, Embedding/Vector latency) justify a `RangeChart` — reusing `MetricsPage.tsx`'s existing
`RangeChart` component nearly verbatim. MCP's most useful view is a **per-tool table** (5 real tools, each
with its own success/failure/denied/timeout breakdown) rather than a chart — better operational visibility
per Step 7's own instruction to prefer that over decoration.

**States** (matching `MetricsPage.tsx`'s existing, established convention exactly — `LoadingState`,
`ErrorState` with `onRetry`, `EmptyState` — all three already exist as shared components):
- *Loading*: `LoadingState message="Querying Prometheus…"` per card/chart, independently.
- *Empty*: `EmptyState` when Prometheus returns zero samples for a query in range — an honest empty state,
  never a fabricated zero (matching `MetricsPage.tsx`'s explicit "never a fabricated line" comment).
- *Error*: `ErrorState` with a retry button, scoped to the one failed query — never blocks the rest of the
  page (see §10).
- *Service unavailable*: a query result for a service whose process is down still returns
  `success:true, samples:[]` from Prometheus (the last-scraped value simply stops updating, or the series
  is absent) — Prometheus itself does not report "service down" as a query error; that distinction would
  need `up{job="paymentx-llm-service"}` (a metric Prometheus generates for every scrape target
  automatically — not currently in the `PrometheusMetricQuery` enum, and a natural one to add for this
  page specifically, see §16).

## 7. Data Freshness (Step 8)

Reuse the existing convention exactly: React Query with `refetchInterval: 30_000` (30s), matching both
`useAllPrometheusMetrics()` and `useAiHealth()` today, chosen to reasonably track Prometheus's own 15s
scrape interval without over-polling. No new polling mechanism, no WebSocket, no SSE — none exist anywhere
in this frontend today, and introducing one would be a new pattern this task's Step 14 explicitly says not
to do.

## 8. Time Ranges (Step 9)

Reuse the existing, already-proven **exact 6 windows**: 5m / 15m / 30m / 1h / 6h / 24h — these are not a
new design choice, they are `PrometheusMetricsService.ALLOWED_RANGE_MINUTES` and
`MetricsPage.tsx`'s `METRIC_TIME_RANGES`, verbatim. No time-series database is needed or proposed —
Prometheus's own local TSDB (default retention, whatever `prom/prometheus:latest`'s built-in default is —
not overridden in `docker-compose.yml`) already backs all 6 windows today for every existing chart on
`/metrics`; the same is true for any new AI PromQL query added to the same enum.

## 9. Error Handling (Step 10)

| Condition | Existing handling in `PrometheusClient` | UI behavior |
|---|---|---|
| Prometheus unreachable | `ResourceAccessException` → `success:false, errorMessage:"Could not reach Prometheus: ..."` | `ErrorState` for that card/chart only |
| Prometheus 4xx/5xx | `RestClientException` → `success:false, errorMessage:<message>` | `ErrorState` for that card/chart only |
| Metric/series missing | `success:true, samples:[]` (empty result, not an error) | `EmptyState` for that card/chart only |
| One query fails, others succeed | Each `PrometheusQueryResult`/`PrometheusRangeResult` is independent | Every other card/chart on the page renders normally — `runAll()`/per-slug calls are independent HTTP round-trips, one failure cannot cascade |
| AI service itself down (not Prometheus) | Not distinguishable from "metric temporarily flat" without an `up{job=...}` query (see §16) | Documented gap, not silently hidden |

The existing architecture already degrades gracefully per-query — nothing new needs to be built for this,
only reused, because every query already returns its own independent success/failure envelope rather than
one endpoint failing the whole page.

## 10. Performance (Step 11)

- **Number of Prometheus queries**: adding ~25-30 AI-specific PromQL entries (see §16) to the existing
  14-entry `PrometheusMetricQuery` enum would roughly triple `GET /api/v1/prometheus/metrics/all`'s fan-out
  if that same endpoint were reused for the AI page's card data. **Recommendation**: do not reuse `/all`
  for the AI page; add either (a) a second `runAll()`-style method that only executes a
  service-scoped subset (e.g. `runAllForCategory("ai")` if the enum grows a category tag), or (b) have the
  AI Metrics page call only the ~10-15 specific slugs it actually needs via the existing single-slug
  endpoint, in parallel from the frontend (React Query supports N independent `useQuery` calls firing
  concurrently already — this is what `RangeChart`-per-slug already does on `/metrics` today).
- **Backend caching**: none exists today (`PrometheusClient` calls Prometheus fresh on every request), and
  Step 11 explicitly says not to add Redis without evidence of need. Given Prometheus instant queries are
  typically single-digit milliseconds and the 30s poll interval, no caching is evidently needed yet — this
  matches the existing `/metrics` page's own (uncached) behavior in production use.
- **Frontend polling**: 30s, matching §7 — not aggressive.
- **Query timeout**: already bounded — `connectTimeoutMs=3000`/`readTimeoutMs=8000` on the shared
  `prometheusRestTemplate` bean, the same timeout every existing Prometheus-backed page already accepts.
- **Parallel query behavior**: `runAll()` is sequential server-side today; a large AI catalog added to it
  as-is would add roughly `N × per-query-latency` to that one endpoint's response time. This is the
  clearest, most concrete performance gap this audit found — see §16's `runAll()` sequential-fan-out row.

## 11. Testing Design (Step 12 — design only, none added)

**Backend:**
- `PrometheusMetricQueryTest` already exists (enum-shape test) — a new AI-specific test would follow its
  exact pattern for any new enum constants (slug uniqueness, non-blank PromQL, `fromSlug` round-trip).
- **Gap**: no `PrometheusClientTest` or `PrometheusMetricsServiceTest` exist today for the *existing*
  Prometheus integration either. A real implementation of this phase should add a WireMock-based
  `PrometheusClientTest` (matching every other client-test convention already audited in Phase 3.10.1/3.10.2
  — e.g. `PaymentServiceClientTest`, `RagAuditClientTest`) covering: successful instant/range query parsing,
  Prometheus down/5xx/malformed-JSON → `success:false` never an exception, and a
  `PrometheusMetricsServiceTest` (Mockito) covering the allowed-range-minutes fallback logic.
- A `PrometheusControllerTest` (`@SpringBootTest` + `TestRestTemplate`, matching this backend's controller-test
  convention) covering the 400-on-unknown-slug behavior would close a real, currently-unverified gap.

**Frontend:**
- `AiAssistantPage.test.tsx` and `ChatInput.test.tsx`/`AIStatus.test.tsx` establish the existing convention
  (Vitest + React Testing Library, one `.test.tsx` per component/page, mocking the service-layer function).
  A new `AiMetricsPage.test.tsx` would follow this exactly: mock `prometheusService`/`aiService`, assert
  loading/error/empty/data states render correctly per card.
- **Gap**: `MetricsPage.tsx` itself has **no** existing test file today — the AI Metrics page test plan
  should not assume that pattern is proven, and should be written fresh rather than "matching MetricsPage's
  test," since none exists to match.

**None of this is implemented in this phase** — Step 12 is explicitly design-only.

## 12. Real Metric Validation (Step 13)

Checked for a live environment before writing this section:

- Ports `8080` (API Gateway), `8092`-`8098` (all 7 AI services), and `9090` (Prometheus): **all
  unreachable** (`curl --max-time 1` on each `/actuator/health` / Prometheus root returned no response).
- `docker ps`: **failed** — "failed to connect to the docker API ... The system cannot find the file
  specified" — Docker Desktop is not running on this machine.

**No live services or containers were found.** Per this task's explicit instruction, none were started to
force evidence. Every metric name and every architectural claim in this document was instead confirmed by
reading the actual Java source (`*Metrics.java` files) and configuration (`prometheus.yml`,
`application.yml`, `docker-compose.yml`) directly — not by querying a running Prometheus instance, and not
assumed from documentation or naming convention.

## 13. Existing Frontend Conventions (Step 14)

- **Routing**: one flat `RouteDefinition[]` in `utils/routes.ts` (path, label, description, icon, group);
  `router.tsx` lazy-loads a page component per path; `Sidebar.tsx` renders `ROUTES` grouped by
  `ROUTE_GROUPS`. Adding a page = one new array entry + one new lazy import line.
- **Component structure**: `pages/*.tsx` (route-level, data-fetching via hooks) →
  `components/<domain>/*.tsx` (presentational, domain-scoped, e.g. `components/ai/`) + generic
  `components/*.tsx` (`PageContainer`, `PageHeader`, `LoadingState`, `ErrorState`, `EmptyState`,
  `TimeRangeSelector`, `MetricCard` — all already exist and are directly reusable for this page, zero new
  generic components needed).
- **API client convention**: one `services/<domain>Service.ts` file per backend domain, each function a
  thin `axiosClient.get<ApiResponse<T>>(...)` call, typed interfaces mirroring backend DTOs 1:1
  (`prometheusService.ts`, `aiService.ts` are the two directly relevant precedents).
- **State management**: TanStack React Query for all server state (`hooks/use*.ts`, one hook per query
  shape); Redux (`app/hooks.ts` + a `theme` slice) only for UI-local state (sidebar collapse, theme) — no
  server data lives in Redux.
- **Styling**: MUI (`@mui/material`), theme tokens (`theme.palette.status.*` for health-state colors,
  already used by `AIStatus.tsx` — reusable for the new per-component health grid).
- **Chart library**: `recharts` (already a dependency, already used exactly once, in `MetricsPage.tsx`) —
  reuse directly, do not introduce a second charting library.
- **Test convention**: Vitest + React Testing Library, `ComponentName.test.tsx` colocated with the
  component.

**No new frontend framework, chart library, or state-management approach is needed or proposed.**

## 14. Architecture (Step 15)

**CURRENT:**
```
AI Services (Prompt/LLM/Embedding/Vector/RAG/MCP/Agent)
    ↓ (hand-registered Micrometer meters)
/actuator/prometheus per service
    ↓ (infra/prometheus.yml, 15s scrape, all 7 already configured)
Prometheus (container, port 9090, host.docker.internal scrape target)
    ↓ (NOT queried by anything AI-specific today)
[no consumer yet for AI metrics specifically]
```

Separately, already fully wired for the *generic* (non-AI) metric catalog:
```
Prometheus → PrometheusClient → PrometheusMetricsService → PrometheusController
    → GET /api/v1/prometheus/metrics(/all|/{slug}|/{slug}/range)
    → prometheusService.ts → useAllPrometheusMetrics()/usePrometheusRange() → MetricsPage.tsx ("/metrics")
```

**TARGET (Phase 3.10.3, if implemented):**
```
AI Services → Prometheus (unchanged — already real, already scraping all 7)
    ↓
PrometheusMetricQuery enum: +N new AI-specific slugs (llm-*, rag-*, mcp-*, agent-*, embedding-*, vector-*, prompt-*)
    ↓
PrometheusClient / PrometheusMetricsService (unchanged - same classes, larger enum)
    ↓
PrometheusController (unchanged endpoints - same 4 routes, more slugs available through them)
    ↓
aiMetricsService.ts (new: which of the enum's slugs make up "the AI page", + reuses fetchPrometheusRange)
    ↓
AiMetricsPage.tsx (new page, '/ai-metrics', 'AI' route group) + useAiHealth() (existing, reused as-is)
```

This target uses only repository-evidenced components — no new architectural layer, no new service, no new
database, no new message broker. It is a data-catalog extension plus one new page, on top of
infrastructure that is already fully built and already in production use for the non-AI metrics catalog.

## 15. Gap Matrix (Step 16)

| Requirement | Existing Capability | Gap | Priority |
|---|---|---|---|
| AI metrics are collected | 7 services, 60+ real Micrometer meters (§1) | None | — (already done) |
| AI metrics are scraped by Prometheus | All 7 targets configured, 15s interval | None | — (already done) |
| Safe, allowlisted PromQL execution path | `PrometheusMetricQuery` enum + `PrometheusClient` + `PrometheusController` | None (mechanism exists) | — (already done) |
| AI-specific PromQL queries registered | 0 of ~60 AI meters have a catalog entry today (the 14 existing entries are generic HTTP/JVM/infra + 3 unrelated business metrics) | **Add AI-specific `PrometheusMetricQuery` enum entries** (e.g. `llm-request-rate`, `llm-generate-latency-p95`, `rag-success-rate`, `rag-total-latency-p95`, `mcp-tool-calls-by-tool`, `mcp-tool-denied-rate`, `agent-execution-latency-p95`, `agent-success-rate`, `embedding-latency-p95`, `vector-search-latency-p95`, `prompt-render-failure-rate`, and an `up{job=~"paymentx-(llm\|rag\|mcp-gateway\|agent-orchestrator\|embedding\|vector\|prompt)-service"}` per-service-up gauge) | **REQUIRED FOR UI** |
| "Is this AI service actually up" signal | Only via `GET /api/v1/ai/health` (per-component READY/NOT_READY, already reused for §4) | `up{job=...}` gives a Prometheus-native, per-target corroborating signal — not strictly required since `/ai/health` already covers it | OPTIONAL |
| Backend endpoint scoped to "AI metrics only" | `/api/v1/prometheus/metrics/all` runs the ENTIRE catalog (would grow to ~40 entries) | A scoped way to fetch only the AI subset (category tag on the enum, or a dedicated `/api/v1/ai/metrics` endpoint reusing `PrometheusMetricsService`) | RECOMMENDED |
| Frontend page | `/metrics` exists but is generic/all-services; no AI-grouped, AI-labelled page exists | New `AiMetricsPage.tsx` at `/ai-metrics`, new `aiMetricsService.ts` (or reuse `prometheusService.ts` with a fixed slug list) | **REQUIRED FOR UI** |
| Nav entry | `'AI'` route group already exists with 1 entry | One new `ROUTES` entry | **REQUIRED FOR UI** |
| Overall AI health widget (multi-component) | `useAiHealth()` fetches the full per-component map already; only the rolled-up dot is rendered today | New presentational component rendering the existing `components` map (no backend change) | **REQUIRED FOR UI** |
| Per-tool MCP breakdown table | `toolName` label already present on every relevant MCP counter | New table component consuming existing labelled samples | RECOMMENDED |
| Backend tests for Prometheus integration | `PrometheusMetricQueryTest` only; no client/service/controller test | New tests for both the existing gap and any new AI entries | RECOMMENDED |
| Frontend tests for the new page | No test exists for `MetricsPage.tsx` either (nothing to extend) | New `AiMetricsPage.test.tsx` written fresh | RECOMMENDED |
| Sequential `runAll()` fan-out at AI-catalog scale | Sequential today, fine at 14 entries | Could add latency at ~40 entries if the AI page naively reuses `/all` | OPTIONAL (avoid by NOT reusing `/all` — see §10) |
| Grafana dashard for AI | Grafana container exists in `docker-compose.yml`, unrelated to Control Center, no AI dashboard | Not this phase's job — explicitly out of scope | **NOT NEEDED** (Control Center UI is the deliverable, not Grafana) |
| New metrics collection | N/A — already exists | None | **NOT NEEDED** — do not create duplicate metrics, per this task's own instruction |

## 16. Implementation Plan (Step 17 — plan only, not executed)

1. **Backend: register AI-specific PromQL queries**
   - *Files*: `dto/prometheus/PrometheusMetricQuery.java` (add ~12-15 enum constants, following the exact
     existing `(slug, description, promQl)` shape and the exact real metric names from §1 — e.g.
     `sum(rate(llm_generate_success_total[5m])) by (provider)`,
     `histogram_quantile(0.95, sum(rate(rag_total_latency_seconds_bucket[5m])) by (le))`,
     `sum(rate(mcp_tool_calls_total[5m])) by (toolName)`).
   - *Dependencies*: none new — same `PrometheusClient`/`PrometheusMetricsService`/`PrometheusController`.
   - *Risk*: LOW — additive enum entries only; must verify each PromQL string against the real metric name
     (histogram bucket suffix `_seconds_bucket` for `publishPercentileHistogram()` timers specifically —
     `rag_total_latency`, `llm_generate_latency`, `embedding_latency`, `vector_search_latency`,
     `agent_execution_latency`, `mcp_tool_latency` all call `.publishPercentileHistogram()`, the other
     Timers do not and so have no `_bucket` series to `histogram_quantile` over — this distinction must be
     honored per-metric, not assumed uniform).
   - *Tests*: extend `PrometheusMetricQueryTest`-style coverage for the new constants (uniqueness,
     non-blank).
   - *Rollback*: revert the enum file; no other component depends on the new constants existing.

2. **Backend: safe metrics API scoped to AI** *(RECOMMENDED, not strictly required)*
   - *Files*: either add a `category`/`domain` field to `PrometheusMetricQuery` + a
     `PrometheusMetricsService.runAllForCategory(...)` method, or add a small new
     `AiMetricsService`/`AiMetricsController` (`GET /api/v1/ai/metrics`) that calls the existing
     `PrometheusClient` for a fixed, hand-picked slug list.
   - *Dependencies*: none new.
   - *Risk*: LOW-MEDIUM — a new controller inherits `DashboardAuthFilter` automatically (path starts with
     `/api/`), so no security work is implied, only correct wiring.
   - *Tests*: new `PrometheusMetricsServiceTest`/`AiMetricsControllerTest` per §11.
   - *Rollback*: remove the new class(es); `PrometheusController`'s existing 4 routes are untouched either
     way.

3. **Frontend: AI Metrics page**
   - *Files*: `pages/AiMetricsPage.tsx` (new), `services/aiMetricsService.ts` or extend
     `prometheusService.ts` (new functions only, existing ones untouched),
     `hooks/useAiMetrics.ts` or extend `hooks/usePrometheusMetrics.ts`, `utils/routes.ts` (+1 entry),
     `router.tsx` (+1 lazy import line). Reuses `PageContainer`, `PageHeader`, `LoadingState`, `ErrorState`,
     `EmptyState`, `TimeRangeSelector`, `MetricCard`, and `useAiHealth()` verbatim.
   - *Dependencies*: none new (recharts already present).
   - *Risk*: LOW — purely additive route; no existing page's code path is touched.
   - *Tests*: new `AiMetricsPage.test.tsx` per §11.
   - *Rollback*: remove the new files + the one `routes.ts` entry + the one `router.tsx` line.

4. **Tests** (backend + frontend, per §11) — written alongside 1-3, not as an afterthought, per this
   platform's own established convention (every phase audited in this repository ships tests with the
   feature, not after it).

5. **Browser validation**
   - Start the 7 AI services + Prometheus + Control Center (backend + frontend) locally (or via
     `infra/docker-compose.yml` where applicable), drive at least one real request through each AI service
     (e.g. one real `/api/v1/ai/chat` call exercises Prompt+LLM+RAG+MCP+Agent together, matching Phase
     3.9's own validation pattern), confirm each new card/chart shows a real, non-zero data point, confirm
     loading/error/empty states render correctly by temporarily pointing `rag.audit-service-url`-style
     config at an unreachable host for one query (or stopping one AI service) and observing graceful
     degradation.

6. **Regression** (Step 19 — see §17 below) — confirm `/metrics`, `/ai-assistant`, and every other existing
   page still work unmodified; confirm no existing `PrometheusMetricQuery` enum constant's slug or PromQL
   text changed (only additions).

No file name above is invented beyond what naturally follows this repository's own established per-domain
file-per-concern convention (`<domain>Service.ts`, `use<Domain>.ts`, `<Domain>Page.tsx`) — the exact final
names are an implementation-time decision, not fixed here.

## 17. Regression Safety (Step 19 — confirmed safe by design)

Every file this plan touches is either brand new (a page, a service file, a hook, a controller) or an
**additive-only** change to an existing enum (new constants, zero constants removed or renamed) and a
route list (one new entry, zero existing entries changed). Nothing in this plan:

- touches Phase 1 payment/validation/routing/audit/notification/reconciliation/reporting service code,
- touches Auth Service,
- touches RAG Service, RAG's audit layer (Phase 3.10.2), MCP Gateway, Agent Orchestrator, LLM Service, or
  Embedding/Vector Service production logic (it only *reads* the metrics they already publish),
- changes any existing `PrometheusMetricQuery` value or any existing frontend route/page,
- requires a database schema change, a new dependency, or a new infrastructure component.

The AI metrics UI is, by construction, a pure read path added on top of already-existing, already-running
telemetry.

---

## FINAL DESIGN SUMMARY

1. **Existing metric inventory**: §1 — 7 services, ~60 real Micrometer meters, fully cataloged by name.
2. **Prometheus architecture**: §2 — all 7 AI services already scraped every 15s; no config change needed.
3. **Control Center architecture**: §3 — the exact safe `Backend → Prometheus → API → Frontend` pattern
   already exists and is production-proven via `/metrics`.
4. **Proposed AI Metrics page**: §6 — `/ai-metrics`, grouped by the 7 real services, cards-first,
   charts only where genuinely time-varying, reusing every existing shared UI component.
5. **Backend API design**: §15/§16 item 1-2 — extend `PrometheusMetricQuery`, optionally add an
   AI-scoped catalog endpoint; zero new architecture.
6. **Frontend design**: §13/§16 item 3 — one new page, reusing every existing convention/component/library.
7. **Security model**: §5 — already safe by construction (allowlisted enum, server-side only,
   inherits the existing dashboard-token gate).
8. **Error handling**: §9 — already handled per-query at the client level; reuse `LoadingState`/
   `ErrorState`/`EmptyState` per card, independent failure per query.
9. **Performance strategy**: §10 — avoid naive reuse of the sequential `/metrics/all` endpoint at AI-catalog
   scale; no caching evidently needed yet.
10. **Test strategy**: §11 — extend the (currently thin) Prometheus backend test coverage; write a fresh
    frontend test for the new page (no existing `MetricsPage` test to extend).
11. **Browser validation strategy**: §16 item 5 — real end-to-end request through all 7 AI services,
    verify real non-fabricated data renders, verify graceful degradation.
12. **Implementation order**: §16 — enum entries → (optional) scoped API → frontend page → tests →
    browser validation → regression check.
13. **Risks**: all rated LOW or LOW-MEDIUM (§16); the main one is correctly matching PromQL to each
    metric's real type (counter/timer/histogram/summary) rather than assuming uniformity.
14. **Out-of-scope items** (per this task's own strict instructions): Grafana, a new time-series database,
    per-user AI identity, conversation persistence, MCP/Agent/LLM/Embedding/Vector production changes, any
    change to Phase 1, Phase 3.9, or the Phase 3.10.2 RAG audit layer.

---

## FINAL RECOMMENDATION

**AI METRICS ALREADY EXIST:** YES — confirmed by direct source inspection of all 7 `*Metrics.java` files
(~60 real Micrometer meters).

**PROMETHEUS SCRAPING:** PASS (config-verified) — all 7 AI services are configured as scrape targets in
`infra/prometheus.yml` at a 15s interval. NOT VERIFIED live (no running Prometheus instance was found to
query — see §12); the configuration itself is unambiguous and was not modified or guessed.

**CONTROL CENTER INTEGRATION:** PARTIAL — the safe backend integration *mechanism*
(`PrometheusClient`/`PrometheusMetricsService`/`PrometheusController`, allowlisted-enum, no-raw-PromQL) is
**COMPLETE and production-proven** via the existing `/metrics` page; what's **MISSING** is (a) AI-specific
PromQL entries in that mechanism's enum, and (b) a dedicated AI-grouped frontend page. Zero new
architecture is needed — only cataloging + one new page.

**RECOMMENDED ARCHITECTURE:** Reuse the existing `PrometheusClient → PrometheusMetricsService →
PrometheusController` pipeline unchanged; add AI-specific entries to `PrometheusMetricQuery`; add one new
Control Center frontend page (`/ai-metrics`, in the existing `'AI'` nav group) built from existing shared
components (`MetricCard`, `RangeChart` pattern from `MetricsPage.tsx`, `LoadingState`/`ErrorState`/
`EmptyState`, `TimeRangeSelector`) plus the already-fetched-but-underused `useAiHealth()` per-component data
for an "Overall Health" section.

**REQUIRED IMPLEMENTATION:**
- Add AI-specific `PrometheusMetricQuery` enum entries (§16 item 1).
- Add the new `AiMetricsPage.tsx` + its route/nav entry + its API-client function(s)/hook(s) (§16 item 3).
- Render the existing `useAiHealth()` per-component map for "Overall Health" (§4, §16 item 3).

**OPTIONAL:**
- A dedicated `/api/v1/ai/metrics`-style scoped endpoint (or a category tag on the enum) instead of reusing
  the generic `/all` endpoint (§16 item 2, §10).
- `up{job=...}` per-AI-service Prometheus-native liveness queries, supplementing `/api/v1/ai/health`.
- A per-tool MCP breakdown table.

**OUT OF SCOPE (per this task's explicit instructions, and confirmed by this audit to genuinely be
unnecessary for this feature):** Grafana, any new metrics collection, any change to Phase 1/Auth/Phase 3.9,
any change to RAG's audit layer, MCP, Agent Orchestrator, LLM, Embedding, or Vector production logic, a new
time-series database, arbitrary/user-controlled PromQL.

**READY FOR IMPLEMENTATION:** YES — the underlying telemetry, the safe query-execution architecture, the
frontend conventions, and the navigation slot are all already in place and evidenced directly from the
repository; implementation is additive cataloging + one new page, not new architecture.
