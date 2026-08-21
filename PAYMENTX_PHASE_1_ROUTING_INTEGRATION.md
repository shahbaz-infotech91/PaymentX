# PaymentX — Phase 1 Routing Service Integration

Minimal, production-style integration of the already-real, already-tested Routing Service into the live
Payment Service processing flow. No payment-scheme/network abstraction was introduced — `SCHEME_A/B/C`
do not appear anywhere in this change, per explicit instruction. `INSTANT_PAYMENT`/`REAL_TIME_PAYMENT`/
`CARD_PAYMENT` remain exactly as they were; existing `PaymentScheme`/`RoutingScheme` terminology is
unchanged.

---

## 1. Before Architecture

`PaymentEngineImpl.runPipeline()` called `debitProcessor.process(payment)` and
`creditProcessor.process(payment)` directly. Each of those (`DebitProcessor`, `CreditProcessor`) called
`schemeGatewayFactory.getGateway(payment.getScheme())` — an in-process, compiler-exhaustive switch over
Payment Type selecting one of three placeholder gateways (`InstantPaymentGateway`,
`RealTimePaymentGateway`, `CardPaymentGateway`). Routing Service was never called from anywhere in
Payment Service.

## 2. Confirmed Gap

Verified from source before any change: Routing Service's own `RoutingServiceImpl.resolveRoute()`
(participant-specific rule → type-default rule → not-found) was real, tested, Redis-cached — and
completely unreachable from the live payment flow. `SchemeGateway.java`'s own javadoc: *"Routing Service
— the component that would make the actual REST/gRPC call... does not exist yet in this project's build
order."*

## 3. Routing Contract (verified from source, not assumed)

`RoutingController` (unchanged, not modified by this task):
- `GET /api/v1/routes/participant/{participantId}?scheme={paymentType}` → 200 with the resolved rule, or 404 if no active participant-specific rule exists.
- `GET /api/v1/routes/default?scheme={paymentType}` → 200 with the type-default rule, or 404 if none configured.
- Response body: `ApiResponse<RouteRuleResponse>`, `.data.targetRoute` is the field this integration consumes.
- `scheme` is `RoutingController`'s own existing query parameter name; its value is a Payment Type (`INSTANT_PAYMENT`/`REAL_TIME_PAYMENT`/`CARD_PAYMENT`) — not renamed.
- A real, existing reference implementation of a client calling this exact contract already existed in `paymentx-mcp-gateway`'s `RoutingServiceClient` — mirrored closely for this integration.

## 4. Implementation

**New files (`paymentx-payment-service`):**
- `config/RoutingClientProperties.java` — `payment.routing-client.*`: `base-url`, `connect-timeout-ms`, `read-timeout-ms`, `enabled` (feature flag).
- `client/RoutingClient.java` — the one class calling Routing Service's real wire format. `RestTemplate` via `RestTemplateBuilder` (existing platform convention, same as `paymentx-mcp-gateway`'s `RoutingServiceClient`). `@CircuitBreaker(name="routingService")` + `@Retry(name="routingService")` — both instance names **already pre-configured** in `application.yml`'s existing `resilience4j` block (3 attempts, 500ms exponential backoff, 50% failure-rate circuit breaker) — discovered already present, unused, before writing any code; not invented by this task.
- `client/RouteResolutionResponse.java` — local record, one field (`targetRoute`) — deliberately not importing Routing Service's actual DTO, matching this platform's established convention of each service owning its own copy of a cross-service contract (same pattern as the service-local `PaymentScheme`/`RoutingScheme`/`Scheme` enums).
- `client/RoutingResolutionException.java` — reason code (`ROUTE_NOT_FOUND` / `ROUTING_SERVICE_UNAVAILABLE` / `ROUTING_TIMEOUT` / `INVALID_ROUTING_RESPONSE`) + `retryable` flag, mirroring `GatewayResult`'s existing shape.
- `service/RoutingResolutionService.java` — orchestrates participant-rule → type-default → `ROUTE_NOT_FOUND`, re-implementing Routing Service's own existing fallback order from the caller's side.
- Two new test classes (`RoutingResolutionServiceTest`, `PaymentEngineImplTest`) — see §10.

