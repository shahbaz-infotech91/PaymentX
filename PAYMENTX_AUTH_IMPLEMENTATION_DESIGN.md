# PaymentX — Auth Service Implementation Design

**DESIGN ONLY.** No source code, configuration, migrations, tests, or database state was created or
modified to produce this document. Every claim/format/algorithm documented below was read directly from
existing source — nothing here is invented. Where the existing contract does not strictly enforce
something, that is stated explicitly rather than assumed.

---

## 1. Executive Summary

API Gateway already contains a complete, real, working JWT **validator** (Nimbus, HMAC-SHA256, configurable
clock-skew-tolerant expiry checking) and Common Library already defines the exact claim shape that
validator's supporting filters read (`participantId` → `X-Participant-Id` propagation, `roles` → Spring
Security authorities). None of this needs to change. The only genuinely missing piece is an **issuer**:
something that authenticates a real credential and produces a JWT in exactly this shape, signed with the
same secret. This document designs that issuer — a login endpoint, a minimal data model, password
hashing, and JWT generation in Auth Service — without touching Gateway, without introducing a second
contract, and without inventing a payment-scheme concept or renaming anything.

---

## 2. Existing Security Architecture (as-is, verified from source)

- **API Gateway**: real JWT validation (`SecurityConfig.reactiveJwtDecoder()`), real Redis-backed API-key validation, real rate limiting, real correlation-ID propagation. Authorization at Spring Security's own layer is `anyExchange().permitAll()` — real enforcement happens in Gateway's own `GlobalFilter` chain (`ApiKeyAuthenticationGlobalFilter`, `AuthenticationEnforcementGlobalFilter`), independently confirmed via real testing in prior tasks.
- **Downstream services** (Validation/Routing/Audit/Notification/Reconciliation/Reporting): `HeaderRoleAuthenticationFilter` + `@PreAuthorize`, trusting `X-Roles`/`X-Participant-Id` headers Gateway attaches. Real, working, currently fed only by API-key lookups or hand-crafted test values.
- **Auth Service**: `permitAll()` placeholder, zero business logic, per the completed audit (`PAYMENTX_AUTH_SERVICE_AUDIT.md`).

---

## 3. Existing JWT Contract (authoritative — not redesigned)

Read directly from `paymentx-api-gateway/src/main/java/com/paymentx/gateway/config/SecurityConfig.java`
and `com.paymentx.common.constant.SecurityConstants`:

