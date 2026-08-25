---
documentType: OPERATIONAL_REFERENCE
service: platform
severity: MEDIUM
source: paymentx-payment-service, paymentx-routing-service, paymentx-reconciliation-service
version: "1.0"
---

# PaymentX Kafka / Messaging Failure Handling

**PaymentX Error Analyzer knowledge document.** Ground truth from source. This document covers what actually exists in consumer error-handling code — it does not describe Kafka broker-level failure modes not reflected in PaymentX's own consumer configuration.

## Poison-message protection exists; full dead-letter recovery does not

Confirmed by direct read of `paymentx-payment-service/src/main/java/com/paymentx/payment/config/KafkaConsumerConfig.java` (the consumer for `instant-payment-validated`/`card-payment-validated`/`real-time-payment-validated`):

- The consumer wraps its `JsonDeserializer` in Spring Kafka's `ErrorHandlingDeserializer` (lines 80-83, 109-111). Purpose, per the class's own javadoc (lines 35-43): a message that cannot be deserialized (corrupt payload, schema drift) would otherwise throw during `poll()` and could crash the entire listener container thread — halting consumption for every *other*, valid message on that partition too. `ErrorHandlingDeserializer` catches the deserialization exception per-record instead.
- **The class's own comment explicitly states**: "Full dead-letter-topic recovery wiring for these poison-pill messages is addressed in Batch 6 (exception package)." This session's source search found **no `DeadLetterPublishingRecoverer` or `DefaultErrorHandler` bean anywhere in `paymentx-payment-service`** — only a `GlobalExceptionHandler` (a REST exception handler, unrelated to Kafka) and a `package-info.java` exist under `exception/`. **The referenced full DLT recovery wiring does not appear to exist in the current implementation** — this is a genuine, source-confirmed gap between the comment's stated intent and what was found, not an assumption.
- Manual acknowledgment mode is explicitly configured (`ContainerProperties.AckMode.MANUAL`, line 122) — the consumer controls exactly when an offset is committed, rather than relying on Spring Boot's auto-configuration (which is bypassed the moment a custom `ConcurrentKafkaListenerContainerFactory` bean is declared, per the class's own comment, lines 45-52).
- A real historical incident is referenced directly in the source comment (lines 96-108): an earlier version of this code passed the bare (unwrapped) deserializer to `DefaultKafkaConsumerFactory`'s 3-arg constructor, which silently discarded the `ErrorHandlingDeserializer` wrapping — meaning the protection this class describes was **not actually active** until fixed. Found via "a real poison-message test against a sibling service during Phase 1 validation."

`KafkaConsumerConfig` classes with the same general shape also exist in `paymentx-routing-service` and `paymentx-reconciliation-service` (confirmed present via search) — their specific error-handling configuration was **not** individually re-verified in this pass; do not assume they are identical to Payment Service's without checking.

## Real topic names (for cross-referencing evidence, not an error taxonomy)

| Service | Publishes | Consumes |
|---|---|---|
| Validation Service | `instant-payment-validated`, `card-payment-validated`, `real-time-payment-validated`, `payment-rejected` | — |
| Payment Service | `payment.processing, payment.debited, payment.credited, payment.completed, payment.failed, payment.returned, payment.reversed, payment.cancelled, payment.timeout` | the three `*-validated` topics above (`PaymentValidatedConsumer`) |
| Routing Service | `routing.route-resolved`, `routing.rule-changed` | NOT DEFINED IN CURRENT IMPLEMENTATION — no consumed topic confirmed in this pass |
| Audit Service | — | consumes centrally across services (`event/AuditEventConsumer.java`, `event/TopicEventTypeResolver.java` maps topic → `EventType`) |

## What this means for an Error Analyzer

- If a payment appears "stuck" (no expected downstream Kafka-driven transition ever happened), a **malformed/undeliverable message is a plausible cause the codebase's own comments anticipate**, but there is **no queryable evidence** (no DLT, no failed-message log with a REST endpoint) to confirm it directly. `audit.search` for `KAFKA_EVENT`-typed events (a real `EventType` value, see `audit.md`) is the closest available evidence, but this document does not confirm what specifically gets published under that type — NOT DEFINED IN CURRENT IMPLEMENTATION without a further read of what publishes `KAFKA_EVENT`-typed audit events.
- **Do not claim a Kafka consumer crashed or a message was dead-lettered as a confirmed root cause** — the evidence to confirm either does not exist in a queryable form today (§20 of the Phase 4.2.0 design document's Risks section already flags this scenario as one where the honest answer is `INSUFFICIENT_CONTEXT`, not a guess).
