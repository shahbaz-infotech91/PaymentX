# PaymentX — Phase 3 AI Platform Architecture & Readiness Audit

**Status:** Phase 3.0 — Architecture & Readiness Audit (design-only; no AI code implemented)
**Date:** 2026-08-17
**Scope:** This document is the output of a full, evidence-based inspection of the existing PaymentX repository at `C:\PaymentX`. Every claim below is backed by actual code/configuration unless explicitly marked otherwise. Where something does not exist, it is marked **NOT AVAILABLE** / **NOT CURRENTLY AVAILABLE** rather than invented.

Every section distinguishes **CURRENT** (verified to exist today) from **FUTURE** (Phase 3 design, not yet built).

---

## 1. Current Architecture

PaymentX is a real-time payment orchestration platform: a Maven multi-module monorepo (`C:\PaymentX\pom.xml`, `groupId com.paymentx`, `paymentx-parent`, packaging `pom`), Spring Boot **3.5.9**, Spring Cloud **2025.0.1**, Java **21**. It is explicitly described in its own README as "a production-grade learning project modeled on how real fintech companies architect payment rails," and its module roadmap **already lists an "AI Layer — Payment Error Analyzer (LLM), RAG-based RCA, MCP integration"** as item 11 — Phase 3 is not a foreign idea being bolted on, it was anticipated from the start (`README.md`, `docs/adr/0003`, `docs/adr/0004` all reference a future "AI Platform" module).

**Modules registered in the root `pom.xml` reactor** (build order):
`paymentx-common` (deprecated) → `paymentx-common-library` → `paymentx-api-gateway` → `paymentx-auth-service` → `paymentx-validation-service` → `paymentx-payment-service` → `paymentx-routing-service` → `paymentx-audit-service` → `paymentx-notification-service` → `paymentx-reconciliation-service` → `paymentx-reporting-service` → `paymentx-control-center/backend`.

