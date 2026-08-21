# Control Center Backend API Reference

Base URL (dev): `http://localhost:8089`. Every endpoint returns the same
envelope:

```json
{ "success": true, "data": { ... }, "error": null, "timestamp": "2026-01-01T00:00:00Z" }
{ "success": false, "data": null, "error": { "errorCode": "...", "message": "...", "path": "..." }, "timestamp": "..." }
```

`errorCode` values you'll actually see: `KAFKA_UNREACHABLE`,
`REDIS_UNREACHABLE`, `UPSTREAM_UNREACHABLE`, `PAYMENT_NOT_FOUND`,
`E2E_RUN_NOT_FOUND`, `UNKNOWN_SERVICE`, `INVALID_FILENAME`,
`FILE_NOT_FOUND`, `BAD_REQUEST`, `VALIDATION_ERROR`, `UNAUTHORIZED`,
`INTERNAL_ERROR`. `ControlCenterException`-derived errors map to HTTP
502 (this backend acting as a proxy to something unreachable);
validation/allowlist errors map to 400; the auth gate (when enabled)
maps to 401.

Every endpoint below is **read-only** unless explicitly marked
otherwise. There is no endpoint anywhere in this API that accepts a
raw SQL string, a raw URL, or a raw shell command.

## Health

| Method | Path | Notes |
|---|---|---|
| GET | `/api/v1/health` | This backend's own health - exempt from the auth gate even when enabled |
| GET | `/actuator/health` | Standard Spring Boot Actuator health |

## Services

| Method | Path | Notes |
|---|---|---|
| GET | `/api/v1/services` | Real health of all 9 PaymentX business services |
| GET | `/api/v1/services/{service}/health` | One service, `service` = a real `ServiceIdentifier` slug |
| GET | `/api/v1/services/{service}/liveness` | |
| GET | `/api/v1/services/{service}/readiness` | |
| GET | `/api/v1/services/{service}/info` | |

## Search / Alerts

| Method | Path | Notes |
|---|---|---|
| GET | `/api/v1/search?q=` | Global search across payments/participants/settlement files, `q` must be 2+ chars |
| GET | `/api/v1/alerts` | Real, computed-on-demand operational alerts (never stored) |

## Kafka (`KafkaAdminMonitoringClient`, real AdminClient, read-only)

| Method | Path | Notes |
|---|---|---|
| GET | `/api/v1/kafka/topics` | Real partitions, replication, DLT/retry classification, message counts |
| GET | `/api/v1/kafka/consumer-groups` | Real state, member count, total committed offset |
| GET | `/api/v1/kafka/consumer-groups/{groupId}/lag` | Real per-partition lag |
| GET | `/api/v1/kafka/topics/{topicName}/throughput` | Real live two-sample delta (~1s) |

## RabbitMQ (`RabbitMqManagementClient`, real Management API, read-only)

| Method | Path |
|---|---|
| GET | `/api/v1/rabbitmq/overview` |
| GET | `/api/v1/rabbitmq/connections` |
| GET | `/api/v1/rabbitmq/channels` |
| GET | `/api/v1/rabbitmq/queues` |
| GET | `/api/v1/rabbitmq/bindings` |

## Redis (`RedisMonitoringClient`, native Lettuce, read-only - never returns a key's value)

| Method | Path | Notes |
|---|---|---|
| GET | `/api/v1/redis/health` | PING latency, version, role, uptime |
| GET | `/api/v1/redis/memory` | |
| GET | `/api/v1/redis/keyspace` | DBSIZE + bounded SCAN grouped by key prefix + hit/miss rate |
| GET | `/api/v1/redis/samples` | Bounded key name + TTL sample (never a value) |

## Postgres (`PostgresController`, 7 real databases, hardcoded parameterized SQL only)

