---
documentType: RISK_REFERENCE
service: paymentx-validation-service
severity: N/A
source: paymentx-validation-service
version: "1.0"
---

# PaymentX Participant and Blacklist Risk

**PaymentX Fraud/Risk knowledge document.** Ground truth extracted directly from source code (Phase 4.5.0 discovery, re-verified). This document contains the most important operational distinction in this entire corpus: **validation-time evidence versus agent-queryable evidence are not the same thing.**

## Where blacklist checking is implemented

- **Service**: `paymentx-validation-service`.
- **Class**: `entity.BlacklistEntry`, enforced by `service.BlacklistValidationService.checkNotBlacklisted(accountNumber, bankId, role)`.
- **Table**: `blacklist`. Real columns: `account_number`, `bank_id` (unique together), `reason`, `blacklisted_at`, `blacklisted_by`.

## What it checks

A static, manually curated exclusion list — checked against the debtor and creditor account of a submitted payment during validation. There is no scoring, no pattern matching, and no automatic population from behavior — every entry was placed there by a human operator (`blacklisted_by`), for a recorded `reason`.

## Where participant status is implemented

- **Service**: `paymentx-validation-service`.
- **Classes**: `entity.Participant` (table `participant`, field `status` → `ParticipantStatus` enum: `ACTIVE`, `SUSPENDED`, `OFFBOARDED`), `entity.ParticipantScheme` (table `participant_scheme`, per-participant per-scheme `active` boolean certification).
- **Enforced by**: `service.ParticipantValidationService.validateParticipant(bankId, scheme, role)`.

## What happens when matched / when a participant is ineligible

Validation rejects the payment before it is ever created as a `Payment` record — a blacklist match or a participant-status/certification failure prevents payment-service from ever seeing the transaction at all.

## The critical distinction: validation-time evidence vs. agent-queryable evidence

**There is currently NO `participant.lookup` MCP tool.** Confirmed absent by Phase 4.5.0's discovery (and unchanged since the original Phase 3.7 MCP tool catalog, which explicitly documented `participant.lookup` as `NOT AVAILABLE`). Neither `blacklist` nor `participant`/`participant_scheme` has a query endpoint reachable by any REST API or any MCP tool.

**A future agent must never write or imply that it can currently query participant blacklist/status through MCP.** This must be stated explicitly, every time this topic comes up, not left implicit.

The practical consequence: because a blacklisted-account or ineligible-participant payment is rejected *before* a `Payment` row is ever created, `payment.lookup` on that reference will typically return `found: false` — indistinguishable, from the agent's evidence alone, from a reference that was simply never submitted at all. The *reason* for the rejection (blacklist match vs. participant status vs. any other validation failure) is visible only inside validation-service's own synchronous response at submission time — which no tool captures or persists in a way any agent can retrieve after the fact, unless it happens to also be visible via an `audit.search` `VALIDATION_COMPLETED` event (not confirmed to carry this level of detail by this discovery — treat as unverified, not assumed present).

## What this signal proves — and does not prove

**Proves** (only when a rejection reason happens to be directly visible in gathered evidence): the account/bank matched a manually curated exclusion list, or the participant was ineligible for the scheme at validation time.

**Does NOT prove**: fraud on *this specific* transaction beyond the fact that an operator previously decided to block this account/participant for some recorded reason — the blacklist entry's own `reason` field, if visible, is the actual evidence; the fact of a match is not itself new fraud evidence, since the blocking decision was already made by a human, not detected by this signal.

## Possible risk interpretation

If a rejection reason citing blacklist or participant ineligibility is directly visible in gathered evidence: treat as a genuine security signal (this document's evidence hierarchy layer 2, "authoritative PaymentX validation/audit evidence") — but still report it as what it is (a pre-existing manual block being enforced), not as newly discovered fraud. If no such reason is visible and a payment simply cannot be found: report `INSUFFICIENT_CONTEXT` for that specific line of inquiry rather than guessing at blacklist/participant involvement (see `09-insufficient-evidence-and-false-positive-controls.md`).

## Limitations

- No tool can confirm or rule out blacklist/participant-status involvement for a `payment.lookup`-not-found result — this is the single largest evidentiary gap this corpus documents (see `11-fraud-data-gaps-and-limitations.md`).
- The blacklist is static and manually curated — it cannot detect anything novel, only re-enforce previously known blocks.
