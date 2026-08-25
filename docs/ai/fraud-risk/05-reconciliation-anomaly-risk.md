---
documentType: RISK_REFERENCE
service: paymentx-reconciliation-service
severity: N/A
source: paymentx-reconciliation-service
version: "1.0"
---

# PaymentX Reconciliation Anomaly Risk

**PaymentX Fraud/Risk knowledge document.** Ground truth extracted directly from source code (Phase 4.5.0 discovery, re-verified). Complements the Error Analyzer corpus's own `reconciliation-errors.md`, which covers this taxonomy from an error-investigation angle.

## Where it's implemented

- **Service**: `paymentx-reconciliation-service`.
- **Class**: `entity.ReconciliationStatus` (a real 10-value enum), carried on `entity.MismatchRecord`. Real columns on `mismatch_record`: `mismatchType`, `description`, `resolved`, `resolvedBy`, `resolvedAt`, `resolutionNotes`.
- **MCP tool**: `reconciliation.status` — requires an already-known `batchId`; there is no discovery path from a payment reference to its batch through any existing tool.

## The real 10-value taxonomy

| Value | Meaning | Authoritative source | Possible operational cause | Possible risk interpretation | Limitations |
|---|---|---|---|---|---|
| `MATCHED` | Internal record and settlement-file record agree | Matching engine, real comparison | Normal, expected outcome | None — no risk signal | N/A |
| `MISSING` | Expected settlement record absent from the file | Matching engine | Settlement delay, file omission, timing | Weak — usually operational | Could indicate a payment that never actually settled; needs corroboration |
| `DUPLICATE` | More than one settlement record for the same internal payment | Matching engine | File-generation error, duplicate submission upstream | **Fraud-adjacent** — worth flagging as a risk indicator | Still no severity/confidence field; human resolution required to determine cause |
| `AMOUNT_MISMATCH` | Settled amount differs from internal record | Matching engine | Rounding, fee application, partial settlement, data error | Weak-to-moderate depending on magnitude (magnitude itself not queryable via any tool at a threshold level) | No tool exposes the actual mismatch delta beyond the mismatch type itself |
| `CURRENCY_MISMATCH` | Settled currency differs from internal record | Matching engine | Data/configuration error | Weak — almost always operational | N/A |
| `STATUS_MISMATCH` | Internal payment status disagrees with settlement file's implied status | Matching engine | Timing/synchronization lag | Weak — usually operational | N/A |
| `SETTLEMENT_DELAY` | Settlement occurred later than expected | Matching engine | Downstream/participant processing delay | Weak — operational | N/A |
| `LATE_SETTLEMENT` | Distinct from `SETTLEMENT_DELAY` per the real enum (both values exist separately in source) | Matching engine | Operational | Weak | Exact distinction between this and `SETTLEMENT_DELAY` is not documented anywhere this corpus could verify — report both honestly as separate values, do not assume they are synonyms |
| `ORPHAN` | A settlement-file record with no matching internal payment | Matching engine | File error, or a payment processed outside PaymentX's own recorded flow | Moderate — worth investigating | Could reflect a data-integrity issue as easily as anything risk-relevant |
| `UNEXPECTED_SETTLEMENT` | A settlement occurred that PaymentX did not expect | Matching engine | Data error, or genuinely unexpected external activity | **Fraud-adjacent** — worth flagging as a risk indicator | Same limitation as `DUPLICATE` — no severity/confidence field, human resolution required |

## A reconciliation mismatch is NOT automatically fraud

Every value above has a plausible, and typically far more likely, **operational** explanation (timing, data format, rounding, configuration) than a fraud explanation. `DUPLICATE` and `UNEXPECTED_SETTLEMENT` are the two most fraud-adjacent values in this taxonomy, but even these require human adjudication in PaymentX's own real workflow (`mismatch_record.resolved`/`resolvedBy`/`resolutionNotes`) — the system itself does not auto-classify any mismatch as fraud, and neither should an agent.

## Possible risk interpretation

- `DUPLICATE` or `UNEXPECTED_SETTLEMENT` alone: **LOW-to-MEDIUM**, worth surfacing as a risk indicator requiring investigation, never as confirmed fraud.
- Any other mismatch type alone: **LOW**, most plausibly operational.
- A mismatch co-occurring with another independent signal for the same participant (e.g., an elevated failure rate): may justify escalating per `08-risk-scoring-and-confidence-rules.md`.

## Limitations

- `reconciliation.status` requires a `batchId` the agent has no tool to discover starting from a payment reference — a limitation already documented identically for Error Analyzer (Phase 4.2.0) and Database Analysis Agent (Phase 4.4).
- No severity or confidence field exists on `MismatchRecord` — every mismatch is a flat, binary "does not match," regardless of how consequential.
- Resolution (`resolved`/`resolvedBy`/`resolutionNotes`) is a human workflow; an agent reading a resolved mismatch record should treat the `resolutionNotes` field, if present in gathered evidence, as authoritative context about the actual cause — never assume unresolved means unexplained-and-therefore-suspicious.
