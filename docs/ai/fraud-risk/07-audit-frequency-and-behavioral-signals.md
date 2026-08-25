---
documentType: RISK_REFERENCE
service: paymentx-audit-service
severity: N/A
source: paymentx-audit-service, paymentx-mcp-gateway
version: "1.0"
---

# PaymentX Audit Frequency and Behavioral Signals

**PaymentX Fraud/Risk knowledge document.** Ground truth extracted directly from source code (Phase 4.5.0 discovery, re-verified). This document covers the single most useful evidence-gathering capability currently available to a future PaymentX Fraud/Risk agent.

## The `audit.search` MCP tool's real filtering capability

- **Tool**: `audit.search` (`paymentx-mcp-gateway`, class `tool.AuditSearchTool`).
- **Real, source-verified filter arguments**: `correlationId`, `paymentId`, `participantId`, `reference`, `status`, `eventType`, `fromDate`, `toDate`, `page`, `size`.
- **Bounded**: `size` is capped at `McpGatewayProperties.auditSearchMaxPageSize` (50), default 20 — never an unbounded result set.
- **Resource-scoped**: if the caller is itself scoped to one participant, `participantId` is force-applied server-side (cannot be overridden or omitted to see other participants' events) — this is a caller-identity concern, distinct from the `participantId` filter an agent supplies when investigating on behalf of an authorized caller.

**This is the only existing capability that can support a genuine, bounded, participant-scoped, time-windowed event count** — the closest thing PaymentX has to a "velocity" or "frequency" signal today.

## The real event-type inventory

`entity.EventType` (`paymentx-audit-service`), 15 real values: `PAYMENT_CREATED`, `PAYMENT_UPDATED`, `PAYMENT_ROUTED`, `PAYMENT_COMPLETED`, `PAYMENT_FAILED`, `PAYMENT_CANCELLED`, `PAYMENT_REFUNDED`, `VALIDATION_COMPLETED`, `PARTICIPANT_UPDATED`, `ROUTING_RULE_CHANGED`, `SECURITY_EVENT`, `API_REQUEST`, `API_RESPONSE`, `SYSTEM_EVENT`, `KAFKA_EVENT`.

## SECURITY_EVENT — defined but dormant

`SECURITY_EVENT` is a real, valid value in `EventType` and is accepted by `audit.search`'s `eventType` filter and by the audit-service's generic event-recording API. **However, Phase 4.5.0's discovery confirmed by full-repo grep that every real construction of `EventType.SECURITY_EVENT` exists only in test code** (`AuditServiceImplTest`, `AuditControllerIntegrationTest`, and a `notification-service` test referencing the analogous `SourceEventType.SECURITY_EVENT`). **No production code path anywhere in PaymentX ever actually emits a `SECURITY_EVENT` today.**

**A future agent must state this exact fact when the topic arises**: *"SECURITY_EVENT exists in the audit schema/model but no production emission was verified."* Never treat `SECURITY_EVENT` as active fraud evidence, and never assume a search for it will return anything meaningful — an empty result for this event type reflects the type's dormancy, not an absence of security-relevant activity.

## What a bounded event-frequency analysis can look like

**Concept** (not a fixed rule): the number of events of a given real type — most usefully `PAYMENT_FAILED` — for one participant, within one explicit time window, is a real, queryable, historical count.

Example concept, phrased generically (not as a rule): *"How many `PAYMENT_FAILED` events exist for participant X between time A and time B?"* — this is answerable today via one bounded `audit.search` call (subject to the 50-row page cap; a count beyond that requires reading `totalElements` from the paginated response rather than assuming the returned page is exhaustive).

## Do not invent thresholds

**No PaymentX rule defines "more than N events in window W constitutes elevated risk."** No such threshold exists anywhere in source, configuration, or this corpus. A future agent must never state a specific number (e.g., "more than 20 failures is suspicious") as if it were a PaymentX rule — any such threshold would be either (a) the LLM's own invented figure, which this corpus explicitly forbids, or (b) a future, real, configurable rule that does not exist yet.

**A future implementation should make any such threshold explicit, configurable, and evidence-based** (e.g., derived from an actual historical baseline once one exists) — not hardcoded into a prompt or this corpus.

## Possible risk interpretation

An elevated count of `PAYMENT_FAILED` (or other negative-outcome) events for one participant in a short window is a genuine **operational-and-security-adjacent signal** worth surfacing — but the interpretation must remain qualitative ("a notably higher count than the surrounding evidence suggests is typical," if such a comparison is actually possible from gathered evidence) rather than a fixed numeric rule, and must never alone justify a risk level above MEDIUM.

## Limitations

- 50-row page cap per call — a very high-volume participant's true event count requires reading `totalElements`, not assuming completeness from one page's `content`.
- No purpose-built aggregation/count-only endpoint exists — `totalElements` is a byproduct of the pagination metadata, not a dedicated statistics feature.
- No historical baseline exists anywhere in PaymentX to judge what "elevated" means in absolute terms — this must be treated as `INSUFFICIENT_CONTEXT` for any claim requiring a comparative baseline the agent cannot actually establish from real evidence.
