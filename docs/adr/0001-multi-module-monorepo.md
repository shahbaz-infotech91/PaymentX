# ADR 0001: Multi-Module Monorepo over Polyrepo

**Status:** Accepted
**Date:** 2026-08-08

## Context
PaymentX consists of ~9 independently deployable services plus a shared
contracts library. We need to decide how source code is organized across
repositories before writing the first service.

## Decision
Use a single Git repository with Maven multi-module structure. One root
`pom.xml` aggregates all service modules and the shared `paymentx-common`
library.

## Consequences
**Positive:**
- `paymentx-common` changes are versioned atomically with the services that
  consume them - no cross-repo version-bump PRs to coordinate.
- Single `mvn clean install` builds and tests the entire platform locally,
  matching what CI does.
- Easier to enforce the "common has no business logic" rule via code review
  when everything is visible in one PR.

**Negative / Trade-offs accepted:**
- CI must be configured to build only affected modules on each change
  (deferred to the CI/CD module) or build times will grow linearly with
  service count.
- Team-level access control (e.g. "Reconciliation team can't merge to
  Payment Service") is harder in a monorepo and requires CODEOWNERS +
  branch protection rather than repo-level permissions.

## Revisit When
Team size exceeds ~15-20 engineers across multiple independent squads, or
when a single service's build/test time starts materially slowing down
unrelated services' CI. At that point, re-evaluate polyrepo per bounded
context (this is what most fintechs at scale do; we are deliberately not
starting there).
