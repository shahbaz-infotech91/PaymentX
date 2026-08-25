---
documentType: RECONCILIATION_REFERENCE
service: paymentx-reconciliation-service
severity: MEDIUM
source: paymentx-reconciliation-service
version: "1.0"
---

# PaymentX Reconciliation Data Gaps and Limitations

**PaymentX Reconciliation Agent knowledge document (Phase 4.6.0).** Explicit, source-verified
gaps this agent must disclose rather than paper over.

## No individual mismatch record access via MCP

`GET /api/v1/reconciliation/mismatches` (search by `batchId`, `mismatchType`, `resolved`) and
its per-mismatch `description`/`resolution_notes`/`resolved_by`/`resolved_at` fields are not
wrapped by any MCP tool as of Phase 4.6.0. The agent can state a batch's aggregate mismatch
*counts* per type (via `reconciliation.status` with `includeSummary`) and a specific payment's
own `reconciliationStatus` (via the `paymentReference` bridge), but cannot retrieve the
free-text description a human operator may have written for a specific mismatch, nor whether
it has already been resolved.

## No CSV report access via MCP

`GET /api/v1/reconciliation/batches/{batchId}/report` (full per-record CSV export) is a
download endpoint, not wrapped by any MCP tool. Bulk/complete-batch detail beyond the
aggregate summary is not available to this agent.

## No batch-triggering or reprocessing capability

`POST /api/v1/reconciliation/batches` (start), `.../reprocess`, and
`POST /api/v1/settlement-files` (upload) all require `RECONCILIATION_ADMIN` and are write
operations. This agent has no such capability, by design (Phase 4.6.0 scope: read-only,
evidence-based analysis only) and by structural enforcement (no write-capable MCP tool exists
on the platform at all, for any agent).

## Configured thresholds are real but not hardcoded knowledge

`amountToleranceThreshold`, `settlementDelayThresholdHours`, and `lateSettlementThresholdHours`
(`ReconciliationProperties.Matching`) are real, environment-configurable values that determine
`AMOUNT_MISMATCH`/`SETTLEMENT_DELAY`/`LATE_SETTLEMENT` classification. Their exact current
values are not encoded in this document and must never be stated as a specific number by the
agent unless retrieved through a tool that actually returns them — no such tool exists as of
this phase, so the agent must describe these thresholds only in relative/structural terms (as
this document does), never invent a specific hour count or currency amount.

## No participant scoping on reconciliation batches

`reconciliation.status` (both the `batchId` and `paymentReference` paths) has no
resource-ownership/participant-boundary check — reconciliation batches are a platform-wide
settlement operation, not owned by a single participant in this data model. This differs from
`payment.lookup`, which does enforce a debtor/creditor resource boundary. The agent should not
imply a participant-scoping guarantee that does not exist for reconciliation data.

## Shared with Fraud Detection Agent's own reconciliation-anomaly document

`docs/ai/fraud-risk/05-reconciliation-anomaly-risk.md` (Phase 4.5.1) covers the same
`ReconciliationStatus` taxonomy from a risk-signal-strength angle (how a mismatch may combine
with other signals in a fraud/risk assessment). This document instead covers the operational
meaning and access limitations of reconciliation data for its own sake. Both are legitimately
retrievable by either agent — RAG has no agent-scoping (Phase 4.5.4 finding) — and are
consistent with each other, not contradictory.
