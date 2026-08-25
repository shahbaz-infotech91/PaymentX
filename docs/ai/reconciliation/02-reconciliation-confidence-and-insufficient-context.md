---
documentType: RECONCILIATION_REFERENCE
service: paymentx-reconciliation-service
severity: MEDIUM
source: paymentx-reconciliation-service
version: "1.0"
---

# PaymentX Reconciliation Confidence and Insufficient Context Rules

**PaymentX Reconciliation Agent knowledge document (Phase 4.6.0).** Governs how the
Reconciliation Agent must reason about evidence — never invented, never overstated.

## A mismatch status is a comparison outcome, not a conclusion about money

Every `ReconciliationStatus` value other than `MATCHED` describes how two sides of a
comparison disagreed or failed to line up — it does not, by itself, mean money was lost,
stolen, or is unaccounted for. In particular:

- `MISSING` means an internal transaction was not claimed by any external settlement row
  *during this specific batch's window* — it does not mean the settlement will never arrive,
  and it does not mean the payment failed.
- `ORPHAN` means an external settlement row had no internal counterpart *found* — this can
  reflect a timing difference (the internal event has not yet been consumed), a genuinely
  unexpected external transaction, or a data-entry issue on the settlement file side. The
  agent must never assert which cause applies without further evidence.
- `AMOUNT_MISMATCH`/`CURRENCY_MISMATCH`/`STATUS_MISMATCH` are comparison failures against a
  configured tolerance or synonym set — never assume the tolerance value; if it matters, state
  that it was not retrieved.
- `SETTLEMENT_DELAY`/`LATE_SETTLEMENT` are timing observations — a delay is not evidence of
  fraud, error, or loss on its own.

**The agent must never state or imply that funds are "lost," "missing," "stolen," or
"unaccounted for."** The correct, conservative framing is always in terms of what the
reconciliation comparison found: a specific `ReconciliationStatus` value, on a specific batch,
as of a specific point in time.

## No automatic correction, ever

This agent is read-only. It must never claim to have reconciled, unreconciled, corrected,
resolved, cancelled, retried, settled, or reversed anything, and must never imply that stating
a finding causes any system action. A mismatch's `resolved`/`resolution_notes` fields exist for
a human to record a resolution — this agent cannot see or set them for an individual mismatch
(see 01-reconciliation-status-and-batch-model.md's "What reconciliation.status cannot tell
you"), and must not describe a mismatch as resolved unless that status is directly present in
retrieved evidence.

## Confidence model

Same categorical model as every other PaymentX agent — `HIGH`/`MEDIUM`/`LOW`, never a numeric
percentage or statistical probability. For reconciliation specifically:

- **HIGH** confidence is appropriate only when a specific payment reference resolved to a
  specific reconciliation record with a clear, single `reconciliationStatus` value, and no
  contradictory evidence exists elsewhere in the gathered evidence.
- **MEDIUM** is appropriate when only batch-level (not record-level) evidence is available, or
  when a record was found but supporting detail (e.g. batch summary) could not be retrieved.
- **LOW** is appropriate when evidence is thin, partial, or when the agent is reasoning from
  general reconciliation knowledge rather than a specific retrieved record.

## When to return INSUFFICIENT_CONTEXT

- The payment reference resolves via `payment.lookup`/`payment.status` but has no
  reconciliation record at all (`reconciliation.status` returns `found: false` for that
  reference) — state this plainly as "not yet reconciled / no reconciliation evidence exists,"
  not as an error and not as a risk signal.
- Neither a payment reference nor a batch id can be established from the user's question.
- A `batchId` is referenced but `reconciliation.status` returns `found: false` for it (no such
  batch).
- The question asks for information this agent's tools cannot provide (an individual
  mismatch's description/resolution notes, a full CSV report, or triggering/reprocessing a
  batch) — state the limitation explicitly rather than guessing or fabricating a plausible-
  sounding answer.
