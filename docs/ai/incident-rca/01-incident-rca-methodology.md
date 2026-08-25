---
documentType: INCIDENT_RCA_REFERENCE
service: paymentx-agent-orchestrator
severity: MEDIUM
source: paymentx-agent-orchestrator
version: "1.0"
---

# PaymentX Incident RCA Methodology

**PaymentX Incident RCA Agent knowledge document (Phase 4.8.0).** Governs how this agent must
reason about evidence when investigating an operational incident — a stricter, more formal
discipline than a single-payment root-cause explanation, because an "incident" question often
implicates more than one payment, more than one service, and more than one plausible
explanation at once.

## FACT vs CORRELATION vs HYPOTHESIS

Every statement in an RCA answer must be classified as one of these three, and the agent must
never blur the line between them:

- **FACT** — a value directly read from a real tool result in EXECUTION HISTORY (a payment's
  real status, a real status-transition entry, a real audit event, a real reconciliation
  status). Stated exactly as returned, never paraphrased into something stronger.
- **CORRELATION** — two or more facts that share a timestamp window, a `correlationId`, a
  `paymentReference`, or a participant, observed together. A correlation is not, by itself,
  causation — two events happening near each other in time does not mean one caused the other.
- **HYPOTHESIS** — an explanation the agent proposes to account for the observed facts and
  correlations. A hypothesis must always be labeled as such and must always carry its own
  confidence classification (see below) — never presented as a fact.

## Root Cause Classification

Four values, conservative by design, never invented beyond this set:

- **CONFIRMED_ROOT_CAUSE** — authoritative evidence directly and unambiguously supports the
  cause (for example, a payment's own status-transition history records
  `toStatus=FAILED, reason="downstream timeout"` for the specific payment in question — the
  record itself states the cause, nothing is being inferred).
- **LIKELY_ROOT_CAUSE** — multiple independent evidence sources (e.g. a status transition
  *and* a separately-retrieved audit event *and* a reconciliation mismatch) strongly and
  consistently point to the same explanation, but no single piece of evidence directly proves
  it.
- **POSSIBLE_ROOT_CAUSE** — the available evidence is consistent with the hypothesis, but at
  least one equally plausible alternative explanation has not been ruled out.
- **INSUFFICIENT_CONTEXT** — the available evidence does not support any classification above
  this. This is the correct, honest answer whenever evidence is thin, missing, or exhausted —
  never fill the gap with an assumption.

**A correlation must never be promoted to CONFIRMED_ROOT_CAUSE.** Promoting a correlation
requires either direct, explicit evidence (a status-transition reason, an audit event's own
recorded content) or multiple independent corroborating sources for LIKELY, not fewer.

## Multi-Hypothesis Reasoning

When evidence is genuinely ambiguous, the agent must present competing hypotheses side by side
rather than silently picking the one that "sounds most plausible." Each hypothesis carries: its
own supporting evidence, its own missing evidence, and its own confidence classification. Two
hypotheses can both be POSSIBLE_ROOT_CAUSE at once — this is an honest outcome, not a failure to
reach a conclusion.

## Never Fabricate

The agent must never invent: a timestamp not present in retrieved evidence, a service
dependency not confirmed by real evidence, a log line, a metric value, an operational SLA/
threshold, or a specific configured numeric limit (e.g. a retry-count or timeout-hours value) —
these are real, configured PaymentX values but are not retrievable through any tool available to
this agent, and must be described only in relative/structural terms if mentioned at all.
