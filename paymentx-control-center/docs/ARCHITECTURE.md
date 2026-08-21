# Control Center Architecture

Status: feature-complete through Phase 6 (production hardening). This
document describes the real, current system - not a roadmap. For the
REST API surface see `API.md`; for setup/ops see `RUNBOOK.md`.

## 1. Why a separate module, not a page inside an existing service

Every existing PaymentX service (`paymentx-auth-service`,
`paymentx-payment-service`, etc.) owns one business responsibility. The
Control Center owns none of them - it is an operational view *over*
all of them. Bolting a dashboard onto any single service would give it
an arbitrary, misleading "home" and couple dashboard deploys to that
service's deploy lifecycle. A separate module with its own backend
process and its own frontend build avoids both problems.

## 2. Why the backend has no dependency on paymentx-common-library

`paymentx-common-library` is for the business services to share event
envelopes, exception hierarchies, and correlation-ID propagation (see
`docs/adr/0004-paymentx-common-library.md` at the repo root). The
Control Center backend never participates in that business-domain
event flow - it is a pure client-facing HTTP API with its own,
deliberately minimal `ApiResponse`/`ErrorResponse` shape. This keeps
its release cadence, dependency surface, and blast radius completely
independent of the platform it observes: a bug in this dashboard can
never break a payment; a change to `paymentx-common-library` can never
break this dashboard.

## 3. System shape

```
                     ┌─────────────────────────┐
   Browser  ───────▶ │  Control Center Frontend │  React 19 + Vite SPA (5173 dev / :80 in Docker via nginx)
                     └────────────┬─────────────┘
                                  │ HTTPS/HTTP, JSON, real Authorization header when auth is enabled
                                  ▼
                     ┌─────────────────────────┐
                     │  Control Center Backend  │  Spring Boot, port 8089
                     │  (this module)           │  zero compile dep on any business service
                     └────────────┬─────────────┘
                                  │ every outbound call target is server-configured
                                  │ (ControlCenterProperties) - never browser-supplied
              ┌───────────────────┼────────────────────────────────────┐
              ▼                   ▼                                    ▼
   ┌─────────────────┐  ┌──────────────────────┐          ┌────────────────────────┐
   │ 7 Postgres DBs   │  │ Kafka / Redis /       │          │ 9 PaymentX services    │
   │ (read-only JDBC) │  │ RabbitMQ / Prometheus │          │ (health + a fixed,     │
   │                  │  │ / Zipkin / MailHog    │          │  allowlisted REST set) │
   └─────────────────┘  └──────────────────────┘          └────────────────────────┘
```

Every one of those outbound targets is resolved from
`ControlCenterProperties` (bound from `application.yml` /
`application-{profile}.yml` / environment variables) - no controller,
service, or client anywhere in this backend accepts a host, port, or
URL from the browser. That is the entire SSRF defense, applied
uniformly (`ServiceIdentifier` enum for the 9 services,
`ApiTesterAllowlist` for the API Tester's extra method/path
restriction on top of that).

## 4. Backend package map

```
backend/src/main/java/com/paymentx/controlcenter/
    controller/   One REST controller per domain (Kafka, RabbitMQ, Redis, Postgres,
                  Prometheus, Zipkin, Files, Logs, ApiTester, E2E, Search, Alerts, Services, Health)
    service/      Business/orchestration logic - PostgresDataService, PaymentFlowService,
                  AlertsService, E2EFlowService, ApiTesterService, LogFileService, SafeFileService,
                  ApiTesterAllowlist, ServiceHealthAggregationService, GlobalSearchService,
                  RedisMonitoringService, PrometheusMetricsService
    client/       One outbound client per real integration - KafkaAdminMonitoringClient (native
                  AdminClient), RedisMonitoringClient (native Lettuce, read-only), RabbitMqManagementClient,
                  PrometheusClient, ZipkinClient, MailHogClient, ServiceHealthClient (all RestTemplate,
                  each with its own connect/read timeout from ControlCenterProperties)
    repository/   One JDBC repository per Postgres domain - hardcoded, parameterized SQL only,
                  never a client-supplied WHERE clause or raw SQL string
    dto/          Records mirroring exactly what the frontend types/*.ts expect
    config/       ControlCenterProperties (the one typed config surface + SSRF allowlist),
                  CorsConfig, CorrelationIdFilter, DashboardAuthFilter, HttpClientConfig,
                  PostgresDataSourceConfig (7 read-only DataSource beans), KafkaAdminConfig
    exception/    ControlCenterException + GlobalExceptionHandler (one response shape for every failure)
    security/     Documented placeholder - see DashboardAuthFilter for the real, lightweight
                  auth mechanism actually in place (not a full Spring Security starter - see §7)
```

## 5. Frontend structure

```
frontend/src/
    app/          App.tsx (provider nesting: Redux -> Theme -> React Query -> AuthGate -> Router),
                  AuthGate.tsx (Phase 6 - the frontend half of the optional dashboard-token gate)
    components/   ~17 reusable components (DataTable, Pager, SearchBar, FilterBar, TimeRangeSelector,
                  ConfirmationDialog, RouteErrorBoundary, StatusBadge, MetricCard, ...)
    layouts/      AppLayout (shell + RouteErrorBoundary around the routed Outlet), Header
                  (desktop sidebar toggle + Phase 6 mobile nav trigger), Sidebar (permanent desktop
                  Drawer + Phase 6 temporary mobile Drawer, both driven by utils/routes.ts), Footer
    pages/        One file per route (19 routes + NotFound)
    features/     Redux slices - theme mode + sidebar-collapsed, the one piece of client-only global state
    hooks/        React Query hooks - one per service module, plus useDebouncedValue (Phase 6)
    services/     Functions that call the backend via axiosClient - one per backend domain
    api/          axiosClient (HTTP + Phase 6 dashboard-token interceptor), queryClient
                  (React Query defaults, including Phase 6's networkMode: 'always' fix - see RUNBOOK.md
                  troubleshooting for why)
    types/        Shared TypeScript types mirroring backend DTOs
    utils/        routes.ts (the ONE route/nav source of truth), formatters.ts,
                  dashboardAuth.ts (Phase 6 token storage), apiTesterHistory.ts (Phase 6 bounded local history)
    theme/        MUI theme + semantic status color tokens (dark/light)
```

