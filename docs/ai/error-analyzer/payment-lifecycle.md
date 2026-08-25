---
documentType: PAYMENT_LIFECYCLE
service: paymentx-payment-service
severity: N/A
source: paymentx-payment-service
version: "1.0"
---

# PaymentX Payment Lifecycle

**PaymentX Error Analyzer knowledge document.** Ground truth from source; no invented states or transitions.

## The full status enum

`PaymentStatus` (`paymentx-payment-service/src/main/java/com/paymentx/payment/entity/PaymentStatus.java:30-49`) — the authoritative, complete set of values a `Payment.status` column can hold:

```
RECEIVED, VALIDATED, PROCESSING, ROUTING, DEBITING, DEBIT_SUCCESS, DEBIT_FAILED,
CREDITING, CREDIT_SUCCESS, CREDIT_FAILED, SETTLING, SETTLED, RETURNED, REVERSED,
FAILED, CANCELLED, TIMEOUT, RETRYING
```

18 values total. Terminal (no further transition expected in the normal flow): `SETTLED, RETURNED, REVERSED, FAILED, CANCELLED`. `TIMEOUT` and `RETRYING` are recoverable/intermediate states, not necessarily final (see `timeout-retry.md`).

## Where a payment is created and how it moves

- `Payment` entity: `paymentx-payment-service/src/main/java/com/paymentx/payment/entity/Payment.java:36-99` — key columns: `status, failure_reason` (512 chars), `scheme` (see `payment-schemes.md`), `trace_id, correlation_id`.
- The engine driving transitions is `PaymentEngineImpl` (`service/impl/PaymentEngineImpl.java`) — it consumes validated-payment events (see below) and drives the payment through `PROCESSING → ROUTING → DEBITING/CREDITING → SETTLING → SETTLED`, or to a failure branch, via `PaymentProcessor` (`service/PaymentProcessor.java`).
- `PaymentProcessor.ProcessingResult` (`service/PaymentProcessor.java:60-66`) is a record — `success, retryable, reason` — the per-step outcome of a processing attempt. `retryable` is a real, explicit boolean set by the processor itself per failure, not inferred.
- On failure, `PaymentEngineImpl.handleFailure` (`service/impl/PaymentEngineImpl.java:282-287`) sets `payment.failureReason` from `ProcessingResult.reason()`.

## Every transition is recorded

`payment_status_history` table, entity `PaymentStatusHistory` — one row per real state change: `from_status, to_status, reason, transitioned_at`. Example real writer: `TimeoutScheduler.markTimedOut` (`scheduler/TimeoutScheduler.java:99-107`), which writes a `PROCESSING → TIMEOUT` row when a stuck payment is detected.

**Important limitation, verified structurally, not assumed:** no REST endpoint in `PaymentController` exposes `payment_status_history`. A `PaymentHistoryResponse` DTO exists in the codebase but is not referenced by any controller — dead code, not a usable API. The only place a failure's *current* state is queryable is `GET /api/v1/payments/{reference}` (full snapshot, includes `failureReason`) — the *history* of how it got there is not retrievable via any existing API, MCP tool, or otherwise, only via direct database access.

## Retry as part of the lifecycle

`PaymentRetry` entity tracks `current_retry, max_retry, retry_reason, status(RetryStatus), next_retry_time`. `RetryStatus` (`entity/RetryStatus.java:15-20`): `SCHEDULED, IN_PROGRESS, SUCCEEDED, EXHAUSTED`. A retry does not happen inline within the original request — `POST /api/v1/payments/{reference}/retry` seeds a `PaymentRetry` row that a scheduler later picks up (see `timeout-retry.md`).

## Kafka events published as a payment moves through the lifecycle

`constant/KafkaTopics.java:37-45` — real topic names, one per major transition:

```
payment.processing, payment.debited, payment.credited, payment.completed,
payment.failed, payment.returned, payment.reversed, payment.cancelled, payment.timeout
```

Consumed input: `instant-payment-validated`, `card-payment-validated`, `real-time-payment-validated` (published by Validation Service, one topic per scheme — see `payment-schemes.md`/`validation-errors.md`), via `PaymentValidatedConsumer`.

## Query surface (MCP-relevant)

- `GET /api/v1/payments/{reference}` — full snapshot, `failureReason` included. Wrapped by MCP tool `payment.lookup`.
- `GET /api/v1/payments/{reference}/status` — lightweight, **no `failureReason`** (`dto/PaymentStatusResponse.java:24-29` has only `paymentReference, status, updatedAt`). Wrapped by MCP tool `payment.status`.
- `GET /api/v1/payments` (list/filter), `GET /api/v1/payments/search` (multi-criteria) — not MCP-exposed.