| Aspect | Existing value/format | Source |
|---|---|---|
| Signing algorithm | **HMAC-SHA256** (`SecretKeySpec(keyBytes, "HmacSHA256")`) | `SecurityConfig.reactiveJwtDecoder()` |
| Key material | Raw UTF-8 bytes of a shared secret string | Same |
| Secret source | `gateway.security.jwt-secret` property, bound from `${GATEWAY_JWT_SECRET:local-dev-only-secret-change-me-32chars}` | `GatewaySecurityProperties.jwtSecret` |
| Clock skew | Configurable, `gateway.security.jwt-clock-skew-seconds`, default **30 seconds** | `GatewaySecurityProperties.jwtClockSkewSeconds` |
| Expiry (`exp`) validation | **Enforced** — `JwtTimestampValidator` wrapped in `DelegatingOAuth2TokenValidator`, tolerant of the configured clock skew | `SecurityConfig.reactiveJwtDecoder()` |
| Issuer (`iss`) validation | **NOT enforced** — no issuer validator is configured on the decoder; a token with any `iss` value (or none) passes as far as the Gateway's decoder itself is concerned | Verified: only `JwtTimestampValidator` is set via `decoder.setJwtValidator(...)`, replacing Nimbus's own default validator chain entirely |
| Audience (`aud`) validation | **NOT enforced** — no audience validator configured | Same |
| Subject (`sub`) | **DEFINED** (`SecurityConstants.CLAIM_SUBJECT = "sub"`) but **not read anywhere in Gateway's filter code** — reserved by contract, not currently functional | Grep-confirmed: no `getClaimAsString(CLAIM_SUBJECT)` call exists in `paymentx-api-gateway` |
| `roles` claim | **Functionally required** for authorization to work — read via `JwtGrantedAuthoritiesConverter.setAuthoritiesClaimName(SecurityConstants.CLAIM_ROLES)` ("roles"), each value prefixed with `SecurityConstants.ROLE_PREFIX` ("ROLE_") to form a Spring Security authority. Expected format: a JSON array of plain role-name strings (e.g. `["ROUTING_ADMIN"]` — no existing `"ROLE_"` prefix in the claim itself; the converter adds it) | `SecurityConfig.rolesClaimAuthoritiesConverter()` |
| `participantId` claim | **Functionally required** for participant propagation — read via `jwt.getClaimAsString(SecurityConstants.CLAIM_PARTICIPANT_ID)` ("participantId"), a **plain string** (not nested), set verbatim as the `X-Participant-Id` header if present and non-blank | `JwtParticipantPropagationGlobalFilter.resolveExchange()` |
| `tokenType` claim | **DEFINED** (`SecurityConstants.CLAIM_TOKEN_TYPE = "tokenType"`, values `ACCESS`/`REFRESH`/`SERVICE`) but **not read anywhere in Gateway's filter code** — reserved by contract, not currently functional | Grep-confirmed: no usage in `paymentx-api-gateway` |
| Bearer scheme | `Authorization: Bearer <token>` (`SecurityConstants.BEARER_PREFIX = "Bearer "`), extracted via `ServerBearerTokenAuthenticationConverter` (Spring Security's standard converter) | `SecurityConfig.securityWebFilterChain()` |

**Precise, honest distinction (not previously stated this exactly anywhere in prior audits): Gateway
strictly enforces only signature validity and expiry.** `sub`/`iss`/`tokenType` are part of the *defined*
contract in Common Library but are not currently *consumed* by any Gateway code path. `roles` and
`participantId` are the two claims that are both defined AND functionally load-bearing today. This
design still populates all five claims — not because Gateway requires it today, but because Common
Library already defines them as the intended contract, and populating them now avoids a second silent
gap the moment Gateway (or any other consumer) is later extended to actually read them.

---

## 4. Auth Target Architecture

```
Client
 ↓
Auth Service (NEW)
 ├─ POST /api/v1/auth/login
 ├─ Identity lookup (new Credential/User table)
 ├─ Password verification (BCrypt)
 ├─ Role/participant lookup (new + existing Validation Service participant data)
 └─ JWT generation (HMAC-SHA256, SAME secret Gateway already validates against)
 ↓
JWT (conforms exactly to §3's contract)
 ↓
API Gateway (UNCHANGED)
 ├─ reactiveJwtDecoder() — validates signature + expiry (existing, unmodified)
 ├─ JwtGrantedAuthoritiesConverter — roles → authorities (existing, unmodified)
 └─ JwtParticipantPropagationGlobalFilter — participantId → X-Participant-Id (existing, unmodified)
 ↓
Downstream services (UNCHANGED) — @PreAuthorize / HeaderRoleAuthenticationFilter, now receiving a
genuinely trustworthy X-Roles/X-Participant-Id for the first time
```

---

## 5. Data Model (design only — no migrations created)

**Minimum entities, and why each is or isn't needed:**

| Concept | Needed? | Design |
|---|---|---|
| **User / identity** | YES | A new `auth_user` table: `id` (UUID), `username` (unique — could be an email or an operator-assigned login name; not a participant's real name), `participant_id` (string, matching the existing platform-wide format — `BANK001`-style — NOT a foreign key across a database boundary, since Auth Service and Validation Service are separate databases, consistent with this platform's established "service-local copy of a shared identifier" convention), `status` (ACTIVE/DISABLED), `created_at`/`updated_at` (matching `AuditableEntity` convention already used everywhere else). |
| **Credential** | YES, but as a field on the same table, not a separate one, for a minimum viable design | `password_hash` (String, BCrypt output — see §6), `failed_login_count` (int, for lockout — optional hardening), `last_login_at` (nullable timestamp). A separate `credential` table would only be warranted if a user could have multiple credential types (e.g. password + API key) — not required for a minimum implementation. |
| **Role** | YES, minimal | A new `auth_user_role` join table (`user_id`, `role` string) — NOT a full `role`/`permission` normalized model. Every downstream service already only checks simple role-name strings (`@PreAuthorize("hasRole('ROUTING_ADMIN')")`) — there is no evidence anywhere in this codebase of a permission-level (as opposed to role-level) authorization check, so designing a full RBAC permission model would be speculative, not evidence-grounded. |
| **Permission** | NOT NEEDED for a minimum implementation | No existing downstream code checks anything more granular than a role name. |
| **Participant** | **Already exists — do not duplicate.** Validation Service's real `participant` table (`id`, `bank_id`, `legal_name`, `status`, `onboarded_at`, `updated_at`) is the authoritative participant model. Auth Service's `auth_user.participant_id` simply stores the same `bank_id`-format string value — it does not own or duplicate participant business data (legal name, onboarding status, scheme eligibility). |
| **Token / Refresh token** | NOT NEEDED for a stateless-access-token-only minimum (see §12) | If refresh tokens are added later, a `refresh_token` table (`id`, `user_id`, `token_hash`, `expires_at`, `revoked_at`) would be needed then — not designed further here since §12 classifies refresh as RECOMMENDED, not REQUIRED. |
| **Session** | NOT NEEDED | Every service in this platform is `SessionCreationPolicy.STATELESS` — no session concept exists or is warranted. |

**Clear separation, as required:**
- **Identity** = `auth_user` (who this is).
- **Credential** = `auth_user.password_hash` (how they prove it).
- **Authorization** = `auth_user_role` (what they're allowed to do — a claim source, not an enforcement point; enforcement stays entirely downstream, unchanged).
- **Participant association** = `auth_user.participant_id` (a plain string reference to Validation Service's existing, authoritative participant record — never a cross-database foreign key).

---

## 6. Password Security

- **Hashing: BCrypt**, via Spring Security's own `BCryptPasswordEncoder` — already transitively available today (part of `spring-security-crypto`, pulled in by the `spring-boot-starter-security` dependency Auth Service **already has**; no new dependency needed for hashing itself). This is Spring Security's own recommended default, not a custom or invented mechanism.
- **Salt**: handled automatically and internally by `BCryptPasswordEncoder` (a random salt is embedded in each generated hash) — no separate salt column or manual salt-management design needed.
- **Verification**: `passwordEncoder.matches(rawPassword, storedHash)` — Spring Security's own standard API, timing-safe by design (BCrypt's own comparison is not a naive `String.equals`).
- **Failed login handling**: increment `auth_user.failed_login_count` on a failed attempt; a minimum design does not require automatic lockout (no evidence any other PaymentX service implements account lockout, so this would be new, unprecedented policy) — recording the count for observability/future policy is sufficient for Phase 1. **Never log the raw password or the hash.**
- **No plaintext password storage anywhere, at any stage** — the login request DTO carries the raw password only in memory, for the single `matches()` call, never persisted or logged.
- **No custom cryptography** — BCrypt only, matching Spring Security's own built-in, audited implementation.

---

## 7. Login API

Matches this platform's own established controller conventions exactly (same `ApiResponse<T>` envelope,
same validation annotation style already used by every other controller in this codebase).

| Aspect | Design |
|---|---|
| HTTP method | `POST` |
| Path | `/api/v1/auth/login` (matches the `/api/v1/{resource}` convention every other service uses; `auth` as the resource segment, consistent with the module's own name) |
| Request DTO | `LoginRequest(String username, String password)` — `@NotBlank` on both, matching `PaymentValidationRequest`'s existing `@NotBlank`-per-field style |
| Response DTO (success) | `ApiResponse.success(LoginResponse(...))` where `LoginResponse(String accessToken, String tokenType, long expiresInSeconds)` — `tokenType` here is the HTTP response field `"Bearer"`, not to be confused with the JWT claim `SecurityConstants.CLAIM_TOKEN_TYPE` (`ACCESS`/`REFRESH`/`SERVICE`), which is a different, JWT-internal concept |
| Response (failure) | `ApiResponse.error(ErrorResponse.of(ErrorCodes.UNAUTHORIZED, "Invalid username or password", path))` — reusing the **existing** `ErrorCodes.UNAUTHORIZED` constant and `ApiResponse`/`ErrorResponse` shape every other service already uses; no new error envelope |
| Validation | `@Valid @RequestBody`, matching every other controller's convention exactly |
| HTTP status (success) | 200 OK |
| HTTP status (failure) | 401 Unauthorized (see §12 for the full failure-status table) |

**Only one authentication API is designed** — a single login endpoint. Refresh/logout endpoints are
deliberately not designed as part of the minimum (§12).

---

## 8. JWT Issuance

Every claim value mapped to its exact source, with no invented values:

| Claim | Value | Source |
|---|---|---|
| `sub` | `auth_user.id` (the UUID) or `auth_user.username` — **recommendation: `username`**, since it's the human-meaningful identity and nothing downstream currently parses `sub` as a UUID | New `auth_user` row |
| `iss` | A fixed string identifying Auth Service, e.g. `"paymentx-auth-service"` — not currently validated by Gateway (§3), but populated for contract completeness and future-proofing | New, service-level constant |
| `roles` | JSON array of role-name strings, e.g. `["ROUTING_ADMIN"]` — **no `"ROLE_"` prefix in the claim itself**, since Gateway's `JwtGrantedAuthoritiesConverter` adds that prefix on read | `auth_user_role` rows for this user |
| `participantId` | The plain string value from `auth_user.participant_id` (e.g. `"BANK001"`) | New `auth_user` row |
| `tokenType` | `"ACCESS"` (`SecurityConstants.TOKEN_TYPE_ACCESS`) for this login flow | Existing Common Library constant, reused verbatim |
| `iat` (issued-at) | Real current timestamp at issuance | Standard JWT claim, set by whichever JWT library is used |
| `exp` (expiry) | `iat` + a configured TTL (e.g. 15–60 minutes — an open question, §23, not a business rule this document invents) | Standard JWT claim |
| Signing key | The **same** `GATEWAY_JWT_SECRET` value Gateway already reads — provisioned to Auth Service via the identical environment-variable name, never duplicated as a second literal (§13) | Shared secret, not a new one |
| Signing algorithm | HMAC-SHA256 — **must match exactly**, since Gateway's decoder is hardcoded to `"HmacSHA256"` | §3 |

**Library choice (not implemented here, only identified):** Auth Service currently has no JWT-signing
library dependency at all. Gateway's *validation* capability comes from Nimbus
(`NimbusReactiveJwtDecoder`, part of the OAuth2 resource-server stack). For consistency with an
already-proven-compatible library in this exact codebase, **Nimbus (`com.nimbusds:nimbus-jose-jwt`) is
the recommended signing library** for Auth Service, rather than introducing a second, different JWT
library (e.g. `io.jsonwebtoken:jjwt`) for the same job. This is a dependency **recommendation** for a
later implementation phase — no dependency is added by this design task.

---

## 9. Gateway Compatibility — explicit claim mapping

| Auth Service issues | Gateway reads it as | Gateway code (unchanged) |
|---|---|---|
| `roles: ["ROUTING_ADMIN"]`, HS256-signed with the shared secret | `ROLE_ROUTING_ADMIN` Spring Security authority | `JwtGrantedAuthoritiesConverter` (`SecurityConfig.rolesClaimAuthoritiesConverter()`) |
| `participantId: "BANK001"` | `X-Participant-Id: BANK001` header on the downstream request | `JwtParticipantPropagationGlobalFilter.resolveExchange()` |
| `exp: <timestamp>` | Rejected if expired, tolerant of ±30s (default) clock skew | `JwtTimestampValidator` in `reactiveJwtDecoder()` |
| Valid HMAC-SHA256 signature with the shared secret | Accepted | `NimbusReactiveJwtDecoder.withSecretKey(...)` |
| `sub`, `iss`, `tokenType` | Present in the token but **not read by any current Gateway code** | — (contract-reserved, not yet consumed) |

**No Gateway file requires any change for this token to be accepted and correctly propagated.** This is
verified by direct mapping to existing, unmodified Gateway source — not asserted without evidence.

---

## 10. Participant Identity

```
auth_user.participant_id (new, string, e.g. "BANK001")
 ↓
JWT participantId claim (verbatim)
 ↓
Gateway's JwtParticipantPropagationGlobalFilter → X-Participant-Id header (existing, unchanged)
 ↓
Downstream services trust this header exactly as they already trust it today for the API-key path
```

**No second participant model is introduced.** Validation Service's existing `participant` table (with
real, seeded rows like `BANK001`/`BANK002`, `status: ACTIVE`) remains the single authoritative source of
what a participant *is*. Auth Service's `auth_user.participant_id` is a reference value only — an
operator, when provisioning a new `auth_user` row, is expected to use a `participant_id` value that
already exists in Validation Service's `participant` table, the same way every other service in this
platform already treats `participantId`/`bank_id` as a shared, cross-service string identifier without a
cross-database foreign key (each service owns its own database; this is the established, repo-wide
pattern, not a new one invented here).

**How this identity is trusted downstream:** exactly the same way an API-key-derived `X-Participant-Id`
is already trusted today — the trust boundary is "Gateway attached this header, therefore it's real,"
unchanged by this design.

---

## 11. Authorization

**Not redesigned.** Existing downstream authorization machinery (`@PreAuthorize("hasRole('...')")` +
`HeaderRoleAuthenticationFilter` reading `X-Roles`, present in Routing/Audit/Notification/Reconciliation/
Reporting Service) is real and already correct. This design's only contribution to authorization is
supplying it with a genuinely trustworthy `roles` claim for the first time — no downstream file needs to
change.

**Roles vs. permissions:** only roles exist as a concept anywhere in this codebase today (§5) — no
permission-level check exists downstream, so none is designed here.

**Participant isolation:** remains an open question already flagged in a prior design task
(`PAYMENTX_PHASE_1_CORE_REMEDIATION_DESIGN.md`, §20 Open Question #4) — not resolved by this document,
since it requires a decision (Gateway-enforced vs. per-service-enforced) this task's evidence does not
settle.

---

## 12. Token Lifecycle

- **Issue**: via the new login endpoint (§7), producing a short-lived access token (§8).
- **Validate**: already fully implemented by Gateway (§3/§9) — no new validation code needed anywhere.
- **Expire**: enforced by Gateway's existing `JwtTimestampValidator` — a token past its `exp` (beyond the
  configured clock skew) is rejected automatically; no server-side revocation list is needed for simple
  expiry.

**Is a refresh token actually required by PaymentX?** No evidence in this codebase demonstrates a current
requirement for long-lived sessions or silent re-authentication — every service is stateless, and the
existing platform pattern (e.g. Control Center's `DashboardAuthFilter`) favors simple, short-lived,
explicit tokens over session-like mechanisms. **Classification: RECOMMENDED, not REQUIRED.** A short
access-token TTL (§8, open question on exact duration) combined with a straightforward "log in again"
UX is a legitimate, simpler minimum. `SecurityConstants.TOKEN_TYPE_REFRESH` already being defined in
Common Library signals refresh was anticipated as a future capability, not that it is mandatory for Phase
1 completion.

**Is logout/revocation required?** With a stateless, short-lived-access-token-only design (no refresh
token, no session table), there is nothing server-side to revoke — the token simply expires. **Logout is
therefore OPTIONAL** for this minimum design; a client-side "discard the token" is sufficient. If refresh
tokens are added later, revocation becomes meaningfully necessary at that point, not before.

---

## 13. Secret Management

**Reuse the existing mechanism exactly — do not invent a second one.** Every secret-like value in this
platform (§ audit) uses Spring's `${VAR:default}` property placeholder convention exclusively — no AWS
Secrets Manager, no Docker secrets, no other mechanism exists anywhere in this repository to mirror.

- Auth Service's signing secret must be **the same value** as Gateway's `GATEWAY_JWT_SECRET` — provisioned
  to Auth Service via the identical environment variable name (`GATEWAY_JWT_SECRET`), not a
  differently-named duplicate that happens to hold the same value (which would risk drifting out of sync).
- In `application.yml`/`application-dev.yml`, this would be declared as
  `${GATEWAY_JWT_SECRET:local-dev-only-secret-change-me-32chars}` — **the exact same property-default
  pattern already used verbatim in `paymentx-api-gateway`'s own config**, including reusing its
  self-labeled non-production placeholder default for local-dev symmetry.
- **No secret value is printed anywhere in this document** — this section describes the *mechanism*
  (an environment variable name and a property-binding pattern), never an actual value.
- Database credentials for Auth Service already follow this exact pattern from a prior remediation
  (`${AUTH_DB_USERNAME:postgres}` / `${AUTH_DB_PASSWORD:postgres}`) — the signing secret should be
  provisioned identically, not differently.

---

## 14. Failure Handling — 401 vs. 403, explicit

| Scenario | Status | Rationale |
|---|---|---|
| Unknown username | **401** | Login failure — do not reveal whether the username exists (standard practice; same generic message as wrong password) |
| Wrong password | **401** | Same generic `"Invalid username or password"` message as unknown username — deliberately indistinguishable to the client, to avoid username enumeration |
| Disabled account (`status = DISABLED`) | **401** | Still a login-time authentication failure, not an authorization question — the account cannot authenticate at all |
| Expired account | **NOT APPLICABLE to the minimum design** — no account-expiry concept exists or is proposed (out of scope unless a real requirement emerges) | — |
| Invalid JWT (bad signature/malformed) | **401** | Already Gateway's existing behavior (§3), unchanged |
| Expired JWT | **401** | Already Gateway's existing behavior, unchanged |
| Missing JWT (no token at all, no API key either) | **401** | Already `AuthenticationEnforcementGlobalFilter`'s existing, tested behavior, unchanged |
| Insufficient role | **403** | Already downstream `@PreAuthorize`'s existing behavior — a *valid, authenticated* identity lacking a required role is a Forbidden case, not Unauthorized |
| Wrong participant (accessing another participant's data) | **403**, conceptually — but no enforcement mechanism currently exists for this specific check (§11's open question); this row documents the *intended* status code for when that check is eventually designed, not a currently-implemented behavior | — |

**The distinction, stated explicitly per instruction:** 401 = "I don't know who you are / your credential
is invalid or absent." 403 = "I know exactly who you are, and you're not allowed to do this."

---

## 15. Audit

No dedicated `LOGIN_SUCCESS`/`LOGIN_FAILURE`/`TOKEN_ISSUED`/`AUTHORIZATION_DENIED` event types exist in
Audit Service's schema today (confirmed in the prior audit — only `SECURITY_EVENT`, `API_REQUEST`,
`API_RESPONSE` are generically available). Two options, neither implemented here:

1. **Minimum**: reuse the existing `SECURITY_EVENT` type for both login success and failure, with the
   outcome distinguished in the event's free-text detail/payload field (matching the pattern
   `PaymentEngineImpl.recordAudit()` already uses — a fixed `action` string plus free-text `details`).