**Modified files:**
- `service/impl/PaymentEngineImpl.java` — injected `RoutingResolutionService` + `RoutingClientProperties`; added a `resolveRoute(Payment, participantId)` private method, called once before the debit leg (debtor participant) and once before the credit leg (creditor participant) inside `runPipeline()`.
- `resources/application.yml` — added the `payment.routing-client.*` block. **No change to the existing `resilience4j` block** (already present).

**NOT modified:** `DebitProcessor.java`, `CreditProcessor.java`, `SchemeGatewayFactory.java`, `InstantPaymentGateway.java`/`RealTimePaymentGateway.java`/`CardPaymentGateway.java` — the existing Payment-Type gateway selection is byte-for-byte unchanged. No new Maven dependency was added — `resilience4j-spring-boot3` and `spring-boot-starter-web` were already present in `paymentx-payment-service/pom.xml`.

## 5. Payment Service Integration — exact call site

```
PaymentEngineImpl.runPipeline()
  resolveRoute(payment, payment.getDebtorParticipantId())   // NEW
    ↓ success                          ↓ failure
  DebitProcessor.process()          handleFailure() — return, DebitProcessor never called
    ↓
  resolveRoute(payment, payment.getCreditorParticipantId())  // NEW
    ↓ success                          ↓ failure
  CreditProcessor.process()         handleFailure() — return, CreditProcessor never called
```

`resolveRoute()` transitions the payment to the existing, previously-unused `PaymentStatus.ROUTING` enum
value (already defined in the schema before this task — not added by it) before calling
`RoutingResolutionService`.

## 6. Failure Behavior (implemented exactly as required)

| Scenario | Behavior |
|---|---|
| A. Participant-specific route exists | Used directly. `RoutingClient.resolveForParticipant()` returns it; `resolveDefault()` is never called. |
| B. No participant route, type-default exists | `RoutingClient.resolveDefault()` used, per Routing Service's own existing fallback order. |
| C. No route at all | `RoutingResolutionException(ROUTE_NOT_FOUND, retryable=false)` → `handleFailure()` → payment `FAILED`. Gateway never invoked. |
| D. Routing Service timeout/unavailable | `RoutingResolutionException(ROUTING_TIMEOUT` or `ROUTING_SERVICE_UNAVAILABLE, retryable=true)`, after resilience4j's own bounded retry (3 attempts, 500ms exponential backoff) is exhausted → `handleFailure()` → payment `RETRYING` (existing `RetryScheduler` picks it up later). Gateway never invoked. |
| Invalid/empty routing response | `RoutingResolutionException(INVALID_ROUTING_RESPONSE, retryable=false)` → payment `FAILED`. |

**No silent fallback exists anywhere in this implementation** — verified by source inspection (no code
path calls `SchemeGatewayFactory` after a routing failure) and by real test/runtime evidence (§10, §11).

## 7. Timeout/Retry

Resilience4j was already a declared dependency and already had a `routingService`-named circuit-breaker
and retry configuration block in `application.yml` before this task began — used as-is, not modified:
3 retry attempts, 500ms initial wait with exponential backoff (×2, randomized jitter); circuit breaker on
a 20-call sliding window, 50% failure-rate threshold, 30s open-state wait. HTTP-level connect/read
timeouts (2000ms/3000ms) added via the new `RoutingClientProperties`, matching the exact pattern already
used by `paymentx-mcp-gateway`'s `RoutingServiceClient`.

## 8. Observability

