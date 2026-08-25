---
documentType: INCIDENT_RCA_REFERENCE
service: paymentx-agent-orchestrator
severity: MEDIUM
source: paymentx-agent-orchestrator
version: "1.0"
---

# PaymentX Incident RCA — Real Evidence Sources

**PaymentX Incident RCA Agent knowledge document (Phase 4.8.0).** Only real, tool-accessible
evidence sources are listed here — verified against actual source, not assumed from service
names.

## Payment status-transition timeline (`payment.lookup` with `includeHistory: true`)

Phase 4.8.0 wired up a real, previously-unexposed capability: `payment_status_history`, written
automatically by `PaymentEngineImpl`/`RetryScheduler`/`TimeoutScheduler` on every real status
transition a payment goes through. Each entry has `fromStatus`, `toStatus`, a free-text
`reason`, and a real `transitionedAt` timestamp — ordered oldest first, a genuine, precise
timeline for a single payment. This is the single best timeline evidence source available to
this agent, and should be the first tool call for any timeline-shaped question.

## Audit trail (`audit.search`)

Real filters: `correlationId`, `paymentId`, `participantId`, `reference`, `status`,
`eventType`, `fromDate`, `toDate` (page/size capped). 15 real `EventType` values exist:
`PAYMENT_CREATED`, `PAYMENT_UPDATED`, `PAYMENT_ROUTED`, `PAYMENT_COMPLETED`, `PAYMENT_FAILED`,
`PAYMENT_CANCELLED`, `PAYMENT_REFUNDED`, `VALIDATION_COMPLETED`, `PARTICIPANT_UPDATED`,
`ROUTING_RULE_CHANGED`, `SECURITY_EVENT` (dormant — never emitted in production, only in test
fixtures, per Phase 4.5.0's own discovery), `API_REQUEST`, `API_RESPONSE`, `SYSTEM_EVENT`,
`KAFKA_EVENT`. Each real event carries its own `sourceService` and `correlationId`, making
cross-service correlation possible by shared `correlationId` or `reference`, not by guessing.

## Payment snapshot and status (`payment.lookup`, `payment.status`)

Current status, scheme, amount, participants, and — critically for RCA —
`failureReason` (free text set by `payment-service` at the point of failure). Data-minimized
(masked accounts, no internal UUIDs) per the platform's established convention.

## Routing state (`routing.lookup`)

Real routing-rule evidence — relevant when a hypothesis involves misrouting or a routing-rule
change coinciding with the incident window.

## Reconciliation state (`reconciliation.status`)

Resolvable by `batchId` or (Phase 4.6.0) by `paymentReference`. Ten real
`ReconciliationStatus` outcomes exist (`MATCHED` plus 9 mismatch/gap values) — a reconciliation
mismatch can be one corroborating signal in a multi-hypothesis incident analysis, never
sufficient alone (see `01-incident-rca-methodology.md`).

## What does NOT exist (verified absent, not assumed)

- **No centralized log-aggregation MCP tool.** No tool reads application log files or a log
  index. `audit.search`'s structured events are the closest available substitute, not a
  replacement.
- **No Kafka MCP tool** (no topic/offset/consumer-lag inspection capability). The real
  `KAFKA_EVENT` audit event type exists, but that is an audit record about an event, not a
  Kafka broker/consumer-group introspection capability.
- **No RabbitMQ MCP tool.**
- **No Redis MCP tool** (no cache-state inspection capability available to this agent).
- **No distributed-tracing MCP tool.** Zipkin exists as real platform infrastructure
  (`traceId`/`correlationId` are captured on real requests and audit events), but no MCP tool
  queries Zipkin directly — a `traceId` value can be *observed* in evidence already retrieved,
  never independently looked up by this agent.
- **No incidentId concept anywhere in the platform.** There is no incident-tracking table,
  entity, or API. This agent's only real inputs are a user's question, an optional
  `paymentReference`, and whatever correlation identifiers appear inside evidence already
  retrieved — never a fabricated incident identifier.
