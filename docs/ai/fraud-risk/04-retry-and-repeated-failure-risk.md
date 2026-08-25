---
documentType: RISK_REFERENCE
service: paymentx-payment-service
severity: N/A
source: paymentx-payment-service
version: "1.0"
---

# PaymentX Retry and Repeated Failure Risk

**PaymentX Fraud/Risk knowledge document.** Ground truth extracted directly from source code (Phase 4.5.0 discovery, re-verified).

## Where it's implemented

- **Service**: `paymentx-payment-service`.
- **Class**: `entity.PaymentRetry`, processed by `service.impl.RetryProcessor`.
- **Table**: `payment_retry`. Real columns: `current_retry`, `max_retry`, `retry_reason`, `status` (→ `RetryStatus`), `next_retry_time`, `last_attempted_at`.

## What information exists

One `payment_retry` row per payment that has entered a retry cycle, tracking how many of a configured maximum attempts have occurred (`current_retry`/`max_retry`), a system-recorded reason for retrying (`retry_reason`), and scheduling fields for the next attempt. This is real, per-payment, system-generated bookkeeping — not a caller-supplied signal, and not something a payer can directly manipulate.

## What repeated failures may indicate — operational explanations

The Phase 4.5.0 discovery confirmed this table records *system-side* retry behavior (retrying a failed leg of payment processing, such as a debit or credit attempt), not payer-side resubmission behavior. Plausible causes for a payment reaching a high retry count include:

- **Transient infrastructure issue** — a downstream dependency (participant bank, settlement rail) temporarily unavailable.
- **Downstream timeout** — a call to routing/settlement infrastructure exceeding its configured deadline.
- **Configuration issue** — a misconfigured routing rule or participant endpoint causing systematic failure.
- **Client retry behavior** — distinct from this table; a caller resubmitting a *new* payment reference after perceiving a failure is a duplicate/idempotency signal (`03-duplicate-and-idempotency-risk.md`), not a `payment_retry` signal.
- **Genuine suspicious activity** — theoretically possible but no PaymentX mechanism distinguishes this from the operational explanations above.

## Do not assign a fraud conclusion automatically

A payment reaching `max_retry` reflects **downstream instability or a processing problem**, not payer intent. `PaymentRetry` has no field describing *why* in a way that distinguishes "the settlement rail was down" from "something suspicious is happening" — `retry_reason` is a system-generated operational label (e.g., a timeout code), not a risk classification.

## Possible risk interpretation

At most a weak **operational anomaly** signal, not a direct risk indicator on its own. It becomes worth considering as context only when combined with other independent signals for the same payment or participant (see `08-risk-scoring-and-confidence-rules.md`) — for example, if a participant shows an elevated rate of retried payments *and* a reconciliation anomaly in the same window. Never sufficient alone to justify a risk level above LOW, and even then only as supporting context, not a primary indicator.

## Limitations

- `payment_retry` is payment-scoped, not participant-scoped — no tool aggregates retry counts across a participant's payments; an agent would need to gather multiple individual `payment.lookup`/`payment.status` results and reason across them manually, which is bounded by the same iteration/tool-call limits every agent already operates under.
- No historical baseline exists ("is this retry rate unusual for this participant") — there is no comparative or statistical mechanism anywhere in PaymentX to answer that question authoritatively.