2. **More precise (requires a schema change to Audit Service, which this task does not authorize)**: add
   `LOGIN_SUCCESS`/`LOGIN_FAILURE`/`TOKEN_ISSUED` to Audit Service's `chk_audit_event_type` CHECK
   constraint — a real, if small, migration to a service this task's instructions explicitly say not to
   modify. **Recommendation: defer this to the actual implementation phase, and use option 1 initially**,
   since it requires zero changes to Audit Service and is immediately available.

Either way, Auth Service would publish these audit facts the same way every other service already does —
via its own local audit record + outbox pattern (mirroring `PaymentEngineImpl.recordAudit()` +
`createOutboxEvent()`), not a new mechanism.

---

## 16. Observability

- **Correlation ID**: propagate via the existing `X-Correlation-Id` header / MDC convention
  (`HeaderConstants.CORRELATION_ID`), identical to every other service.
- **Authentication logs**: log username (not password), outcome (success/failure), and failure *reason
  category* (unknown user / wrong password / disabled) at INFO (success) / WARN (failure) level, matching
  the log-level convention already used in `PaymentEngineImpl`.
- **Failure reason**: a short, generic category, never verbose enough to aid credential-guessing (e.g. log
  `"reason=invalid_credentials"`, never log which of username/password was wrong).
