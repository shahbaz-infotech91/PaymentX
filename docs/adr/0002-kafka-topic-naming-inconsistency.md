# ADR 0002: Kafka Topic Naming Convention Needs Standardization

**Status:** Open (not yet resolved)
**Date:** 2026-08-09

## Context
Validation Service publishes scheme-validated events to hyphen-separated
topic names: `instant-payment-validated`, `card-payment-validated`, `real-time-payment-validated`.

Payment Service's specification (Module 3) requested dot-separated output
topic names: `payment.processing`, `payment.completed`, `payment.failed`,
`payment.returned`, `payment.reversed`, `payment.cancelled`,
`payment.timeout`.

These are two different naming conventions coexisting in the same Kafka
cluster.

## Decision (interim)
Payment Service's KafkaTopics constants class documents both conventions
as-is rather than silently normalizing one to match the other:
- Renaming Validation Service's already-referenced input topics is a
  breaking change for any other consumer relying on those names.
- Silently converting Payment Service's specified output names to match
  the hyphen convention would diverge from what was explicitly requested.

## Consequences
Anyone onboarding onto the Kafka topic list has to learn two conventions
instead of one - minor cognitive overhead, no functional problem today.

## Revisit When
Before Routing Service is built (it will both consume Payment Service's
output topics AND produce its own) - pick ONE convention project-wide
(recommendation: hyphen-separated, matching Kafka community convention
and what's already deployed in Validation Service) and migrate Payment
Service's output topic names to match, coordinating the rename with any
consumers that exist by that point.
