# PaymentX — Frontend Defect Remediation

**Scope:** Payment Flow "Gateway: Running" stuck state (Defect #1, HIGH) and Payment Flow "Validation/Routing: Not Started" incorrect state (Defect #2, MEDIUM). Nothing outside this scope was touched.
**Backend services modified:** NO. Only `paymentx-control-center` (the Control Center's own dashboard backend/frontend) was changed — none of Payment/Routing/Validation/API Gateway/Auth/Audit/Notification/Reconciliation/Reporting/LLM/Embedding/Vector/RAG/MCP Gateway/Agent Orchestrator services were touched.
**Date:** 2026-08-20.

---

## Defect #1 — HIGH: E2E Payment Flow live-progress stuck at "Gateway: Running"

### Root cause

Root-caused via live browser/network/query-cache introspection (not guesswork):

1. `performance.getEntriesByType('resource')` on a live, reproduced stuck run showed **exactly one** GET to `/api/v1/e2e/run/{runId}` fired, then none for 4+ minutes, despite the UI still showing `overallStatus: RUNNING`.
2. Direct introspection of the TanStack Query cache (via the mounted `QueryClient`) showed the query observer was correctly present and subscribed (`observersCount: 1`), correctly configured with a function-form `refetchInterval` — but `fetchStatus: 'idle'`, stale by minutes.
3. `document.visibilityState` for the affected tab was `'hidden'`.
4. This app's global TanStack Query defaults (`src/api/queryClient.ts`) set `refetchOnWindowFocus: false`. TanStack Query v5's own default for `refetchIntervalInBackground` is also `false`. Together: whenever the tab hosting this page is not the visible/focused tab, every scheduled `refetchInterval` tick is silently skipped (`focusManager.isFocused()` gate), and nothing else ever re-triggers a fetch (window-focus-triggered refetch is explicitly off app-wide). The live view is then permanently frozen on whatever snapshot it last fetched — in the reproduced case, the very first snapshot (`Gateway: RUNNING`, `Duration: 0 ms`) — even though the real backend (`E2EFlowService`) had already reached a real terminal state in ~1.1–4 seconds.

The backend (`E2EFlowService.java`, `E2EController.java`) was independently verified correct by inspection and by checking the Run History table, which always showed the correct final terminal status (e.g. `Success`, `2.9s`) for prior runs — confirming this was purely a frontend live-view display bug, not a backend defect.

### Fix

`paymentx-control-center/frontend/src/hooks/useE2EFlow.ts` — added `refetchIntervalInBackground: true` to the `useE2ERun` query, scoped to this one query only (not the app's global defaults, since most other polls — health checks, metrics — have no such correctness requirement and background-polling them would be pure waste). This does not fake or shortcut anything: the query still fetches the real backend snapshot on the real 1.5s interval and still stops polling the instant the real backend reports a terminal state; the only change is that it no longer stops fetching just because the tab isn't the active one.

No `setTimeout`-based fake progress, no hardcoded "Completed", no backend change.

### Evidence

- Live reproduction (before fix): `fetchE2ERun` called once, then not again for 4+ minutes while `document.visibilityState === 'hidden'`; run history for that run independently confirmed the real backend had reached `SUCCESS` in seconds.
- Live re-test (after fix, new real payment `CC-E2E-1787209015843-63EFD492`): live view progressed from `Gateway: Running` → overall `Success` within the real, observed elapsed time (`Duration: 4.0 s`), matching real backend completion — no manual refresh needed.
- Automated regression test `E2EPage.test.tsx` — `root-cause regression: polling keeps reaching the real backend even while the tab is not visible/focused` — forces `document.visibilityState` to `'hidden'` for the whole test and asserts the mocked backend's terminal `SUCCESS` snapshot is still reached; this test would time out against the pre-fix code.

---

## Defect #2 — MEDIUM: Payment Flow shows "Validation/Routing: Not Started" despite real completion

### Root cause

Root-caused via direct `paymentx_audit.audit_event` queries for a real, completed payment (`CC-E2E-1787204229563-FCA0D0DA`), correlated against `PaymentFlowService.java`'s existing stage-derivation logic:

- **Validation**: `validation-service`'s real `VALIDATION_COMPLETED` audit row **does** populate `reference` (`CC-E2E-1787204229563-FCA0D0DA`), but leaves `payment_id` **blank** — because that event is recorded before the payment row exists. `PaymentFlowService` looked up audit events via `AuditEventRepository.findByPaymentId(payment.id())` only, so this row was never found → Validation displayed the honest-sounding but **factually wrong** `NOT_STARTED`, even though validation had genuinely completed.
- **Routing**: `routing-service`'s real `PAYMENT_ROUTED` audit rows populate **none** of `payment_id`, `correlation_id`, `trace_id`, or `reference` — confirmed live, for multiple real routing events across multiple real payments. There is currently **no existing data field** that lets this schema attribute a routing-service audit event to one specific payment. This is a genuine backend (Routing Service audit-emission) gap, and Routing Service is on the explicit DO NOT MODIFY list.

### Fix

Both changes are confined to `paymentx-control-center/backend` (the Control Center's own dashboard-assembly layer, not any of the prohibited core services) and use only existing, already-populated database columns — no schema change, no new backend API, no other microservice touched:

1. **Validation — real fix.** `AuditEventRepository.java`: added `findByPaymentIdOrReference(paymentId, reference)`, an OR-widened version of the existing `findByPaymentId` query (kept unchanged and still used elsewhere). `PaymentFlowService.java`: `buildFlow()` now calls the widened lookup with the payment's own real `paymentReference`. Validation's existing `serviceAuditStage()` derivation logic is otherwise unchanged — it still only reports `COMPLETED` when a real audit row is found, and still correctly reports `NOT_STARTED` when none exists (verified by a dedicated test).
2. **Routing — honest fix, not a workaround.** Because no existing field can reliably attribute a routing-service event to a specific payment, inventing a heuristic (e.g. timestamp proximity) would be guessing, not fixing — explicitly out of bounds per this task's own instructions. Instead, `PaymentFlowService.java` gained a dedicated `routingStage()` method: if a real, attributable routing-service audit row is ever found (kept as a real, live code path — see the "if this is ever fixed" test below), it reports the real `COMPLETED` state exactly as before; when none can be found, it now reports the already-existing, already-established `UNAVAILABLE` status (the same status this file already uses for Authentication/Reconciliation/Reporting) with an accurate, specific reason — replacing the previous, factually incorrect `NOT_STARTED` ("routing has not started") with an honest "this cannot currently be determined" ("routing may well have already run for this payment"). No new business state was invented; `UNAVAILABLE` already existed for exactly this purpose.

### Evidence

- Live re-test on the original payment (`CC-E2E-1787204229563-FCA0D0DA`), via direct GET to the fixed endpoint:
  - `Validation`: `COMPLETED`, detail `VALIDATION_COMPLETED` (was `NOT_STARTED`).
  - `Routing`: `UNAVAILABLE`, detail *"routing-service's audit events do not carry payment_id, correlation_id, trace_id, or reference (verified live)... This does not mean routing has not run."* (was `NOT_STARTED`).
- Live re-test on a brand-new real payment (`CC-E2E-1787209015843-63EFD492`), in-browser at `/payment-flow?reference=...`: Validation shows green **Completed** / `VALIDATION_COMPLETED`; Routing shows **Unavailable** with the same honest reason — screenshotted and cross-checked against `paymentx_audit.audit_event` directly (query in Backend Correlation section below).
- Backend unit tests (`PaymentFlowServiceTest.java`, mocked repositories, no real DB): a validation event with blank `payment_id` but real `reference` is now found and reported `COMPLETED`; a payment with genuinely no validation event still reports `NOT_STARTED` (proves the fix doesn't turn everything green); a routing event with no correlation fields at all is reported `UNAVAILABLE` with the honest reason (proves the false-positive fix, and that this stays a documented, reported defect, not a hidden one); a routing event that *does* carry a matching identifier is still reported the real `COMPLETED` (proves the fix doesn't hardcode `UNAVAILABLE` regardless of data).

---

## Files Changed

| File | Change |
|---|---|
| `paymentx-control-center/backend/src/main/java/com/paymentx/controlcenter/repository/AuditEventRepository.java` | Added `findByPaymentIdOrReference(paymentId, reference)`. Existing `findByPaymentId` untouched. |
| `paymentx-control-center/backend/src/main/java/com/paymentx/controlcenter/service/PaymentFlowService.java` | `buildFlow()` uses the widened lookup; added `routingStage()`; updated class Javadoc. |
| `paymentx-control-center/backend/src/test/java/com/paymentx/controlcenter/service/PaymentFlowServiceTest.java` | New — 4 tests covering Defect #2 (see above). |
| `paymentx-control-center/frontend/src/hooks/useE2EFlow.ts` | Added `refetchIntervalInBackground: true` to `useE2ERun`, with an explanatory comment. |
| `paymentx-control-center/frontend/src/pages/E2EPage.test.tsx` | New — 4 tests covering Defect #1 (see above). |

No other files were changed. No dependencies were added. No secrets were added or touched.

---

## Tests

**Backend** (`mvn test`, full suite, not just the new file): **65/65 passed, 0 failures, 0 errors** (61 pre-existing + 4 new in `PaymentFlowServiceTest`).

**Frontend** (`vitest run`, full suite): **29/29 passed** across 6 test files (25 pre-existing + 4 new in `E2EPage.test.tsx`).

**Frontend production build** (`npm run build`, `tsc -b && vite build`): **PASS**, clean, no TypeScript errors.

---

## Browser Validation

One new, real, controlled test payment was created through the actual "Run Complete Payment Flow" (`/e2e`) UI action — the platform's real Create-Payment mechanism (confirmed in the prior full validation phase; the Payments page itself is read-only). This is also the exact mechanism Defect #1 lives in, so it is the most direct real-world validation available for both fixes.

- Note on naming: the E2E tool generates its own real reference format (`CC-E2E-<timestamp>-<id>`) server-side; there is no frontend field to supply a custom reference such as `TEST-UI-FIX-<id>` without going around the real Create-Payment UI action (e.g. hand-crafting a raw Gateway request), which would defeat the point of validating through the real user journey. The generated reference is a real, synthetic, disposable test value in the same spirit — no real participant or production data.

**New UI payment reference:** `CC-E2E-1787209015843-63EFD492`

Verified in-browser at `/e2e`:
1. Payment created — real reference and correlation ID assigned immediately.
2. Payment Flow opened automatically (E2E page's live view).
3. Gateway progressed from `Running` → `Success`.
4. Gateway did **not** remain stuck at `Running` (this was the exact, reproduced pre-fix behavior on the prior run in this same session — `CC-E2E-1787206913535-FE5497B8`, which stayed frozen for 4+ minutes of active observation before this fix).
5. Validation state: `Success` (`Real Validation-Service returned VALIDATED.`) — correct.
6. Routing state: `Pending` on the E2E page (this page's own PENDING/RUNNING/SUCCESS/FAILED/TIMEOUT model maps the honest `UNAVAILABLE` to `PENDING`, matching how `Authentication`'s permanent `UNAVAILABLE` state has always been shown here) — with the real, honest detail text visible underneath, not silently hidden.
7. Final payment status: `SETTLED`, overall run result `Success`, `Duration: 4.0 s`.

Also independently re-verified at the dedicated `/payment-flow?reference=CC-E2E-1787209015843-63EFD492` page (separate from the E2E page's own live view): `Validation: Completed` (green), `Routing: Unavailable` (honest reason shown), all other stages unchanged from pre-fix behavior.

---

## Backend Correlation Evidence

Direct `docker exec paymentx-postgres psql` queries against the new payment, run immediately after the browser validation above:

```
paymentx_payment.payment:
 payment_reference              | status
 CC-E2E-1787209015843-63EFD492  | SETTLED

paymentx_validation.validation_log:
 payment_reference              | validation_status
 CC-E2E-1787209015843-63EFD492  | VALIDATED

paymentx_audit.audit_event (by reference):
 event_type            | source_service      | payment_id                            | reference
 VALIDATION_COMPLETED  | validation-service  | (blank)                                | CC-E2E-1787209015843-63EFD492
 PAYMENT_UPDATED       | payment-service     | f07016ed-aada-4f25-a66f-6c255a63b375   | CC-E2E-1787209015843-63EFD492
 PAYMENT_UPDATED       | payment-service     | f07016ed-aada-4f25-a66f-6c255a63b375   | CC-E2E-1787209015843-63EFD492
 PAYMENT_UPDATED       | payment-service     | f07016ed-aada-4f25-a66f-6c255a63b375   | CC-E2E-1787209015843-63EFD492
 PAYMENT_COMPLETED     | payment-service     | f07016ed-aada-4f25-a66f-6c255a63b375   | CC-E2E-1787209015843-63EFD492
```

This is the exact real-world shape the fix targets: `VALIDATION_COMPLETED` has a blank `payment_id` but a real `reference` — and the UI now correctly shows it as `Completed`, matching this real backend state, where it previously would have shown `NOT_STARTED`.

`routing-service`'s two real `PAYMENT_ROUTED` events for this run (visible on `/audit`, timestamped `12:26:58 PM`, immediately after `VALIDATION_COMPLETED`) independently confirm routing genuinely did run for this payment — carrying no `payment_id`, `correlation_id`, `trace_id`, or `reference` at all, exactly matching the root cause above and confirming the `UNAVAILABLE` message ("This does not mean routing has not run") is itself factually accurate, not just honestly worded.

---

## Regression Result

Checked after restarting `paymentx-control-center-backend` with the fix (necessary — Java is not hot-reloaded; the frontend dev server picked up the `useE2EFlow.ts` change via Vite HMR without a restart):

| Frontend area | Result |
|---|---|
| Dashboard | PASS — loads real payment lifecycle/service health, now includes both new test payments in Total Payments (41). |
| Payments | PASS — Transaction Monitor lists both new test payments, `SETTLED`, unaffected by the fix (uses `findPage`, not the changed lookup). |
| Payment details/flow | PASS — see Browser Validation above; this is the area the fix targets. |
| Audit | PASS — 357 real events listed, correct, unaffected (uses `findPage`/`search`, not the changed lookup). |
| Reconciliation | PASS — real batch list unaffected, unrelated code path. |
| Reporting | PASS — real execution history unaffected, unrelated code path. |
| AI Assistant | Not independently re-verified this session — the page loaded, but showed "Not Configured" because the LLM/Agent Orchestrator/RAG backends were not running in this environment at check time (confirmed unrelated: these are separate services this task never touched, and the same "service unreachable" warnings appeared in this session's own `mvn test` log output for reasons unconnected to this change). No code in the changed files is imported by, or shares any path with, the AI Assistant feature. |

Static check: `PaymentFlowService` is used by exactly two callers in the whole backend — `PostgresController` (the `/flow` endpoint) and `E2EFlowService` — both directly exercised above. The pre-existing `findByPaymentId` method is still used, unchanged, elsewhere in the codebase (outside Payment Flow), so nothing else could have regressed from widening the query.

---

## Known Limitations

- **Routing per-payment attribution remains genuinely unavailable.** The fix makes the UI honest about this (no longer claims `NOT_STARTED`), but does not — and, given the DO NOT MODIFY constraint on Routing Service, cannot — make Routing per-payment traceable end-to-end from the Control Center. A real fix would require Routing Service to add a correlation field (e.g. `payment_id` or `reference`) to its own `PAYMENT_ROUTED`/`ROUTING_RULE_CHANGED` audit events. This is flagged, not silently worked around.
- **Chrome's background-tab timer throttling** is not fully eliminated by `refetchIntervalInBackground: true` — for a run left backgrounded for a very long time (Chrome's "Intensive Wake Up Throttling," roughly 5+ minutes hidden), polling could still slow to as infrequently as once per minute. This does not affect the actual bug (E2E runs complete in single-digit seconds), so no further change was made for this edge case.
- **AI Assistant regression check was inconclusive**, not failed — the relevant backend services were not running in this environment session; this is an environmental/availability matter unrelated to the two files this remediation touched.