## 6. Route/navigation single source of truth

`frontend/src/utils/routes.ts` is the only place a route's path, label,
icon, and nav group are defined. `app/router.tsx` (route -> page
component mapping, with a runtime guard that fails loudly at
app-construction time if the two ever drift) and `layouts/Sidebar.tsx`
(what the user sees in navigation, both the permanent desktop Drawer
and the Phase 6 temporary mobile Drawer) are both built directly from
that one array.

## 7. Security model (see RUNBOOK.md#security for the full picture)

- **SSRF**: every outbound call target is a server-configured value
  resolved through a fixed enum/allowlist, never browser input. The
  API Tester adds a second layer on top: a hardcoded
  `{service, method, pathTemplate}` catalog (`ApiTesterAllowlist`) -
  DELETE is only reachable on the two real endpoints that explicitly
  allow it.
- **Authentication**: off by default (matches every prior phase's
  verified local-dev behavior). When
  `control-center.security.enabled=true`, `DashboardAuthFilter` gates
  every `/api/**` call (except the health check) behind a real,
  constant-time-compared `Authorization: Bearer <token>` check, and
  **refuses to start** if enabled without a real token configured
  (fail-closed, not fail-open). This is deliberately a single
  shared-token gate, not multi-user authorization - PaymentX's real
  `auth-service` has zero REST endpoints today (confirmed live), so
  there is no real per-user identity to authenticate against yet.
- **Secrets**: no JWT secret, API key, DB/Kafka/RabbitMQ credential,
  or private key is ever returned in a response or written to a log
  line anywhere in this module (verified by direct code review of
  every client, every exception path, and the API Tester's request
  logging, which logs only method/path/status/duration - never
  headers or bodies).
- **Database access**: every Postgres repository is hardcoded,
  parameterized SQL - no endpoint accepts a raw SQL string, and there
  is no UPDATE/DELETE anywhere in this module's SQL.
- **CSRF**: not applicable - this backend is a stateless REST API with
  no cookie-based session; the optional dashboard token is a bearer
  token a script must explicitly attach, never an ambient credential a
  browser sends automatically the way a cookie would.

## 8. Performance posture

- Every list endpoint is server-paginated (`PageResponse<T>`, size
  clamped 1-200 server-side) - no unbounded Postgres query.
- The Log Viewer is hard-bounded (`maxLinesScannedPerFile`,
  `maxEntriesReturned`) - never loads or returns an unlimited log.
- Every live-search input (Audit, Notifications, Reconciliation
  Records, Database viewer, Logs, Global Search) is debounced
  (`useDebouncedValue`, Phase 6) before it reaches a query - typing a
  5-character term fires one request, not five.
- Every polling interval is 15-30s (Alerts, health checks, Payments
  stats, Prometheus) - nothing polls sub-second, nothing polls
  forever without a terminal condition (E2E's poll stops the moment
  `overallStatus` leaves `RUNNING`).
- Vendor code-splitting (`vite.config.ts` `manualChunks`, Phase 6)
  separates React/MUI/React-Query into their own cacheable chunks;
  every page is still individually lazy-loaded via `router.tsx`.
- E2E and Log Viewer results are bounded, in-memory, capped
  collections (history limit 20, log entries limit 1000) - never
  unbounded browser or server state.

## 9. Error handling posture

Every query-driving hook surfaces `isLoading` / `isError` /
`data` distinctly, and every page renders the matching
`LoadingState` / `ErrorState` / real content - there is no page that
silently shows nothing when its backend call fails. A
`RouteErrorBoundary` (Phase 6) wraps the routed page content so an
unexpected render error in one page can never take down the whole
app shell (Header/Sidebar/Footer stay interactive; the user can
navigate away without a browser reload). See `RUNBOOK.md`'s
troubleshooting section for the one real, subtle bug this posture
depends on having fixed (`networkMode`).

## 10. Historical design notes (still accurate)

**Why Redux Toolkit is used for exactly one thing.** Theme mode (and
sidebar-collapsed state) is genuinely global, synchronous, client-only
UI state read simultaneously by `ThemedApp`, `Header`, and `Sidebar` -
the textbook case Redux is good for. Every other kind of state this
dashboard uses - payments, reconciliation batches, Kafka topics,
metrics, E2E run state - is *server* state with its own
caching/staleness/retry semantics, which is what TanStack React Query
is for. Putting server data in Redux (a common overreach) would mean
hand-rolling cache invalidation Query already solves.

**Why no fabricated data anywhere.** A dashboard that shows
plausible-looking numbers with no real backend behind them is actively
misleading. This was an explicit requirement from Phase 1 onward and
has held through every subsequent phase: every page either shows real
data, or an honest `EmptyState`/`ErrorState` naming exactly what's
missing and why (e.g. Payment Flow's Reconciliation/Reporting stages
are `UNAVAILABLE` with a stated reason when this schema genuinely has
no per-payment linkage for them - never silently shown as complete).
