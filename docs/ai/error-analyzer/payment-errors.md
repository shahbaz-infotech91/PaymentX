---
documentType: ERROR_CODE_REFERENCE
service: paymentx-payment-service
severity: HIGH
source: paymentx-payment-service
version: "1.0"
---

# PaymentX Payment Processing Errors

**PaymentX Error Analyzer knowledge document.** Ground truth from source.

## The mechanism, not a fixed error-code list

Unlike Validation Service, Payment Service does **not** have a small set of named exception classes for processing failures. Failure is expressed through `PaymentProcessor.ProcessingResult` (`service/PaymentProcessor.java:60-66`), a record with three fields:

- `success` (boolean)
- `retryable` (boolean) — **a real, per-failure, explicit signal set by the processor itself**, not inferred after the fact
- `reason` (free text) — becomes `Payment.failureReason`

`PaymentEngineImpl.handleFailure` (`service/impl/PaymentEngineImpl.java:282-287`) is where a non-`success` `ProcessingResult` is turned into a persisted failure: `payment.setFailureReason(result.reason())`, and the payment's `status` moves to one of the failure-shaped `PaymentStatus` values (`DEBIT_FAILED, CREDIT_FAILED, FAILED, TIMEOUT` — see `payment-lifecycle.md` for the full enum).

**This means `failureReason` is free text, not a closed error-code enum, for this specific service.** An Error Analyzer must treat the *text* of `failureReason` as the evidence, not expect it to match a fixed code list the way `ValidationStatus`/`ReconciliationStatus` do. NOT DEFINED IN CURRENT IMPLEMENTATION: a formal, closed error-code taxonomy for payment-processing failures specifically (as opposed to validation/reconciliation, which do have one).

## Timeout as a specific, structured case

`TimeoutScheduler.detectStuckPayments` (`scheduler/TimeoutScheduler.java:64-90`) is the one processing failure with dedicated, purpose-built handling:

- Scans for payments stuck in `PROCESSING` past a configured threshold.
- `TimeoutScheduler.markTimedOut` (lines 99-107) sets `status=TIMEOUT`, writes a `payment_status_history` row, writes a local `payment_audit` row with type `PAYMENT_TIMEOUT_DETECTED` (lines 112-119), and publishes a `PaymentTimeoutEvent`.
- **Retryability**: `TIMEOUT` is a distinct `PaymentStatus` value, separate from `FAILED` — its presence in the enum alongside `RETRYING` suggests it is intended as recoverable, but no explicit `retryable` flag is attached to the `TIMEOUT` status itself the way `ProcessingResult.retryable` is attached to an individual processing attempt. Treat this as **PARTIAL**: the platform's design intent (a distinct, non-`FAILED` status) suggests recoverability, but there is no single boolean field confirming it — NOT DEFINED IN CURRENT IMPLEMENTATION as an explicit flag on `TIMEOUT` itself.

See `timeout-retry.md` for the full retry mechanism.

## Where evidence is actually queryable

- `GET /api/v1/payments/{reference}` (`payment.lookup` MCP tool) — current `status` + current `failureReason` (single value, not history).
- `payment_status_history`, the local `payment_audit` table — **not exposed by any endpoint or MCP tool** (confirmed, `payment-lifecycle.md`). The richest failure-transition record in the platform is not currently investigable by an agent.
- `audit.search` (central Audit Service, `PAYMENT_FAILED` event type) — the only remaining avenue for historical context beyond the single current `failureReason`.

## Local audit event type observed in source

`PAYMENT_TIMEOUT_DETECTED` — written to Payment Service's own local `payment_audit` table by `TimeoutScheduler` (line 112-119). **This is distinct from, and not automatically the same as, the central Audit Service's `EventType.PAYMENT_FAILED`/other values** (see `audit.md`) — NOT DEFINED IN CURRENT IMPLEMENTATION whether `PAYMENT_TIMEOUT_DETECTED` is also forwarded to the central Audit Service as a Kafka-consumed event; not confirmed in this pass.
