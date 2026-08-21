# PaymentX Control Center

A real, browser-based operational dashboard for the PaymentX platform -
Kafka, RabbitMQ, Redis, all 7 Postgres databases, Prometheus, Zipkin,
MailHog, and all 9 PaymentX business services, plus a safe API tester
and a bounded end-to-end payment flow runner. Every number on every
page comes from a real, live call to real infrastructure or a real
PaymentX service - there is no mock data, no fabricated metrics, and no
simulated success anywhere in this module.

**Status: feature-complete through Phase 6 (production hardening).**
See `docs/ARCHITECTURE.md` for the full module map and design
rationale, `docs/API.md` for the complete backend REST surface, and
`docs/RUNBOOK.md` for setup, configuration, troubleshooting, security,
and production considerations - this README is intentionally just the
front door.

## What it does

| Area | Real integration |
|---|---|
| Dashboard / Services | Live health of all 9 PaymentX business services + this backend |
| Payments / Payment Flow | Real, paginated, filtered/sorted `paymentx_payment.payment` rows, with a real 9-stage flow view per payment |
| Kafka | Real AdminClient - topics, partitions, DLT/retry classification, consumer group lag, live throughput sampling |
| RabbitMQ | Real Management API - overview, connections, channels, queues, bindings |
| Redis | Real PING/INFO - health, memory, keyspace (bounded SCAN), bounded key+TTL samples |
| Database | Read-only viewer across all 7 real Postgres databases (Participants/Payments/Routing Rules/Audit/Notifications/Reconciliation/Reports/Settlement) - no SQL, no UPDATE, no DELETE |
| Audit / Notifications / Reconciliation / Reporting | Real Postgres-backed history, real MailHog status for notifications, real downloadable report files |
| Logs | Bounded, filterable viewer over each real service's real redirected stdout |
| Traces | Real Zipkin trace lookup with a real span waterfall |
| Metrics | Real Prometheus range queries + a real Postgres-derived payments/success-rate chart, with a 5m-24h time range |
| Files | Safe, allowlisted browser for real generated report files |
| API Tester | Postman-like tester restricted to a real, fixed allowlist of ~38 real PaymentX endpoints - never an arbitrary URL |
| E2E | Runs one real, bounded payment through the real Gateway -> Validation -> Kafka -> Payment engine, live stage tracking, finite timeout, never fabricates SUCCESS |

## Quick start

```bash
# 1. Infra + the 9 PaymentX services must already be running (see repo-root
#    paymentx-validation-suite/scripts/start-all.ps1, or start them yourself)

# 2. Backend
cd paymentx-control-center/backend
mvn spring-boot:run                    # http://localhost:8089

# 3. Frontend (separate terminal)
cd paymentx-control-center/frontend
npm install
npm run dev                            # http://localhost:5173
```

Full setup, environment variables, and troubleshooting: `docs/RUNBOOK.md`.

## Repository layout

```
paymentx-control-center/
    frontend/     React 19 + TypeScript + Vite SPA
    backend/      Spring Boot dashboard backend (independent Maven module)
    docs/         ARCHITECTURE.md, API.md, RUNBOOK.md
    scripts/      Local dev convenience scripts
    docker-compose.yml   Optional - runs this module itself as containers
```

The backend has **zero compile-time dependency** on any PaymentX
business service or on `paymentx-common`/`paymentx-common-library` -
every real connection (Postgres, Kafka, Redis, RabbitMQ, Prometheus,
Zipkin, MailHog, the 9 services) is made through its own, independently
configured clients (`backend/src/main/java/com/paymentx/controlcenter/client/`).

## Security posture

Read-only by design everywhere it can be: no SQL from the browser, no
raw URLs in the API Tester (server-enforced allowlist), no destructive
Kafka/Redis/RabbitMQ operations, no secrets ever logged or returned in
a response. Authentication is a real, opt-in, single-shared-token gate
(`control-center.security.enabled`, off by default for local dev) -
see `docs/RUNBOOK.md#security` for the full picture, including its
honest limitations.

## License / ownership

Internal PaymentX tooling - part of the `paymentx` monorepo. See the
repo root `README.md` for the platform this dashboard observes.