| Method | Path | Query params |
|---|---|---|
| GET | `/api/v1/postgres/databases` | Connection + Liquibase status, all 7 |
| GET | `/api/v1/postgres/databases/{database}/status` | |
| GET | `/api/v1/postgres/databases/{database}/tables` | Row counts |
| GET | `/api/v1/postgres/participants` | `page,size,search` |
| GET | `/api/v1/postgres/payments` | `page,size,search,status,scheme,participantId,dateFrom,dateTo,sortField,sortAscending` |
| GET | `/api/v1/postgres/payments/stats` | |
| GET | `/api/v1/postgres/payments/timeseries` | `windowMinutes` (one of 5/15/30/60/360/1440) |
| GET | `/api/v1/postgres/payments/by-reference/{reference}` | |
| GET | `/api/v1/postgres/payments/by-reference/{reference}/flow` | Real 9-stage Payment Flow |
| GET | `/api/v1/postgres/routing-rules` | `page,size,search` |
| GET | `/api/v1/postgres/audit-events` | `page,size,search` |
| GET | `/api/v1/postgres/notifications` | `page,size,search` |
| GET | `/api/v1/postgres/reconciliation-batches` | `page,size` |
| GET | `/api/v1/postgres/reconciliation-records` | `page,size,search,status` |
| GET | `/api/v1/postgres/settlement-files` | `page,size` |
| GET | `/api/v1/postgres/report-executions` | `page,size` |
| GET | `/api/v1/postgres/report-files` | `page,size` - `fileName` matches `/api/v1/files/download/{fileName}` |

## Prometheus (`PrometheusClient`, fixed named-query catalog only - no raw PromQL from the browser)

| Method | Path | Notes |
|---|---|---|
| GET | `/api/v1/prometheus/metrics` | Lists the fixed query catalog (no execution) |
| GET | `/api/v1/prometheus/metrics/all` | Runs every named query |
| GET | `/api/v1/prometheus/metrics/{slug}` | Runs one, `slug` from `PrometheusMetricQuery` |
| GET | `/api/v1/prometheus/metrics/{slug}/range` | `rangeMinutes` (5/15/30/60/360/1440) |

## Zipkin (`ZipkinClient`, real v2 API)

| Method | Path |
|---|---|
| GET | `/api/v1/zipkin/services` |
| GET | `/api/v1/zipkin/traces/{traceId}` |

## Notifications infra

| Method | Path | Notes |
|---|---|---|
| GET | `/api/v1/notifications/mailhog` | Real MailHog reachability + captured-message count + a link to its own UI |

## Files (`SafeFileService` - scoped to exactly one configured directory)

| Method | Path | Notes |
|---|---|---|
| GET | `/api/v1/files` | Lists real files (bounded to 500, newest first) |
| GET | `/api/v1/files/download/{filename}` | Streams one real file - filename validated against a strict allowlist pattern and a canonical-path containment check |

## Logs (`LogFileService` - bounded, allowlisted services only)

| Method | Path | Query params |
|---|---|---|
| GET | `/api/v1/logs/services` | Fixed 9-service allowlist |
| GET | `/api/v1/logs` | `service,level,correlationId,traceId,paymentReference,search,from,to,limit` (limit capped server-side, default 200, max 1000) |

## API Tester (`ApiTesterAllowlist` + `ApiTesterService` - the real SSRF boundary for this feature)

| Method | Path | Notes |
|---|---|---|
| GET | `/api/v1/api-tester/endpoints` | The full, real, fixed allowlist (~38 entries across all 9 services + gateway) |
| POST | `/api/v1/api-tester/execute` | Body: `{service, method, path, queryParams, headers, body}` - rejected with 400 unless it exactly matches an allowlist entry |

## E2E (`E2EFlowService` - real, bounded, async)

| Method | Path | Notes |
|---|---|---|
| POST | `/api/v1/e2e/run` | Starts one real E2E payment run in the background, returns an immediate `RUNNING` snapshot with a `runId` |
| GET | `/api/v1/e2e/run/{runId}` | Poll the run's current real snapshot |
| GET | `/api/v1/e2e/history` | Bounded (last 20) in-memory run history |

`overallStatus` is one of `RUNNING`, `SUCCESS`, `FAILED`, `TIMEOUT` -
`SUCCESS` is only ever set when the real payment reached a real
terminal `SETTLED` status; a run that exceeds
`control-center.e2e.max-duration-seconds` (default 60s) is reported as
`TIMEOUT`, never `SUCCESS`.
