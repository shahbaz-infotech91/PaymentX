---
documentType: OPERATIONAL_REFERENCE
service: paymentx-payment-service
severity: MEDIUM
retryable: VARIES_PER_FAILURE
source: paymentx-payment-service
version: "1.0"
---

# PaymentX Timeout & Retry Behavior

**PaymentX Error Analyzer knowledge document.** Ground truth from source.

## Timeout detection

`TimeoutScheduler.detectStuckPayments` (`scheduler/TimeoutScheduler.java:64-90`) — a scheduled job that scans for payments stuck in `PaymentStatus.PROCESSING` past a configured threshold. On detection, `TimeoutScheduler.markTimedOut` (lines 99-107):

1. Sets `Payment.status = TIMEOUT`.
2. Writes a `payment_status_history` row (`from_status=PROCESSING, to_status=TIMEOUT, reason=..., transitioned_at=...`).
3. Writes a local `payment_audit` row of type `PAYMENT_TIMEOUT_DETECTED` (lines 112-119).
4. Publishes a `PaymentTimeoutEvent` (consumed downstream — specific consumer not re-verified in this pass).

The exact configured threshold value (how long "stuck" means) was **not read in this pass** — NOT DEFINED IN THIS DOCUMENT; would require reading the scheduler's configuration properties directly.

## Retry

`PaymentRetry` entity: `current_retry, max_retry, retry_reason, status(RetryStatus), next_retry_time`.

`RetryStatus` (`entity/RetryStatus.java:15-20`):

```
SCHEDULED, IN_PROGRESS, SUCCEEDED, EXHAUSTED
```

**Retry is not inline.** `POST /api/v1/payments/{reference}/retry` (`PaymentController`) seeds a `PaymentRetry` row — it does not itself re-execute the payment synchronously. A scheduler (not individually re-verified as a separate class name in this pass — likely paired with or adjacent to `TimeoutScheduler`; NOT CONFIRMED) picks up `SCHEDULED` rows and attempts reprocessing, eventually marking `SUCCEEDED` or, after `max_retry` attempts, `EXHAUSTED`.

## The real per-failure retryable signal

`PaymentProcessor.ProcessingResult` (`service/PaymentProcessor.java:60-66`) carries a real, explicit `retryable` boolean **set per individual processing failure**, distinct from the `PaymentRetry`/`RetryStatus` retry-scheduling mechanism above. This is the most reliable source-confirmed signal for "should this specific failure be retried":

- Validation-layer failures (`BusinessRuleViolationException`, `DuplicatePaymentException`) are **always `retryable=false`** (confirmed in both exception classes directly, see `validation-errors.md`) — retrying with the same input will always fail the same way.
- Payment-processing-layer failures use `ProcessingResult.retryable`, set per-attempt by the processor — genuinely variable (e.g. a downstream timeout would plausibly be retryable; a definitive rejection would not) — the *specific* rule for which processing failures get `retryable=true` vs `false` was **not enumerated field-by-field in this pass**; NOT DEFINED IN THIS DOCUMENT beyond confirming the field exists and is real.

## What this means for Error Analyzer's "recommended action"

A sound, evidence-backed recommendation can distinguish:
- **"Do not retry"** — when the evidence shows a validation-layer rejection (`ValidationStatus.REJECTED`/`DUPLICATE`) or any `ProcessingResult` explicitly marked `retryable=false`.
- **"May be retried"** — when a `ProcessingResult` was `retryable=true`, or the payment is in `TIMEOUT`/`RETRYING` status (states the platform's own design treats as recoverable).
- **"Unknown / insufficient evidence"** — whenever the specific `retryable` value for a given failure was not itself part of the evidence retrieved (e.g. only `payment.lookup`'s free-text `failureReason` was available, not the underlying `ProcessingResult.retryable` flag, which is not exposed by any MCP tool today — NOT DEFINED IN CURRENT IMPLEMENTATION as a queryable field via any existing tool).