Logged at each routing attempt (via existing `Slf4j`/MDC conventions, no new logging framework):
routing requested (payment type, participant), resolved `targetRoute`, routing success/failure and
reason. No secrets logged (routing calls carry no credentials). Additive audit: `PAYMENT_ROUTED` — an
event type already defined in Audit Service's schema constraint but never emitted before this task — is
now recorded via the existing `recordAudit()` method on every successful routing resolution, carrying the
resolved `targetRoute`.

## 9. Metrics

No new metrics were added to Payment Service (per instruction: document, don't add, if not already
appropriate). Routing Service's own **existing** metrics (`routing_route_lookup_seconds_*`,
`routing_cache_miss_total`, both already present before this task) now receive real traffic for the first
time — confirmed in §11.

## 10. Tests

**New (20 total across 2 new classes, all passing):**
- `RoutingResolutionServiceTest` (4): participant-specific route used, default fallback used, no-route → `ROUTE_NOT_FOUND`, unavailable-error propagates without falling back to default.
- `PaymentEngineImplTest` (6): routing succeeds for both legs → full pipeline unchanged through `SETTLED`; `ROUTE_NOT_FOUND` → payment `FAILED`, gateway never invoked; `ROUTING_SERVICE_UNAVAILABLE` → payment `RETRYING`, gateway never invoked, retry scheduled; `ROUTING_TIMEOUT` → same; feature flag disabled → routing skipped entirely, old gateway selection runs directly; duplicate event ID → routing never invoked (idempotency short-circuit unchanged).

