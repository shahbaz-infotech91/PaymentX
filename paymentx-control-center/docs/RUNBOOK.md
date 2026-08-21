# Control Center Runbook

Operational reference for running, configuring, and troubleshooting
the Control Center. See `ARCHITECTURE.md` for system design and
`API.md` for the REST surface.

## 1. Prerequisites

- Java 21, Maven 3.9+ (backend)
- Node.js 20+, npm (frontend)
- The real PaymentX infra running (`infra/docker-compose.yml` at the
  repo root: Postgres 5433, Kafka 9092, Redis 6379, RabbitMQ 15672,
  Zipkin 9411, Prometheus 9090, MailHog 8025) - `docker compose up -d`
  from `infra/`
- Ideally, some or all of the 9 real PaymentX business services
  running too (`paymentx-validation-suite/scripts/start-all.ps1`,
  or `mvn spring-boot:run` per service) - every page still loads and
  shows a clear error/down state without them (see §6), but most real
  data obviously requires them

## 2. Running the backend

```bash
cd paymentx-control-center/backend
mvn spring-boot:run                          # http://localhost:8089, profile: dev (default)
```

Or from the repo root as part of the full reactor:
`mvn -pl paymentx-control-center/backend -am clean install`.

Health check: `curl http://localhost:8089/api/v1/health`.

Production profile: `mvn spring-boot:run -Dspring-boot.run.profiles=prod`
(or `SPRING_PROFILES_ACTIVE=prod java -jar target/*.jar`) - see §7 and
`application-prod.yml` for what changes (every connection detail
becomes environment-variable-driven with no default, and the auth gate
is on by default).

## 3. Running the frontend

```bash
cd paymentx-control-center/frontend
npm install
cp .env.example .env.local     # edit VITE_API_BASE_URL if the backend isn't on :8089
npm run dev                    # http://localhost:5173
```

Production build: `npm run build` (`tsc -b && vite build`, output in
`dist/`). Preview a production build locally: `npm run preview`.

## 4. Running via Docker

Optional - see `paymentx-control-center/docker-compose.yml` for the
full rationale (why `host.docker.internal`, not a shared network).

```bash
# From the repo root (build context matters - see the compose file's own comment)
docker compose -f paymentx-control-center/docker-compose.yml up --build
# backend: http://localhost:8089, frontend: http://localhost:8090
```

This does **not** touch `infra/docker-compose.yml` - run that
separately for the actual infra containers.

## 5. Configuration reference

All backend config lives under the `control-center.*` prefix in
`application.yml` (local-dev defaults - real, publicly-documented
values matching `infra/docker-compose.yml`, not secrets) and
`application-prod.yml` (every value from an environment variable, no
default). Full property list: `ControlCenterProperties.java`. The most
commonly overridden ones:

| Property | Env var | Default (dev) |
|---|---|---|
| `control-center.services.payment-service-url` | `CONTROL_CENTER_SERVICES_PAYMENT_SERVICE_URL` | `http://localhost:8083` |
| `control-center.postgres.host` / `.port` | `CONTROL_CENTER_POSTGRES_HOST` / `_PORT` | `localhost` / `5433` |
| `control-center.kafka.bootstrap-servers` | `CONTROL_CENTER_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` |
| `control-center.redis.host` / `.port` | `CONTROL_CENTER_REDIS_HOST` / `_PORT` | `localhost` / `6379` |
| `control-center.rabbitmq.management-url` | `CONTROL_CENTER_RABBITMQ_MANAGEMENT_URL` | `http://localhost:15672` |
| `control-center.prometheus.base-url` | `CONTROL_CENTER_PROMETHEUS_BASE_URL` | `http://localhost:9090` |
| `control-center.zipkin.base-url` | `CONTROL_CENTER_ZIPKIN_BASE_URL` | `http://localhost:9411` |
| `control-center.mailhog.base-url` | `CONTROL_CENTER_MAILHOG_BASE_URL` | `http://localhost:8025` |
| `control-center.files.reports-export-directory` | `CONTROL_CENTER_FILES_REPORTS_EXPORT_DIRECTORY` | `/tmp/paymentx-reporting/exports` |
| `control-center.logs.output-directory` | `CONTROL_CENTER_LOGS_OUTPUT_DIRECTORY` | `../../paymentx-validation-output` |
| `control-center.e2e.max-duration-seconds` | `CONTROL_CENTER_E2E_MAX_DURATION_SECONDS` | `60` |
| `control-center.security.enabled` | `CONTROL_CENTER_SECURITY_ENABLED` | `false` |
| `control-center.security.dashboard-token` | `CONTROL_CENTER_SECURITY_DASHBOARD_TOKEN` | *(none - required if enabled)* |
| `control-center.cors.allowed-origins` | `CONTROL_CENTER_CORS_ALLOWED_ORIGINS` | `http://localhost:5173` |

Frontend config (`.env.local`, see `.env.example`):

| Var | Default | Notes |
|---|---|---|
| `VITE_API_BASE_URL` | `http://localhost:8089` | Where the backend is |
| `VITE_API_TIMEOUT_MS` | `15000` | Axios request timeout |
| `VITE_APP_ENVIRONMENT` | `dev` | Shown as a chip in the header |

## 6. Integrations and how failure looks

Every integration client has its own real connect/read timeout
(`ControlCenterProperties`) and its own real error handling - a
downstream outage never crashes the backend or the frontend, it
surfaces as a real error the corresponding page shows clearly.

