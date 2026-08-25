# PaymentX Phase 4.5.1 — Fraud/Risk Knowledge Corpus (Master Review)

**Status:** 11 corpus documents created under `docs/ai/fraud-risk/`. Not yet ingested (per explicit stop condition). No source, configuration, or database change. No MCP tool created. No agent implemented.

---

## 1. Corpus Objective

Close the RAG knowledge gap Phase 4.5.0's discovery identified as the primary blocker to a future Fraud Detection Agent (classified B — foundation ready, knowledge required): zero fraud/risk-specific knowledge existed anywhere in the PaymentX RAG corpus. This corpus provides a reviewable, source-grounded knowledge base a future agent can retrieve from — explaining what real signals exist, what each proves and does not prove, how to combine them conservatively, and what remains missing — without implementing the agent itself.

---

## 2. Source-of-Truth Rules

Every PaymentX-specific claim in this corpus traces to one of: Phase 4.5.0's own source-verified discovery findings (`PAYMENTX_PHASE_4_5_0_FRAUD_DETECTION_DISCOVERY.md`, itself built from two independent, source-cited research passes this session), direct re-verification against actual class/table/field names during this phase (e.g., `AuditSearchTool`'s real filter arguments, re-read in full before writing `07-audit-frequency-and-behavioral-signals.md`), or the existing, already-verified Error Analyzer corpus (`docs/ai/error-analyzer/`) where a mechanism overlaps (idempotency, reconciliation, payment schemes). No statement in this corpus relies on generic payments-industry knowledge presented as PaymentX fact — every such temptation is instead redirected to `11-fraud-data-gaps-and-limitations.md`'s explicit "no external fraud claims" section.

---

## 3. Signal Inventory

The same 10 real signals Phase 4.5.0 discovered, each now given its own dedicated corpus treatment (what it is, where implemented, what it proves/does not prove, limitations):

| # | Signal | Corpus document |
|---|---|---|
| 1 | Static blacklist | `06-participant-and-blacklist-risk.md` |
| 2 | Business amount-limit rules | `02-amount-and-transaction-limits.md` |
| 3 | Idempotency / duplicate-reference rejection | `03-duplicate-and-idempotency-risk.md` |
| 4 | Payment retry bookkeeping | `04-retry-and-repeated-failure-risk.md` |
| 5 | Reconciliation mismatch taxonomy (10 real values) | `05-reconciliation-anomaly-risk.md` |
| 6 | Dormant `SECURITY_EVENT` audit type | `07-audit-frequency-and-behavioral-signals.md` |
| 7 | `audit.search` participantId filtering | `07-audit-frequency-and-behavioral-signals.md` |
| 8 | `audit.search` date-range filtering | `07-audit-frequency-and-behavioral-signals.md` |
| 9 | Participant/payment-related audit evidence | `06-participant-and-blacklist-risk.md`, `07-audit-frequency-and-behavioral-signals.md` |
| 10 | Existing validation/security outcomes, where directly visible in evidence | `06-participant-and-blacklist-risk.md` |

Every occurrence is explicitly labeled a **risk indicator**, never proof of fraud, per `01-fraud-risk-overview.md`'s foundational four-way distinction (fraud / risk indicator / operational anomaly / business validation).

---

## 4. Risk Terminology

Defined once, authoritatively, in `01-fraud-risk-overview.md`, and referenced (never redefined inconsistently) by every other document:

- **Fraud** — confirmed fraudulent activity. Does not exist as a recorded concept anywhere in PaymentX today.
- **Risk indicator** — a signal that may warrant investigation, without proving wrongdoing.
- **Operational anomaly** — unexpected behavior with a plausible non-fraud explanation.
- **Business validation** — a rule violation reflecting policy/eligibility, not intent.

The required agent output framing is **`POTENTIAL_RISK`**, never `FRAUD_CONFIRMED`, enforced consistently across all 11 documents (verified: `grep` confirms "fraud detected"/"fraud confirmed"/"is fraudulent" appear only inside explicit prohibition sentences in `08` and `09`, never as endorsed phrasing).

---

## 5. Evidence Hierarchy

Defined in `01-fraud-risk-overview.md`, five layers, higher always overriding lower: (1) authoritative runtime/database evidence, (2) authoritative PaymentX validation/audit evidence, (3) curated PaymentX RAG knowledge (this corpus), (4) LLM interpretation, (5) general model knowledge (must be explicitly labeled generic if used at all, never presented as PaymentX fact).

---

## 6. Confidence Model

Qualitative only (`08-risk-scoring-and-confidence-rules.md`) — HIGH / MEDIUM / LOW / INSUFFICIENT_CONTEXT, matching the identical rubric every existing PaymentX agent (Error Analyzer, Knowledge Assistant, Database Analysis Agent) already uses. No numeric percentage or statistical probability appears anywhere in the corpus as an endorsed output — the only two numeric-confidence mentions in the entire corpus (`85% confidence`, `3x more likely`) are inside `08`'s own explicit prohibition sentence, verified by direct grep.

---

## 7. Risk Model

LOW / MEDIUM / HIGH / INSUFFICIENT_CONTEXT (`08-risk-scoring-and-confidence-rules.md`). HIGH is explicitly and repeatedly defined as "high risk / requires investigation," never "fraud confirmed" — this exact distinction is stated three separate times across the corpus (`01`, `08`, `09`) to make it structurally hard for a future prompt to drift from it.

---

## 8. False-Positive Controls

