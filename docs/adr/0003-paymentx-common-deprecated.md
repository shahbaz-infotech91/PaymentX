# ADR 0003: `paymentx-common` Deprecated in Favor of `paymentx-common-library`

**Status:** Accepted — deprecation in progress
**Date:** 2026-08-10

## Context
`paymentx-common` was created in Module 1 as the shared contracts module
(event envelope, base exception, correlation-ID filter). A second,
separately-scaffolded module, `paymentx-common-library`, existed in the
repository as an empty shell (no source, no populated `pom.xml`) with a
name signaling broader intent as THE canonical shared library for every
service in the platform (API Gateway, Validation, Payment, Routing,
Notification, Audit, Reconciliation, Reporting, AI Platform).

Rather than maintaining two shared-library concepts indefinitely, the
decision was made to consolidate: `paymentx-common-library` becomes the
one and only shared library, and `paymentx-common`'s existing classes
(`PaymentEvent<T>`, `PaymentXException`, `CorrelationIdFilter`) are
migrated into it.

## Decision
- `paymentx-common-library` is the canonical shared library going forward.
- `paymentx-common`'s three existing classes are migrated verbatim (same
  package: `com.paymentx.common.*` — see ADR 0004 for why the package
  namespace is intentionally NOT renamed during this migration).
- `paymentx-common` remains in the Maven reactor (`<modules>`) and
  continues to build during the transition, but:
  - Every class in it is marked `@Deprecated(since = "2.0", forRemoval = true)`
    with Javadoc pointing to its `paymentx-common-library` replacement.
  - No service's `pom.xml` retains a `<dependency>` on it after this
    migration completes.
- `paymentx-common` is a candidate for physical deletion once confirmed
  zero remaining dependents exist (a later cleanup phase, not part of
  this migration).

## Consequences
**Positive:** Zero-downtime, zero-breaking-change transition — nothing
depends on a module that vanishes mid-migration. Deprecation warnings at
compile time make any missed reference immediately visible.

**Negative:** Two modules briefly coexist with overlapping purpose. This
is accepted as a deliberate, temporary cost of a safe migration, not a
permanent state.

## Revisit When
Every service's `pom.xml` has been confirmed to reference only
`paymentx-common-library`. At that point, remove `paymentx-common` from
the reactor's `<modules>` list and delete the module directory.
