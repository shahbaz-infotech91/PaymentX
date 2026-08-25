---
documentType: OPERATIONAL_REFERENCE
service: paymentx-audit-service
severity: N/A
source: paymentx-audit-service
version: "1.0"
---

# PaymentX Audit Service Reference

**PaymentX Error Analyzer knowledge document.** Ground truth from source.

## Role

The platform's aggregation point for cross-service history. `event/AuditEventConsumer.java` + `event/TopicEventTypeResolver.java` consume Kafka events centrally from other services and map `topic → EventType`.

## Event type enum

`EventType` (`entity/EventType.java:16-30`) — 15 values:

```
PAYMENT_CREATED, PAYMENT_UPDATED, PAYMENT_ROUTED, PAYMENT_COMPLETED, PAYMENT_FAILED,
PAYMENT_CANCELLED, PAYMENT_REFUNDED, VALIDATION_COMPLETED, PARTICIPANT_UPDATED,
ROUTING_RULE_CHANGED, SECURITY_EVENT, API_REQUEST, API_RESPONSE, SYSTEM_EVENT, KAFKA_EVENT
```

Note: this list has no dedicated `RECONCILIATION_MISMATCH`/`TIMEOUT` value — a timeout or reconciliation-related audit entry, if one is recorded here at all, would use one of the above general values (most plausibly `SYSTEM_EVENT` or `PAYMENT_FAILED`) — NOT DEFINED IN CURRENT IMPLEMENTATION exactly which; not confirmed by a direct read of every publisher in this pass.

## Event status enum

`EventStatus` (`entity/EventStatus.java:15-19`):

```
RECORDED, PROCESSING_FAILED, ARCHIVED
```

## Query surface

`AuditController`: `GET /api/v1/audit-events/{id}`; `GET /api/v1/audit-events` (search by `correlationId, paymentId, participantId, reference, status, eventType, fromDate, toDate`, paginated). Write: `POST /api/v1/audit-events` (requires `AUDIT_WRITER` role) — used by every other service's fire-and-forget audit writes, and (per Phase 4.0/4.1 of this project) by MCP Gateway and Agent Orchestrator's own audit clients.

## MCP tool: `audit.search`

Wraps the search endpoint (see `PAYMENTX_PHASE_4_2_0_ERROR_ANALYZER_DESIGN.md` §3). **Never returns the raw `payload` field** of an audit event — only `id, eventType, eventStatus, sourceService, correlationId, paymentId, participantId, reference, occurredAt`. This means an Error Analyzer can confirm *that* an event of a given type happened, and *when*, and correlate it by reference/correlationId — but cannot see the full original request/response body that generated it.

## What this means for Error Analyzer

`audit.search` is the only tool with cross-service historical reach — it is the practical substitute for the several missing query endpoints documented elsewhere in this corpus (`validation_log`, `payment_status_history`, per-mismatch-record detail). Its value is real but bounded: it confirms *that* something happened and roughly *when*, via a closed 15-value event-type taxonomy and a 3-value status taxonomy, never the full detail of *what* happened.
