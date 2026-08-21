# PaymentX — Phase 1 Core Remediation Design
## Routing + Scheme Selection + Authentication

**Status: DESIGN ONLY. No source code, configuration, or data was modified to produce this document.**
Generic scheme terminology (`SCHEME_A`/`SCHEME_B`/`SCHEME_C`) is used throughout per public-repository
safety instructions. Payment Type (`INSTANT_PAYMENT`/`REAL_TIME_PAYMENT`/`CARD_PAYMENT`) is kept strictly
separate from Scheme. No existing enum, class, or identifier is renamed here.

---

## 1. Executive Summary

Phase 1's audit (see `PAYMENTX_PHASE_1_CORE_REMEDIATION_DESIGN` companion audit) classified the platform
as **C — Phase 1 Incomplete — Core Functional Gap**, on the strength of source-code evidence, not
inference. Three gaps drive that classification, and this document designs a remediation for all three
without touching the currently-working, previously-proven-with-a-real-transaction payment lifecycle:

1. **Routing Service is not integrated** — Payment Service bypasses it entirely, by explicit, self-documented design (`SchemeGateway`'s own javadoc calls Routing Service *"a module not yet built"*).
2. **No payment-scheme (network) concept exists anywhere in the codebase** — every enum literally named `*Scheme` (`PaymentScheme`, `RoutingScheme`, Validation's `Scheme`) actually holds payment **type** values. `SCHEME_A/B/C` as a real, selectable concept does not exist today.
3. **Authentication Service has real database connectivity but zero authentication logic** — no controllers, no JWT issuance, no identity handling.

This design proposes an **incremental, additive** path: introduce Routing Service as a real hop reachable
via a synchronous call from Payment Service (Part 6 explains why REST, not Kafka), introduce Scheme as a
genuinely new, separate concept from Payment Type, formalize the existing placeholder gateways as real
Scheme Adapters behind the existing `SchemeGateway` interface (a seam that already supports this without
a redesign), and implement Authentication against claim/role contracts that **already exist, unused**, in
Common Library (`SecurityConstants`, `JwtClaims`) and are **already consumed** by API Gateway's JWT
converter. Nothing here is a green-field design — every recommendation anchors to a pattern already
proven somewhere else in this codebase.

---

## 2. Current Architecture

```
Client
 ↓
API Gateway            (real: JWT validation via NimbusReactiveJwtDecoder + HMAC secret,
                         API-key validation via Redis, rate limiting, 2 configured routes)
 ↓
Validation Service      (real: schema, blacklist, participant/scheme-limit, idempotency checks)
 ↓
Kafka                   ({paymentType}-validated topics — e.g. instant-payment-validated)
 ↓
Payment Service          PaymentValidatedConsumer listens directly on the {paymentType}-validated
                          topics. SchemeGatewayFactory performs a compiler-exhaustive switch over
                          PaymentType (misleadingly typed as "PaymentScheme" — see §4) to select one
                          of three placeholder gateways (InstantPaymentGateway / RealTimePaymentGateway
                          / CardPaymentGateway), each implementing the SchemeGateway interface
                          (debit/credit/returnPayment/reverse → GatewayResult).
 ↓
Settlement                Each placeholder gateway performs real input validation (mirroring
                          Validation Service's own business rules as defense-in-depth), logs a
                          structured "accepted" message, and returns success — no external network
                          or rail call is made anywhere in this path.
 ↓
Audit / Notification / Reporting   (event-consumed from Payment Service's own outbox, real, proven)
```

**Where Routing is currently bypassed:** entirely, between the Kafka hand-off and `SchemeGatewayFactory`.
No HTTP client to Routing Service's port exists in Payment Service; Routing Service's only Kafka
consumer subscribes to `participant.deactivated`, not any payment/validation topic.

**Where scheme selection currently occurs:** it doesn't — `SchemeGatewayFactory.getGateway(PaymentScheme
scheme)` selects a gateway **by Payment Type**, not by any competing-network scheme. There is no second
axis of selection anywhere in the current code.

---

## 3. Confirmed Gaps

| # | Gap | Evidence |
|---|---|---|
| 1 | Routing Service not integrated | No HTTP client in Payment Service; Routing Service's Kafka consumer only handles `participant.deactivated` |
| 2 | No Scheme concept implemented | `RoutingScheme`/`PaymentScheme`/`Scheme` enums all hold Payment Type values; seeded `routing_rule.target_route` values are per-type labels, never competing per-type alternatives |
| 3 | Payment Service performs placeholder scheme-dispatch internally | `SchemeGatewayFactory` + 3 gateway classes, each explicitly documented as a placeholder awaiting Routing Service |
| 4 | Authentication Service has no real functionality | Zero `@Entity`/`@Repository`/business `@RestController` classes; all probed auth endpoints return 404 |

---

## 4. Target Architecture

```
Client
 ↓
API Gateway
 ↓
Validation Service
 ↓
Routing Service              ← NEW real hop (design in §5)
 ↓
Scheme Selection              ← NEW concept (design in §6), distinct from Payment Type
 ↓
Scheme Adapter (SCHEME_A / SCHEME_B / SCHEME_C)   ← formalization of existing placeholder gateways (§7)
 ↓
Payment Processing             (Payment Service, narrowed scope — see §8)
 ↓
Settlement
 ↓
Audit / Notification / Reconciliation / Reporting   (unchanged — already real and proven)
```

The missing boundary is the same one identified in the prior audit: the Validation→Payment hand-off.
Today it is a direct Kafka topic; the target design inserts Routing Service (and, behind it, Scheme
Selection and a Scheme Adapter) into that hand-off **without changing the topics or contracts on either
side of it** (see §11, Backward Compatibility).

---

## 5. Routing Service Responsibility

### Routing Service SHOULD own:
- **Payment Type interpretation** — accept the already-validated Payment Type as input (not re-derive it).
- **Participant configuration lookup** — this already exists and works today (`RoutingRule` table, participant-specific override → type-default fallback, Redis-cached). No change needed to this mechanism; it becomes load-bearing instead of unused.
- **Routing configuration** — the existing `routing_rule` table, extended (additively — see §9) with an actual scheme identifier per rule, not just a free-text `target_route` label.
- **Scheme selection** — deciding which of `SCHEME_A`/`SCHEME_B`/`SCHEME_C` a given (Payment Type, participant) pair resolves to. This is new logic, but it slots into the existing `resolveRoute()` method's existing fallback structure (participant override → type default → error).
- **Adapter selection (identification only)** — Routing Service returns *which* adapter identifier to use; it does not itself hold adapter client code (that stays with Payment Service / a dedicated adapter layer — see §7).
- **Routing rules** — as today: priority-ordered, active-flagged, participant-scoped or default.
- **Fallback behavior** — as today: participant rule → type default → `ResourceNotFoundException`-equivalent (design as a well-defined "no route" outcome, see §10).
- **Invalid route handling** — reject with a clear, typed error distinguishing "no rule configured" from "rule configured but scheme temporarily unavailable."
- **Correlation ID propagation** — pass through unchanged, exactly as every other service in this platform already does (`X-Correlation-Id` header / MDC pattern already established).
- **Idempotency interaction** — Routing Service's own resolution call should be a **read-only, side-effect-free lookup** (it already is, today). It does not need its own idempotency table; the idempotency boundary stays where it already correctly is (Validation Service's `idempotency_record` + Payment Service's outbox), consistent with the existing platform pattern that idempotency belongs to the component that causes a state change, not the component that makes a routing decision.
- **Timeout behavior** — define an explicit, short timeout for the routing decision call (see §6/§10), since Payment Service will now be blocked on it synchronously.
- **Error handling** — return typed, machine-readable errors (reusing the existing `ApiResponse`/`ErrorResponse` envelope and `ErrorCodes` convention already used by every other service).

### Routing Service SHOULD NOT own:
- Payment execution, debit/credit/settlement logic — that stays in Payment Service / the Adapter layer.
- Scheme-specific protocol/network integration details — that belongs to the Scheme Adapter, not Routing Service (Routing Service decides *which* adapter; it does not *become* one).
- Payment state persistence — Routing Service's database stays limited to routing configuration, as it already is today.
- Retry of the payment itself — payment-level retry (already implemented via Payment Service's `RetryScheduler`) is unrelated to a routing-decision failure and must not be conflated with it.

**Distinguishing Routing from Payment Service, concretely:** Routing Service answers *"given this
Payment Type and participant, which scheme and adapter should handle this?"* — a configuration/policy
question. Payment Service answers *"execute this payment through the adapter I was told to use, and
manage its lifecycle/state/retries."* — an execution question. The existing `SchemeGateway` interface
boundary already cleanly separates these two concerns at the code level; what's missing is only which
component decides *which* gateway to invoke.

---

## 6. Payment Type vs Scheme

```
Payment Type (INSTANT_PAYMENT / REAL_TIME_PAYMENT / CARD_PAYMENT)
    +
Participant/Routing Rules (existing RoutingRule: participant override → type default)
    ↓
Scheme Selection  → SCHEME_A | SCHEME_B | SCHEME_C   (placeholder set — do not invent real mappings)
    ↓
Adapter Selection → SchemeAdapterA | SchemeAdapterB | SchemeAdapterC
```

Per instruction, **no specific Payment-Type-to-Scheme mapping is invented here** — current business
rules do not define one (confirmed: no such mapping exists in seeded data or code today). The design
treats this as a genuine open configuration question (see §19, Open Questions) — Routing Service's data
model (§9) is built to hold this mapping once a real business decision is made, using a placeholder
default (e.g., every Payment Type defaults to `SCHEME_A` until participant/business-specific overrides
are configured) purely as a bootstrap value, not a business rule this document is asserting.

---

## 7. Adapter Design

### `SchemeAdapter` — conceptual interface

This is **not a new interface** — it is the existing `SchemeGateway` interface (already defined, already
implemented three times, already exercised in every real settled payment), reframed under the Scheme
axis instead of the Payment Type axis once Routing Service exists. No code renaming happens as part of
this document; this is a description of the target role the existing interface shape already fulfills.

- **Responsibilities:** execute a debit/credit/return/reversal against one specific scheme's rail, given a fully-resolved `Payment` plus routing metadata (scheme, adapter identifier).
- **Input:** the `Payment` entity (as today) plus the routing decision (scheme identifier, target reference) resolved by Routing Service.
- **Output:** the existing `GatewayResult(success, retryable, reason)` shape — already well-designed for this purpose (the `retryable` flag already exists and is already consumed by Payment Service's retry logic).
- **Errors:** typed failures distinguishing permanent rejection (not retryable) from transient failure (retryable) — this distinction **already exists** in `GatewayResult` and should be preserved unchanged.
- **Timeout:** each adapter call should have an explicit, scheme-appropriate timeout (today's placeholder gateways are synchronous in-process calls with no real timeout surface; a real adapter reaching an external system needs one — design as a configurable per-scheme value, following the same `application.yml`-driven-properties convention already used everywhere else in this platform, e.g. `GatewaySecurityProperties`, `ReconciliationProperties`).
- **Retry:** adapter-level retry (transport-level, e.g. one immediate retry on transient network failure) is distinct from Payment Service's own business-level `RetryScheduler` retry (which re-attempts a whole payment later) — keep these two retry concepts separate, matching how `GatewayResult.retryable` already signals "Payment Service, you may retry this" without the adapter retrying internally by default.
- **Correlation ID:** propagate unchanged, per the platform-wide MDC/header convention.
- **Idempotency:** each adapter call should be tagged with the existing `payment.idempotency_key` (already a real column on the `payment` table) so a real external network integration can safely dedupe on redelivery — this reuses an existing column, not a new mechanism.
- **Observability:** structured logging (already present in the placeholder gateways, e.g. `"InstantPayment debit accepted..."`) plus new per-adapter metrics (see §13).

### SchemeAdapterA / SchemeAdapterB / SchemeAdapterC

Conceptually, one adapter implementation per scheme, analogous to today's one-gateway-per-Payment-Type
pattern but on the new Scheme axis. Each:
- Implements the same `SchemeGateway`-shaped contract.
- Owns its own scheme-specific request/response mapping internally (not exposed outside the adapter).
- Is invoked only by whatever component ends up making the actual outbound call — this document does
  not mandate whether that caller is Payment Service (invoking an adapter bean directly, as today's
  gateways are invoked) or a dedicated Adapter layer service; see §15 for why this document recommends
  keeping adapters as in-process components of Payment Service initially, not a new microservice.

---

## 8. Service Communication (Routing boundary)

**Recommendation: synchronous REST**, not Kafka, for the Payment Service → Routing Service call.

| Consideration | REST | Kafka |
|---|---|---|
| Nature of the interaction | A request/response *decision* needed before Payment Service can proceed — Payment Service cannot continue processing without an answer | Better suited to *notifications* / fire-and-forget facts, not blocking decisions |
| Existing precedent in this codebase | Every other synchronous decision in this platform (Validation's blacklist/participant checks, Gateway's rate-limit/auth checks) is REST-shaped | Routing Service's only existing Kafka usage is for asynchronous facts (`participant.deactivated` in, `routing.route-resolved`/`routing.rule-changed` out) — never a blocking decision |
| Latency | Routing Service's existing `resolveRoute()` is already fast (Redis-cached, sub-millisecond on cache hit) — well-suited to a synchronous call | Would require a request/reply pattern over Kafka (correlation-based reply-topic), adding real complexity for no benefit here |
| Existing client tooling | `RestClient`/`WebClient` usage is already the established pattern for service-to-service calls elsewhere (API Gateway → downstream) | N/A |
| Failure semantics | A REST timeout is simple to reason about and matches the existing per-call timeout conventions (`ConnectTimeoutMs`/`ReadTimeoutMs` already used in Control Center's downstream clients) | A Kafka request/reply failure mode (no reply within window) is a real pattern but not one this codebase uses anywhere yet — would be a genuinely new technology introduction |

**Conclusion:** REST is not a new technology for this platform, matches the request/response nature of a
routing *decision* (as opposed to a routing *fact*, which the existing `RouteResolvedEvent`/Kafka topic
already correctly models for downstream consumers like audit/reporting), and requires no new
infrastructure. Kafka remains correctly used for the asynchronous side of Routing Service (rule-change
notifications, resolved-route facts for audit trail) — this design does not change that.

---

## 9. Payment Service Boundary

**Current:**
```
Payment Service
 ├── routing decision        (SchemeGatewayFactory: switch on Payment Type)
 ├── scheme selection        (does not exist as a real decision — conflated with routing decision above)
 └── payment processing      (debit/credit/return/reverse orchestration, outbox, retry, timeout scheduling)
```

**Target:**
```
Routing Service
 ├── routing decision         (participant + Payment Type → scheme + adapter identifier)
 └── scheme selection         (as a genuine, separate decision — see §6)

Payment Service
 └── payment processing        (calls the resolved adapter; owns debit/credit/return/reverse
                                orchestration, outbox, retry, timeout scheduling — unchanged)

Adapter (scheme-specific)
 └── scheme-specific integration   (formalized version of today's placeholder gateways)
```

**What conceptually moves out of Payment Service:** the *decision* of which gateway to use
(`SchemeGatewayFactory`'s switch statement). **What stays:** everything about *executing* a payment once
the gateway/adapter is known — `PaymentEngineImpl`, `SettlementProcessor`, `TimeoutScheduler`,
`RetryScheduler`, the outbox pattern, all of which are real, working, and untouched by this design. This
document does not recommend moving any code in this task — this section describes the target
responsibility split for a future implementation phase.

---

## 10. Data Model (documented, not created)

**Routing rules** — extend the existing `routing_rule` table (additively, no destructive change):
- Existing columns unchanged: `scheme` (Payment Type — misnamed, kept as-is per no-rename instruction), `participant_id`, `target_route`, `priority`, `active`, `is_default`.
- New column needed: a real scheme identifier (`SCHEME_A`/`SCHEME_B`/`SCHEME_C`) distinct from the existing `scheme` (Payment Type) column — this is the concrete data-model consequence of §6's Payment-Type-vs-Scheme separation.

**Participant configuration** — already exists (Validation Service's `participant` + `participant_scheme` tables — noting `participant_scheme` today is *also* Payment-Type-scoped, same naming pattern). Routing Service would need read access to participant status (active/suspended), either via a lightweight local cache (consistent with its existing participant-lifecycle Kafka consumption) or a real-time check.

**Scheme selection** — new table/config needed: a mapping of (Payment Type, participant-or-default) →
Scheme, following the exact same three-column shape (`participant_id` nullable, `is_default`, `priority`)
Routing Service's existing `routing_rule` table already uses — a structurally identical, additive table,
not a new pattern.

**Adapter configuration** — per-scheme connection/timeout/retry settings, following the existing
`@ConfigurationProperties`-per-service convention already used everywhere (`GatewaySecurityProperties`,
`ReconciliationProperties`, etc.) — likely `SchemeAdapterProperties` with one nested config block per
scheme.

**Authentication identities/credentials** — genuinely new tables needed in Auth Service (currently has
zero): a participant/user identity table (subject, participant ID, credential hash — never plaintext),
and a token/session tracking table only if refresh-token revocation is required (stateless
access-token-only design could avoid this — see §12, Open Questions).

No migrations are created by this document.

---

## 11. Failure Handling

| Scenario | Design |
|---|---|
| No route (no rule for Payment Type + participant, no default) | Routing Service returns a typed "no route configured" error (same shape as today's `ResourceNotFoundException` path) — Payment Service marks the payment `FAILED` with a clear reason, exactly as it already does for other rejection paths |
| Invalid route (rule exists but references an unknown/disabled scheme) | Distinct typed error from "no route" — surfaces a configuration problem, not a business rejection; should alert operators (existing Prometheus/Grafana infra, no new tooling) |
| Unsupported Payment Type | Already impossible today — `PaymentType` is a closed, compiler-enforced enum; this remains true |
| Unsupported scheme | Adapter factory (mirroring today's `SchemeGatewayFactory` exhaustiveness pattern) rejects at startup/compile time for any scheme without a registered adapter |
| Adapter unavailable | `GatewayResult.failure(reason, retryable=true)` — reuses the existing mechanism; Payment Service's existing `RetryScheduler` already knows how to act on this |
| Routing timeout | Payment Service treats a Routing Service timeout as a transient failure (retryable), NOT a rejection — the payment stays in a pending/retry state rather than being marked `FAILED`, using the existing `TimeoutScheduler`/`RetryScheduler` machinery already in place for exactly this kind of transient-failure recovery |
| Payment timeout | Unchanged — already handled by `TimeoutScheduler` |
| Duplicate request | Unchanged — already handled at two existing layers (Gateway's Redis idempotency-key cache, Validation Service's `idempotency_record`) |
| Retry | Unchanged for payment-level retry (`RetryScheduler`); adapter-level retry is a new, narrower concept (§7) |
| Dead-letter handling | Already supported infrastructure (every service's `KafkaConsumerConfig` already publishes to a `.DLT` topic on listener failure) — Routing Service's own Kafka consumer already has this; no new DLT mechanism needed |
| Partial failure (routing succeeds, adapter fails) | Payment stays in an intermediate state (e.g. `ROUTED`/`PROCESSING`) distinct from `FAILED` until the adapter call resolves — avoids a payment being incorrectly marked failed for a transient adapter issue |

---

## 12. Backward Compatibility

The existing, proven lifecycle must not break. Concretely:

- **Kafka contracts unchanged:** Validation Service continues publishing to the same `{paymentType}-validated` topics. Payment Service's `PaymentValidatedConsumer` continues consuming them unchanged — Routing Service is invoked *from inside* Payment Service's processing (replacing the internal `SchemeGatewayFactory` call site), not by inserting a new Kafka hop. This is the single most important compatibility decision in this design: **no topic, event schema, or consumer contract changes.**
- **Idempotency unchanged:** the existing idempotency mechanisms (Gateway Redis cache, Validation `idempotency_record`, Payment Service's outbox/idempotency_key column) are untouched — Routing Service's call is a read-only decision inserted *between* already-idempotent steps, not a new idempotency boundary.
- **Audit unchanged:** Payment Service continues emitting the same audit events (`PAYMENT_UPDATED`, `PAYMENT_COMPLETED`, etc.) at the same points. Optionally, a new `PAYMENT_ROUTED` audit event type (already a valid, defined-but-unused enum value in Audit Service's own `chk_audit_event_type` constraint — confirmed present in the schema) could be emitted by Payment Service once routing is real, purely additive.
- **Notification/Reporting/Reconciliation unchanged:** none of these consume routing information directly today; none need to change.
- **Migration strategy — incremental, flag-gated:**
  1. Deploy Routing Service's new scheme-selection capability alongside its existing CRUD API — additive only, doesn't affect anything since nothing calls it yet.
  2. Add the Payment Service → Routing Service call **behind a feature flag** (matching the platform's existing convention of flag-gated features — e.g. Control Center's own `control-center.ai.enabled` pattern), defaulting OFF, so today's placeholder-gateway path remains the active default until the flag is explicitly enabled.
  3. Enable the flag in a non-production/dev profile first, run the existing regression suite (the same targeted-test discipline already used throughout this project's remediation history) plus one real test transaction (matching the established `TEST-E2E-*` convention), verify no regression.
  4. Only then consider enabling by default — a separate, later decision, not part of this design.

This flag-gated approach means the "current architecture" (§2) and "target architecture" (§4) can coexist
in the same codebase for as long as needed, with a single, reversible switch between them.

---

## 13. Security

- **Authentication (see §14 for full design):** reuse existing `SecurityConstants` claim contract (`sub`, `iss`, `roles`, `participantId`, `tokenType`) already defined in Common Library and already consumed by API Gateway's `JwtGrantedAuthoritiesConverter` — Auth Service becomes the *issuer* of tokens whose *shape* is already specified and already validated downstream.
- **Authorization:** reuse the existing trusted-header pattern (`X-Roles` propagated by Gateway, checked via `@PreAuthorize` + `HeaderRoleAuthenticationFilter`, already implemented identically in routing/audit/notification/reconciliation/reporting) — Routing Service's new admin-mutating endpoints (if any beyond the existing rule CRUD) follow this exact, already-proven pattern.
- **Secret handling:** any new adapter credentials (scheme-specific API keys, if a real adapter ever calls a real external system) must follow the existing env-var-driven pattern already used platform-wide (`${VAR:default}` with either no default or an explicitly-labeled non-production default) — never hardcoded, matching every `application-prod.yml` audited.
- **API key handling:** unchanged — Gateway's existing Redis-backed API-key mechanism already covers service-to-service and MCP-tool authentication; no new key mechanism needed for Routing Service's own inbound calls (it sits behind the same Gateway trust boundary as every other internal service).
- **JWT handling:** Auth Service should issue tokens using the same HMAC-secret convention API Gateway already validates against (`GATEWAY_JWT_SECRET`) — this is a **shared secret** that must be provisioned identically to both services via environment variable, never duplicated as a literal in either service's committed config.
- **Participant isolation:** Routing Service's rule resolution already scopes by `participant_id`; this must be preserved and, if Authentication is implemented, cross-checked against the authenticated participant's identity (a participant should not be able to request routing resolution or payment status for another participant's data) — this is a new authorization check to design, not one that exists today.
- **Scheme configuration protection:** Routing Service's rule-mutation endpoints already require `ROUTING_ADMIN` — the same role-gate extends naturally to new scheme-selection-configuration endpoints.
- **Audit requirements:** every new routing decision and every new authentication event (login success/failure, token issuance) should be audited via the existing Audit Service integration — matching the existing `SECURITY_EVENT`/`API_REQUEST` audit event types already defined (and already used by Auth Service's own `SecurityConfig`-driven scenarios, per the platform convention) in Audit Service's schema.
- **No secret is printed, logged, or embedded in any newly-created content in this document.**

---

## 14. Authentication Design

**Principle: reuse, do not reinvent.** No new identity platform, no new token format, no new claim
shape — Auth Service becomes a real implementation of a contract that already exists and is already
consumed.

| Capability | Design |
|---|---|
| Authentication | New `POST /api/v1/auth/login` (or equivalent) endpoint in Auth Service, verifying participant credentials against a new (to-be-created, not created here) identity table |
| JWT generation | Auth Service signs tokens using the **same** HMAC secret (`GATEWAY_JWT_SECRET`, shared via env var) that API Gateway's `NimbusReactiveJwtDecoder` already validates against — no new algorithm, no new key management needed |
| Claims | Populate exactly the claims Common Library's `SecurityConstants` already defines and API Gateway's converter already reads: `sub` (subject), `iss` (issuer), `roles` (array, read via `CLAIM_ROLES`), `participantId` (via `CLAIM_PARTICIPANT_ID`), `tokenType` (`ACCESS`/`REFRESH`/`SERVICE` — all three already defined) |
| Participant identity | New identity table (see §9), linking a credential to a `participantId` matching the same identifier already used platform-wide (`BANK001` etc.) |
| Authorization | Unchanged — Gateway already converts the `roles` claim to Spring Security authorities with the existing `ROLE_` prefix; every downstream service's `@PreAuthorize`/`X-Roles` pattern already works against this — Auth Service issuing real tokens with real roles makes the *existing* authorization machinery load-bearing for the first time, without changing it |
| Token expiry | New — Auth Service sets a real `exp` claim; API Gateway's `JwtTimestampValidator` (already wired, already tolerating a configured clock-skew) already enforces this — currently enforced against tokens that don't really exist yet from Auth Service's perspective (test tokens are hand-crafted via `TestJwtUtil`); this becomes real once Auth Service issues them |
| Invalid token handling | Unchanged — Gateway already rejects invalid/expired/malformed tokens via its existing `AuthenticationEnforcementGlobalFilter`/`JwtReactiveAuthenticationManager` chain; Auth Service doesn't need to duplicate this, only to issue tokens that this existing chain can validate |
| Security filters | Auth Service needs a **minimal** new filter chain for its own login endpoint only (public, rate-limited) — everything else about its `SecurityConfig` (actuator permitAll, etc.) stays as-is |
| API Gateway integration | **No change required on the Gateway side** — it already fully implements JWT validation against exactly this claim shape; this is the strongest piece of evidence that Authentication is an isolated, additive change, not a cross-cutting one |

**What this design explicitly avoids:** OAuth2/OIDC federation, a separate identity provider, session
state beyond what a stateless JWT already provides, and any new cryptographic scheme — all are
unnecessary given the existing, already-consumed HMAC-JWT contract.

---

## 15. Test Strategy

Following this platform's own established conventions (JUnit 5, AssertJ, Mockito, Testcontainers where a
real Postgres/Kafka dependency exists, `MockServerWebExchange`/unit tests for pure filter logic) — no new
framework.

**Unit:**
- Routing Service: scheme-selection resolution logic (participant override → default → no-route), mirroring the existing `RoutingServiceImplTest` pattern.
- Adapter factory: exhaustiveness (compiler-enforced, as today) plus a unit test per adapter's own input-validation logic (currently missing even for the placeholder gateways — a real gap this design should close).
- Auth Service: token generation/claim population, token validation edge cases (expired, malformed, wrong signature).

**Integration:**
- Routing Service ↔ Payment Service: real REST call, real timeout/failure injection (a WireMock-based test — `wiremock-standalone` is already a declared, currently-unused dependency in API Gateway; the same tool fits here).
- Auth Service ↔ API Gateway: issue a real token from Auth Service, confirm Gateway accepts it — closes the gap the earlier real-transaction validation found (AI/MCP-driven lookups had to use hand-crafted test tokens because Auth Service issued none).

**Contract:**
- Verify the `{paymentType}-validated` Kafka event schema is unchanged before/after Routing Service integration (backward-compatibility guardrail per §12).
- Verify `GatewayResult` shape is unchanged for existing gateway/adapter callers.

**E2E — minimum scenarios (mirroring this project's own established `TEST-E2E-*` real-transaction discipline):**
- Valid route → successful payment (regression baseline — must still pass unchanged)
- Invalid route (misconfigured rule) → payment fails with a clear, typed reason
- Unsupported scheme → rejected at configuration time, not at payment time
- Adapter selection → correct adapter invoked for a given (type, participant) pair
- Adapter failure → payment retried per existing `RetryScheduler` behavior, not incorrectly marked permanently failed
- Routing timeout → payment treated as transient/retryable, not rejected
- Duplicate/idempotency → unchanged behavior, re-verified end-to-end (matching the already-proven Test #2 from the real-transaction validation)
- Successful payment → full lifecycle re-verified (Validation → Routing → Adapter → Settlement → Audit → Notification → Reporting)
- Rejected payment → unchanged behavior, re-verified
- Authentication success → real login, real token, real Gateway acceptance
- Authentication failure → wrong credentials rejected
- Authorization failure → valid token, insufficient role, rejected by an existing `@PreAuthorize` check

---

## 16. Observability

- **Correlation ID / Trace ID:** propagate through the new Routing hop exactly as every existing hop already does (header + MDC pattern) — no new mechanism.
- **Routing logs:** structured, at the same log points the existing `RoutingServiceImpl` already logs at (cache hit/miss, rule resolution), extended to log the new scheme decision.
- **Adapter logs:** extend the existing placeholder-gateway logging pattern (`"...accepted (real-time rail) paymentReference=..."`) to include the resolved scheme.
- **Metrics (design examples only, not added by this document):** `payment_routing_total`, `payment_routing_success_total`, `payment_routing_failure_total`, `payment_scheme_selected_total` — these would close the confirmed gap (Payment Service currently has zero dedicated business metrics) and follow the exact naming convention already used by every other service's real metrics (`reconciliation_record_classification_total`, `notification_channel_success_total`, etc.).
- **Audit events:** a new `PAYMENT_ROUTED` event (already a valid, unused enum value in Audit Service's schema constraint) emitted once routing becomes real — purely additive, no schema change needed.
- **Known, pre-existing gap this design does not solve on its own:** Zipkin trace continuity already breaks at the Kafka boundary (confirmed in the prior audit). Adding a REST hop for Routing (§8) actually *improves* traceability for that specific hop (REST spans propagate correctly today, as proven by the real Gateway→Validation trace found in the prior gap-validation task) — but the Kafka-boundary gap remains a separate, pre-existing issue, not solved or worsened by this design.

---

## 17. Implementation Order

Adjusted from the requested template based on actual repository evidence — Authentication is
recommended **first**, ahead of Routing, because (a) it is fully isolated (§14 shows zero required
changes to any other service) and lowest-risk, (b) every other phase's E2E testing benefits from having
real tokens instead of hand-crafted `TestJwtUtil` ones, and (c) it has no dependency on Routing/Scheme
work at all — the two efforts are independent and Authentication is strictly less risky to land first.

1. **Phase 1A — Authentication.** Isolated, additive, zero required changes elsewhere. Real tokens available for all subsequent testing.
2. **Phase 1B — Scheme Selection data model.** Additive-only schema change to `routing_rule` (new scheme column) + new scheme-mapping table. No behavior change yet — Routing Service's existing API surface is untouched until §17.3.
3. **Phase 1C — Routing Service resolution logic.** Extend `resolveRoute()` to also return a scheme decision. Still not called by anyone yet — safe to deploy and test in isolation.
4. **Phase 1D — Scheme Adapter formalization.** Turn today's three placeholder gateways into scheme-keyed adapters (still in-process, still no external network call — a structural change only, per Payment-Type-vs-Scheme separation).
5. **Phase 1E — Payment Service boundary: flag-gated Routing Service call.** The actual integration point, behind a feature flag defaulting OFF (§12).
6. **Phase 1F — Observability/Metrics.** New business metrics + `PAYMENT_ROUTED` audit event, informed by whatever the real §17.5 integration looks like once built.
7. **Phase 1G — Full regression.** Re-run this platform's own established targeted-test discipline (module-by-module sequential runs, Postgres-connection monitoring, one real `TEST-E2E-*` transaction) before ever considering flipping the flag's default to ON.

---

## 18. Rollback Strategy

Because §12's migration strategy is flag-gated, rollback at every stage is the same single action:
**disable the flag.** No data migration needs to be reversed (§9's new tables/columns are purely
additive — nothing existing is altered or dropped), no Kafka contract changes need to be reverted (§12 —
none exist), and Payment Service's existing placeholder-gateway path remains fully intact and
untouched underneath the flag for as long as needed.

| Failure mode | Rollback |
|---|---|
| Payment failures after enabling Routing | Disable flag → Payment Service reverts to its existing internal `SchemeGatewayFactory` path immediately, no deploy needed if the flag is externalized (env var / config server), consistent with this platform's existing `${VAR:default}` convention |
| Kafka failures | Not expected — §12 explicitly makes no Kafka contract changes; if observed, it indicates a regression unrelated to this design and should be investigated as such, not "rolled back" |
| Adapter failures | Same flag rollback; additionally, per-scheme adapter can be individually disabled if the adapter layer is designed with a per-scheme circuit-breaker-style flag (a refinement to consider during Phase 1D, not mandated here) |
| Latency increase | Measure via the new `payment_routing_total`/duration metrics (§16) before committing to enabling the flag by default; rollback is the same flag disable if latency regresses beyond an agreed threshold |
| Authentication failures | Independent of the Routing flag entirely (§17 phases are independent) — rollback is reverting Auth Service's deploy; API Gateway's existing JWT validation is unaffected either way since it doesn't change |

No rollback procedure in this design requires a database rollback, a Kafka topic change reversal, or a
coordinated multi-service rollback — this is a deliberate design property, not an afterthought.

---

## 19. Risks

- **Conceptual risk (highest):** without a real business decision on what `SCHEME_A/B/C` actually map to, Phase 1B/1C risk being built against a placeholder mapping that later needs rework. Mitigated by treating the mapping table (§9) as configuration data, not code, so a later correction doesn't require a redeploy.
- **Naming confusion carried forward:** the existing `PaymentScheme`/`RoutingScheme`/`Scheme` enums (all actually Payment Type) remain unrenamed per instruction — new code introducing a *real* Scheme concept alongside these must be extremely careful in code review and documentation to keep the two concepts visually and semantically distinct, or the exact confusion this audit uncovered will recur for the next engineer.
- **Latency risk:** adding a synchronous REST hop increases Payment Service's processing latency by however long Routing Service's call takes — mitigated by Routing Service's existing Redis caching (already fast) and a short, enforced timeout (§5) with a transient-failure fallback (§11) rather than blocking indefinitely.
- **Authentication rollout risk:** once Auth Service issues real tokens, any existing hand-crafted test tokens (`TestJwtUtil`) must continue to validate identically (same secret, same claim shape) — low risk since §14 explicitly reuses the exact existing contract, but worth an explicit regression check.
- **Scope creep risk:** the temptation to "fix" the Payment-Type/Scheme naming conflation while implementing this design must be resisted per the no-rename instruction — flagged explicitly here as a discipline risk, not a technical one.

---

## 20. Open Questions

1. What are the real, intended business mappings from Payment Type to Scheme? (Not answerable from existing code/data — explicitly not invented in this design.)
2. Should Scheme Adapters remain in-process components of Payment Service, or become their own service(s) eventually? This design recommends staying in-process initially (lower risk, consistent with today's placeholder-gateway pattern) but does not rule out extraction later.
3. Does Authentication need refresh-token revocation (stateful) or is a short-lived access-token-only model (fully stateless) sufficient for Phase 1? Affects whether a token/session table is needed in §9's data model.
4. Should participant isolation checks (§13) be enforced at Routing Service, API Gateway, or both? Current platform convention leans toward "Gateway authenticates, downstream services authorize via trusted headers" — likely the right default, but worth an explicit decision before implementation.
5. Is a per-scheme circuit breaker (mentioned as a possible Phase 1D refinement in §18) in scope for Phase 1, or a later hardening pass?

---

## 21. Final Recommendation

Proceed with the phased, flag-gated design above, starting with Authentication (§17, Phase 1A) as the
lowest-risk, fully-isolated first step. Do not attempt Routing/Scheme integration and Authentication
simultaneously — they are independent efforts and sequencing them reduces the blast radius of any single
change. Do not rename any existing enum or class as part of this work. Resolve Open Question #1 (the real
Payment-Type-to-Scheme business mapping) before beginning Phase 1B, since building the data model against
an assumed mapping that turns out wrong is the single highest-cost mistake available in this plan.
