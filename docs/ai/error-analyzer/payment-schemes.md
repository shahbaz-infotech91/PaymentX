---
documentType: SCHEME_REFERENCE
service: platform
severity: N/A
source: paymentx-payment-service, paymentx-routing-service, paymentx-validation-service
version: "1.0"
---

# PaymentX Payment Schemes

**PaymentX Error Analyzer knowledge document.** Ground truth extracted directly from source code, current as of this document's version. Covers exactly the three payment scheme identifiers that exist in PaymentX. No other scheme (ACH, FedNow, TCH, SEPA, SWIFT, RTP, or any other) exists anywhere in this codebase — do not reason about or reference any scheme not listed below.

## The three schemes

| Identifier | Defined in |
|---|---|
| `INSTANT_PAYMENT` | `PaymentScheme` (payment-service), `RoutingScheme` (routing-service), `Scheme` (validation-service) |
| `REAL_TIME_PAYMENT` | same three enums |
| `CARD_PAYMENT` | same three enums |

## Where each identifier is defined (source-verified)

- **`paymentx-payment-service`**: `entity/PaymentScheme.java` — `enum PaymentScheme { INSTANT_PAYMENT, REAL_TIME_PAYMENT, CARD_PAYMENT }`. Stored on `Payment.scheme` (`entity/Payment.java:67-68`, column `scheme`, `nullable=false`, `length=16`).
- **`paymentx-routing-service`**: `entity/RoutingScheme.java` — `enum RoutingScheme { INSTANT_PAYMENT, REAL_TIME_PAYMENT, CARD_PAYMENT }`. The class's own javadoc states this is **deliberately a service-local copy**, not shared via the common library (per ADR 0004 in `paymentx-common-library`'s docs) — "a shared enum would couple this service's compile unit to every other service's release cadence for a value set that is, in practice, platform configuration data."
- **`paymentx-validation-service`**: `entity/Scheme.java` — `enum Scheme { INSTANT_PAYMENT, CARD_PAYMENT, REAL_TIME_PAYMENT, ALL }`. Note the **fourth value, `ALL`**, unique to this service — used only on `business_rule` rows that apply to every scheme (per the enum's own inline comment), never as an actual payment's scheme.
- **MCP Gateway's `routing.lookup` tool** (`paymentx-mcp-gateway/src/main/java/com/paymentx/mcp/tool/RoutingLookupTool.java:77`) independently validates the same three values (`ALLOWED_SCHEMES = List.of("INSTANT_PAYMENT", "REAL_TIME_PAYMENT", "CARD_PAYMENT")`) — confirms the three-value set is consistent everywhere it is checked across the platform.

## Validation behavior

Validation rules are attached to a scheme via `BusinessRule` entities (`paymentx-validation-service/entity/BusinessRule.java`) and `ParticipantScheme` records — **no scheme-specific validation LOGIC branch was found in source**; rather, individual `BusinessRule` rows are scoped to a specific `Scheme` value (or `ALL`), and the validation engine evaluates whichever rules apply to the payment's scheme. The specific business rules configured (amount limits, blacklists, etc.) are **data**, not something this document can enumerate from the entity definition alone — NOT DEFINED IN CURRENT IMPLEMENTATION beyond "rules are scheme-scoped."

## Idempotency behavior

Identical across all three schemes — `IdempotencyRecord` (`paymentx-validation-service/entity/IdempotencyRecord.java`) carries a `scheme` column but the duplicate-detection mechanism itself (a `UNIQUE` constraint on `payment_reference`, enforced by `IdempotencyService.claim`, `service/IdempotencyService.java:51-69`) is **scheme-independent** — a duplicate `paymentReference` is rejected regardless of scheme. See `idempotency.md`.

## Routing behavior / route selection

**Identical resolution algorithm for all three schemes** — confirmed by direct read of `RoutingServiceImpl.resolveRoute(RoutingScheme scheme, String participantId, String traceId)` (`service/impl/RoutingServiceImpl.java:141-172`):

1. Check `routeCacheService` for a cached `(scheme, participantId)` result first.
2. On a cache miss, query `routingRuleRepository.findFirstBySchemeAndParticipantIdAndActiveTrueOrderByPriorityAsc(scheme, participantId)` — a participant-specific active rule, if one exists, ordered by priority.
3. If none, fall back to `findFirstBySchemeAndIsDefaultTrueAndActiveTrueOrderByPriorityAsc(scheme)` — the scheme's default active rule.
4. If neither exists, throw `ResourceNotFoundException("RoutingRule", "scheme=<scheme>[,participantId=<id>] (no active rule, no default configured)")`.
5. On success, cache the result and publish a `RouteResolvedEvent` (`ruleId, scheme, participantId, targetRoute, resolvedAt`) via `routingEventProducer.publishRouteResolved`.

**No scheme-specific routing branch exists** — `scheme` is used purely as a filter/cache key, applied identically for `INSTANT_PAYMENT`, `REAL_TIME_PAYMENT`, and `CARD_PAYMENT`. Any behavioral difference between schemes in practice comes entirely from which `RoutingRule` rows are configured for each scheme (data), not from code logic.

## Relevant configuration

Route rules themselves (`RoutingRule` entity) carry `scheme, participantId, active, priority, isDefault, targetRoute, description` — configured via `RoutingController`'s admin endpoints (`POST/PUT/DELETE`, `ROUTING_ADMIN` role). No scheme-specific YAML/properties configuration was found — routing behavior per scheme is entirely database-driven (`RoutingRule` rows), not static config.

## Scheme-specific behavior (explicitly checked, not assumed)

No scheme-specific code branch was found in `RoutingServiceImpl`, `ValidationService`, or `PaymentEngineImpl` beyond using the scheme value as a lookup/filter key. **NOT DEFINED IN CURRENT IMPLEMENTATION**: any assertion that `CARD_PAYMENT` is processed differently from `INSTANT_PAYMENT`/`REAL_TIME_PAYMENT` beyond which routing rules and business rules happen to be configured for it.

## Known errors, per scheme

No scheme produces a distinct error code of its own — errors are scheme-agnostic (`DuplicatePaymentException`, `BusinessRuleViolationException`, routing's `ResourceNotFoundException`, etc. — see `payment-errors.md`, `validation-errors.md`, `routing-errors.md`) and simply carry whichever scheme the failing payment had. **NOT DEFINED IN CURRENT IMPLEMENTATION**: any scheme-exclusive error type.
