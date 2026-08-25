---
documentType: RISK_REFERENCE
service: platform
severity: N/A
source: PAYMENTX_PHASE_4_5_0_FRAUD_DETECTION_DISCOVERY.md
version: "1.0"
---

# PaymentX Insufficient Evidence and False Positive Controls

**PaymentX Fraud/Risk knowledge document.** This is the mandatory anti-hallucination reference for any future PaymentX Fraud/Risk agent. If any other document in this corpus and this document appear to conflict, this document's restrictions govern.

## Forbidden automatic conclusions

A future agent must **never** convert any single signal, alone, into a fraud conclusion. Specifically, none of the following automatic conclusions are ever justified by PaymentX's real evidence:

| Never conclude | Because (see) |
|---|---|
| failed payment = fraud | `04-retry-and-repeated-failure-risk.md` — failures are predominantly operational |
| duplicate = fraud | `03-duplicate-and-idempotency-risk.md` — duplicates are predominantly legitimate retries |
| retry = fraud | `04-retry-and-repeated-failure-risk.md` — retries reflect downstream instability |
| blacklist validation = fraud | `06-participant-and-blacklist-risk.md` — a block is a pre-existing manual decision, not new evidence of fraud on this transaction |
| amount limit exceeded = fraud | `02-amount-and-transaction-limits.md` — a business/compliance band, not a risk model |
| reconciliation mismatch = fraud | `05-reconciliation-anomaly-risk.md` — every mismatch type has a plausible operational cause |
| high transaction frequency = fraud | `07-audit-frequency-and-behavioral-signals.md` — no baseline exists to define "high," and no threshold is defined anywhere in PaymentX |

**Instead**: report each of these as an indicator requiring contextual analysis — name the real signal, cite its real source (per its own document above), and explicitly state what it does and does not prove, exactly as each document above already models.

## When to return INSUFFICIENT_CONTEXT

A future agent must return `INSUFFICIENT_CONTEXT` (not a guess, not a LOW-confidence guess dressed up as an answer) when:

- **Only one weak signal exists** and no corroborating evidence was found or attempted.
- **Missing participant evidence** — the question requires knowing participant status/blacklist membership, and no such evidence is visible in gathered tool results (per `06-participant-and-blacklist-risk.md`'s core limitation — this will be common, not exceptional).
- **Missing historical baseline** — the question requires knowing whether something is "unusual," and no comparative baseline exists anywhere in PaymentX to establish that (per `07-audit-frequency-and-behavioral-signals.md`).
- **Insufficient audit history** — a relevant `audit.search` call returned zero or minimal results.
- **Ambiguous operational explanation** — multiple equally plausible non-fraud explanations remain and cannot be narrowed by any available tool.
- **No relevant RAG knowledge** — this corpus itself does not cover the specific question asked (e.g., a question about a signal category not documented here).
- **Conflicting evidence** — two real tool results appear to disagree, and no basis exists to resolve which is authoritative.

## The agent must not fill missing evidence with LLM assumptions

If a fact is not directly supported by a real tool result or a retrieved knowledge chunk actually shown in the agent's own execution history, it must not be stated — this is the same evidence-grounding discipline every existing PaymentX agent prompt already enforces (Error Analyzer's rule 3/5, Knowledge Assistant's rule 5, Database Analysis Agent's rule 4). This corpus adds no exception for fraud/risk questions — if anything, the bar for grounding is *higher* here, because an incorrect risk-related claim carries more real-world consequence than an incorrect documentation answer.

## Preferred phrasing

Prefer: *"A potential risk indicator was observed (name it), which may warrant further investigation, but the available evidence does not establish fraud."*

Over: *"Fraud detected"* or *"This payment is fraudulent"* — never acceptable phrasing under any evidence combination this corpus documents, per `01-fraud-risk-overview.md`'s foundational framing and `08-risk-scoring-and-confidence-rules.md`'s non-negotiable rule.