`09-insufficient-evidence-and-false-positive-controls.md` enumerates all 7 forbidden automatic conclusions the task specified (failed payment, duplicate, retry, blacklist validation, amount limit, reconciliation mismatch, high frequency — none of these alone equal fraud), each cross-referenced to its own signal document, plus the full `INSUFFICIENT_CONTEXT` trigger list (7 conditions) and the required preferred phrasing ("potential risk indicator... may warrant further investigation" over any "fraud detected" framing).

---

## 9. Scheme Handling

`10-payment-scheme-risk-context.md` confirms — by direct re-verification of every signal document's own implementation — that **no scheme-specific fraud/risk mechanism exists anywhere in PaymentX**; every risk-adjacent mechanism (amount limits, idempotency, retry, reconciliation, blacklist, audit search) is scheme-independent in its actual code. Exactly the 3 real schemes (`INSTANT_PAYMENT`, `REAL_TIME_PAYMENT`, `CARD_PAYMENT`) are used throughout the corpus; forbidden scheme names (ACH, FedNow, TCH, SEPA, SWIFT, RTP) appear exactly once, inside `10`'s own explicit negation sentence — verified by direct grep, with one additional false-positive regex match ("separately" containing the substring "sepa") confirmed harmless by manual inspection.

---

## 10. Data Gaps

`11-fraud-data-gaps-and-limitations.md` — the mandatory, explicit list: no ML fraud model, no fraud labels, no fraud scoring engine, no persisted IP/device/location data, no failed-login history, no `participant.lookup` MCP tool, no production `SECURITY_EVENT` emission. Also carries the "no external fraud claims" section (velocity thresholds, fraud scores, chargeback ratios, device/IP/geolocation risk, behavioral biometrics — all explicitly named as not-PaymentX-verified).

---

## 11. Security Rules

`01-fraud-risk-overview.md`'s security guidance section states explicitly that retrieved corpus content can never grant MCP permissions, authorize a tool, change agent identity, modify database state, or override `AgentToolPolicy`/`ToolAuthorizationService` — the actual boundary remains entirely code-enforced, unchanged by this corpus, matching every existing PaymentX agent's real security model.

---

## 12. Corpus File Index

All under `C:\PaymentX\docs\ai\fraud-risk\`:

| File | Word count |
|---|---|
| `01-fraud-risk-overview.md` | 676 |
| `02-amount-and-transaction-limits.md` | 595 |
| `03-duplicate-and-idempotency-risk.md` | 437 |
| `04-retry-and-repeated-failure-risk.md` | 459 |
| `05-reconciliation-anomaly-risk.md` | 695 |
| `06-participant-and-blacklist-risk.md` | 643 |
| `07-audit-frequency-and-behavioral-signals.md` | 682 |
| `08-risk-scoring-and-confidence-rules.md` | 688 |
| `09-insufficient-evidence-and-false-positive-controls.md` | 565 |
| `10-payment-scheme-risk-context.md` | 499 |
| `11-fraud-data-gaps-and-limitations.md` | 663 |
| **Total** | **6,602 words across 11 documents** |

All 11 documents share a consistent `documentType: RISK_REFERENCE` (a new, but consistently-applied, value for the existing free-text `documentType` metadata field — no new metadata *key* was introduced, matching the existing corpus's own established convention of reusing `documentType`/`service`/`severity`/`source`/`version` exactly as `docs/ai/error-analyzer/*.md` already does).

---

## 13. Validation Results

- **Security scan**: PASS — zero matches for password/API-key/JWT/private-key/connection-string patterns and zero PII-shaped patterns (email, SSN, credit-card-shaped) across all 11 files.
- **Payment schemes**: exactly 3 real schemes used (`INSTANT_PAYMENT`/`REAL_TIME_PAYMENT`/`CARD_PAYMENT`); forbidden scheme names appear only inside one explicit negation sentence, verified by direct grep and manual review of the one ambiguous regex hit.
- **Unsupported fraud claims**: 0 — every "fraud detected"/"fraud confirmed"/"is fraudulent" occurrence is inside an explicit prohibition, never endorsed phrasing.
- **Invented thresholds**: 0 — the only numeric-threshold-shaped text in the corpus is inside two explicit "do not invent" prohibition sentences (`07`, `08`).
- **Invented MCP capabilities**: 0 — every mention of `participant.lookup` explicitly states it does not exist; no other MCP tool name appears in the corpus that doesn't map to a real, currently-registered tool.
- **Corpus quality review** (per task §24, all 11 documents individually re-checked): every PaymentX-specific claim traces to a cited source; no unsupported fraud claim; no unsupported scheme; no invented threshold; no invented MCP capability; limitations documented in every signal document; confidence/risk-level guidance consistent across `01`, `08`, `09`; terminology (fraud/risk indicator/operational anomaly/business validation, `POTENTIAL_RISK`, `INSUFFICIENT_CONTEXT`) consistent throughout.

---

## Final Report

- Corpus documents created: **11**
- Master document: **CREATED**
- Source files modified: **0**
- Configuration files modified: **0**
- Database changed: **NO**
- MCP tools modified: **NO**
- MCP writes: **0**
- Payments created: **0**
- Fraud Agent implemented: **NO**
- Security scan: **PASS**
- Payment schemes: **EXACTLY 3**
- Unsupported fraud claims: **0**
- Invented thresholds: **0**
- Invented MCP capabilities: **0**
- Corpus ready for review: **YES**

---

**STOP — corpus created and validated. Not ingested. RAG/Embedding/Vector services not started. Fraud Detection Agent not implemented. `participant.lookup` not created. No MCP, database, source, or configuration change. No commit. No push. Waiting for explicit approval before Phase 4.5.2.**
