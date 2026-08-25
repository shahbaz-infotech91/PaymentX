---
documentType: INCIDENT_RCA_REFERENCE
service: paymentx-agent-orchestrator
severity: MEDIUM
source: paymentx-agent-orchestrator
version: "1.0"
---

# PaymentX Incident RCA — Known Data Gaps and Limitations

**PaymentX Incident RCA Agent knowledge document (Phase 4.8.0).** Explicit, source-verified
gaps this agent must disclose rather than paper over.

## No cross-service log correlation beyond audit events

`audit.search` is the only structured, queryable record of what happened across services. It is
not a full application log — it does not carry stack traces, debug-level detail, or every
internal decision a service made, only the discrete business events each service chooses to
audit. A "why exactly did the retry fail internally" question may be genuinely unanswerable from
available evidence — this is a real limitation to state, not a prompt to guess.

## Retry/timeout mechanics are observed only through their outcomes

`PaymentRetry`/the real `RetryScheduler`/`TimeoutScheduler` govern retry and timeout behavior,
but no MCP tool exposes `PaymentRetry` rows directly (current retry count, max retry, next
retry time). What IS visible is the *effect* of retry/timeout logic: a `payment_status_history`
entry whose `reason` text was written by these schedulers (e.g. a timeout- or retry-related
transition reason) — real, but indirect. The agent must not claim to know the configured
`max_retry` or timeout threshold values; those are real, configured numbers this agent has no
tool to retrieve.

## No SLA or operational-threshold knowledge

PaymentX has no MCP-exposed concept of an SLA, an alert threshold, or a "this is abnormal"
baseline. Whether a given latency or retry count is "normal" or "excessive" is not something
this agent can determine from available data — describing a duration as long or short without a
retrievable baseline to compare against would be fabricating a threshold.

## Reconciliation mismatch is a comparison outcome, not a root cause

Per the Reconciliation Agent's own corpus (`docs/ai/reconciliation/`), a `ReconciliationStatus`
value describes what a batch comparison found, not why. It is valid corroborating evidence in an
incident hypothesis, never a root cause by itself.

## No incident identifier, no incident timeline store

There is no `incident` table or entity anywhere in PaymentX. Every "incident" this agent
investigates is reconstructed live, per request, from real payment/audit/reconciliation/routing
evidence — never retrieved from a pre-existing incident record, because none exists.

## Shared with Error Analyzer's own corpus

`docs/ai/error-analyzer/` already documents real per-service error-code taxonomies
(`payment-errors.md`, `validation-errors.md`, `routing-errors.md`, `reconciliation-errors.md`)
and operational documents (`kafka-failures.md`, `timeout-retry.md`). RAG has no agent-scoping
(confirmed, Phase 4.5.4) — this agent's own retrieval can and should draw on that existing,
already-grounded content for interpreting a specific error code or failure category; this
corpus adds the RCA *methodology* layer (fact/correlation/hypothesis, confidence
classification, timeline construction) that did not exist anywhere before this phase, not a
duplicate of that error-code reference material.