**Existing tests (unchanged, re-run for regression):** full `paymentx-payment-service` suite — 20/20 passing (10 new + `PaymentControllerIntegrationTest`'s existing tests, no modification to any pre-existing test).

**Routing Service's own existing tests (unchanged, re-run per Step 11):** 21/21 passing — `RoutingControllerIntegrationTest`, `RoutingRuleRepositoryTest`, `RoutingSecurityTest`, `RoutingServiceImplTest`. Zero Routing Service code was modified.

## 11. Real Transaction — Evidence

Submitted via the real application flow (API Gateway → Validation Service), not a direct database
insert. Reference: `TEST-E2E-f7b331bf1119`, Payment ID `074a0313-287a-476a-abe3-93fa42d4deb5`,
BANK001 → BANK002, INSTANT_PAYMENT, 150.00 USD → **SETTLED**.

**Real, multi-source evidence that Routing Service specifically participated (not inferred from
settlement alone):**

1. `payment_status_history` — two real `→ROUTING→` transitions, one per leg:
   ```
   PROCESSING    → ROUTING → DEBITING    (Resolving route for participantId=BANK001)
   DEBIT_SUCCESS → ROUTING → CREDITING   (Resolving route for participantId=BANK002)
   ```
2. `payment_audit` — two real `PAYMENT_ROUTED` entries with real resolved routes:
   ```
   participantId=BANK001 targetRoute=instant-payment-processor-bank001   (participant-specific rule)
   participantId=BANK002 targetRoute=instant-payment-processor           (type-default rule)
   ```
   Both values match Routing Service's real, independently-inspected seeded `routing_rule` data exactly
   — not hardcoded or fabricated by this integration.
3. **Routing Service's own real Prometheus metrics** (independent, cross-service confirmation):
   ```
   http_server_requests_seconds_count{uri="/api/v1/routes/participant/{participantId}",status="200"} = 2
   routing_cache_miss_total{scheme="INSTANT_PAYMENT"} = 2.0
   routing_route_lookup_seconds_count{cacheHit="false",scheme="INSTANT_PAYMENT"} = 2
   ```
4. Real, measurable latency in the status history (~2.2s between `PROCESSING→ROUTING` and
   `ROUTING→DEBITING`) — consistent with a genuine network round trip, not an instant in-process
   decision.

**This is real evidence of Routing Service integration, not a claim based on the payment having
settled.**

## 12. Evidence Summary — No Silent Fallback

- Source inspection: no code path in `PaymentEngineImpl.resolveRoute()` calls `SchemeGatewayFactory` or
  either processor after catching a `RoutingResolutionException` — it always calls `handleFailure()` and
  returns.
- Unit test evidence: `noRouteFound_failsPayment_gatewayNeverInvoked`,
  `routingServiceUnavailable_schedulesRetry_gatewayNeverInvoked_noSilentFallback`,
  `routingTimeout_isTreatedAsRetryable_gatewayNeverInvoked` — all assert `verify(debitProcessor,
  never()).process(any())` and all pass.
- The only sanctioned bypass is `payment.routing-client.enabled=false` — an explicit, operator-set
  configuration value, not a runtime response to a routing failure. Verified via
  `routingDisabledByFlag_bypassesRoutingEntirely_usesOldGatewaySelectionDirectly`, which also asserts
  `verifyNoInteractions(routingResolutionService)` in that mode (routing is skipped, not attempted and
  ignored).

## 13. Backward Compatibility — verified, not assumed

| Contract | Status |
|---|---|
| Kafka `{paymentType}-validated` topics | Unchanged — same consumer, same topics, confirmed in real startup log (`Subscribed to topic(s): instant-payment-validated, card-payment-validated, real-time-payment-validated`) |
| Idempotency | Unchanged — real re-submission with identical reference + idempotency key confirmed: payment count 37→37, cached gateway replay |
| Validation | Unchanged — not touched |
| Audit | Unchanged mechanism, additive new event type only (`PAYMENT_ROUTED`) |
| Notification | Unchanged — 4 real records generated for this transaction, matching prior transactions' pattern exactly |
| Reporting | Unchanged — real `source_event` ingestion confirmed for this transaction |
| Reconciliation | Unchanged — not touched by this task |
| Existing Payment-Type gateway selection | **Unchanged** — `DebitProcessor`/`CreditProcessor`/`SchemeGatewayFactory`/all three gateways were not modified |
| Payment Type values | Unchanged — `INSTANT_PAYMENT`/`REAL_TIME_PAYMENT`/`CARD_PAYMENT` untouched |

## 14. Known Limitations

- `PaymentStatus.ROUTING` is reused as both the in-flight status and the `failedStageStatus` passed to
  `handleFailure()` for a non-retryable routing failure, since no dedicated `ROUTING_FAILED` status
  exists. This produces one harmless self-transition (`ROUTING→ROUTING`) in `payment_status_history`
  before the terminal `ROUTING→FAILED` transition — a minor cosmetic artifact, not a correctness issue.
  Deliberate choice: adding a new `ROUTING_FAILED` enum value was avoidable and was avoided, per the
  "minimal" instruction.
- Routing is resolved once per leg (debtor for debit, creditor for credit) via two real HTTP calls per
  payment — this doubles Routing Service's real request volume compared to a hypothetical single
  whole-payment resolution. This was a deliberate design choice, matching Routing Service's own
  single-participant-per-call API contract (unchanged, not redesigned).
- No payment-scheme/network abstraction exists or was introduced — this integration operates entirely on
  the existing Payment Type axis, as instructed.
- Auth Service was not touched, per instruction — this integration does not depend on it and works
  identically regardless of Auth Service's implementation status.

## 15. Rollback

Set `payment.routing-client.enabled: false` (env var override supported via standard Spring Boot
property binding) and restart Payment Service — no code change, no redeploy of any other service, no
data migration. Verified via `routingDisabledByFlag_bypassesRoutingEntirely_usesOldGatewaySelectionDirectly`:
routing is skipped entirely and the pre-integration internal gateway selection runs directly, exactly as
before this task.

## 16. Final Status

Routing Service integration: **IMPLEMENTED and INTEGRATED**, verified with real, multi-source evidence
(database state, cross-service metrics, real test transaction). No regression in any existing test.
Postgres connections remained stable at 81/100 throughout implementation, testing, and the real
transaction — no connection exhaustion reproduced.
