# ADR 0004: `paymentx-common-library` as the Canonical Shared Library

**Status:** Accepted
**Date:** 2026-08-10

## Context
As PaymentX grows beyond Validation and Payment Service into a full
platform (API Gateway, Routing, Notification, Audit, Reconciliation,
Reporting, AI Platform), a genuinely general-purpose shared library
became necessary: cross-cutting concerns (event envelope, common DTOs,
exception hierarchy, security/logging primitives, constants) that every
service needs, distinct from business domain logic that must stay
service-local.

## Decision

### Scope boundary: cross-cutting concerns only, never business domain objects
`paymentx-common-library` contains ONLY infrastructure/cross-cutting
code: `PaymentEvent<T>` envelope, `ApiResponse`/`ErrorResponse`/
`PageResponse`/`HealthResponse`, the common exception hierarchy,
cross-cutting enums (`Currency`, `Country`, `EventStatus`, `MessageType`),
constants, validation utilities, security primitives (`JwtClaims`,
`SecurityContext`), logging (`LoggingContext`), annotations, mapper
utilities, and Jackson configuration.

It explicitly does NOT contain: `Money`, `PaymentStatus`, `PaymentScheme`,
`RoutingDecision`, `ValidationStatus`, `NotificationStatus`, or any other
business domain model. Those remain defined independently inside each
owning service - see Payment Service's `PaymentScheme.java` javadoc from
earlier in this project for the original reasoning: a shared
business-domain type creates version-lockstep coupling between services
that should be able to evolve independently. This boundary is
deliberate and load-bearing - a future contributor proposing to move a
business enum into this library should read this ADR first.

### Package namespace: kept as `com.paymentx.common`, NOT renamed
Despite the module/artifact being named `paymentx-common-library`, code
inside it lives under the SAME package namespace as the module it
supersedes (`com.paymentx.common`), not a new one like
`com.paymentx.commonlibrary`. This was a deliberate choice, weighed
against the "cleaner" alternative:

- Chosen (Option A): keep `com.paymentx.common`. Every consuming
  service's migration is a single pom.xml artifactId swap
  (paymentx-common -> paymentx-common-library) - zero source file
  changes, zero import statement updates anywhere in the codebase.
- Rejected (Option B): rename to a new package. "Textbook clean"
  (artifact name matches package name), but requires updating every
  import statement across every service that already depends on the
  original module - a much larger, higher-risk blast radius for a
  purely cosmetic improvement.

Given the migration's explicit goals (zero breaking changes, backward
compatible, incremental), Option A was chosen.

## Consequences
**Positive:** Every service's migration is minimal and low-risk. Existing
code continues to compile unchanged after the pom.xml swap.

**Negative:** The package name and the artifact name don't match, which
is mildly confusing to a new engineer scanning the repository for the
first time. Accepted as the correct trade-off given the migration's risk
constraints.

## Revisit When
If `paymentx-common` is ever fully deleted (ADR 0003) and the team has
bandwidth for a dedicated, low-risk-window package rename pass.