- **Latency**: standard Spring Boot Actuator HTTP timing metrics (`http_server_requests_seconds_*`),
  already automatically available for any new `@RestController` endpoint — no custom metric required for
  a minimum design.
- **Safe metrics** (design-only, not added): a `auth_login_attempts_total{outcome="success|failure"}`
  counter would follow the exact naming convention already used by other services' real business metrics
  (`reconciliation_record_classification_total`, `notification_channel_success_total`) — documented as a
  future addition, not implemented now.
- **Never logged**: raw password, generated JWT value, signing secret, any API key.

---

## 17. Database Migration Strategy

**Yes — Auth Service should use the existing Liquibase pattern**, exactly as every other service with a
real schema already does (`db/changelog/db.changelog-master.yaml` + numbered changesets under
`db/changelog/changes/`, e.g. audit-service's `V1_0_0__create_source_event_table.yaml` naming
convention). This requires adding `liquibase-core` to Auth Service's `pom.xml` — deliberately **not**
added by the earlier connectivity-only remediation (Defect 3), specifically because there was no schema
yet to manage. There is now a real schema to design, so this becomes appropriate at implementation time.

- **Initial schema**: one changeset creating `auth_user` and `auth_user_role` (§5).
- **Seed strategy**: mirroring Validation Service's own seed-changeset convention (e.g. its
  `V1_0_0`-numbered seed of `BANK001`/`BANK002` test participants) — a seed changeset creating one or two
  known development-only users (clearly named, e.g. `dev-admin`), matching the platform's existing
  precedent for local-dev-only seed data shipped via Liquibase, not a manual SQL script.
