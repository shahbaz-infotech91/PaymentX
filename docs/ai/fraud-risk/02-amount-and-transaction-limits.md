---
documentType: RISK_REFERENCE
service: paymentx-validation-service
severity: N/A
source: paymentx-validation-service
version: "1.0"
---

# PaymentX Amount and Transaction Limit Risk

**PaymentX Fraud/Risk knowledge document.** Ground truth extracted directly from source code (Phase 4.5.0 discovery, re-verified). Covers the only amount-related check that exists anywhere in PaymentX today.

## Where the rule exists

- **Service**: `paymentx-validation-service`.
- **Class**: `entity.BusinessRule`, enforced by `service.BusinessRuleValidationService.validateAmount(scheme, amount)`.
- **Table**: `business_rule`. Real columns: `rule_code`, `rule_type`, `scheme`, `min_amount`, `max_amount`, `active`.
- **Only one `ruleType` is ever actually branched on in code: `AMOUNT_LIMIT`.** `BusinessRule` is a data-driven table, but its real, exercised behavior today is a single per-scheme min/max amount band — not a general-purpose fraud-rules engine, whatever other `ruleType` values might exist as unused data.

## What it checks

A submitted payment's amount is compared against the `min_amount`/`max_amount` of the active `business_rule` row(s) scoped to its scheme (or a row scoped to `ALL`, per the validation-service `Scheme` enum's own fourth value — see `payment-schemes.md` in the Error Analyzer corpus for the source-verified detail). If the amount falls outside the configured band, the payment is rejected during validation.

## Is it participant-specific or global?

**Global per scheme**, not participant-specific. `business_rule` rows are keyed by `scheme` (or `ALL`), never by `participantId` or account. There is no per-participant amount-limit override, tier, or exception mechanism anywhere in the schema.

## Is it scheme-specific?

Yes — `business_rule.scheme` scopes a rule to one of the three real PaymentX schemes, or to `ALL`. There is no scheme-specific *fraud* logic here, only a scheme-specific *amount band*, which is a business/compliance configuration, not a risk model.

## Is it a fraud rule or a business validation rule?

**Business validation rule.** It exists to enforce policy/compliance amount bands, not to detect suspicious behavior. It has no concept of "unusual for this participant," "unusual for this time of day," or any comparative/statistical dimension — it is a single static threshold check, identical in kind to a currency-format check.

## What this signal proves — and does not prove

**Proves**: the submitted amount fell outside a configured band for its scheme, causing a validation-time rejection.

**Does NOT prove**: fraud, suspicious intent, or even unusual behavior for the specific payer. A legitimately large payment (e.g., a real high-value business settlement) is rejected identically to a suspicious one — the mechanism cannot distinguish them, because it has no data about the payer's history, only the raw amount and the scheme's configured band.

## Possible risk interpretation

An amount-limit rejection is, at most, a **LOW** risk indicator in isolation — worth noting as context if it co-occurs with other signals (see `08-risk-scoring-and-confidence-rules.md`), never sufficient on its own to justify anything above LOW. If the same participant has *repeated* amount-limit rejections in a short window (observable only via `audit.search`'s `VALIDATION_COMPLETED` events, participant+date scoped — see `07-audit-frequency-and-behavioral-signals.md`), that pattern may justify escalating to MEDIUM — never HIGH from this signal category alone, since no fraud-specific scoring mechanism exists to justify it.

## Thresholds

**Do not invent or assume specific `min_amount`/`max_amount` values in this document.** The real values are stored as data in the `business_rule` table and can change at any time by design (they are configuration, not code) — an agent must always read the *actual, current* value via real evidence (the rejection reason returned at validation time, if visible in gathered evidence) rather than assume any figure from this corpus.

## Limitations

- No participant-specific or time-of-day-aware threshold exists.
- No statistical/comparative dimension exists — the check has no concept of "unusual for this account."
- The specific business rules configured (exact amounts) are data this corpus cannot enumerate, per the same finding already documented in the Error Analyzer corpus's `payment-schemes.md`.
