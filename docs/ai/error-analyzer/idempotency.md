---
documentType: OPERATIONAL_REFERENCE
service: paymentx-validation-service
severity: MEDIUM
errorCode: DUPLICATE_PAYMENT_REFERENCE
retryable: false
source: paymentx-validation-service
version: "1.0"
---

# PaymentX Idempotency Mechanism

**PaymentX Error Analyzer knowledge document.** Ground truth from source.

## The mechanism

`IdempotencyRecord` (`paymentx-validation-service/src/main/java/com/paymentx/validation/entity/IdempotencyRecord.java`) — table `idempotency_record`:

```
id, payment_reference (UNIQUE, NOT NULL, 128 chars), trace_id, scheme, result_status (ValidationStatus), first_seen_at
```

**The `UNIQUE` database constraint on `payment_reference` is the actual duplicate-detection mechanism** — not application-level logic. Confirmed directly from `IdempotencyService.claim` (`service/IdempotencyService.java:51-69`) and its own inline comment: *"The UNIQUE constraint on payment_reference is the actual duplicate-detection mechanism; this class is just a thin, correctly-scoped wrapper around it."*

## Exact flow

1. `IdempotencyService.claim(paymentReference, traceId, scheme, resultStatus)` builds an `IdempotencyRecord` and calls `idempotencyRecordRepository.saveAndFlush(record)`.
2. If the reference was never seen before, the insert succeeds — the reference is now claimed.
3. If the reference already exists, Postgres rejects the insert with a constraint violation, caught as `DataIntegrityViolationException`, and re-thrown as `DuplicatePaymentException` (see `validation-errors.md`).

## Why `Propagation.REQUIRES_NEW` specifically

Documented directly in the class's own javadoc (`IdempotencyService.java:16-31`): the caller (`ValidationService`) runs inside its own `@Transactional` method that also writes a `validation_log` row and publishes a Kafka event. If the idempotency claim ran in that *same* transaction and something later in the flow threw an unexpected exception, Spring would roll back the **entire** transaction — including the idempotency claim just made. That would un-claim the reference, and a genuine retry of the same payment would be allowed through again, defeating the entire point. `REQUIRES_NEW` forces the claim to commit in its own transaction, independent of what happens next in the caller's flow.

## Race-condition safety

The mechanism is explicitly designed to be safe under concurrent requests for the same `paymentReference` — the comment references "someone already claimed this reference (possibly a concurrent request, possibly a true retry of an earlier call)" as the two cases the `UNIQUE` constraint (not an application-level check-then-insert, which would have a race window) protects against equally.

## Scope

Idempotency is **scheme-independent** — the `scheme` column is recorded on the `IdempotencyRecord` but plays no role in the uniqueness check itself, which is purely on `payment_reference`. See `payment-schemes.md`.

## What this means for Error Analyzer

A `DuplicatePaymentException`/`DUPLICATE_PAYMENT_REFERENCE` finding is **high-confidence and definitive** — it is backed by a real database constraint, not a heuristic. The correct RCA for this case is unambiguous: the payment reference was already claimed, `retryable=false`, and the remediation is to use a different reference for a genuinely new payment (or recognize this as an expected duplicate-submission rejection, not a platform defect).