- **Development user creation**: via that same seed changeset, with a BCrypt-hashed password for a known
  (documented, non-production, clearly-labeled) local-dev credential — never a plaintext password in the
  changeset, matching this design's own §6 rule even for seed data.
- **Migration rollback**: Liquibase's own standard `rollback` changeset tags, exactly as available to
  every other service already using Liquibase — no new rollback mechanism needs to be designed.

---

## 18. Backward Compatibility

The existing, already-proven payment lifecycle (Gateway → Validation → Kafka → Payment → Routing →
Settlement → Audit → Notification → Reporting) has zero dependency on Auth Service today (confirmed in
the prior audit) — introducing a real login endpoint changes nothing about that flow's own internals.

**Rollout strategy** (design only, matching the requested shape and this platform's own established
incremental-rollout discipline from the Routing integration):

```
build
 ↓
unit tests (password hashing, JWT claim shape, login success/failure)
 ↓
Auth local validation (login endpoint alone, no Gateway involved yet)
 ↓
Gateway compatibility (issue a real token, confirm Gateway's EXISTING validator accepts it — no Gateway
   code change, this step is verification only)
 ↓
protected read-only API (use a real issued token against an existing read-only endpoint, e.g. Routing
   Service's GET /api/v1/routes, to prove the full chain works end-to-end for a low-risk operation)
 ↓
protected payment flow (use a real issued token through the already-proven real-transaction path)
 ↓
full regression (the same module-by-module sequential discipline already established throughout this
   session's remediation work, with Postgres-connection monitoring)
```

