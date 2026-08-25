---
documentType: RISK_REFERENCE
service: paymentx-validation-service
severity: N/A
source: paymentx-validation-service
version: "1.0"
---

# PaymentX Duplicate and Idempotency Risk

**PaymentX Fraud/Risk knowledge document.** Ground truth extracted directly from source code (Phase 4.5.0 discovery, re-verified). Same underlying mechanism the Error Analyzer corpus's own `idempotency.md` already documents from an error-taxonomy angle — this document covers the identical mechanism from a risk-interpretation angle.

## Where it's implemented

- **Service**: `paymentx-validation-service`.
- **Class**: `entity.IdempotencyRecord`, enforced by `service.IdempotencyService`.
- **Table**: `idempotency_record`. Real columns: `payment_reference` (`unique=true`, length 128), `trace_id`, `scheme`, `result_status`, `first_seen_at`.

## What it checks

A `UNIQUE` database constraint on `payment_reference`. When a payment reference is submitted a second time, the duplicate-detection mechanism rejects it — enforced at the database level, not by application-layer pattern matching. This is scheme-independent: the same mechanism applies identically to `INSTANT_PAYMENT`, `REAL_TIME_PAYMENT`, and `CARD_PAYMENT`.

## What this signal proves — and does not prove

**Proves**: this exact reference string was already submitted once before.

**Does NOT prove**: fraud, malicious replay, or even that anything unusual happened. `IdempotencyRecord` carries no actor-identity field, no attempt-count, and no distinction mechanism between a legitimate client retry, a network-level retry, an accidental double-submission by a real user, and a deliberate malicious replay — all four produce byte-identical evidence: one row, one rejection.

## Legitimate explanations for a duplicate reference

- **Client retry** — a caller's own retry logic resubmitting after a timeout or an ambiguous response.
- **Network retry** — a request retried by an intermediate layer (load balancer, proxy) without the caller's knowledge.
- **Legitimate repeated operation** — a caller intentionally reusing a reference for idempotent-by-design purposes.
- **Accidental duplicate** — human error (e.g., double-clicking a submit action).
- **Potentially suspicious behavior** — a deliberate replay attempt.

All five are indistinguishable from the `idempotency_record` evidence alone.

## Therefore: duplicate != fraud

A duplicate-reference rejection, standing alone, must never be reported as fraud or even as a meaningful risk indicator above **LOW**. It only becomes worth escalating if it co-occurs with other independent signals (see `08-risk-scoring-and-confidence-rules.md`) — e.g., multiple duplicate attempts across different references for the same participant in a short window, which would need to be observed via `audit.search`'s participant+date filtering (see `07-audit-frequency-and-behavioral-signals.md`), not via `idempotency_record` directly (no MCP tool queries this table).

## Limitations

- No actor-identity, IP, or device association exists on `idempotency_record` (confirmed absent platform-wide — see `11-fraud-data-gaps-and-limitations.md`).
- No attempt-count field exists — a second submission and a hundredth submission of the same reference are recorded identically (one rejection each), so raw repetition volume for one specific reference cannot be measured from this table alone.
- No MCP tool queries `idempotency_record` directly; any duplicate-related evidence an agent can actually gather comes indirectly, if at all, through `audit.search`'s `VALIDATION_COMPLETED` event trail.
