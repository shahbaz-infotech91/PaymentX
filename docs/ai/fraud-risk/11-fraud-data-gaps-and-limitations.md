---
documentType: RISK_REFERENCE
service: platform
severity: N/A
source: PAYMENTX_PHASE_4_5_0_FRAUD_DETECTION_DISCOVERY.md
version: "1.0"
---

# PaymentX Fraud Data Gaps and Limitations

**PaymentX Fraud/Risk knowledge document.** This document is mandatory reading context for any future PaymentX Fraud/Risk agent — it lists everything Phase 4.5.0's discovery **confirmed absent**, by exhaustive source search, not merely unexamined. Treat this list as binding: a future agent must never imply, assume, or reason as if any of these capabilities exist.

## Confirmed missing capabilities

| Missing capability | Confirmed by |
|---|---|
| No ML fraud model | Full-repo search for "ML," "machine learning" in source code — zero real hits anywhere |
| No fraud labels | No table, field, or event type anywhere records a "confirmed fraud," "disputed," or "chargeback" outcome |
| No fraud scoring engine | No numeric scoring, weighting, or point system exists anywhere in source |
| No persisted IP data | `api-gateway`'s `RequestLoggingGlobalFilter` captures `getRemoteAddress()` only for a `log.info(...)` line — never written to any database table or `audit_event` row |
| No persisted device data | No device-fingerprint field or table exists anywhere in the repo |
| No persisted location data | No geolocation field or table exists anywhere in the repo |
| No failed-login history | `auth-service`'s real `AuthController.login()` throws an identical generic `UnauthorizedException` for every failure case (unknown user, wrong password, disabled account); no failed-attempt counter, lockout mechanism, or rate-limit-by-identity exists |
| No `participant.lookup` MCP tool | Confirmed absent since the original Phase 3.7 MCP tool catalog (`NOT AVAILABLE`), re-confirmed unchanged by Phase 4.5.0 — see `06-participant-and-blacklist-risk.md` for the full consequence of this gap |
| No production `SECURITY_EVENT` emission | Every real construction of `EventType.SECURITY_EVENT` found by full-repo grep is in test code only — see `07-audit-frequency-and-behavioral-signals.md` |

## No external fraud claims

A future agent, and this corpus, must **never** import or assert generic payments-industry fraud concepts as if they were PaymentX-verified facts, including but not limited to:

- **Velocity thresholds** (e.g., "more than N transactions per hour is suspicious") — no PaymentX rule defines any such threshold (see `07-audit-frequency-and-behavioral-signals.md`).
- **Fraud scores** (e.g., a 0–100 risk score) — no scoring mechanism exists (this document, above).
- **Chargeback ratios** — PaymentX has no chargeback concept anywhere in its schema; not applicable to this platform's real data model.
- **Device risk** — no device data is persisted (this document, above).
- **IP reputation** — no IP data is persisted (this document, above).
- **Geolocation risk** — no location data is persisted (this document, above).
- **Behavioral biometrics** — no such capability exists or is referenced anywhere in PaymentX.

If a user's question requires any of the above to answer meaningfully, the correct, honest response is that this capability does not currently exist in PaymentX — not a generic industry-standard answer presented as if it described PaymentX's actual behavior. This is not merely a preference; it is required by the evidence hierarchy in `01-fraud-risk-overview.md` (layer 5, general model knowledge, must never be conflated with layers 1-3, and must be explicitly labeled as generic context if used at all).

## Why these gaps matter for risk classification

Every gap above directly limits what a future agent can honestly conclude:

- Without IP/device/location data, no request-origin risk signal is possible.
- Without failed-login history, no credential-stuffing or brute-force signal is possible.
- Without `participant.lookup`, blacklist/participant-status involvement in a "payment not found" case can never be confirmed or ruled out — the honest answer is `INSUFFICIENT_CONTEXT`, not a guess in either direction.
- Without fraud labels, this agent's own conclusions can never be validated against ground truth — its accuracy is fundamentally unmeasurable with today's data, a limitation any future implementation-planning document should state honestly rather than assume away.

These gaps are not this corpus's failure to document a capability — they are Phase 4.5.0's confirmed finding about PaymentX's actual current state, current as of this document's version. A future phase that closes any of these gaps (e.g., by building a `participant.lookup` tool) should update this document accordingly, following the same source-of-truth discipline used to write it.