---

## 19. GitHub / Security Safety

- No JWT secret, database password, API key, token, or private key is hardcoded anywhere in this design
  — every secret uses the existing `${VAR:default}` pattern (§13).
- No real-world payment-network or provider name is introduced by this design.
- `INSTANT_PAYMENT`/`REAL_TIME_PAYMENT`/`CARD_PAYMENT` are not referenced or altered by this design at
  all — Authentication is fully orthogonal to payment type/scheme concerns.
- No secret value appears anywhere in this document.

---

## 20. Implementation Order

Adjusted from the requested template based on actual repository evidence:

1. **Dependencies** — add `liquibase-core` (schema management) and a JWT signing library (`nimbus-jose-jwt`, recommended for consistency with Gateway's existing choice, §8).
2. **Database model** — `auth_user`, `auth_user_role` entities.
3. **Liquibase migration** — initial schema + dev-only seed user, per §17.
4. **Password security** — `BCryptPasswordEncoder` bean, verification logic.
5. **Repository** — `AuthUserRepository` (Spring Data JPA, matching every other service's repository convention).
6. **Authentication service** — credential lookup + verification (`AuthenticationService`, mirroring the `*ServiceImpl` naming convention used everywhere else).
7. **JWT issuer** — a `JwtIssuer` component producing the exact claim shape of §8.
8. **Login API** — the controller + DTOs of §7.
9. **Security configuration** — extend the existing `SecurityConfig` to permit `POST /api/v1/auth/login` publicly (everything else remains `permitAll()` until this service gains other endpoints, unchanged from today).
10. **Audit** — wire login success/failure into the existing `SECURITY_EVENT` type (§15, option 1).
11. **Tests** — per §21/22 below.
12. **Gateway compatibility verification** — no code change, a real test confirming acceptance.
13. **Real local E2E** — Auth → JWT → Gateway → a real protected endpoint.
14. **Full regression** — this platform's own established sequential-module discipline.

This order was chosen specifically because steps 1–8 are entirely self-contained within Auth Service
(zero cross-service risk), step 9 is the first point of any external surface change (a new public
endpoint — still zero risk to other services), and steps 12–14 are pure verification, not further
implementation — matching the Routing Service integration's own successful "isolated first, verify
compatibility second" pattern from the prior task.

---

## 21. Testing Strategy (design only — none executed or added)

| # | Scenario | Design |
|---|---|---|
| 1 | Valid login | Real username + real password → 200, real JWT returned |
| 2 | Invalid password | Real username + wrong password → 401, generic message |
| 3 | Unknown user | Non-existent username → 401, identical generic message to #2 |
| 4 | Password hashing | Unit test: `BCryptPasswordEncoder.matches()` behaves correctly for a known hash/password pair; a stored hash never equals the raw password string |
| 5 | JWT generation | Unit test: issued token, when decoded (test-side, not via Gateway), contains all five claims (§8) with correct values |
| 6 | Required claims | Unit test: `roles` and `participantId` are always present and correctly typed (array of strings; plain string, respectively) |
| 7 | JWT expiry | Unit test: `exp` claim is `iat` + configured TTL, not a hardcoded/wrong offset |
| 8 | Gateway JWT validation compatibility | **Integration test**: issue a real token via the new `JwtIssuer`, feed it through API Gateway's real, unmodified `reactiveJwtDecoder()` bean (in a Gateway-side test, or via a shared secret + Nimbus decode in an Auth-Service-side test) — proves byte-for-byte compatibility, not merely "looks right" |
| 9 | Missing token | Already covered by Gateway's own existing test suite (`AuthenticationEnforcementGlobalFilterTest`, prior remediation) — not re-designed here |
| 10 | Invalid token | Same — already covered by Gateway's existing tests |
| 11 | Insufficient role | Already covered by each downstream service's own existing security tests (e.g. `RoutingSecurityTest`) — not re-designed here |
| 12 | Wrong participant | **NOT APPLICABLE** — no enforcement mechanism exists to test yet (§11) |

**One real local E2E validation** (designed, not executed): obtain a real JWT from the new login endpoint
→ present it as a real `Authorization: Bearer` header to API Gateway → confirm Gateway's real, unmodified
validator accepts it and correctly attaches `X-Participant-Id`/authorities → confirm a real downstream
protected read-only endpoint (e.g. Routing Service's admin-only rule-creation endpoint, or a similarly
role-gated existing endpoint) responds according to the token's actual role, mirroring this platform's
own established `TEST-E2E-*` real-transaction discipline used throughout every prior implementation task
in this session.

---

## 22. Risks

- **Secret synchronization risk**: Auth Service and API Gateway must share the exact same
  `GATEWAY_JWT_SECRET` value at runtime — a real, if simple, operational coordination requirement (§13
  mitigates this by reusing the identical environment variable name, not a copy).
- **Algorithm mismatch risk**: if a different JWT library's default settings are used carelessly (e.g. a
  library defaulting to RS256), Gateway's decoder (hardcoded to `"HmacSHA256"`) would reject every token
  — §8's library recommendation and explicit algorithm callout exists specifically to prevent this.
- **Claim-name typo risk**: since Gateway reads claims by exact string name (`"roles"`, `"participantId"`)
  rather than a shared typed contract class, a typo in Auth Service's issuance code would silently produce
  tokens that decode successfully but grant zero authorities / no participant propagation — mitigated by
  reusing `SecurityConstants.CLAIM_ROLES`/`CLAIM_PARTICIPANT_ID` directly (already a shared Common Library
  constant) rather than re-typing the literal strings in Auth Service.
- **Downstream authorization now becomes load-bearing for the first time**: once real tokens exist, any
  latent bug in the already-existing `@PreAuthorize`/`HeaderRoleAuthenticationFilter` machinery
  (previously only exercised by test values) could surface for the first time under real traffic — this
  is a real regression-testing risk, addressed by §18's rollout strategy's "protected read-only API"
  step before touching the payment flow.

---

## 23. Open Questions (require a decision before implementation)

1. **Access token TTL** — no existing requirement specifies a duration; 15–60 minutes is a reasonable
   unset placeholder range, not a business decision this document makes.
2. **Refresh tokens** — classified RECOMMENDED (§12), not REQUIRED; a real decision on whether Phase 1
   needs them is still open.
3. **Logout/revocation** — classified OPTIONAL (§12) under a no-refresh-token design; revisit if refresh
   tokens are added.
4. **Audit event vocabulary** (§15) — reuse generic `SECURITY_EVENT` now, or extend Audit Service's schema
   for precise `LOGIN_SUCCESS`/`LOGIN_FAILURE` types? The former requires no cross-service change; the
   latter is more precise but is explicitly out of this task's authorized scope.
5. **Participant isolation enforcement point** (§11) — Gateway-level or per-downstream-service-level?
   Still unresolved from the prior Routing design task, not settled here either.
6. **Account lockout policy** (§6) — is recording `failed_login_count` sufficient for Phase 1, or is an
   active lockout threshold required? No existing precedent in this codebase to derive an answer from.
7. **`sub` claim value** — username or user ID (§8)? Either is technically compatible with Gateway (since
   `sub` isn't currently read by any Gateway code), but the choice affects future extensibility.

---

## 24. Final Recommendation / Final Design Decisions

1. **Minimum Auth implementation required for Phase 1 Auth to be COMPLETE:** a real login endpoint,
   password verification against a real hashed credential, and JWT issuance conforming exactly to the
   existing Gateway-validated claim contract (§3/§8). Refresh tokens, logout, and fine-grained permissions
   are not required for completeness — only recommended/optional (§12).
2. **Gateway components that remain completely unchanged:** all of them —
   `SecurityConfig.reactiveJwtDecoder()`, `rolesClaimAuthoritiesConverter()`, `securityWebFilterChain()`,
   `JwtParticipantPropagationGlobalFilter`, `ApiKeyAuthenticationGlobalFilter`,
   `AuthenticationEnforcementGlobalFilter` — none require modification (§9).
3. **Exact JWT claims Auth must issue:** `sub`, `iss`, `roles` (array), `participantId` (string),
   `tokenType`, `iat`, `exp` — all five contract claims plus the two JWT-standard ones (§8).
4. **Exact database entities required:** `auth_user`, `auth_user_role` — nothing more (§5).
5. **Is refresh token required?** No — RECOMMENDED only.
6. **Is logout/revocation required?** No — OPTIONAL, and only meaningfully relevant if refresh tokens are
   later added.
7. **How will participant identity be established?** Via a plain string reference (`auth_user.
   participant_id`) to Validation Service's already-existing, authoritative `participant` table — no
   second participant model (§10).
8. **How will secrets be managed?** By reusing the exact same `GATEWAY_JWT_SECRET` environment variable
   Gateway already reads, via the identical `${VAR:default}` Spring property-placeholder convention
   already used throughout this platform (§13).
9. **Safest rollout strategy:** build → unit tests → Auth-local validation → Gateway-compatibility
   verification (no Gateway change) → protected read-only endpoint → protected payment flow → full
   regression (§18) — isolated-first, verify-compatibility-second, matching the Routing Service
   integration's own successful precedent from the immediately prior implementation task in this session.

---

**STOP.** No source code, configuration, database migration, test, or runtime state was created or
modified. No dependency was added. No user, token, or transaction was created. No service was restarted.
Routing Service, Payment Service, and API Gateway were not modified. Phase 3.10 was not started. Only
this design document was created.
