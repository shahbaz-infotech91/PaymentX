---
documentType: ERROR_CODE_REFERENCE
service: paymentx-reconciliation-service
severity: MEDIUM
source: paymentx-reconciliation-service
version: "1.0"
---

# PaymentX Reconciliation Errors

**PaymentX Error Analyzer knowledge document.** Ground truth from source.

## Mismatch type enum

`ReconciliationStatus` (`entity/ReconciliationStatus.java:19-30`) — used as the mismatch-type classification on `MismatchRecord.mismatch_type`:

```
MATCHED, MISSING, DUPLICATE, AMOUNT_MISMATCH, CURRENCY_MISMATCH, STATUS_MISMATCH,
SETTLEMENT_DELAY, LATE_SETTLEMENT, ORPHAN, UNEXPECTED_SETTLEMENT
```

10 values — the only category in this platform with a formal, closed, multi-value mismatch taxonomy (contrast with `payment-errors.md`'s free-text `failureReason`). `MATCHED` itself is not an error — it represents a successfully reconciled record.

## Batch status enum

`BatchStatus` (`entity/BatchStatus.java:15-21`):

```
PENDING, RUNNING, COMPLETED, FAILED, PARTIALLY_COMPLETED
```

## Persistence

`mismatch_record` table, entity `MismatchRecord` (`entity/MismatchRecord.java:44-70`) — `reconciliation_record_id, batch_id, mismatch_type, description, resolved, resolved_by, resolved_at, resolution_notes`.

Per mismatch-type meaning (inferred directly from the enum name, no separate description field exists per-value — NOT DEFINED IN CURRENT IMPLEMENTATION beyond the name itself for each):

| Value | Field | Value |
|---|---|---|
| `MISSING` | Error code | `MISSING` |
| | Meaning | A record expected on one side of reconciliation was not found on the other |
| | Retryability | NOT DEFINED IN CURRENT IMPLEMENTATION |
| | Remediation | NOT DEFINED IN CURRENT IMPLEMENTATION — `resolution_notes`/`resolved_by` fields exist for a human to record one, but no automated remediation is encoded in source |
| `DUPLICATE` | Meaning | A record appears more than once where exactly one was expected (distinct from Validation Service's `DuplicatePaymentException` — this is a reconciliation-time finding, not an idempotency-claim rejection) |
| `AMOUNT_MISMATCH` | Meaning | The reconciled amount differs between the two sides being compared |
| `CURRENCY_MISMATCH` | Meaning | The reconciled currency differs |
| `STATUS_MISMATCH` | Meaning | The payment status differs between the two sides |
| `SETTLEMENT_DELAY` | Meaning | Settlement occurred later than expected, by more than `settlementDelayThresholdHours` but not more than `lateSettlementThresholdHours` (both configurable via `ReconciliationProperties.Matching`) |
| `LATE_SETTLEMENT` | Meaning | Settlement occurred later than expected, by more than `lateSettlementThresholdHours` — a stricter/longer-delay classification than `SETTLEMENT_DELAY`, not a duplicate of it. Precedence confirmed directly in `service/matching/MatchingEngine.classifySettlementTiming()` (Phase 4.6.0 re-read): the two thresholds are checked in order, `LATE_SETTLEMENT` first (the longer delay), then `SETTLEMENT_DELAY` |
| `ORPHAN` | Meaning | A record exists with no corresponding counterpart at all |
| `UNEXPECTED_SETTLEMENT` | Meaning | A settlement occurred that was not expected |

For every row above, **retryability and remediation are NOT DEFINED IN CURRENT IMPLEMENTATION** as structured fields — `MismatchRecord.resolved/resolved_by/resolved_at/resolution_notes` exist for tracking human resolution, but nothing in source encodes an automated "this mismatch type is retryable" or "the fix for this mismatch type is X" rule.

## Settlement file parsing failure

`SettlementFileParseException` (`service/importer/SettlementFileParseException.java`) — thrown on malformed settlement file upload. Details (specific trigger conditions, error message format) were **not read in this pass** — NOT DEFINED IN THIS DOCUMENT.

## Query surface

`GET /api/v1/reconciliation/batches/{batchId}` (status), `.../summary`, `GET /api/v1/reconciliation/mismatches` (search by `batchId, mismatchType, resolved`), `.../report` (CSV). MCP tool `reconciliation.status` wraps the batch status + summary endpoints (see `PAYMENTX_PHASE_4_2_0_ERROR_ANALYZER_DESIGN.md` §3) — **it does not wrap the mismatch-search endpoint**, so an Error Analyzer using only the existing MCP tool set can confirm a batch's aggregate mismatch *counts* (via `includeSummary`) but cannot retrieve the *individual* `MismatchRecord` rows or their `description`/`resolution_notes` — NOT DEFINED IN CURRENT IMPLEMENTATION as an MCP-tool-accessible capability.

## No participant scoping

`reconciliation.status` has no resource-ownership check — confirmed in the Phase 4.2.0 design document (§3): reconciliation batches are platform-wide, not owned by a single participant.

## Prerequisite limitation for Error Analyzer (Phase 4.2.0) — RESOLVED Phase 4.6.0

As of Phase 4.2.0, `reconciliation.status` required a `batchId` (a UUID) with no tool or field
mapping a `paymentReference` to the `batchId` that reconciled it. **This gap was closed in
Phase 4.6.0**: `reconciliation.status` now also accepts an optional `paymentReference`
argument (mutually exclusive with `batchId` — batchId takes priority if both are supplied).
When `paymentReference` is given, the tool calls reconciliation-service's new
`GET /api/v1/reconciliation/records?paymentReference=...` endpoint (backed by the real,
pre-existing `reference_id` column on `reconciliation_record` — the same value every other
PaymentX read path already keys on) and returns that payment's most recent reconciliation
record, including the `batchId` that reconciled it. An empty result (`found: false`) is a
legitimate business state — the payment has never been reconciled, or reconciliation has not
yet run for it — not an error and not evidence that the payment does not exist.