`paymentx-control-center/frontend` is **not** a Maven module (it's a separate Vite/npm project, invoked independently).

**Local infrastructure** (`infra/docker-compose.yml`): Postgres 16-alpine (one container, 8 logical databases via `POSTGRES_MULTIPLE_DATABASES`, mapped to host port **5433** — not 5432, because a native Windows PostgreSQL 18 service already occupies 5432 on the dev host), Redis 7, Kafka 7.7.0 in **KRaft mode** (no Zookeeper), RabbitMQ 3.13-management, Kafka UI, pgAdmin, Zipkin, Prometheus, Grafana (Prometheus datasource auto-provisioned), MailHog (SMTP capture for notification-service). **All 9 application services run as host-level Java processes** (`mvn spring-boot:run` / built jar) in the documented dev workflow — only `api-gateway` has an optional `docker-build` profile container definition, not used by default. This is why `infra/prometheus.yml` scrapes `host.docker.internal:<port>`, not container DNS names.

**Port map (confirmed from `application.yml` in each module):**

| Port | Module |
|---|---|
| 8080 | paymentx-api-gateway |
| 8081 | paymentx-auth-service |
| 8082 | paymentx-validation-service |
| 8083 | paymentx-payment-service |
| 8084 | paymentx-routing-service |
| 8085 | paymentx-audit-service |
| 8086 | paymentx-notification-service |
| 8087 | paymentx-reconciliation-service |
| 8088 | paymentx-reporting-service |
| 8089 | paymentx-control-center/backend |
| 5173 (dev) / 80 (docker, nginx) | paymentx-control-center/frontend |

**Important pre-existing gaps discovered (not introduced by this audit, but load-bearing for every Phase 3 decision below):**

1. **`paymentx-auth-service` is unimplemented.** Its source tree contains exactly two files: `AuthServiceApplication.java` and `config/SecurityConfig.java`. No controllers, no entities, no login/token/refresh endpoints. `SecurityConfig.java` literally `permitAll()`s everything with a `// TODO: replace with internal-service-token validation once this service gains real endpoints` comment. There is **no real JWT issuance anywhere in the platform today** — the only JWT-minting code found anywhere in the repo is a test-only helper (`paymentx-api-gateway/src/test/java/.../TestJwtUtil.java`).
2. **`paymentx-api-gateway` only proxies 2 of the 9 business services** — `/api/v1/validations/**` → validation-service and `/api/v1/payments/**` → payment-service (`GatewayConfig.java`). Routing, audit, notification, reconciliation, reporting, and auth are **not reachable through the gateway today**.
3. **No downstream business service re-validates the JWT itself.** validation-service `permitAll()`s and trusts the network boundary outright; routing-service, audit-service, notification-service, reconciliation-service, and reporting-service each run a `HeaderRoleAuthenticationFilter` that trusts an **unsigned** `X-Roles` / `X-Participant-Id` header (presumably set by the gateway) with no cryptographic verification, and each carries the identical `// TODO(Auth Service): replace with internal-service-token validation` comment. `payment-service` has **no `SecurityConfig` at all** (only a placeholder `package-info.java`) — its actual protection posture is unconfirmed.
4. RabbitMQ is provisioned in `infra/docker-compose.yml` but **no service in the repository currently produces or consumes anything through it** — notification-service's "email channel" is SMTP direct to MailHog, not RabbitMQ. Treat RabbitMQ as infrastructure capacity, not a live integration bus, for Phase 3 planning purposes.
5. `paymentx-common` (deprecated per ADR 0003) is still an active dependency of `paymentx-auth-service` — the migration to `paymentx-common-library` is incomplete.
6. No AWS Secrets Manager integration exists anywhere in the codebase (confirmed by grep across every module). All secrets today are `${ENV_VAR}` placeholders in `application-prod.yml` with no defaults — that env-var convention, not Secrets Manager, is the real "existing secrets approach" Phase 3 should match.

These are not hypothetical risks — they are the actual current state, and they directly shape the AI security boundary in §12 and the MCP design in §10.

---

## 2. Existing Services (Module Inventory)

Evidence-based inventory, per module. "Depends on" indicates `paymentx-common` (deprecated) vs `paymentx-common-library` (canonical).

### paymentx-common — **DEPRECATED** (ADR 0003)
Three classes only: `PaymentEvent<T>`, `PaymentXException`, `CorrelationIdFilter`. Kept building solely so nothing breaks mid-migration. Still depended on by `paymentx-auth-service` only.

### paymentx-common-library — canonical shared library (ADR 0004)
Cross-cutting code only, deliberately **no business domain objects** (that boundary is explicit ADR-level policy — `Money`, `PaymentStatus`, `PaymentScheme` etc. stay service-local). Contains: `ApiResponse`/`ErrorResponse`/`PageResponse`/`HealthResponse`; exception hierarchy (`BusinessException`, `ValidationException`, `ForbiddenException`, `UnauthorizedException`, `ResourceNotFoundException`, `ConflictException`, `RoutingException`); `BaseEntity`/`AuditableEntity`; event model (`BaseEvent`, `PaymentEvent`, `EventMetadata`, `EventType`, `EventHeaders`); `@Mask`/`MaskingSerializer`/`DataMaskingUtils` (PII/PCI masking utilities — directly reusable for AI output sanitization, see §12); `SecurityConstants`, `JwtClaims`, `SecurityContext`; `CorrelationIdUtils`, `TraceIdUtils`, `MdcUtils`, `LoggingContext`; `KafkaTopics` (suffix conventions: `.DLT`, `.retry`, `-group` — not per-topic names, those are owned per-producer); `HeaderConstants` (`X-Correlation-Id`, `X-Trace-Id`, `X-Api-Key`, `X-Idempotency-Key`, `X-Request-Id`, `X-Client-Id`); `ErrorCodes`; MapStruct base mappers. **ADR 0004 explicitly names "AI Platform" as one of the future consumers this library was generalized for.**

### paymentx-api-gateway (port 8080)
**Purpose:** Single entry point — Spring Cloud Gateway (WebFlux/reactive), JWT+API-key auth enforcement, rate limiting, correlation/trace ID injection.
**Routes today:** only `/api/v1/validations/**` → validation-service (default `http://localhost:8082`) and `/api/v1/payments/**` → payment-service (default `http://localhost:8083`), each with Redis-backed `RedisRateLimiter` (keyed on `X-Participant-Id` or remote IP) and a 1 MiB request-size cap.
**Security:** Dual auth — (a) JWT Bearer via `NimbusReactiveJwtDecoder`, **symmetric HMAC-SHA256**, key from `gateway.security.jwt-secret` / env `GATEWAY_JWT_SECRET`, **plaintext default fallback** `local-dev-only-secret-change-me-32chars` in `application.yml`; roles read from a custom `roles` claim (not standard `scope`); (b) API key via `X-Api-Key`, resolved to participant ID through Redis-backed caches. Spring Security's own `authorizeExchange` is `permitAll()` on everything — real enforcement is a custom ordered `GlobalFilter` chain: header sanitization (strips client-supplied trust headers) → correlation/trace ID injection → API-key/JWT participant propagation (sets `X-Participant-Id`) → participant validation → **`AuthenticationEnforcementGlobalFilter`** (the actual 401 gate) → idempotency (`X-Idempotency-Key`, 86400s TTL) → logging. Public paths: `/actuator/**`, `/swagger-ui/**`, `/api-docs/**`, `/v3/api-docs/**`, `/fallback`. **CORS is wide open**: `allowedOriginPatterns=["*"]`, all methods/headers, `allowCredentials=true`. No TLS/mTLS anywhere (plain HTTP, localhost/docker-DNS URIs).
**Observability:** Actuator (`health,prometheus,metrics,gateway`), Zipkin export, 100% trace sampling, correlationId/traceId in log pattern.
**Depends on:** `paymentx-common-library`.

### paymentx-auth-service (port 8081) — **unimplemented skeleton**
Purpose per pom.xml description: "JWT issuance and verification service." Reality: 2 files, no controllers, no entities, no persistence layer, no changelog. `SecurityConfig.java` `permitAll()`s everything with a literal TODO for future real endpoints. Database URL (`paymentx_auth`) is only configured under the `docker` profile — no default/local URL. **Depends on deprecated `paymentx-common`.**

### paymentx-validation-service (port 8082, db `paymentx_validation`)
**Purpose:** Entry-point business-rule validation (blacklist, participant/scheme checks, idempotency dedup) before a payment is allowed onto Kafka.
**Tables:** `participant`, `participant_scheme`, `business_rule` (has `rule_code`), `blacklist`, `certificate`, `idempotency_record`, `validation_log`.
**REST:** **only** `POST /api/v1/validations` — no GET/lookup endpoint exists anywhere in this service.
**Kafka produced:** `instant-payment-validated`, `card-payment-validated`, `real-time-payment-validated`, `payment-rejected` — hyphen-separated (this is the "Validation Service" side of the open naming inconsistency tracked in ADR 0002).
**Security:** `permitAll()`, explicit comment that JWT verification is assumed to happen upstream at the gateway; this service trusts the network boundary outright (no header-role filter even).
**Depends on:** `paymentx-common-library`.

### paymentx-payment-service (port 8083, db `paymentx_payment`)
**Purpose:** Payment lifecycle orchestration/state machine (debit → credit → settle) with a transactional outbox for reliable Kafka publish.
**Tables:** `payment`, `payment_status_history` (free-text `reason` column — **no coded failure-reason catalog exists**), `payment_retry`, `payment_settlement`, `payment_outbox`, `payment_audit`.
**Status model:** 18-state `PaymentStatus` enum (RECEIVED → VALIDATED → PROCESSING → ROUTING → DEBITING → DEBIT_SUCCESS/DEBIT_FAILED → CREDITING → CREDIT_SUCCESS/CREDIT_FAILED → SETTLING → SETTLED, plus RETURNED/REVERSED/FAILED/CANCELLED/TIMEOUT/RETRYING).
**REST** (base `/api/v1/payments`, every response wrapped in `ApiResponse<T>`):
- `GET /{paymentReference}` — full snapshot
- `GET /{paymentReference}/status` — lightweight polling
- `GET ?status=&page=&size=` — paginated list
- `GET /search?status=&scheme=&participantId=&createdFrom=&createdTo=&minAmount=&maxAmount=&page=&size=`
- `POST /{paymentReference}/retry` — operational control only (not a new money-movement entry point — code comment is explicit that money movement enters only via Gateway→Validation→Kafka)
- `POST /{paymentReference}/cancel` — pre-debit only
**Kafka:** consumes `instant/card/real-time-payment-validated`; produces `payment.processing`, `payment.debited`, `payment.credited`, `payment.completed`, `payment.failed`, `payment.returned`, `payment.reversed`, `payment.cancelled`, `payment.timeout` — **dot-separated**, the other half of ADR 0002's naming split. Manual ack, `enable-auto-commit: false`, producer `acks: all` + idempotence.
**Schedulers:** `OutboxScheduler` (5s), `RetryScheduler` (10s), `TimeoutScheduler` (60s, 15-min stuck threshold). Resilience4j circuit breaker + retry for calls to routing-service.
**Security:** **no `SecurityConfig.java`** — only an empty `security/package-info.java`. Flagged as a genuine gap: unconfirmed whether Spring Security is even on the classpath.
**Depends on:** `paymentx-common-library`.

### paymentx-routing-service (port 8084, db `paymentx_routing`)
**Purpose:** Scheme-based routing-rule management + route resolution.
**Tables:** `routing_rule`.
**REST** (base `/api/v1/routes`): `POST /`, `PUT /{id}`, `DELETE /{id}` (all `@PreAuthorize("hasRole('ROUTING_ADMIN')")`); `GET ?scheme=&participantId=&active=&isDefault=&page=&size=`, `GET /{id}`, `GET /default?scheme=`, `GET /participant/{participantId}?scheme=` (all open reads, accept `X-Trace-Id`).
**Kafka:** produces `routing.route-resolved`, `routing.rule-changed`; consumes `participant.deactivated` (producing service unconfirmed from this module alone).
**Security:** `permitAll()` + `HeaderRoleAuthenticationFilter` trusting an unsigned `X-Roles`/`X-Participant-Id` header, `@EnableMethodSecurity` for the `@PreAuthorize` checks. Same TODO comment as elsewhere.
**Depends on:** `paymentx-common-library`.

### paymentx-audit-service (port 8085, db `paymentx_audit`)
**Purpose:** Immutable, read-optimized aggregator of the full cross-service event stream — the platform's single best "what happened to payment X" source.
**Tables:** `audit_event`.
**REST** (base `/api/v1/audit-events`): `POST /` (`@PreAuthorize("hasRole('AUDIT_WRITER')")`, for API/security events not carried by Kafka); `GET /{id}`; **`GET ?correlationId=&paymentId=&participantId=&reference=&status=&eventType=&fromDate=&toDate=&page=&size=`** — the primary audit-trail lookup, and the strongest existing candidate for an MCP `getAuditTrail` tool.
**Kafka:** single `@KafkaListener` on **14 topics** (every validated/payment/routing event), producing `audit.event-recorded`. Uses a `TopicEventTypeResolver` to infer event type/source service from the topic name.
**Security:** identical `permitAll()` + `HeaderRoleAuthenticationFilter` pattern; only the write endpoint is role-gated.
**Depends on:** `paymentx-common-library`.

### paymentx-notification-service (port 8086, db `paymentx_notification`)
**Purpose:** Terminal event consumer — dispatches email/SMS/webhook notifications from payment/validation/routing/audit lifecycle events; admin-triggerable retry/resend.
**Tables:** `notification`, `delivery_attempt`.
**REST** (base `/api/v1/notifications`): `GET /{id}`, `GET ?status=&channel=&sourceEventType=&recipient=&participantId=&dateFrom=&dateTo=&page=&size=`, `GET /{id}/history` (all open); `POST /{id}/retry`, `POST /{id}/resend` (`NOTIFICATION_ADMIN`).
**Kafka:** consumes the **widest topic set on the platform** (14 topics — everything validation/payment/routing/audit produce); **produces nothing** — a "notification sent" event is not currently observable by any other service or future AI audit trail, only queryable via this service's own REST API.
**RabbitMQ:** confirmed **not used** — SMTP direct (MailHog in dev, real SMTP via `${SMTP_PASSWORD}` in prod), pluggable `SmsProvider`, HMAC-signed webhook delivery.
**Statuses:** `PENDING, SENDING, SENT, FAILED, RETRYING, DEAD_LETTERED`.
**Security:** same `permitAll()` + `HeaderRoleAuthenticationFilter` pattern.
**Scheduling:** none — purely event-driven / on-demand.
**Depends on:** `paymentx-common-library`.

### paymentx-reconciliation-service (port 8087, db `paymentx_reconciliation`)
**Purpose:** Matches internal transactions against externally uploaded settlement files, detects mismatches, tracks settlement outcomes.
**Tables:** `settlement_file`, `reconciliation_batch`, `reconciliation_record`, `mismatch_record`, `reconciliation_summary`.
**REST** (base `/api/v1/reconciliation`): `POST /settlement-files` (multipart, `RECONCILIATION_ADMIN`), `POST /batches` (admin), `GET /batches/{id}` (open), `GET /batches/{id}/summary` (open — `totalRecords, matchedCount, missingCount, duplicateCount, amountMismatchCount, currencyMismatchCount, statusMismatchCount, settlementDelayCount, lateSettlementCount, orphanCount, unexpectedSettlementCount, generatedAt`), `POST /batches/{id}/reprocess` (admin), `GET /batches/{id}/report` (CSV, open), `GET /mismatches` (search, open), `POST /mismatches/{id}/resolve` (admin).
**Mismatch catalog** (`ReconciliationStatus` enum): `MATCHED, MISSING, DUPLICATE, AMOUNT_MISMATCH, CURRENCY_MISMATCH, STATUS_MISMATCH, SETTLEMENT_DELAY, LATE_SETTLEMENT, ORPHAN, UNEXPECTED_SETTLEMENT` — a genuinely useful RAG knowledge-source candidate (see §9).
**Kafka:** consumes terminal payment events + `audit.event-recorded`; produces `reconciliation.batch-completed`, `reconciliation.mismatch-detected`, `reconciliation.settlement-completed`.
**Scheduled:** `ReconciliationScheduler` runs nightly, cron externalized via `${reconciliation.batch.scheduled-cron}` (no API exists to query the *next* scheduled run time — only past executions).
**Security:** same pattern as routing/audit.
**Depends on:** `paymentx-common-library`.

### paymentx-reporting-service (port 8088, db `paymentx_reporting`)
**Purpose:** Aggregates events from every other service into 11 report types, exports CSV/XLSX/PDF/JSON.
**Tables:** `source_event`, `report` (seeded catalog), `report_request`, `report_execution`, `report_result`, `report_metadata`, `report_schedule`, `report_export`.
**Report catalog:** `PAYMENT_SUMMARY, SETTLEMENT_SUMMARY, PARTICIPANT_SUMMARY, TRANSACTION_VOLUME, FAILED_TRANSACTIONS, SUCCESS_RATE, NOTIFICATION_SUMMARY, AUDIT_SUMMARY, ROUTING_SUMMARY, VALIDATION_SUMMARY, RECONCILIATION_SUMMARY`.
**REST** (base `/api/v1/reports`): `GET /` (types, open), `POST /generate` (`REPORTING_ADMIN`), `GET /executions/{id}` (open), `GET /executions/{id}/result` (open), `GET /executions` (search, open), `GET /executions/{id}/download?format=` (open), `POST /schedules` (admin), `POST /executions/{id}/cancel` (admin), `DELETE /executions/{id}` (admin).
**Kafka:** consumes near-union of all platform topics; produces `reporting.report-generated`, `reporting.report-failed`, `reporting.report-completed`.
**Scheduled:** `ReportScheduler` (execution-processing + retry pollers) plus `ReportSchedule`-entity-driven recurring generation.
**Security:** same pattern.
**Depends on:** `paymentx-common-library`.

---

## 3. Existing Control Center

`paymentx-control-center` is a genuinely separate observability/operations product, not a page bolted onto a business service — and it says so explicitly in its own `docs/ARCHITECTURE.md`: *"a bug in this dashboard can never break a payment; a change to `paymentx-common-library` can never break this dashboard."*

### Frontend (`paymentx-control-center/frontend`)
React **19**, TypeScript **5.7**, Vite **6**, MUI **6.1** (+Emotion), Axios **1.7**, TanStack Query **5.62**, Recharts **2.13**, react-hook-form + zod, Redux Toolkit **2.3** (used for exactly one thing — theme mode + sidebar-collapsed client-only UI state; every other piece of state is server state via React Query, by explicit design choice documented in `ARCHITECTURE.md §10`).

**Routing:** `src/utils/routes.ts` is the single source of truth for path/label/icon/nav-group; `src/app/router.tsx` builds `createBrowserRouter` from it with a runtime guard that fails loudly if a route and its page component ever drift; `layouts/Sidebar.tsx` renders navigation from the same array. **19 routes today**, grouped: Overview (Dashboard, Services), Payments (Payments, Payment Flow, Reconciliation, Reporting), Infrastructure (Kafka, RabbitMQ, Redis, Database), Operations (Audit, Notifications, Logs, Traces, Metrics, Files), Tools (API Tester, E2E, Settings).

**API client:** single `axiosClient` (`src/api/axiosClient.ts`), `baseURL` from `VITE_API_BASE_URL` (default `http://localhost:8089` — **the frontend only ever talks to its own backend, never directly to the API Gateway or any business service**), 15s timeout, request interceptor attaches `Authorization: Bearer <dashboardToken>` when present. Every `services/*.ts` file is a thin wrapper over this one client — this is the exact seam a future `aiService.ts` plugs into.

**Auth:** no per-user login. `dashboardAuth.ts` stores a single shared "dashboard token" in `sessionStorage` (never `localStorage`), entered via `AuthGate.tsx`, enforced only when the backend flag is on (default off).

**AI-related code today:** **NOT FOUND** — grep for `ai|chat|assistant|llm|openai|anthropic|embedding|rag` across `frontend/src` returns nothing. No placeholder nav item exists.

### Backend (`paymentx-control-center/backend`, port 8089)
Deliberately **zero compile dependency on any business service module or `paymentx-common-library`** (confirmed: root `pom.xml` comment calls it "Phase 1: independent dashboard skeleton... zero compile dependency on any business service module," and its own `docs/ARCHITECTURE.md §2` explains why). It is a read-only aggregation/monitoring backend with its own direct integrations, not a thin proxy:

- **Postgres:** direct JDBC (`PostgresDataSourceConfig`, 7 read-only `DataSource` beans) across all 8 per-service databases, hardcoded parameterized SQL only.
- **Kafka:** native `AdminClient` (not via any business service).
- **Redis:** native Lettuce client, read-only, never returns a key's value.
- **RabbitMQ / Prometheus / Zipkin / MailHog:** each via its own real management/HTTP API client.
- **The 9 business services:** only their Actuator health/liveness/readiness/info endpoints, via `ServiceHealthClient`.

**REST surface** (all under `/api/v1/...`, ~50 endpoints, one controller per domain — full catalog in `paymentx-control-center/docs/API.md`). The single most Phase-3-relevant fact: `PostgresController` already exposes **`GET /api/v1/postgres/payments/by-reference/{reference}`** (+ `/flow` for the full 9-stage view), **`/audit-events`**, **`/reconciliation-records`**, **`/reconciliation-batches`**, **`/report-executions`**, **`/routing-rules`**, and **`/participants`** as read JSON — i.e., read views over exactly the domains Phase 3's MCP tools want, including two (`participants`, and effectively validation-adjacent data) that **no business service itself exposes as a REST API today**. See §10 for why this is a decision point, not a free win.

**Security:** no `spring-boot-starter-security` dependency at all. `DashboardAuthFilter` is a documented, deliberately lightweight single-shared-bearer-token gate (`control-center.security.enabled`, default **false**; constant-time comparison; fail-closed at startup if enabled without a token configured). Its own `SecurityConfig.java` states outright: *"Auth Service has zero endpoints today (confirmed live again during Phase 5/6 work)"* — independently corroborating §1's finding #1.

**Observability:** `micrometer-registry-prometheus` + Actuator; `CorrelationIdFilter` present. **Not currently in `infra/prometheus.yml`'s scrape list** (a pre-existing gap, unrelated to Phase 3).

**Real-time:** **NOT FOUND** — no WebSocket/SSE/STOMP anywhere. Every page is request/response polling via React Query (15–30s intervals). A streaming chat UI is genuinely new infrastructure, not reuse.

### Verdict: can the AI Chat Interface reuse this Control Center?
**Yes, for the UI.** Adding an "AI Assistant" page is a two-file, low-risk change (`routes.ts` + `router.tsx`) plus new additive files (`pages/AiAssistantPage.tsx`, `services/aiService.ts`, `hooks/useAiChat.ts`) following every existing convention exactly — no new React app is justified or needed.

**Not necessarily for the backend business logic.** Control Center's backend has a stated, deliberate architectural boundary: it is a human-operator observability surface with zero dependency on the business platform, not a business API. Bolting agent/LLM/RAG orchestration logic into it would violate the exact isolation property it was built for. The recommended integration boundary (detailed in §5) is: **frontend page → Control Center backend (thin new proxy controller, following its existing `ServiceHealthClient`-style "one outbound client per real integration, server-configured target only" pattern) → API Gateway (new routes) → dedicated new AI Platform services.** This satisfies "reuse the Control Center" for the UI while keeping Control Center's own stated boundary intact.

---

## 4. AI Platform Architecture (Target)

```
                            PAYMENTX (CURRENT)
                                    │
                ┌───────────────────┴────────────────────┐
                │                                          │
          Core Services                              AI Platform (FUTURE — Phase 3)
   (gateway, auth*, validation,                              │
    payment, routing, audit,                    Control Center "AI Assistant" page
    notification, reconciliation,                    (frontend, reused)
    reporting)                                              │
                │                              Control Center backend — thin AI proxy
                │                                    (new controller, existing pattern)
                │                                            │
                │                              API Gateway — new /api/v1/ai/** routes
                │                                            │
                │                                 AI Chat Interface (new service)
                │                                            │
                │                                 Agent Orchestrator (new service)
                │                                            │
                │                    ┌───────────────────────┼───────────────────────┐
                │                    │                        │                        │
                │              Prompt Service              RAG Service            MCP Gateway
                │              (new service)              (new service)          (new service)
                │                    │                        │                        │
                │               LLM Service              Vector DB              existing business
                │              (new service)          (pgvector, new                 REST APIs
                │                    │                  `paymentx_ai` db)        (via new gateway
                │              LLM Provider                    │                     routes)
                │              (external, config-               Embedding Service
                │               selected)                      (new service)
                │
                └── (*auth-service is currently unimplemented — see §1)
```

**Validated against the repository, with two deliberate deviations from the prompt's suggested shape, both evidence-driven:**

1. **MCP Gateway does not call PaymentX APIs directly today for 5 of 8 services**, because those services aren't behind the API Gateway yet (§1, finding #2). New gateway routes are a Phase 3.7 prerequisite, not an assumption.
2. **The AI Chat Interface's UI lives inside the existing Control Center frontend**, per the prompt's own instruction to reuse it — but the *backend* AI logic lives in new dedicated services reached through the gateway, not inside `control-center/backend`, because that module's own architecture doc states its isolation from business logic as a deliberate, load-bearing property (§3). Collapsing AI orchestration into it would be the one thing that document says not to do.

---

## 5. Service Boundaries

The **boundary rule**, restated from the task's own instruction and reinforced by everything found in §1–§3: **AI services must never talk to Postgres, Redis, Kafka, or RabbitMQ directly.** All payment-domain data must be reached through the same kind of authorized REST APIs every other caller uses — through the API Gateway. The one narrow exception: **infrastructure/observability reads** (Zipkin trace lookup, Prometheus metrics) carry no participant-sensitive business authorization model, so an MCP tool may call those directly (or via Control Center's existing read-only proxy for them) without violating the spirit of the rule — this exception is called out explicitly in §10.

