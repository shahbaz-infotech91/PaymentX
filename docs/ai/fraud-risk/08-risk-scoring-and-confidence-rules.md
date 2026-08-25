---
documentType: RISK_REFERENCE
service: platform
severity: N/A
source: PAYMENTX_PHASE_4_5_0_FRAUD_DETECTION_DISCOVERY.md
version: "1.0"
---

# PaymentX Risk Scoring and Confidence Rules

**PaymentX Fraud/Risk knowledge document.** This document is guidance for a future agent's *reasoning process* — unlike the other documents in this corpus, it does not describe an existing PaymentX mechanism, because **no scoring engine, weighting system, or numeric risk model exists anywhere in PaymentX today** (Phase 4.5.0 discovery, confirmed absent). Everything below is conservative interpretive guidance, not a description of implemented behavior — treat statements here as "how a future agent should reason," never as "what PaymentX already computes."

## Confidence model

Qualitative only — matching the same HIGH/MEDIUM/LOW/INSUFFICIENT rubric every existing PaymentX agent (Error Analyzer, Knowledge Assistant, Database Analysis Agent) already uses, never a numeric percentage or statistical probability:

| Confidence | When to use |
|---|---|
| **HIGH** | Multiple independent, authoritative signals agree, with minimal ambiguity — e.g., a directly-visible blacklist rejection reason (`06-participant-and-blacklist-risk.md`) combined with a corroborating `DUPLICATE`/`UNEXPECTED_SETTLEMENT` reconciliation finding (`05-reconciliation-anomaly-risk.md`) for the same participant. In practice, given the current signal set, HIGH confidence will be rare — most available signals are individually weak (see each document's own limitations). |
| **MEDIUM** | Meaningful evidence exists (e.g., an elevated `audit.search` failure count for one participant), but multiple plausible non-fraud explanations remain (operational instability, legitimate retry behavior). |
| **LOW** | A single weak or isolated indicator — one amount-limit rejection, one duplicate reference, one retried payment — with no corroborating pattern. |
| **INSUFFICIENT_CONTEXT** | Evidence gathered is inadequate for any meaningful assessment — see `09-insufficient-evidence-and-false-positive-controls.md` for the full trigger list. |

**Never** state a numeric confidence percentage (e.g., "85% confidence") or a statistical probability (e.g., "3x more likely than baseline") — no PaymentX mechanism computes either, and stating one would be a fabricated figure, not evidence.

## Risk level model

| Level | Meaning |
|---|---|
| **LOW** | A weak or isolated indicator present; unlikely to warrant action on its own. |
| **MEDIUM** | Multiple independent weak signals co-occur for the same participant/payment, or one moderately fraud-adjacent signal (e.g., `DUPLICATE`/`UNEXPECTED_SETTLEMENT` reconciliation) stands alone. |
| **HIGH** | **"High risk / requires investigation" — NOT "fraud confirmed."** Reserved for cases where several independent, meaningfully fraud-adjacent signals corroborate each other (e.g., blacklist-adjacent rejection evidence, elevated failure frequency, and a reconciliation anomaly, all for the same participant in the same window). Given today's signal set (Phase 4.5.0), this bar will rarely be met — that is an honest reflection of what evidence actually exists, not a flaw in this guidance. |
| **INSUFFICIENT_CONTEXT** | No relevant evidence was found, or the only evidence found cannot support any classification above this. |

**This distinction must never collapse.** HIGH risk level is a call to investigate further using human judgment and any evidence sources beyond this agent's own tools (e.g., an operator's own access to `blacklist`/`participant` data) — it is never itself a fraud determination, because PaymentX has no mechanism, today, capable of making one.

## Multi-signal reasoning

Signals should be evaluated together, never in isolation when more than one is available:

- **Single duplicate reference alone** → weak indicator (LOW at most).
- **Repeated duplicates, unusual retry activity, and a reconciliation anomaly, all for the same participant in the same window** → a stronger potential-risk pattern (MEDIUM, possibly HIGH if a blacklist-adjacent signal is also directly visible), because independent signal categories corroborating each other is meaningfully stronger evidence than any one alone.

**Do not invent a numeric scoring formula** (e.g., "+10 points per failed payment, +25 for a reconciliation mismatch") — no such system exists in PaymentX, and inventing one would misrepresent an LLM's own arithmetic as an authoritative PaymentX risk score. The reasoning must remain qualitative and must explicitly cite which real signals (with their real evidentiary weight, per each signal's own document in this corpus) support the conclusion.

## The non-negotiable rule

Regardless of how many signals corroborate each other, or how high the resulting risk level, **the agent must never state or imply "fraud confirmed" or "fraud detected."** The strongest honest statement this signal set can ever support is "high risk, requires investigation" (`POTENTIAL_RISK` at `HIGH`) — per `01-fraud-risk-overview.md`'s foundational framing, which this document does not and cannot override.
