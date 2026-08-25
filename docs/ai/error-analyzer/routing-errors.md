---
documentType: ERROR_CODE_REFERENCE
service: paymentx-routing-service
severity: MEDIUM
source: paymentx-routing-service
version: "1.0"
---

# PaymentX Routing Errors

**PaymentX Error Analyzer knowledge document.** Ground truth from source.

## No dedicated "failed routing attempt" record exists

This is the most important honest finding in this document: **Routing Service has no persisted record of a failed/rejected routing decision for a specific payment.** `RoutingController` and `RoutingServiceImpl` manage routing *rules* (CRUD + resolve), not a per-payment routing-attempt log. There is no `routing_attempt` table, no `RoutingFailure` entity, nothing equivalent to Validation Service's `validation_log` or Payment Service's `payment_status_history` for routing decisions.

## The one real failure mode: no active/default rule

`RoutingServiceImpl.resolveRoute` (`service/impl/RoutingServiceImpl.java:141-172`) throws `ResourceNotFoundException("RoutingRule", "scheme=<scheme>[,participantId=<id>] (no active rule, no default configured)")` when:

- No `RoutingRule` row matches `(scheme, participantId, active=true)`, **and**
- No `RoutingRule` row matches `(scheme, isDefault=true, active=true)` either.

| Field | Value |
|---|---|
| Error name/type | `ResourceNotFoundException` (routing-service) |
| Trigger | No active participant-specific or default routing rule exists for the requested scheme |
| Meaning | The platform has no configured route for this scheme/participant combination |
| Affected state | The `routing.lookup` MCP tool call returns `found: false` (not a thrown MCP error — see `PAYMENTX_PHASE_4_2_0_ERROR_ANALYZER_DESIGN.md` §3) rather than a payment-side status; if this occurs during real payment processing, it would surface as a `Payment.failureReason` (free text) on the payment side instead (per `payment-errors.md`) — NOT DEFINED IN CURRENT IMPLEMENTATION exactly what text Payment Service writes into `failureReason` in this specific case; not confirmed by a direct read of `PaymentEngineImpl`'s routing-integration code in this pass |
| Retryability | NOT DEFINED IN CURRENT IMPLEMENTATION — no explicit flag; logically not retryable without a configuration change (adding a rule), but this is inference from the situation, not a source-confirmed field |
| Remediation | NOT DEFINED IN CURRENT IMPLEMENTATION beyond "an operator must configure an active rule or default for this scheme" |
| Source reference | `paymentx-routing-service/src/main/java/com/paymentx/routing/service/impl/RoutingServiceImpl.java:156-157` |

## What routing.lookup can and cannot tell an investigator

The MCP tool `routing.lookup` (see `PAYMENTX_PHASE_4_2_0_ERROR_ANALYZER_DESIGN.md` §3) can confirm, **at the time of the query**, whether an active rule/default currently exists for a scheme/participant. It **cannot** confirm what rule (if any) existed *at the time the original payment was processed* — routing rules can change, and no historical routing-decision record exists to check against. If a payment failed for a routing reason but the rule has since been fixed, `routing.lookup` today would show `found: true`, appearing to contradict the historical failure — this is a real limitation an Error Analyzer's prompt must account for (see `PAYMENTX_PHASE_4_2_0_ERROR_ANALYZER_DESIGN.md` §13's "conflicting evidence" guidance).

## Kafka events

`RoutingKafkaTopics.java:23-24` — `routing.route-resolved` (published on every successful resolution, via `routingEventProducer.publishRouteResolved`, carrying `ruleId, scheme, participantId, targetRoute, resolvedAt`), `routing.rule-changed` (published on rule mutation). **No "routing failed" event topic exists** — a failed resolution throws synchronously; it does not publish an event.
