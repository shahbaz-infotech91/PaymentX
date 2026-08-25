---
documentType: RECONCILIATION_REFERENCE
service: paymentx-reconciliation-service
severity: MEDIUM
source: paymentx-reconciliation-service
version: "1.0"
---

# PaymentX Reconciliation Status and Batch Model

**PaymentX Reconciliation Agent knowledge document (Phase 4.6.0).** Ground truth from source
(`paymentx-reconciliation-service`), not inferred.

## The two-level model: batch and record

A `ReconciliationBatch` is one run of the reconciliation engine — either against a specific
uploaded settlement file, or against a date-range window driven purely by already-consumed
internal Payment Service events. Its `BatchStatus` is one of `PENDING`, `RUNNING`,
`COMPLETED`, `FAILED`, `PARTIALLY_COMPLETED`.

A `ReconciliationRecord` is one row per compared transaction within a batch — the internal
side (from a consumed payment event) compared against the external side (from an imported
settlement file row), when both exist. Its `reconciliationStatus` is exactly one of the 10
`ReconciliationStatus` values below.

## The 10 ReconciliationStatus values and their real classification order

Read directly from `service/matching/MatchingEngine.match()` — this is the actual, exact
precedence the matching engine applies, checked in this order for every compared record:

1. **DUPLICATE** — the external row's payment id was already seen once in this batch
   (`ReconciliationDedupService`). Checked first regardless of anything else — a duplicate is
   a data-quality problem independent of what it contains.
2. **ORPHAN** — no internal transaction exists at all for this external settlement row.
3. **UNEXPECTED_SETTLEMENT** — the internal transaction's own status is terminal-non-settling
   (`CANCELLED`, `FAILED`, `REVERSED`, `TIMEOUT`) yet an external settlement arrived for it
   anyway.
4. **CURRENCY_MISMATCH** — internal and external currency differ. Checked before amount, since
   comparing "100 USD" against "100 EUR" numerically as equal would be materially wrong.
5. **AMOUNT_MISMATCH** — the absolute internal/external amount difference exceeds the
   configured `amountToleranceThreshold` (`ReconciliationProperties.Matching`, a real,
   configurable value — never hardcoded, never assume a specific number without retrieving it).
6. **STATUS_MISMATCH** — internal and external status are not both members of the same
   success-synonym set (`COMPLETED`/`SETTLED`/`SUCCESS`/`SUCCESSFUL` are treated as
   equivalent; a settlement provider's own vocabulary is compared semantically, not by exact
   string).
7. **LATE_SETTLEMENT** — settlement occurred more than `lateSettlementThresholdHours` after
   the internal event (the longer/stricter delay classification).
8. **SETTLEMENT_DELAY** — settlement occurred more than `settlementDelayThresholdHours` but
   not more than `lateSettlementThresholdHours` after the internal event (the shorter delay).
9. **MATCHED** — none of the above triggered; a genuinely successful reconciliation.

A tenth value, **MISSING**, is built separately (`buildMissingRecord`) at the end of a batch
for any internal transaction that completed successfully but was never claimed by any external
settlement row during that batch — the mirror image of ORPHAN.

**MATCHED is not an error or a finding — it is the successful, expected outcome.** Only the
other 9 values represent something worth surfacing as a discrepancy.

## The paymentReference -> batchId bridge (Phase 4.6.0)

Every `ReconciliationRecord` carries `referenceId` (the exact `paymentReference`/`reference`
value from the originating payment event — the same identifier `payment.lookup`,
`payment.status`, and `audit.search` already use) alongside its own `batchId`. As of Phase
4.6.0, the `reconciliation.status` MCP tool accepts this `paymentReference` directly (as an
alternative to `batchId`) and resolves the most recent matching record, including which batch
reconciled it. A `found: false` result from this path means **no reconciliation record exists
for this payment reference** — this is a real, legitimate state (the payment has never been
reconciled, or reconciliation has not yet run for it), never proof that the payment itself
does not exist, and never proof of any wrongdoing.

## What reconciliation.status can and cannot tell you

Can: a specific payment's own reconciliation record (status, internal/external amounts,
currencies, statuses, settlement dates, which batch) via `paymentReference`; a batch's overall
status and, with `includeSummary`, its aggregate mismatch-type counts, via `batchId`.

Cannot: individual `MismatchRecord` rows (description, resolution notes, resolved-by) for a
batch — the mismatch-search endpoint (`GET /api/v1/reconciliation/mismatches`) is not wrapped
by any MCP tool as of this phase. A specific mismatch's human-readable description or
resolution history is therefore not retrievable by this agent.
