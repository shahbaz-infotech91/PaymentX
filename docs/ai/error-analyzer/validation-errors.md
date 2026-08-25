---
documentType: ERROR_CODE_REFERENCE
service: paymentx-validation-service
severity: MEDIUM
source: paymentx-validation-service
version: "1.0"
---

# PaymentX Validation Errors

**PaymentX Error Analyzer knowledge document.** Ground truth from source.

## Validation outcome enum

`ValidationStatus` (`paymentx-validation-service/src/main/java/com/paymentx/validation/entity/ValidationStatus.java:15-19`):

```
VALIDATED, REJECTED, DUPLICATE
```

Persisted on the `validation_log` table (entity `ValidationLog`) — columns: `payment_reference, trace_id, validation_status, rejection_reason` (free text, 512 chars), `validated_at`.

`ValidationController.validate` (`controller/ValidationController.java:48-61`) returns HTTP 200 for `VALIDATED`/`REJECTED`, and **HTTP 409** specifically for `DUPLICATE` (a deliberate, distinct status code, confirmed at lines 55-58).

## Error 1: Business rule violation

| Field | Value |
|---|---|
| Error name/type | `BusinessRuleViolationException` |
| Service | paymentx-validation-service |
| Source | `exception/BusinessRuleViolationException.java:26-37` |
| Trigger | A payment fails an active `BusinessRule` row scoped to its scheme (or `ALL`) — e.g. an amount-limit or blacklist rule (the specific rules configured are data, not enumerable from the exception class itself) |
| Meaning | The payment violates a configured PaymentX business policy |
| Affected state | `ValidationStatus.REJECTED`; the payment is not created/does not proceed to Payment Service in a `VALIDATED` state |
| Retryability | **`retryable=false`** — explicitly set in the exception's own constructor (confirmed by direct read) |
| Remediation | NOT DEFINED IN CURRENT IMPLEMENTATION beyond "the payment as submitted violates policy" — no specific corrective action is encoded in source |
| Source reference | `paymentx-validation-service/src/main/java/com/paymentx/validation/exception/BusinessRuleViolationException.java` |

## Error 2: Duplicate payment reference

| Field | Value |
|---|---|
| Error name/type | `DuplicatePaymentException` |
| Error code | `DUPLICATE_PAYMENT_REFERENCE` |
| Service | paymentx-validation-service |
| Source | `exception/DuplicatePaymentException.java:17-23` |
| Trigger | A `paymentReference` that has already been claimed — enforced by a real `UNIQUE` database constraint on `idempotency_record.payment_reference`, caught as `DataIntegrityViolationException` by `IdempotencyService.claim` (`service/IdempotencyService.java:61-68`) and re-thrown as this exception |
| Meaning | This exact payment reference was already processed (or is concurrently being processed) — see `idempotency.md` for the full mechanism |
| Affected state | `ValidationStatus.DUPLICATE`; HTTP 409 |
| Retryability | **`retryable=false`** — a duplicate is not a transient failure; retrying with the same reference will always produce the same result |
| Remediation | NOT DEFINED IN CURRENT IMPLEMENTATION beyond "use a different payment reference if this was not intended to be the same payment" — no specific corrective action is encoded in source |
| Source reference | `paymentx-validation-service/src/main/java/com/paymentx/validation/exception/DuplicatePaymentException.java` |

## Kafka events published on validation outcomes

`event/KafkaTopics.java:23-26` — per-scheme "validated" topics (`instant-payment-validated`, `card-payment-validated`, `real-time-payment-validated`) plus `payment-rejected`. Publisher: `ValidationEventPublisher`. Payload types: `ValidatedPaymentPayload`, `RejectedPaymentPayload` (real event classes, confirmed present in `event/` package; field-level contents not enumerated here — NOT DEFINED IN THIS DOCUMENT, would require a further read of those two classes if needed).

## Query surface

**No REST endpoint exposes `validation_log` for later lookup.** The only place `rejection_reason` is ever visible is the synchronous `POST /api/v1/validations` response at the moment of the original request — an Error Analyzer investigating after the fact cannot query Validation Service directly; it must rely on `audit.search`'s `VALIDATION_COMPLETED` event type (see `audit.md`) or, for duplicates specifically, `payment.lookup`'s `failureReason` if a `Payment` row was ever created (NOT DEFINED IN CURRENT IMPLEMENTATION whether a rejected-at-validation payment ever gets a `Payment` row at all — validation happens before Payment Service's own lifecycle begins, per the flow in `payment-lifecycle.md`).