For each Phase 3 component:

### AI Chat Interface (new service)
- **Purpose:** Owns conversation/session lifecycle for the AI Assistant; receives user messages from the Control Center frontend, streams responses back, persists conversation history.
- **REST:** `POST /api/v1/ai/chat`, `GET /api/v1/ai/conversations/{id}`, `GET /api/v1/ai/conversations` (see §14 for full contract).
- **Kafka:** none required initially; optionally emits an `ai.conversation-completed` event later for audit-service to consume (future, not Phase 3 baseline).
- **Database:** `ai_conversations`, `ai_messages` (§9).
- **Dependencies:** calls Agent Orchestrator (for tool-using requests) or directly Prompt Service + LLM Service (for simple non-agentic chat).
- **Security:** reached only via API Gateway; inherits whatever real identity model exists at that time (today: participant/role headers set by the gateway — same trust model every other service already has, not weaker).
- **Observability:** same Actuator/Micrometer/Zipkin pattern as every other service, plus AI-specific metrics (§13).
- **Failure handling:** LLM/tool failures must degrade to an honest error state in the chat UI — never a fabricated answer (mirrors Control Center's own stated "no fabricated data anywhere" principle, §3).
- **Scaling:** stateless request handling; conversation state in Postgres, not in-memory — horizontally scalable like any other service here.
- **Docker:** new service block, host-process dev workflow matching every existing service (not container-first).

### Prompt Service (new service)
- **Purpose:** Central registry and versioning for system/tool/RAG prompt templates — the one place prompt text changes without a code deploy.
- **REST:** `POST /api/v1/ai/prompts`, `GET /api/v1/ai/prompts/{key}`, `GET /api/v1/ai/prompts/{key}/versions`.
- **Database:** `prompt_templates`, `prompt_versions` (§9).
- **Dependencies:** none (leaf service); consumed by AI Chat Interface, RAG Service, Agent Orchestrator.
- **Security/Observability/Docker:** same platform-standard pattern.

### LLM Service (new service)
- **Purpose:** The one place that talks to an actual LLM provider — implements the provider abstraction (§8).
- **REST:** internal-only (`POST /api/v1/ai/llm/complete`), not exposed through the gateway to the browser.
- **Database:** none (stateless) — token-usage/latency metrics go to Prometheus, not a table (unless a `ai_audit_events` row is written for compliance — see §9).
- **Dependencies:** outbound HTTPS to the configured external LLM provider only.
- **Security:** provider API key from environment variable (matching the platform's existing `${ENV_VAR}` convention — see §1 finding #6), never logged, never returned in any response.
- **Failure handling:** timeout + retry + circuit breaker (Resilience4j — already a proven pattern in payment-service's call to routing-service) + explicit fallback behavior (§8).

### Embedding Service (new service)
- **Purpose:** Converts text (documents at ingest time, queries at search time) into vectors.
- **REST:** internal-only (`POST /api/v1/ai/embeddings`).
- **Database:** writes into `embedding_chunks` (owned conceptually by RAG Service's ingestion pipeline, but this service is the one that computes the vector — see §9 for the exact ownership split).
- **Dependencies:** external embedding-model provider (may be the same or different provider than chat completion).

### Vector Database — **not a new service, an extension of existing Postgres** (see §8)
`pgvector` extension inside a new `paymentx_ai` database on the same Postgres 16 instance already running in `infra/docker-compose.yml`.

### RAG Service (new service)
- **Purpose:** Owns the ingestion pipeline (parse → clean → chunk → embed → store) and the retrieval pipeline (embed query → vector search → metadata filter → top-K → context build) — see §11.
- **REST:** `POST /api/v1/ai/rag/ingest`, `POST /api/v1/ai/rag/search`.
- **Database:** `rag_sources`, `embedding_documents` (§9); calls Embedding Service for vectors, queries `embedding_chunks` via pgvector.
- **Dependencies:** Embedding Service, Vector DB.

### MCP Gateway (new service)
- **Purpose:** The controlled boundary between the Agent Orchestrator and PaymentX's real business APIs — a fixed, read-only tool catalog (§10), mirroring Control Center's own `ApiTesterAllowlist` pattern (a fixed `{service, method, pathTemplate}` catalog, never a client-supplied URL — a proven, already-in-repo design for exactly this problem).
- **REST:** internal-only, tool-invocation surface consumed by Agent Orchestrator.
- **Dependencies:** API Gateway (new `/api/v1/ai/**`-adjacent routes to audit-service, routing-service, reconciliation-service, reporting-service, payment-service — a prerequisite, see §1 finding #2) + Zipkin (direct, for `getTrace`).
- **Security:** every tool call is logged as an `ai_audit_events` row (§9); tool authorization is checked against a fixed allowlist, never dynamic.

### Agent Orchestrator (new service)
- **Purpose:** Runs the tool-calling loop for the "Payment Operations Investigation Agent" (§12) — Prompt Service → LLM Service → MCP Gateway tools → RAG Service → LLM Service → answer.
- **REST:** internal-only, called by AI Chat Interface.
- **Database:** `agent_runs`, `agent_tool_calls` (§9).
- **Failure handling:** hard max-iteration cap, hard timeout, every tool call individually authorized and audited (§12).

---

## 6. Database Design

**Reuse vs. new database — decision: one new database, `paymentx_ai`, added to the existing Postgres 16 instance.**

Rationale: the platform's established convention is strictly one database per *independently owned business bounded context* (`paymentx_auth`, `paymentx_validation`, etc.). The eight Phase 3 components, unlike the nine business services, are not independent bounded contexts with separate release/ownership lifecycles — they are one cohesive new platform vertical ("the AI platform"), and pgvector setup should not be duplicated across N tiny databases. A single `paymentx_ai` database, with each AI service owning its own tables inside it via its own Liquibase changelog subtree (mirroring how `paymentx-control-center/backend` cleanly owns zero schema while still following the "one changelog per owner" idea), is the pragmatic middle ground. **This is a deliberate deviation from the strict one-DB-per-service pattern — flagged as an open decision for whoever owns platform conventions to confirm before Phase 3.1 (§19 Open Items).**

Per the task's explicit instruction to determine which tables are *actually* required rather than creating every "potential area" listed, the following trims were made:

- **`ai_requests` / `ai_responses` — dropped.** These would duplicate what `ai_messages` (the conversational content), `agent_tool_calls` (tool-level request/response), and `ai_audit_events` (the cross-cutting compliance log) already capture at the correct grain. Adding a third layer on top adds no new information.
- **`rag_sources` kept separate from `embedding_documents`** — they answer different questions: `rag_sources` is the curated, admin-controlled registry of *where content is allowed to come from* (governance); `embedding_documents` is *what was actually ingested* on a given run, each row referencing the `rag_sources` row it came from.

| Table | Purpose | Key columns | PK | Indexes | Relationships | Retention |
|---|---|---|---|---|---|---|
| `prompt_templates` | Named, versioned prompt registry | `template_key` (unique), `name`, `category`, `active` | `id` | unique on `template_key` | 1:N → `prompt_versions` | Indefinite (config, not user data) |
| `prompt_versions` | Immutable version history per template | `prompt_template_id`, `version_number`, `content`, `variables (jsonb)`, `is_current` | `id` | unique `(prompt_template_id, version_number)` | N:1 → `prompt_templates` | Indefinite |
| `ai_conversations` | One row per chat session | `conversation_ref (uuid)`, `participant_id`, `status`, `correlation_id`, timestamps | `id` | on `conversation_ref`, `correlation_id` | 1:N → `ai_messages` | **Open — see below** |
| `ai_messages` | Turn-by-turn chat content | `conversation_id`, `role`, `content`, `token_count_prompt/completion`, `latency_ms`, `model` | `id` | on `(conversation_id, created_at)` | N:1 → `ai_conversations` | **Open — see below** |
| `embedding_documents` | One row per ingested source artifact | `rag_source_id`, `source_uri`, `title`, `checksum`, `version`, `ingested_at` | `id` | unique `(source_uri, checksum)` | N:1 → `rag_sources`; 1:N → `embedding_chunks` | Indefinite (re-ingest supersedes by checksum) |
| `embedding_chunks` | Vector-searchable chunks | `document_id`, `chunk_index`, `content`, `embedding (vector(N))`, `metadata (jsonb)` | `id` | HNSW/IVFFlat on `embedding`; btree on `document_id` | N:1 → `embedding_documents` | Tied to parent document |
| `rag_sources` | Governance registry of allowed ingestion sources | `name`, `source_type`, `location`, `enabled`, `refresh_cron` | `id` | none beyond PK | 1:N → `embedding_documents` | Indefinite (config) |
| `agent_runs` | One row per agent invocation | `conversation_id`, `agent_type`, `status`, `iteration_count`, `max_iterations`, `correlation_id`, `trace_id` | `id` | on `conversation_id`, `correlation_id` | N:1 → `ai_conversations`; 1:N → `agent_tool_calls` | Matches `ai_conversations` |
| `agent_tool_calls` | One row per MCP tool invocation within a run | `agent_run_id`, `tool_name`, `input (jsonb)`, `output (jsonb)`, `status`, `latency_ms`, `risk_level` | `id` | on `agent_run_id` | N:1 → `agent_runs` | Matches `ai_conversations` |
| `ai_audit_events` | Security/compliance-grade event log — the AI-platform analog of `audit_event` in `paymentx-audit-service` | `event_type`, `conversation_id`, `agent_run_id`, `participant_id`, `correlation_id`, `trace_id`, `detail (jsonb)` | `id` | on `correlation_id`, `created_at` | Loosely N:1 to both conversations and agent runs | **Longer than chat content — audit trails typically outlive the content they describe; exact number needs compliance input, not invented here** |

**Retention — explicitly an open item, not decided here:** `ai_messages`/`ai_conversations` may contain sensitive payment context discussed in chat. No retention policy for `audit_event` (the closest existing analog, in `paymentx-audit-service`) was found documented anywhere in the repo either, so there is no existing convention to inherit. This needs an explicit decision before Phase 3.1 ships to production, not a guessed number.

---

## 7. Vector DB Design

**Decision: PostgreSQL + pgvector, not a separate vector database.** This matches the task's own stated default preference and holds up against the evidence:

- **Why:** The platform already runs Postgres 16 with a proven per-service-database operational pattern (backup, `pgAdmin` access, Liquibase-driven schema, the exact JDBC access pattern Control Center's `PostgresDataSourceConfig` already demonstrates). Introducing a dedicated vector database (Pinecone/Weaviate/Qdrant/Milvus) would add a new infrastructure dependency, a new backup/DR story, a new credential to manage, and a new client library — for a learning/demo-scale platform with no evidence anywhere in the repo of vector-search scale requirements that would justify it.
- **Expected scale:** Initial RAG corpus is PaymentX's own documentation (§11) — error codes, runbooks, API docs — realistically low thousands of chunks, not the tens-of-millions-of-vectors range where pgvector's HNSW index starts to lose to purpose-built vector engines.
- **Indexing:** HNSW (Postgres ≥ 16 / pgvector ≥ 0.5.0 supports it) for the `embedding_chunks.embedding` column — better recall/latency tradeoff than IVFFlat at this scale, and doesn't require a pre-training pass on an empty table the way IVFFlat's clustering does.
- **Metadata filtering:** plain SQL `WHERE` on `embedding_chunks.metadata` (jsonb) or joined columns from `embedding_documents`/`rag_sources`, combined with the vector distance operator in one query — no separate metadata-filter round trip needed, which is exactly the kind of complexity a dedicated vector DB would otherwise add back.
- **Backup:** identical to every other PaymentX database today — the same `pg_dump`/volume-backup story `infra/docker-compose.yml`'s `paymentx-postgres-data` named volume already implies. No new backup tooling.
- **Migration:** if RAG corpus size or query volume ever genuinely outgrows pgvector, the `embedding_chunks` table's shape (id, document reference, content, vector, metadata) is already close to what any dedicated vector DB's ingestion format expects — migrating out later is not a rewrite.
- **Operational complexity:** the *only* new operational item is that the `postgres` image in `infra/docker-compose.yml` needs to become a pgvector-enabled image (§15) — everything else (connection pooling, credentials, monitoring via Control Center's existing `PostgresController`) is already solved.

---

## 8. LLM Provider Abstraction

```
AI Chat Interface / Agent Orchestrator
                │
          LLM Service
                │
     LlmProvider (interface)
                │
      ┌─────────┴─────────┐
      │                   │
ProviderA (e.g.       ProviderB
Anthropic)             (e.g. OpenAI,
                        or a local model)
```

The application layer (Chat Interface, Agent Orchestrator, RAG Service) depends only on `LlmProvider`'s interface (`complete(ChatRequest): ChatResponse`, with structured-output and streaming variants), never a provider SDK type — the same discipline the platform already applies elsewhere (e.g. `SmsProvider` in notification-service is exactly this pattern: a pluggable interface with a mock dev implementation and a real provider selected by config, `${SMS_PROVIDER}`).

- **Model configuration:** `ai.llm.provider`, `ai.llm.model`, `ai.llm.temperature`, `ai.llm.max-tokens` — externalized properties (§15), never hardcoded.
- **API key handling:** `${LLM_API_KEY}`-style env var per provider, following the platform's existing `${ENV_VAR}`-in-`application-prod.yml`-with-no-default convention (§1 finding #6) — there is no AWS Secrets Manager integration anywhere in this platform to "reuse," so this is the actual existing pattern to match, not an invented one.
- **Timeout/retry/circuit breaker:** Resilience4j — already a proven, in-repo pattern (payment-service's routing-service call).
- **Rate limiting:** token-bucket or fixed-window at the LLM Service layer, mirroring the gateway's existing `RedisRateLimiter` pattern.
- **Token usage:** captured per-call, written into `ai_messages.token_count_prompt/completion` and exported as a Prometheus counter (§13).
- **Structured output:** provider-native structured/JSON-mode output where available, with a schema-validation fallback layer in LLM Service so the application layer gets a typed, validated object either way.
- **Error handling / fallback strategy:** on provider timeout or 5xx, retry with backoff up to a bounded limit, then fail the request honestly (matching Control Center's "no fabricated data" principle, §3) — no silent fallback to a second provider that could return a materially different answer without the caller knowing the provider changed.
- **Observability:** every call emits latency, token counts, and success/failure to Prometheus (§13), and — for anything agent/tool-related — an `ai_audit_events` row (§9).
- **No secrets in source:** enforced the same way the rest of the platform enforces it — `${ENV_VAR}` placeholders only, verified absent from `application.yml` defaults for anything beyond local-dev-only values (mirroring the gateway's own `local-dev-only-secret-change-me` pattern, which is an acceptable *local* default precisely because it's obviously not usable in prod).

---

## 9. RAG Architecture

**Ingestion:**
```
Document → Parser → Cleaner → Chunker → Metadata tagging → Embedding Service → embedding_chunks (pgvector)
```

**Query:**
```
User Question → Query Embedding → Vector Search (pgvector) → Metadata Filtering → Top-K → Context Builder → LLM → Grounded Answer
```

**Initial knowledge sources — evaluated against what actually exists in this repository today:**

| Candidate source | Status |
|---|---|
| Payment error codes | **Gap, not a ready source.** No centralized error-code catalog exists (§2, payment-service: failure reason is free text in `payment_status_history.reason`). Closest existing structured catalog is validation-service's `business_rule.rule_code` and reconciliation-service's 10-value `ReconciliationStatus` enum — both real and usable, but a proper payment error-code catalog would need to be *authored* as part of Phase 3.2/3.6 work, not extracted from existing data. |
| PaymentX runbooks | **Available now.** `paymentx-control-center/docs/RUNBOOK.md` exists today and is exactly the kind of operational document RAG should ingest. |
| API documentation | **Available now.** `paymentx-control-center/docs/API.md`, `docs/ARCHITECTURE.md`; individual service READMEs where present. |
| ADRs | **Available now.** `docs/adr/0001`–`0004` are high-value grounding for "why does the platform work this way" questions an operator might ask the AI Assistant. |
| Reconciliation mismatch taxonomy | **Available now.** The 10-value `ReconciliationStatus` enum (§2) is small but well-defined domain knowledge. |
| ISO 20022 / scheme documentation | **Not present in this repository at all** — these would be externally sourced. Per the task's explicit instruction, do not auto-upload external copyrighted documents; this is a content-sourcing decision for a human, not something to script.
| Kafka topic-naming inconsistency (ADR 0002) | **Available now**, and specifically valuable: an agent reasoning about "why does this event have two different topic-name shapes" should be grounded in this ADR rather than guessing. |

---

## 10. MCP Architecture

Design mirrors a pattern already proven in this exact repository: Control Center's `ApiTesterAllowlist` — a fixed `{service, method, pathTemplate}` catalog, never a client-supplied URL. The MCP Gateway's tool catalog is the same idea at the AI layer.

**Start read-only, per the task's explicit instruction — no `retryPayment`/`cancelPayment`/`refundPayment` in this phase**, even though `payment-service` already exposes `POST /{ref}/retry` and `POST /{ref}/cancel` today (§2) — those remain human/operator-only actions for now.

| Tool | Underlying PaymentX API | Status | Risk | Audit |
|---|---|---|---|---|
| `getPayment` | `GET /api/v1/payments/{reference}` (payment-service) | **AVAILABLE** (via new gateway route) | Low (read) | Log every call |
| `searchPayments` | `GET /api/v1/payments/search` (payment-service) | **AVAILABLE** (via new gateway route) | Low | Log |
| `getValidationResult` | — | **NOT CURRENTLY AVAILABLE** — validation-service has no GET/lookup endpoint at all (§2) | N/A | N/A |
| `getAuditTrail` | `GET /api/v1/audit-events` (audit-service) | **AVAILABLE** (via new gateway route) | Low | Log |
| `getTrace` | Zipkin v2 API directly (or Control Center's existing `/api/v1/zipkin/traces/{traceId}` proxy) | **AVAILABLE** | Low — infra/observability data, not participant-sensitive business state, so a direct call here does not violate the "go through business APIs" rule | Log |
| `getRoutingInformation` | `GET /api/v1/routes/participant/{id}` or `/default` (routing-service) | **AVAILABLE** (via new gateway route) | Low | Log |
| `getReconciliationStatus` | `GET /api/v1/reconciliation/batches/{id}` + `/summary` (reconciliation-service) | **AVAILABLE** (via new gateway route) | Low | Log |
| `getReportStatus` | `GET /api/v1/reports/executions/{id}` (reporting-service) | **AVAILABLE** (via new gateway route) | Low | Log |
| `getParticipant` | — | **NOT CURRENTLY AVAILABLE** — no business service exposes a participant-lookup REST API; the table exists (`validation-service`'s `participant`) but is only reachable via Control Center's incidental DB-read endpoint, which is not an authorized business API and is deliberately **not** recommended as the permanent backing for this tool (see decision below) | N/A | N/A |

**Decision on the `getValidationResult` / `getParticipant` gap:** Control Center's backend already reads both of these tables directly and could technically back these two tools today. **This is not recommended as the Phase 3 design**, for the same reason given in §3: Control Center's direct-DB-read pattern exists for a human-operator dashboard with its own stated isolation guarantee, and repurposing it as the machine-facing API for an autonomous agent would quietly expand its scope beyond that without an explicit decision by whoever owns that module. The correct fix is adding a proper `GET /api/v1/validations/{reference}` to validation-service and a participant-lookup endpoint to whichever service should own it — tracked as a **prerequisite backlog item for Phase 3.7**, not solved by borrowing Control Center's dashboard API. Both tools are marked **NOT CURRENTLY AVAILABLE** and will not be implemented in this phase, per instruction.

**Prerequisite also flagged for all five gateway-reachable tools above:** the API Gateway currently only routes to validation-service and payment-service (§1, finding #2). `getAuditTrail`, `getRoutingInformation`, `getReconciliationStatus`, and `getReportStatus` all require **new gateway routes** to be added before the MCP Gateway can reach them safely — this is real, scoped work that belongs to Phase 3.7, not something already in place.

---

## 11. Agent Architecture

Initial target: **Payment Operations Investigation Agent** — "Investigate payment PMT-123 and explain why it failed."

```
User → AI Chat → Agent Orchestrator → Payment Tool → Validation Tool* → Audit Tool → Trace Tool → RAG → LLM → RCA → User
```
*Validation Tool is `NOT CURRENTLY AVAILABLE` (§10) — the initial agent's investigation depth is bounded by that gap until the prerequisite endpoint exists.

- **Agent state:** persisted as one `agent_runs` row per invocation, one `agent_tool_calls` row per tool call (§9) — not in-memory-only, matching every other stateful thing in this platform being Postgres-backed rather than ephemeral.
- **Tool registry:** the MCP Gateway's fixed catalog (§10) — the orchestrator cannot invoke anything outside it.
- **Tool authorization:** every tool call checked against the fixed allowlist before execution; no tool is ever selected by unconstrained LLM output alone (mirrors `ApiTesterAllowlist`'s "even a matching request must exactly match an allowlist entry" behavior).
- **Maximum iterations:** hard cap (`ai.agent.max-iterations`, config, §15) — the agent must terminate with a partial answer rather than loop indefinitely.
- **Timeout:** hard wall-clock cap per run (mirrors Control Center's own E2E flow, which already has exactly this pattern: `control-center.e2e.max-duration-seconds`, default 60s, reported as `TIMEOUT` rather than silently hanging).
- **Failure handling:** any tool failure is surfaced to the LLM as a real error (never silently swallowed), and if the run as a whole fails/times out, the user gets an honest "investigation incomplete" state — never a fabricated RCA.
- **Conversation context:** `agent_runs.conversation_id` links back to `ai_conversations` (§9) so a follow-up question can reuse investigation context.
- **Correlation ID / Trace ID:** propagated through every tool call exactly the way `X-Correlation-Id`/`X-Trace-Id` already propagate through the rest of the platform (§2) — an agent's tool calls should be traceable end-to-end in Zipkin/logs the same way a real payment request is today.
- **Audit:** every tool call and every LLM call in a run writes an `ai_audit_events` row (§9) — this is the AI-platform's answer to what `audit-service` already does for business events.

---

## 12. Observability

**Reused as-is (no changes needed):** Prometheus (`infra/prometheus.yml`, `host.docker.internal:<port>` scrape pattern — new AI services add themselves as new scrape jobs the same way every existing service did), Grafana (Prometheus datasource already provisioned, no new datasource needed), Zipkin (100% sampling convention already used everywhere), the `X-Correlation-Id`/`X-Trace-Id` header propagation convention from `paymentx-common-library`.

**New AI-specific metrics to expose via Micrometer/`/actuator/prometheus`** (mirrors the counter/timer patterns already used for e.g. routing-service's `metrics/` package):

- `ai_requests_total` (by endpoint, status)
- `ai_llm_latency_seconds` (histogram, by provider/model)
- `ai_llm_failures_total` (by provider, error type)
- `ai_llm_tokens_total` (by provider/model, prompt vs. completion)
- `ai_rag_retrieval_latency_seconds`
- `ai_vector_search_latency_seconds`
- `ai_embedding_latency_seconds`
- `ai_agent_execution_seconds` (histogram, by agent type, outcome)
- `ai_agent_tool_calls_total` (by tool name, status)
- `ai_agent_tool_failures_total`
- `ai_agent_iteration_count` (histogram)
- `ai_errors_total` (by component, error type)

**Control Center surfacing (future, not built in Phase 3.0):** once these metrics exist, a new "AI Monitoring" page follows the exact same pattern as the existing Metrics page (`PrometheusClient` fixed named-query catalog → `/api/v1/prometheus/metrics/{slug}` → React Query hook → Recharts panel) — no new architectural pattern needed, just new named queries and a new page, consistent with §3's verdict.

---

## 13. AI Security

| Threat | Mitigation |
|---|---|
| Prompt injection (direct) | System prompt isolation via Prompt Service versioning; user input never concatenated into a privileged instruction context without a clear delimiter; LLM output treated as untrusted (see below) |
| Indirect prompt injection (via RAG content) | `rag_sources` is a curated, admin-controlled allowlist (§9) — RAG only retrieves from vetted sources, never arbitrary user-supplied URLs/documents at query time |
| RAG poisoning | Ingestion only from `rag_sources`-registered locations; `embedding_documents.checksum` enables detecting unexpected content changes on re-ingest |
| PII / sensitive payment data | `paymentx-common-library` already has `@Mask`/`MaskingSerializer`/`DataMaskingUtils` (§2) — reuse these for any payment data surfaced through chat, rather than inventing new masking logic |
| Secrets | Never logged, never returned in a response — same standard Control Center's backend already documents and enforces for its own clients (§3) |
| Tool authorization | Fixed MCP allowlist only (§10) — no dynamically-constructed tool call ever reaches a real API |
| Output validation | Structured-output schema validation at the LLM Service layer (§8) before anything downstream consumes it |
| Agent authorization | Same identity/role model as every other authenticated call through the gateway — no separate, weaker AI-specific auth path |
| Rate limiting | Reuse the gateway's existing `RedisRateLimiter` pattern (§2) for `/api/v1/ai/**` routes |
| Audit | `ai_audit_events` (§9) for every tool call and LLM call |

**The load-bearing principle, restated because it matters most here: LLM output is not a trusted business decision.** Every one of the existing business services already enforces this pattern architecturally — `payment-service`'s controller comment is explicit that operational endpoints are not money-movement entry points; validation/authorization decisions are made by business services with real rules (`business_rule.rule_code`, `@PreAuthorize` role checks), never by a probabilistic model. The AI platform recommends; PaymentX's existing services decide and enforce, exactly as before Phase 3.

**A note carried forward from §1 that materially affects this section:** because no downstream business service currently re-validates a JWT independently (§1, finding #3), and because `X-Roles`/`X-Participant-Id` are trusted unsigned headers, **the MCP Gateway must not be able to mint or influence those headers**. It must call through the API Gateway like any other caller and receive whatever identity the gateway assigns — it must never be given a shortcut that lets it assert its own role/participant identity, which would make it strictly more dangerous than the pre-existing gap, not just exposed to it.

---

## 14. Configuration

Following the platform's existing convention exactly: `application.yml` (local-dev defaults, safe to commit) + `application-prod.yml` (`${ENV_VAR}` only, no defaults) + `application-docker.yml` where relevant.

```yaml
ai:
  enabled: false                      # AI_ENABLED
  llm:
    provider: anthropic               # AI_LLM_PROVIDER — config-selected, see §8
    model: claude-sonnet-5            # AI_LLM_MODEL
    temperature: 0.2                  # AI_LLM_TEMPERATURE
    max-tokens: 4096                  # AI_LLM_MAX_TOKENS
    timeout-seconds: 30                # AI_LLM_TIMEOUT_SECONDS
  embedding:
    provider: anthropic               # AI_EMBEDDING_PROVIDER
    model: <provider-specific>        # AI_EMBEDDING_MODEL
  vector-db:
    top-k: 5                          # AI_RAG_TOP_K
    similarity-threshold: 0.7         # AI_RAG_SIMILARITY_THRESHOLD
  agent:
    max-iterations: 8                 # AI_AGENT_MAX_ITERATIONS
    timeout-seconds: 60                # AI_AGENT_TIMEOUT_SECONDS — mirrors control-center.e2e.max-duration-seconds
  rate-limit:
    requests-per-minute: 30           # AI_RATE_LIMIT_RPM
```

No secret (API key, etc.) ever appears with a real value in `application.yml` — only `${ENV_VAR}` in `application-prod.yml`, matching the gateway's own `GATEWAY_JWT_SECRET` pattern exactly.

---

## 15. Docker / Local Development

**One infra-affecting change required:** `infra/docker-compose.yml`'s `postgres` service image must move from `postgres:16-alpine` to a pgvector-enabled equivalent (e.g. `pgvector/pgvector:pg16`) to support §7. This is the only change to *existing* infra — everything else is additive.

**Additive changes, only as each phase actually needs them (not all at once):**
- `POSTGRES_MULTIPLE_DATABASES` gains `paymentx_ai`.
- `CREATE EXTENSION IF NOT EXISTS vector;` belongs in the first Liquibase changeset of whichever AI service owns baseline schema for `paymentx_ai` — consistent with how every other service's Liquibase changelog owns its own schema, not in `infra/init-scripts` (which today only creates empty databases, per `infra/init-scripts/01-create-databases.sh`).
- New service blocks in `docker-compose.yml` per AI service, added incrementally as each is actually built (Phase 3.1 onward) — matching the existing pattern where `api-gateway`'s container block exists but is gated behind a `docker-build` profile not used by the documented default dev workflow (host-process `mvn spring-boot:run` for everything).
- `infra/prometheus.yml` gains one new `scrape_configs` entry per AI service, targeting `host.docker.internal:<port>`, once that service exists — same pattern as every entry already there.

**Not recommended:** adding all 7 AI service containers speculatively now, or adding any new infra container (message queue, cache, vector DB product) beyond the one Postgres image swap — nothing in the actual requirements justifies it yet.

---

## 16. Module Structure

**Recommendation: flat, at repository root, matching every existing business service exactly** — `paymentx-ai-chat-interface`, `paymentx-prompt-service`, `paymentx-llm-service`, `paymentx-embedding-service`, `paymentx-rag-service`, `paymentx-mcp-gateway`, `paymentx-agent-orchestrator`, each a sibling of `paymentx-payment-service` etc., each added to the root `pom.xml`'s `<modules>` list.

**Validated against the one existing counter-example** (`paymentx-control-center/backend` nested under `paymentx-control-center/`): that nesting exists specifically because frontend+backend are two halves of one product that isn't independently a Maven artifact (the frontend isn't Maven at all) — it is not evidence for a general "group related things in a parent folder" convention. The seven AI components are, like the nine business services, independently deployable services with their own lifecycle — flat placement keeps the repository's actual convention (11 of 12 current modules are flat) rather than introducing the only other nested grouping in the repo for a case that doesn't share the frontend/backend justification.

**Suggested port continuation** (next free block after the existing 8080–8089 range, avoiding the already-used 8090 = Kafka UI): `paymentx-ai-chat-interface` 8091, `paymentx-prompt-service` 8092, `paymentx-llm-service` 8093, `paymentx-embedding-service` 8094, `paymentx-rag-service` 8095, `paymentx-mcp-gateway` 8096, `paymentx-agent-orchestrator` 8097.

Each new module depends on `paymentx-common-library` (never `paymentx-common` — §1 finding #5 shows what happens when that migration is left incomplete; Phase 3 should not add a second module doing the same wrong thing) for `ApiResponse`/`PageResponse`/exception hierarchy/correlation-ID utilities/masking utilities, exactly as ADR 0004 already anticipates.

---

## 17. API Contracts (Design Only)

**Path convention correction from the task template:** the existing platform convention (every business service, and Control Center) is `/api/v1/{resource}`, not `/api/ai/...`. Phase 3 endpoints should follow the existing convention: **`/api/v1/ai/...`**.

| Method | Path | Notes |
|---|---|---|
| POST | `/api/v1/ai/chat` | Send a chat message; request `{conversationId?, message, stream?}`; response `{conversationId, messageId, content}` or an SSE stream |
| GET | `/api/v1/ai/conversations/{id}` | Fetch a conversation + its messages |
| GET | `/api/v1/ai/conversations` | List/search conversations (paginated, `PageResponse<T>` — matching every existing list endpoint's envelope) |
| POST | `/api/v1/ai/prompts` | Create/update a prompt template (admin-role-gated, matching e.g. `ROUTING_ADMIN`/`REPORTING_ADMIN` conventions) |
| GET | `/api/v1/ai/prompts/{key}` | Fetch current version of a prompt |
| POST | `/api/v1/ai/embeddings` | Internal-only; not exposed through the browser-facing gateway route |
| POST | `/api/v1/ai/rag/search` | Retrieve top-K chunks for a query |
| POST | `/api/v1/ai/rag/ingest` | Trigger ingestion from a registered `rag_sources` entry (admin-role-gated) |
| GET | `/api/v1/ai/health` | Standard health, exempt from any auth gate — matching Control Center's own `/api/v1/health` exemption pattern |
| GET | `/api/v1/ai/models` | List configured/available LLM models |

Every response uses `ApiResponse<T>`/`ErrorResponse`/`PageResponse<T>` from `paymentx-common-library` — not a new envelope shape, matching every existing business service (and deliberately unlike Control Center's independently-defined envelope, since these are business services, not an isolated dashboard).

---

## 18. Data Flow — Target End-to-End

```
User
 ↓  [CURRENTLY IMPLEMENTED] Control Center Frontend (React) — new "AI Assistant" page (PHASE 3 TO BUILD)
 ↓  [PHASE 3 TO BUILD] Control Center Backend — new thin AI proxy controller
 ↓  [PHASE 3 TO BUILD] API Gateway — new /api/v1/ai/** route (gateway itself, JWT/rate-limit filter chain: CURRENTLY IMPLEMENTED and reused as-is)
 ↓  [PHASE 3 TO BUILD] AI Chat API (paymentx-ai-chat-interface)
 ↓  [PHASE 3 TO BUILD] Agent Orchestrator
 ↓  [PHASE 3 TO BUILD] Prompt Service
 ↓  [PHASE 3 TO BUILD] RAG Service
 ↓  [PHASE 3 TO BUILD] Embedding Service
 ↓  [PHASE 3 TO BUILD] Vector DB (pgvector in new paymentx_ai database — Postgres instance itself: CURRENTLY IMPLEMENTED)
 ↓  [PHASE 3 TO BUILD] LLM Service → external LLM provider
 ↓  [PHASE 3 TO BUILD] MCP Gateway
 ↓  [PARTIALLY IMPLEMENTED] PaymentX Services — payment-service/audit-service/routing-service/reconciliation-service/reporting-service REST APIs exist (CURRENTLY IMPLEMENTED); validation-service and participant lookup do not (NOT CURRENTLY AVAILABLE); gateway routes to reach most of them do not exist yet (PHASE 3 TO BUILD, §1 finding #2)
 ↓  [CURRENTLY IMPLEMENTED] Database / Kafka / Redis — reached only by the business services themselves, never directly by AI components
 ↓  [PHASE 3 TO BUILD] Audit — new ai_audit_events (existing audit-service's audit_event trail: CURRENTLY IMPLEMENTED, unrelated/parallel)
 ↓  Response
 ↓  [CURRENTLY IMPLEMENTED, reused] Control Center
```

No component above marked "PHASE 3 TO BUILD" exists in the repository today. This flow is a design target, not a description of current behavior.

---

## 19. Implementation Order

Validated against the repository; the task's suggested sequence holds up well, with one addition (a called-out prerequisite inside 3.7, not a renumbered phase) and one italicized rationale for keeping 3.5 distinct from 3.4:

- **Phase 3.0 — Architecture & Readiness Audit** *(this document)*
- **Phase 3.1 — AI Chat Interface**: new Control Center page + new `paymentx-ai-chat-interface` service + new gateway route, wired to a trivial echo/stub response first — validates the whole plumbing (routing, auth inheritance, gateway wiring) before any real LLM call exists.
- **Phase 3.2 — Prompt Service**: template CRUD, no live LLM dependency needed to test it.
- **Phase 3.3 — LLM Service**: now Chat Interface can do real (non-RAG, non-agentic) chat end-to-end.
- **Phase 3.4 — Embedding Service**
- **Phase 3.5 — Vector Database**: kept as its own phase rather than folded into 3.4 *because it includes the one existing-infra change (Postgres image swap, §15), which deserves isolated validation before anything depends on it.*
- **Phase 3.6 — RAG Service**: grounded chat becomes possible.
- **Phase 3.7 — MCP Gateway**: **includes, as a prerequisite sub-task, adding the missing API Gateway routes for audit/routing/reconciliation/reporting services (§1 finding #2) and the missing validation-service/participant lookup endpoints if those tools are to move past NOT CURRENTLY AVAILABLE (§10)** — this is real scoped work hiding inside "build the MCP Gateway," not something already done.
- **Phase 3.8 — Agent Orchestrator**: ties MCP tools + RAG + LLM together for the investigation agent (§12).
- **Phase 3.9 — End-to-End AI Flow**
- **Phase 3.10 — Security + Observability + Production Hardening**

---

## Risks

1. **Auth-service is unimplemented platform-wide** (§1) — any Phase 3 security design that assumes "reuse existing JWT auth" is assuming something that doesn't exist yet. The AI platform inherits the same trust-the-network-boundary posture every other service has today; it must not be built as if a real per-user identity system exists.
2. **API Gateway only fronts 2 of 9 services** — the MCP Gateway's usefulness is directly bounded by how many new gateway routes get built (§1, §10).
3. **Kafka topic-naming inconsistency (ADR 0002, still open)** — any future AI component that consumes Kafka events directly (not in Phase 3 baseline, but plausible later per §5) needs to handle both conventions.
4. **No error-code catalog exists** (§9) — the most obviously valuable RAG knowledge source ("payment error codes") requires authoring, not extraction.
5. **`payment-service` has no `SecurityConfig`** (§2) — its real protection posture should be confirmed by whoever owns that service before Phase 3.7 wires an MCP tool through it.
6. **Wide-open CORS on the gateway** (`*` + credentials) — worth revisiting before browser-facing AI chat traffic starts flowing through it, independent of Phase 3.
7. **Retention policy for AI conversation content is undecided** (§6) — needs a compliance decision, not a default.

## Decisions

- Reuse `paymentx-common-library`, never `paymentx-common` (§16).
- One new `paymentx_ai` Postgres database, not per-AI-service databases (§6) — flagged as a deliberate convention deviation, not silently done.
- PostgreSQL + pgvector, not a dedicated vector DB (§7).
- Flat module structure, not nested under a new `paymentx-ai/` parent (§16).
- `/api/v1/ai/...` path convention, not `/api/ai/...` (§17).
- AI Chat Interface UI reuses Control Center frontend; AI backend logic does **not** live inside `control-center/backend` (§3, §5).
- MCP Gateway calls real business-service REST APIs via new gateway routes, not Control Center's incidental direct-DB-read endpoints, for `getValidationResult`/`getParticipant` (§10) — both marked NOT CURRENTLY AVAILABLE rather than backed by a workaround.
- Read-only MCP tools only in this phase; no mutation tools (§10, per explicit instruction).

## Open Items

- AI conversation/message retention policy (§6).
- Whether `paymentx_ai` as one shared database (vs. per-component databases) is acceptable to whoever owns platform database conventions (§6).
- Confirm `payment-service`'s actual current protection posture (§2, §11 risk 5).
- Decide who authors the payment error-code RAG knowledge source and from what source of truth (§9).
- Decide LLM/embedding provider(s) — left config-selectable by design (§8), not decided in this document.
- Confirm whether `control-center.security.enabled` is actually turned on anywhere beyond local dev — affects how much weight the shared-dashboard-token model should carry in the AI proxy's own security story (§3, carried from Control Center fork findings).

---

# Final Report

**1. Repository understanding:** Full evidence-based inspection completed across all 11 Maven-reactor modules, the Control Center frontend and backend, root `pom.xml`, `infra/docker-compose.yml`, `infra/prometheus.yml`, all 4 ADRs, and Control Center's own `ARCHITECTURE.md`/`API.md`/`RUNBOOK.md`. Every claim in this document traces to actual code or configuration; every gap is marked NOT AVAILABLE / NOT CURRENTLY AVAILABLE rather than assumed.

**2. Current architecture summary:** Spring Boot 3.5.9 / Java 21 Maven monorepo, 9 business services + gateway + (unimplemented) auth-service + Control Center, Postgres/Kafka/Redis/RabbitMQ(unused)/Zipkin/Prometheus/Grafana infra, all running as host processes in local dev. Business services communicate via Kafka (two competing topic-naming conventions, ADR 0002) and are reachable via REST for reads on payment/audit/routing/reconciliation/reporting (not validation, not participants). Security is a network-trust model throughout — no service independently re-verifies identity; auth-service, the intended source of real identity, has zero implementation today.

**3. Phase 3 readiness score: 6/10.** Strong foundations (proven multi-module pattern, shared library explicitly designed for this, mature Postgres/observability infra, several business services already expose exactly the read APIs MCP tools need). Held back by: no real platform-wide identity system, API Gateway covering only 2 of 9 services, two real REST-API gaps (validation lookup, participant lookup), and an unresolved Kafka naming inconsistency.

**4. Existing components reusable:** `paymentx-common-library` (as the shared dependency for all new AI modules), API Gateway's filter-chain pattern and rate-limiting infra, Control Center frontend (for the UI, per §3), Control Center's `ApiTesterAllowlist` design pattern (directly informs MCP Gateway's tool-allowlist design, §10), existing Prometheus/Grafana/Zipkin setup, `paymentx-common-library`'s `@Mask`/masking utilities (for AI output PII handling, §12), payment-service/audit-service/routing-service/reconciliation-service/reporting-service REST APIs (as MCP tool backing once gateway-routed).

**5. Components that need creation:** all 7 Phase 3 services (`paymentx-ai-chat-interface`, `paymentx-prompt-service`, `paymentx-llm-service`, `paymentx-embedding-service`, `paymentx-rag-service`, `paymentx-mcp-gateway`, `paymentx-agent-orchestrator`), a new Control Center "AI Assistant" page + thin backend proxy controller, new API Gateway routes (both for the new AI services and — prerequisite — for the 4-5 existing business services not currently gateway-routed).

**6. Recommended module structure:** Flat, at repository root, matching the existing 11-of-12 flat convention (§16).

**7. Database recommendation:** One new `paymentx_ai` Postgres database on the existing instance, minimum viable table set per §6 (9 tables, 3 candidate tables explicitly dropped as redundant).

**8. LLM recommendation:** Provider-abstracted (`LlmProvider` interface, config-selected implementation), no provider hardcoded in this document — that is an intentional open decision (§8, Open Items).

**9. RAG architecture:** PostgreSQL + pgvector (§7), initial sources = runbooks/API docs/ADRs/reconciliation taxonomy (available now) + payment error codes (needs authoring, §9).

**10. MCP architecture:** Fixed, read-only tool catalog mirroring Control Center's proven `ApiTesterAllowlist` pattern; 6 of 8 requested tools have a real API to back them (pending new gateway routes), 2 are genuinely NOT CURRENTLY AVAILABLE and should not be faked via a workaround (§10).

**11. Agent architecture:** Bounded-iteration, bounded-timeout tool-calling loop, fully audited via `ai_audit_events`, mirroring Control Center's own E2E-flow timeout pattern (§11).

**12. Security risks:** No real platform-wide identity system yet (§1); MCP Gateway must never be able to self-assert `X-Roles`/`X-Participant-Id` (§12); wide-open gateway CORS (§1); undecided AI content retention (§6).

**13. Observability plan:** Standard Micrometer/Prometheus/Zipkin, new AI-specific metric set (§12), future Control Center "AI Monitoring" page following the existing Metrics-page pattern exactly.

**14. Docker changes required:** One existing-image change (Postgres → pgvector-enabled), everything else additive and incremental per phase (§15).

**15. Exact next implementation task:** Phase 3.1 — build the AI Chat Interface (new Control Center page + new `paymentx-ai-chat-interface` service + new gateway route), starting with a stub/echo response to validate the full plumbing before any LLM integration.

---

NEXT TASK: PHASE 3.1 — AI CHAT INTERFACE