| Dependency | Down looks like | Where |
|---|---|---|
| A PaymentX business service | `ServiceHealthStatus.status = "DOWN"` with a real error message | Dashboard, Services page |
| Postgres (any of the 7 DBs) | `DatabaseStatus.reachable = false` | Database page |
| Kafka broker | `KAFKA_UNREACHABLE`, real ErrorState | Kafka page |
| Redis | `RedisHealthStatus.reachable = false`; the other 3 endpoints now throw `REDIS_UNREACHABLE` too (Phase 6 fix - previously an uncaught connection failure surfaced as a generic 500) | Redis page |
| RabbitMQ | Real ErrorState from the Management API call failing | RabbitMQ page |
| Prometheus | `success:false` per named query, shown per-panel | Metrics page |
| Zipkin | Real ErrorState on lookup | Traces page |
| MailHog | `MailHogStatus.reachable = false` with the real connection error | Notifications page |
| This backend itself unreachable | Every page's ErrorState: "Could not reach the Control Center backend." | Everywhere |

## 7. Security

See `ARCHITECTURE.md` §7 for the model. Operationally:

- **Local dev (default)**: `control-center.security.enabled=false` -
  no token needed anywhere, matches every prior phase's verified
  workflow.
- **Anywhere beyond localhost**: set
  `CONTROL_CENTER_SECURITY_ENABLED=true` and
  `CONTROL_CENTER_SECURITY_DASHBOARD_TOKEN=<a real, random, long
  value>` (e.g. `openssl rand -hex 32`). The backend refuses to start
  if enabled without a token - this is intentional fail-closed
  behavior, not a bug.
- The frontend will show a real "Dashboard Access Required" prompt
  (`AuthGate.tsx`) the first time it hits a real 401 from the health
  check, store the token in `sessionStorage` (never `localStorage`,
  never committed to source), and attach it as
  `Authorization: Bearer <token>` on every subsequent request.
- `/actuator/prometheus` and `/actuator/health` are **not** gated by
  this filter even when security is enabled (only `/api/**` is) -
  this is a deliberate, common tradeoff: this platform's real
  Prometheus scrape config doesn't send a bearer token, and gating the
  scrape endpoint would silently break metrics collection. If your
  deployment needs the metrics endpoint locked down too, put it behind
  a network-level control (security group, reverse-proxy ACL), not
  application-level auth.
- Never commit `.env`, `.env.local`, `*.pem`, `*.key`, or
  `application-local.yml`/`application-secrets.yml` - already in the
  repo-root `.gitignore`.

## 8. Production considerations

- Run with `SPRING_PROFILES_ACTIVE=prod` and every
  `CONTROL_CENTER_*` environment variable in `application-prod.yml`
  set - there are no hardcoded credentials to fall back to in that
  profile by design.
- Enable the auth gate (§7).
- Point `CONTROL_CENTER_CORS_ALLOWED_ORIGINS` at the real frontend
  origin only - never `*`.
- `management.endpoint.health.show-details` is `never` in the prod
  profile (dependency details aren't leaked to an unauthenticated
  health check caller).
- The Postgres connection pool (`control-center.postgres.max-pool-size`,
  default 3) is intentionally small - this backend only ever runs
  read-only monitoring queries, never a business workload; raise it
  only if you observe real pool contention under real dashboard
  traffic.
- Build the frontend once per deploy (`VITE_API_BASE_URL` is inlined
  at build time, not read at runtime) and serve the static `dist/`
  output from a real static file server/CDN - see
  `frontend/Dockerfile` + `nginx.conf` for a working example with SPA
  fallback routing and asset caching.
- The E2E feature creates a real, small (1.00 USD) test payment
  against the real platform every time it runs - treat "who can click
  Run Complete Payment Flow" as a real access-control question in any
  shared environment (currently gated only by whatever gates the
  dashboard itself, i.e. the auth token in §7).

## 9. Troubleshooting

**A page shows a spinner forever, never an error, when the backend is
actually down.** This was a real bug found and fixed during Phase 6
production hardening: TanStack Query's default `networkMode: 'online'`
pauses a failed query's retry indefinitely based on its own
online/offline heuristic, which is not a reliable proxy for "is this
specific backend reachable." Fixed in `frontend/src/api/queryClient.ts`
via `networkMode: 'always'`. If you see this symptom again after a
React Query version upgrade, check that setting first.

**Backend won't start, logs `dashboard-token is blank`.** You set
`CONTROL_CENTER_SECURITY_ENABLED=true` without
`CONTROL_CENTER_SECURITY_DASHBOARD_TOKEN`. This is intentional
fail-closed behavior (§7) - set a real token.

**`mvn clean` fails to delete a jar on Windows.** A previous run of a
service (including this backend) is still holding the jar's file
handle. Stop it first (`Stop-Process`, or close the terminal running
`spring-boot:run`), then retry.

**Docker build for this module fails on a missing sibling `pom.xml`.**
This backend is registered in the root reactor `pom.xml` alongside 10
other real modules; Maven validates the full reactor structure even
when building with `-pl`. `backend/Dockerfile` already copies every
sibling module's `pom.xml` for exactly this reason (see its own
comment) - if you copy a trimmed-down version of that Dockerfile,
keep that step.

**Logs page shows nothing for a service.** The Log Viewer reads real,
already-written files at `control-center.logs.output-directory`
(default: `paymentx-validation-output/svc-<service>.log`, the same
files `paymentx-validation-suite/scripts/start-all.ps1` writes when it
starts a service). If that service was never started via that script
(e.g. started some other way, or its log file was cleaned up), there
is genuinely nothing to show - this is honest, not a bug.

**Redis panels other than "Health" show an error while Health shows
reachable.** Real, transient connection issue mid-request - retry. If
persistent while `health` reports reachable, check for Redis-side
connection limits or ACL restrictions on the SCAN/INFO commands this
backend's read-only client uses.
