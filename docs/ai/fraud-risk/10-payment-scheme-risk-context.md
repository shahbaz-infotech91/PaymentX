---
documentType: RISK_REFERENCE
service: platform
severity: N/A
source: paymentx-payment-service, paymentx-routing-service, paymentx-validation-service
version: "1.0"
---

# PaymentX Payment Scheme Risk Context

**PaymentX Fraud/Risk knowledge document.** Ground truth extracted directly from source code (Phase 4.5.0 discovery, re-verified, consistent with the Error Analyzer corpus's own `payment-schemes.md`).

## The three real PaymentX schemes

PaymentX supports exactly three payment schemes, confirmed across three independent service-local enum definitions: `INSTANT_PAYMENT`, `REAL_TIME_PAYMENT`, `CARD_PAYMENT`. No other scheme (ACH, FedNow, TCH, SEPA, SWIFT, RTP, or any other) exists anywhere in this codebase — do not reason about or reference any scheme not listed here, for fraud purposes or any other.

## What PaymentX source actually tells us about scheme-specific risk

**Nothing scheme-specific exists.** Every risk-adjacent mechanism documented in this corpus is scheme-independent in its actual implementation:

- **Amount limits** (`02-amount-and-transaction-limits.md`): `business_rule.scheme` scopes a *band configuration* per scheme, but the check itself is identical logic for all three — a static min/max comparison, never a scheme-aware risk model.
- **Idempotency** (`03-duplicate-and-idempotency-risk.md`): the unique-constraint mechanism is scheme-independent — confirmed by direct read of `IdempotencyService.claim`, which enforces uniqueness on `payment_reference` regardless of scheme.
- **Retry bookkeeping** (`04-retry-and-repeated-failure-risk.md`): `PaymentRetry` carries no scheme field relevant to its retry logic.
- **Reconciliation** (`05-reconciliation-anomaly-risk.md`): the matching engine's mismatch taxonomy applies identically regardless of scheme.
- **Blacklist/participant checks** (`06-participant-and-blacklist-risk.md`): the blacklist itself is account/bank-scoped, not scheme-scoped (though `participant_scheme` certification *is* scheme-specific — a participant may be certified for some schemes and not others, which is an eligibility fact, not a fraud signal).
- **Audit frequency** (`07-audit-frequency-and-behavioral-signals.md`): `audit.search` supports no scheme filter in its own tool definition — event counts cannot currently be scoped by scheme through this tool.

This matches the Phase 4.2.0/4.2.1 finding (unchanged, re-confirmed here): scheme is used purely as a filter/lookup key throughout PaymentX, never a behavioral branch — the same holds true for risk-relevant logic, which simply does not vary by scheme anywhere in the current implementation.

## What scheme-independent means for this corpus

Every risk indicator document in this corpus (02 through 07) applies identically to `INSTANT_PAYMENT`, `REAL_TIME_PAYMENT`, and `CARD_PAYMENT` — a future agent does not need, and should not attempt, separate scheme-specific reasoning for any of them.

## What scheme-specific information is missing

**Scheme-specific fraud rules are not currently implemented.** This must be stated explicitly by a future agent whenever a user asks a scheme-specific risk question (e.g., "are CARD_PAYMENT transactions more prone to fraud?") — the honest, source-grounded answer is that no PaymentX mechanism differentiates risk by scheme today, and any scheme-specific fraud characteristic an LLM might otherwise state from generic pretrained knowledge (e.g., real-world card-fraud patterns) must be explicitly labeled as generic external context, never presented as PaymentX-verified behavior, per `01-fraud-risk-overview.md`'s evidence hierarchy (layer 5, general model knowledge, must never be conflated with layers 1-3).

## Classification summary

| Scheme | Scheme-specific fraud signal? | Classification |
|---|---|---|
| `INSTANT_PAYMENT` | None — only the generic, scheme-independent mechanisms above | MISSING |
| `REAL_TIME_PAYMENT` | None — only the generic, scheme-independent mechanisms above | MISSING |
| `CARD_PAYMENT` | None — only the generic, scheme-independent mechanisms above | MISSING |
