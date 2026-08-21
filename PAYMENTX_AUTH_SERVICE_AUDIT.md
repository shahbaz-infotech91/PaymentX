# PaymentX — Auth Service Read-Only Security & Architecture Audit

**Read-only. No source, configuration, database, or runtime state was modified to produce this
document.** No secret values are printed anywhere below.

---

## 1. Executive Summary

Auth Service is a **bare skeleton**: two Java classes total (`AuthServiceApplication`,
`SecurityConfig`), zero controllers, zero entities, zero repositories, zero JWT-related code, zero
password-handling code. Its own `pom.xml` describes it as *"JWT issuance and verification service"* —
none of that exists yet. Database connectivity was fixed in a prior remediation (real HikariCP pool to a
real Postgres database), but `paymentx_auth` itself has zero tables. This finding is independently
corroborated by Control Center's own `SecurityConfig` class, which documents (as of its own Phase 5/6
work) *"Auth Service has zero endpoints today (confirmed live again)."*

Real JWT **validation** infrastructure already exists and is fully wired — but in **API Gateway**, not
Auth Service. Real, working claim contracts (`sub`, `iss`, `roles`, `participantId`, `tokenType`) already
exist in Common Library, unused by anyone as an issuer, but already consumed as a validator by API
Gateway. This means Authentication's missing piece is narrower than "build JWT from scratch" — it is
specifically "build a token **issuer** conforming to a contract that already has a working **validator**."

**Classification: C — AUTH NOT IMPLEMENTED** (justification in §20).

---

## 2. Current Auth Architecture

```
paymentx-auth-service/
├── src/main/java/com/paymentx/auth/
│   ├── AuthServiceApplication.java     [IMPLEMENTED - bare @SpringBootApplication]
│   └── config/SecurityConfig.java      [IMPLEMENTED - but a documented, deliberate placeholder]
├── src/main/resources/
│   ├── application.yml                  [IMPLEMENTED - base config + incomplete "docker" profile block]
│   └── application-dev.yml              [IMPLEMENTED - real Postgres connectivity only]
├── src/test/java/com/paymentx/auth/
│   ├── AuthServiceApplicationTests.java [IMPLEMENTED - context-load smoke test]
│   └── security/AuthServiceSecurityTest.java [IMPLEMENTED - actuator reachability tests only]
└── pom.xml                              [IMPLEMENTED - web/security/actuator/JPA/Postgres, NO JWT library]
```

No `application-docker.yml` file exists — the "docker" profile is a block inside the single
`application.yml`, and it is itself incomplete (URL only, no username/password/driver-class-name).

**Component classification:**

| Component | Classification | Notes |
|---|---|---|
| Controllers | NOT_IMPLEMENTED | None exist |
| Services | NOT_IMPLEMENTED | None exist |
| Repositories | NOT_IMPLEMENTED | None exist |
| Entities | NOT_IMPLEMENTED | None exist (zero `@Entity` classes) |
| DTOs | NOT_IMPLEMENTED | None exist |
| Security configuration | PLACEHOLDER | Real class, explicitly documented as temporary (`permitAll()` everywhere) |
| Filters | NOT_IMPLEMENTED | No custom filter beyond Spring Security's default chain shape |
| Interceptors | NOT_IMPLEMENTED | None |
| Exception handlers | NOT_IMPLEMENTED | No `@ControllerAdvice`/`GlobalExceptionHandler` in this module |
| JWT-related classes | NOT_IMPLEMENTED | None — no JWT library dependency even present |
| Token-related classes | NOT_IMPLEMENTED | None |
| Password handling | NOT_IMPLEMENTED | No `PasswordEncoder` bean, no hashing code |
| Database configuration | IMPLEMENTED | Real datasource, real Hikari pool, real connectivity (dev profile) |
| Migrations | NOT_IMPLEMENTED | No Liquibase/Flyway, no changelog, deliberately not added (no schema to manage yet) |
| Tests | PARTIALLY_IMPLEMENTED | Real, but limited to context-load + actuator-reachability; zero auth-behavior coverage (nothing to cover) |

---

## 3. Authentication Audit (Part 2)

| Question | Answer | Evidence |
|---|---|---|
| Is there a login/authentication endpoint? | **NO** | Zero `@RestController` classes in the module; direct probes (prior task) of `/login`, `/token`, `/api/v1/auth/login` all returned 404 |
| Is there a request DTO? | **NO** | No DTO classes exist |
| Are credentials accepted? | **NO** | No endpoint exists to accept them |
| Is identity loaded from database? | **NO** | No repository/entity exists |
| Are credentials verified? | **NO** | No verification code exists |
| Is a token generated? | **NO** | No token-generation code exists |
| Is JWT generated? | **NO** | No JWT library dependency in `pom.xml`; no signing code |
| Is token expiry configured? | **NOT APPLICABLE** (nothing issues a token to configure expiry on) | — |
| Is refresh token supported? | **NO** | No refresh endpoint/logic exists — though `SecurityConstants.TOKEN_TYPE_REFRESH` is already defined in Common Library, unused |
| Is logout/revocation supported? | **NO** | No such endpoint/logic exists |

**Actual conceptual flow implemented: none of it.** The expected flow (Client → Endpoint → Credential
Validation → Identity Lookup → Credential Verification → Token Generation → JWT Returned) does not exist
at any stage past "Client" in this service.

---

## 4. JWT Audit (Part 3)

| Item | Status | Evidence |
|---|---|---|
| JWT library | **NOT CONFIGURED** (in Auth Service) | No `nimbus-jose-jwt`/`jjwt`/`spring-boot-starter-oauth2-resource-server` in `paymentx-auth-service/pom.xml` |
| Token generation | NOT CONFIGURED | No code |
| Signing algorithm | NOT CONFIGURED (in Auth Service) | — |
| Signing key configuration | NOT CONFIGURED (in Auth Service) | — |
| Issuer / Audience / Subject / Issued-at / Expiry / Claims | NOT CONFIGURED | No token is ever built |
| Token parsing | **CONFIGURED — but in API Gateway, not Auth Service** | `NimbusReactiveJwtDecoder.withSecretKey(...)`, real HMAC secret via `${GATEWAY_JWT_SECRET:...}` |
| Signature validation | CONFIGURED (API Gateway) | Same decoder |
| Expiration validation | CONFIGURED (API Gateway) | `JwtTimestampValidator` wrapped in a `DelegatingOAuth2TokenValidator`, with a configured clock-skew tolerance |
| Malformed-token handling | CONFIGURED (API Gateway, inferred from Spring Security's documented default `AuthenticationWebFilter` behavior — not independently re-tested in this read-only audit) | `AuthenticationWebFilter` with no custom failure handler defaults to a 401 response via `ServerAuthenticationEntryPointFailureHandler` |
| Revoked-token handling | **NOT APPLICABLE** | No token issuance exists, so nothing to revoke; no revocation list/mechanism exists anywhere |

**Explicit statement per instruction:** a JWT library and full decode/validate pipeline exist and are
real — in API Gateway. Auth Service itself has **no JWT library and no token generation whatsoever.**
These are two different services; the presence of working JWT *validation* elsewhere does not make Auth
Service's JWT capability "PARTIAL" — Auth Service's own capability is NOT CONFIGURED.

**Common Library's real, existing, unused JWT claim contract** (`com.paymentx.common.constant.
SecurityConstants`): `BEARER_PREFIX`, `ROLE_PREFIX`, `CLAIM_SUBJECT`("sub"), `CLAIM_ISSUER`("iss"),
`CLAIM_ROLES`("roles"), `CLAIM_PARTICIPANT_ID`("participantId"), `CLAIM_TOKEN_TYPE`("tokenType"),
`TOKEN_TYPE_ACCESS`/`TOKEN_TYPE_REFRESH`/`TOKEN_TYPE_SERVICE`. This is a real, already-defined,
already-**consumed** (by API Gateway's `JwtGrantedAuthoritiesConverter`, reading `CLAIM_ROLES` with
`ROLE_PREFIX`) contract that Auth Service does not yet produce tokens against.

---

## 5. Authorization Audit (Part 4)

Authorization **exists as a platform-wide pattern**, but is entirely independent of Auth Service:

- Every business service (`routing`, `audit`, `notification`, `reconciliation`, `reporting`) implements the identical pattern: a `HeaderRoleAuthenticationFilter` reading a trusted `X-Roles` header (propagated by whatever called it — normally API Gateway, which derives it from the validated JWT's `roles` claim), combined with `@PreAuthorize("hasRole('...')")` on mutating endpoints.
- MCP Gateway implements a deeper, per-tool-call authorization (`ToolAuthorizationService`) reading `X-Roles`/`X-Participant-Id` from a custom transport context — the same trusted-header source, applied at finer granularity than HTTP-endpoint level.
- **Authentication and Authorization are architecturally distinct in this platform, and the codebase's own comments say so explicitly**: every service's `SecurityConfig` javadoc states some variant of *"JWT verification happens upstream at API Gateway; this service is not internet-facing"* (authentication is centralized) while `@PreAuthorize` + `X-Roles` (authorization) is implemented independently, per-service, downstream.
- **The gap:** authorization's trusted `X-Roles` header is only as trustworthy as whatever populates it. Today, nothing populates it with a real, verified value, because nothing issues real JWTs with a real `roles` claim (Auth Service doesn't exist). Downstream authorization code is real and functional, but currently has no genuine input to authorize against in a live authenticated request — it has only ever been exercised with hand-crafted test values (e.g. `RoutingSecurityTest`'s `X-Roles: ROUTING_ADMIN` sent directly, bypassing Gateway).
- Participant isolation / resource ownership checks: **NOT FOUND** as a general mechanism. Individual services scope data by `participantId` in their own query logic (e.g. Routing Service's participant-specific rule lookup), but there is no cross-cutting check preventing an authenticated participant from requesting another participant's data — this was flagged as an open question in a prior design task, not resolved here.

---

## 6. Database Audit (Part 5, read-only)

`paymentx_auth` database — confirmed via read-only `\dt` equivalent inspection:

| Table needed for | Status |
|---|---|
| Users | NOT FOUND |
| Participants | NOT FOUND (participant data lives in Validation Service's own `participant` table, not Auth Service) |
| Credentials | NOT FOUND |
| Roles | NOT FOUND |
| Permissions | NOT FOUND |
| Tokens | NOT FOUND |
| Refresh tokens | NOT FOUND |
| Sessions | NOT FOUND (not applicable — platform is stateless/JWT-oriented by every other service's `SessionCreationPolicy.STATELESS` convention) |
| Authentication audit | NOT FOUND (in Auth Service's own schema — see §11 for the platform-wide Audit Service, which is a separate concept) |

**`paymentx_auth` has zero tables of any kind.** No migration tooling (Liquibase/Flyway) is even wired
into the module, so no schema exists to inspect beyond "empty."

---

## 7. Credential Security Audit (Part 6)

No credential-handling code exists to audit. No password hashing (BCrypt/Argon2/PBKDF2), no plaintext
handling, no comparison logic, no reset flow, no rotation — none of it is present, because there is no
credential storage or verification code at all. `spring-boot-starter-security` transitively provides
`spring-security-crypto` (so `BCryptPasswordEncoder` is *available* on the classpath), but no
`PasswordEncoder` bean is defined anywhere in this module. **No plaintext credential handling was found —
because no credential handling of any kind exists.** This is a "nothing to audit" finding, not a clean
bill of health on an implemented mechanism.

---

## 8. API Gateway Integration (Part 7)

**Actual current flow, verified from source:**

```
Client
 ↓
API Gateway
 ├─ SecurityConfig.securityWebFilterChain: authorizeExchange().anyExchange().permitAll()
 │  (Spring Security's own authorization layer does not block anything by itself)
 ├─ AuthenticationWebFilter (JwtReactiveAuthenticationManager, NimbusReactiveJwtDecoder)
 │  - Bearer token present + valid  → authenticates, roles claim → Spring Security authorities
 │  - Bearer token present + invalid/expired → 401 via default AuthenticationWebFilter failure handling
 │    (inferred from documented Spring Security default behavior; not independently re-tested this audit)
 │  - No Authorization header → ServerBearerTokenAuthenticationConverter returns empty, filter no-ops
 ├─ ApiKeyAuthenticationGlobalFilter (GlobalFilter, order +104)
 │  - Authorization header present → skipped entirely (JWT path owns it)
 │  - X-Api-Key present + valid (real Redis lookup) → sets X-Participant-Id, continues
 │  - X-Api-Key present + invalid → 401 (own JSON error body)
 │  - Neither present → passes through (not this filter's job to reject)
 ├─ AuthenticationEnforcementGlobalFilter (GlobalFilter, order +106 — "the ACTUAL final authentication gate" per its own javadoc)
 │  - Public path (actuator/swagger/api-docs) → bypassed
 │  - X-Participant-Id set (by either JWT or API-key path having succeeded) → continues
 │  - Neither succeeded → 401, own JSON error body
 └─ Routing to downstream (only 2 configured routes: validation-service, payment-service)
 ↓
Downstream service (trusts X-Participant-Id / X-Roles headers Gateway attached)
```

**Does API Gateway call Auth Service?** **NO.** No HTTP client to port 8081 exists anywhere in API
Gateway's source.
**Does API Gateway validate JWT locally?** **YES**, real and independent of Auth Service — it validates
signature and expiry against a shared secret, without ever calling Auth Service to check anything.
**Is authentication currently permitAll?** At the Spring-Security-authorization layer, yes
(`anyExchange().permitAll()`) — but this is a deliberate architectural choice, not an oversight: real
enforcement happens in Gateway's own custom `GlobalFilter`s (`AuthenticationEnforcementGlobalFilter`),
confirmed by real testing in a prior task (missing/invalid credentials → real 401).
**Correlation IDs preserved?** YES — confirmed via real transaction evidence in prior tasks (`corr-`
value propagated end-to-end through Validation, Payment, Audit, Notification).
**Claims forwarded?** Partially — `X-Participant-Id` is forwarded (from either JWT claim or API-key
lookup); `X-Roles` forwarding was not independently re-verified in this specific audit pass.

---

## 9. Existing Security at Other Services (Part 8)

| Service | Existing security pattern | Auth Service alignment | Gap |
|---|---|---|---|
| API Gateway | Real JWT validation (Nimbus, HMAC), real Redis-backed API-key validation, real rate limiting | None — Gateway never calls Auth Service | Gateway validates tokens Auth Service doesn't yet issue |
| Validation/Routing/Audit/Notification/Reconciliation/Reporting | `HeaderRoleAuthenticationFilter` + `@PreAuthorize`, trusting `X-Roles`/`X-Participant-Id` from Gateway | None — these services never call Auth Service either | Trusted headers currently only ever populated by hand-crafted test values or API-key lookups, never a real issued JWT's real roles claim |
| Payment Service | No admin-mutating endpoints exposed; `permitAll()`-shaped, matching the "not internet-facing, trust boundary is upstream" convention | Consistent | None specific to Payment Service |
| Control Center | Real, but *different*: a single shared-token (`DashboardAuthFilter`, constant-time comparison, opt-in via `control-center.security.enabled`) — explicitly NOT multi-user auth, by its own documented design choice, for the same reason recorded here: *"Auth Service has zero endpoints today"* | Independently confirms this audit's own finding | Demonstrates the platform already has precedent for an honestly-scoped, minimal security mechanism when a real IdP isn't available |
| MCP Gateway | Real per-tool-call authorization (`ToolAuthorizationService`), same trusted-header source, finer granularity than HTTP-endpoint level | None | Same trusted-header dependency as everything else |
| Agent Orchestrator | `permitAll()`-shaped, same "not internet-facing" rationale | Consistent | None specific |

**No security code exists in Auth Service that could be reused conceptually from elsewhere** — Auth
Service is the one module in this list with *no* security-relevant business logic at all; every other
service's pattern assumes Auth Service (or something upstream of it) has already done real authentication
and is not itself a template for *building* that missing piece.

---

## 10. API Contract (Part 9)

**No endpoints of any kind exist beyond Spring Boot Actuator's own auto-configured ones.** Explicitly
checked for and confirmed absent (prior direct probing, and re-confirmed by source inspection — zero
`@RestController` classes exist):

| Candidate endpoint | Found? |
|---|---|
| `POST /login` | NOT FOUND |
| `POST /authenticate` | NOT FOUND |
| `POST /token` | NOT FOUND |
| `POST /refresh` | NOT FOUND |
| `POST /logout` | NOT FOUND |

No endpoint names were invented for this report — this table lists exactly the candidates the task asked
about, all confirmed absent.

---

## 11. Security Configuration (Part 10)

| Aspect | Configuration |
|---|---|
| `SecurityFilterChain` | One bean, `SecurityConfig.filterChain()` |
| CSRF | Disabled (`csrf.disable()`) — reasonable for a stateless service-to-service API, consistent with every other service |
| Session management | `SessionCreationPolicy.STATELESS` |
| Endpoint authorization | `/actuator/**` → `permitAll()`; `anyRequest()` → `permitAll()` (explicit `// TODO` comment: *"replace with internal-service-token validation once this service gains real endpoints"*) |
| Authentication provider | None configured |
| `UserDetailsService` | None configured — Spring Boot's default auto-configuration would normally generate an in-memory user with a random password if `spring-boot-starter-security` is present and no `UserDetailsService`/security customization exists, but this module's explicit `SecurityConfig` with `permitAll()` everywhere prevents that default from ever mattering |
| `PasswordEncoder` | None configured |
| JWT filter | None |
| Exception handling | None customized |
| CORS | Not configured in this module |

**Explicit required statement:** this service's real, current configuration is
**`anyRequest().permitAll()`** across the board, including actuator. **AUTHENTICATION NOT ENFORCED.**
This is consistent with the module's own documented design intent (nothing to protect yet), not a
discovered oversight.

---

## 12. Secrets Management (Part 11)

| Secret | Externalized / Hardcoded / Missing |
|---|---|
| Auth Service's own signing key | **MISSING** — no signing key configuration exists anywhere in this module, because no signing code exists |
| API Gateway's JWT secret (`GATEWAY_JWT_SECRET`) | **EXTERNALIZED**, with a clearly-labeled non-production fallback: `${GATEWAY_JWT_SECRET:local-dev-only-secret-change-me-32chars}` — SECRET DETECTED — VALUE REDACTED for the actual runtime value if an env var is set; the fallback literal itself is a self-documented placeholder, not a real secret |
| Auth Service's own database credentials (dev profile) | **HARDCODED**, but env-var-overridable: `${AUTH_DB_USERNAME:postgres}` / `${AUTH_DB_PASSWORD:postgres}` — SECRET DETECTED — VALUE REDACTED. This pattern deliberately deviates from every other service's bare-literal `password: postgres` convention specifically for this service, per a prior remediation's explicit reasoning (identity-service credential hygiene) |
| Auth Service's own database credentials (docker profile) | **MISSING** — the "docker" profile block has a URL only, no username/password/driver at all |

No environment-variable inventory, AWS Secrets Manager integration, or Docker secrets mechanism exists
anywhere in this repository for any service — every secret-like value across the platform uses Spring's
own `${VAR:default}` property placeholder convention exclusively.

---

## 13. Audit / Observability (Part 12)

Audit Service's real, schema-enforced `event_type` vocabulary (`chk_audit_event_type` CHECK constraint)
contains: `PAYMENT_CREATED`, `PAYMENT_UPDATED`, `PAYMENT_ROUTED`, `PAYMENT_COMPLETED`, `PAYMENT_FAILED`,
`PAYMENT_CANCELLED`, `PAYMENT_REFUNDED`, `VALIDATION_COMPLETED`, `PARTICIPANT_UPDATED`,
`ROUTING_RULE_CHANGED`, `SECURITY_EVENT`, `API_REQUEST`, `API_RESPONSE`, `SYSTEM_EVENT`, `KAFKA_EVENT`.

**No dedicated `LOGIN_SUCCESS`/`LOGIN_FAILURE`/`TOKEN_ISSUED`/`TOKEN_REFRESHED`/`TOKEN_REJECTED`/
`UNAUTHORIZED_ACCESS` event types exist.** The closest generic fit already present is `SECURITY_EVENT`
(and possibly `API_REQUEST`/`API_RESPONSE`), but nothing in Auth Service (or anywhere else) currently
emits any of these — because there is no authentication logic to generate such events from. This is a
**NOT FOUND**, not a "found but unused" — no auth-specific audit vocabulary exists in the schema at all.

---

## 14. Test Coverage (Part 13)

| Scenario | Status |
|---|---|
| Login success | NOT_IMPLEMENTED (no login exists) |
| Login failure | NOT_IMPLEMENTED |
| Invalid credentials | NOT_IMPLEMENTED |
| Unknown user | NOT_IMPLEMENTED |
| Password verification | NOT_IMPLEMENTED |
| JWT generation | NOT_IMPLEMENTED |
| JWT validation | NOT_APPLICABLE to Auth Service (real JWT validation tests exist for API Gateway's own `SecurityConfig`, a different module, not audited here) |
| Expired JWT | NOT_IMPLEMENTED (Auth Service) |
| Malformed JWT | NOT_IMPLEMENTED (Auth Service) |
| Missing JWT | NOT_IMPLEMENTED (Auth Service) |
| Authorization failure | NOT_APPLICABLE to Auth Service directly (covered elsewhere, e.g. `RoutingSecurityTest`, not this module) |
| Role/permission checks | NOT_IMPLEMENTED (Auth Service has no role-bearing endpoints) |
| Refresh token | NOT_IMPLEMENTED |
| Logout/revocation | NOT_IMPLEMENTED |

**What real tests DO exist and pass** (from the prior remediation task, not re-executed in this read-only
audit): `AuthServiceApplicationTests.contextLoads` (real Spring context + real Testcontainers Postgres
connection boots cleanly) and `AuthServiceSecurityTest` (2 tests: actuator health reachable without auth;
an undefined path returns 404, not 401/403 — proving the current `permitAll()` configuration behaves as
configured). These are genuine, meaningful tests of what actually exists — they are not, and were never
claimed to be, authentication-behavior tests.

---

## 15. Runtime Verification (Part 14)

Per instruction, only safe read-only checks were considered (no login attempts, no state changes):

| Check | Result |
|---|---|
| `GET /actuator/health` | Was confirmed reachable and returning `{"status":"UP"}` with real database connectivity in a prior task's session; **NOT independently re-executed as part of this specific read-only audit response** — reported here as prior evidence, not fabricated as freshly re-verified |
| `GET /actuator/info` | NOT EXECUTED in this audit |
| Any documented read-only business endpoint | NOT APPLICABLE — none exist |

Per the instruction "if runtime behavior cannot be safely verified, report NOT EXECUTED, do not infer
PASS" — this audit deliberately did not re-run new live checks against the running service to strictly
honor "read the existing implementation/test results" as the evidentiary basis for this particular
document, rather than performing fresh runtime probing.

---

## 16. Security Gap Analysis (Part 15)

| Gap | Severity |
|---|---|
| No authentication implemented at all | **CRITICAL** |
| Auth Service's own `SecurityConfig` is `permitAll()` on every path | **HIGH** (mitigated: not internet-facing by platform design, and there are currently no endpoints for this to matter against) |
| No JWT issuance capability | **CRITICAL** (this is the root cause blocking real authentication anywhere in the platform) |
| No password hashing / credential storage | **INFORMATIONAL** (nothing to hash — no credential handling exists at all, so this is not "plaintext passwords found," it is "no credential mechanism exists") |
| Downstream authorization (`X-Roles`) has no genuine trusted input in a real request path | **HIGH** — real `@PreAuthorize` machinery exists everywhere but currently authorizes against headers no real authentication flow populates |
| Missing participant-isolation cross-check | **MEDIUM** — flagged in a prior design task as an open question, not yet a demonstrated exploit path since there's no live authenticated flow to exploit yet |
| Token expiry not configurable (Auth Service) | **NOT APPLICABLE** — no tokens are issued |
| Missing auth-specific audit event types | **MEDIUM** — real audit infrastructure exists and works; the specific vocabulary for auth events is absent |
| Missing auth-behavior tests | **LOW** — appropriate given there is no auth behavior yet; will become HIGH once real auth logic is added without matching tests |
| Auth Service's docker-profile datasource block is incomplete | **LOW** — dev profile works; docker profile was never functional even before this audit and is not currently used to run the platform |

No finding here is exaggerated beyond what source evidence supports; none of these are newly discovered
in this pass — all were previously identified in earlier remediation/audit tasks this session and are
re-confirmed, not re-invented, here.

---

## 17. Target Architecture (Part 16)

```
Client
 ↓
API Gateway            — validates JWT (ALREADY REAL, unchanged); does not need to change
 ↓
Authentication          — NEW: Auth Service issues tokens against real identity/credentials
 ↓
JWT                      — signed with the SAME shared secret Gateway already validates against
 ↓
Claims                   — sub / iss / roles / participantId / tokenType (ALREADY DEFINED in
                            Common Library's SecurityConstants, currently unused as an issuer contract)
 ↓
Authorization             — @PreAuthorize + X-Roles (ALREADY REAL everywhere downstream), now
                            receiving a genuinely trustworthy value for the first time
 ↓
PaymentX Services (unchanged)
```

**Where each responsibility belongs, given what already exists:**
- **Token issuance** (login, credential verification, signing): Auth Service — the only genuinely
  missing piece.
- **Token validation** (signature, expiry, claim extraction): API Gateway — already real, no change
  needed.
- **Authorization decisions**: each downstream service, via its own already-real `@PreAuthorize` +
  `X-Roles` — no change needed, this becomes genuinely load-bearing once Auth Service issues real tokens.
- **No external identity platform is warranted** — the existing repository has no OAuth2/OIDC federation
  requirement anywhere, and the claim contract Common Library already defines is a simple, self-contained
  HMAC-JWT shape fully compatible with what API Gateway already validates. Introducing an external IdP
  would be pure speculation not supported by any existing requirement.

---

## 18. Minimum Required Functionality (Part 17)

| Capability | Classification |
|---|---|
| Login endpoint | REQUIRED |
| Credential verification | REQUIRED |
| Password hashing | REQUIRED (any real credential storage without it is a genuine security defect, not an optional hardening) |
| JWT generation (matching Gateway's existing contract) | REQUIRED |
| JWT validation | ALREADY SATISFIED (Gateway) — not Auth Service's responsibility to duplicate |
| Token expiry | REQUIRED (real `exp` claim, since Gateway already enforces it) |
| Claims (`sub`/`roles`/`participantId`/`tokenType`) | REQUIRED — the contract already exists, must be honored exactly |
| Authorization | ALREADY SATISFIED (downstream services) |
| Participant identity | REQUIRED (as a claim on the issued token) |
| API Gateway integration | ALREADY SATISFIED — no gateway-side change needed if the claim contract is honored |
| Unauthorized (401) response | REQUIRED for the login endpoint's own failure case (already the platform-wide response shape) |
| Forbidden (403) response | ALREADY SATISFIED (downstream `@PreAuthorize` already does this) |
| Refresh token | RECOMMENDED, not strictly required for a minimum viable Phase 1 (stateless short-lived access tokens alone can be a valid minimum) |
| Logout/revocation | OPTIONAL for a stateless-JWT minimum (no session state exists to revoke without a token/session table, which is itself optional) |
| Auth-specific audit events | RECOMMENDED — real audit infrastructure exists; wiring auth events into it is low-cost and high-value, not mandatory for a bare-minimum login flow |
| Auth-behavior tests | REQUIRED alongside any real implementation, per this platform's own consistent testing discipline throughout every other remediation in this session |

---

## 19. Implementation Impact (Part 18)

**Auth Service:**
- New: controller, service, repository, entity/entities (identity table), DTOs, JWT issuance component, `PasswordEncoder` bean, exception handler.
- Modified: `pom.xml` (add a JWT library — e.g. the same Nimbus family API Gateway already uses, for consistency, rather than introducing a second JWT library), `SecurityConfig.java` (add a public, unauthenticated login endpoint path), `application.yml`/`application-dev.yml` (add signing-secret configuration, shared with Gateway's existing `GATEWAY_JWT_SECRET` — likely renamed/aliased conceptually but the existing Gateway property itself should not need to change).

**API Gateway:**
- **No files require modification** — this is the strongest piece of evidence that Authentication is an isolated, additive change. `SecurityConfig.java`, `GatewaySecurityProperties.java`, and every filter already fully implement the validating side of this contract.

**Other services:** none require modification — every downstream `@PreAuthorize`/`HeaderRoleAuthenticationFilter` already exists and already works against the claim shape Auth Service would produce.

**Database:** one new database/schema needed — `paymentx_auth` currently has zero tables; a real identity/credential table (and optionally a token/session table only if refresh-token revocation is required) would need a real migration tool wired in (currently absent from this module entirely — no Liquibase/Flyway dependency exists yet).

**Configuration:** `paymentx-auth-service/src/main/resources/application.yml` and `application-dev.yml` (signing secret, new datasource requirements if a real schema is added), and environment-variable provisioning for the shared JWT secret across both Auth Service and API Gateway.

---

## 20. Backward Compatibility (Part 19)

The real, already-proven payment lifecycle (Gateway → Validation → Kafka → Payment → Routing → Settlement
→ Audit → Notification → Reporting) has **zero dependency on Auth Service today** — none of these
services call it, and Gateway's JWT validation already works independently of whether Auth Service
exists. This means:

- **Public endpoints that must remain public:** the new login endpoint itself (by definition — a client
  cannot present a JWT to obtain one), and every existing `permitAll()` actuator/health/swagger path
  across all services (unchanged).
- **Internal service-to-service calls:** none currently depend on Auth Service; introducing it changes
  nothing about the existing Validation/Routing/Payment internal call graph.
- **Required service credentials:** if any service ever needs to call another as itself (not on behalf of
  a participant), the existing `TOKEN_TYPE_SERVICE` claim value (already defined, unused) is the
  already-designed mechanism for this — no new concept needed.
- **JWT propagation:** already fully implemented (Gateway → downstream `X-Participant-Id`/`X-Roles`
  headers) — introducing a real issuer does not change this propagation mechanism at all, only makes its
  input genuine instead of test-only.
- **Backward compatibility concern, specifically:** none identified that would break the existing,
  already-verified real transaction lifecycle. The one thing to verify (not implement) once Auth Service
  exists: that hand-crafted test tokens (`TestJwtUtil`, already used in API Gateway's test suite) continue
  to validate identically against the same shared secret and claim shape a real Auth Service would
  produce.

---

## 21. Recommended Implementation Order (Part 20)

Based on actual repository evidence (not a generic template):

1. **Data model** — the one genuinely new piece of infrastructure needed (identity/credential table); everything else reuses existing patterns.
2. **Password security** — hashing must exist before any credential is ever persisted, not bolted on after.
3. **Authentication endpoint** (credential verification against the new data model).
4. **JWT generation** — conforming exactly to the claim contract Common Library already defines and API Gateway already validates; this is the step where "reuse, don't reinvent" matters most.
5. **JWT validation** — already satisfied; explicitly skip re-implementing this in Auth Service.
6. **API Gateway integration** — expected to require zero changes; this step is really "verify, don't implement."
7. **Authorization** — already satisfied downstream; this step is "verify real tokens flow through correctly," not new code.
8. **Audit** — wire `SECURITY_EVENT` (or a new, additive auth-specific event type if the team decides one is warranted) into the login flow.
9. **Tests** — unit (password/JWT), integration (login endpoint), and a real end-to-end token issuance + Gateway acceptance test, matching this platform's own established `TEST-E2E-*` discipline.
10. **Real development validation** — one real login → real token → real authenticated request through the already-proven payment lifecycle, closing the loop this audit and prior tasks have repeatedly identified as missing.

---

## Final Auth Gap Matrix

| Capability | Current State | Evidence | Required? | Priority |
|---|---|---|---|---|
| Authentication | NOT IMPLEMENTED | Zero endpoints, zero controllers | REQUIRED | CRITICAL |
| Credential verification | NOT IMPLEMENTED | No code exists | REQUIRED | CRITICAL |
| Password hashing | NOT IMPLEMENTED | No `PasswordEncoder` bean | REQUIRED | CRITICAL |
| JWT generation | NOT IMPLEMENTED | No JWT library dependency | REQUIRED | CRITICAL |
| JWT validation | IMPLEMENTED (API Gateway, not Auth Service) | `NimbusReactiveJwtDecoder` + `JwtTimestampValidator`, real | ALREADY SATISFIED | — |
| JWT expiry | IMPLEMENTED (validation side, API Gateway) / NOT IMPLEMENTED (issuance side) | Clock-skew-tolerant validator exists; nothing issues an `exp` claim yet | REQUIRED (issuance) | HIGH |
| Claims | DEFINED, UNUSED | `SecurityConstants` in Common Library | REQUIRED (to populate) | HIGH |
| Authorization | IMPLEMENTED (downstream services) | `@PreAuthorize` + `HeaderRoleAuthenticationFilter`, consistent across 5+ services | ALREADY SATISFIED | — |
| Roles | PARTIALLY IMPLEMENTED | Consumed everywhere, never genuinely issued | REQUIRED (issuance) | HIGH |
| Permissions | NOT FOUND as a distinct concept from roles | Only role-based checks exist | OPTIONAL (roles may suffice for Phase 1) | LOW |
| Participant identity | PARTIALLY IMPLEMENTED | Propagated via headers real-world; not yet sourced from a real authenticated identity | REQUIRED | HIGH |
| API Gateway integration | ALREADY SATISFIED | No Gateway code change anticipated | — | — |
| Service-to-service security | DEFINED, UNUSED | `TOKEN_TYPE_SERVICE` claim value exists | RECOMMENDED | MEDIUM |
| Audit | NOT FOUND (auth-specific) | No `LOGIN_*`/`TOKEN_*` event types in schema | RECOMMENDED | MEDIUM |
| Secrets management | PARTIALLY IMPLEMENTED | Gateway's secret is externalized; Auth Service has none to manage yet | REQUIRED (once issuance exists) | HIGH |
| Tests | NOT_IMPLEMENTED (auth behavior) | Only context-load/actuator tests exist | REQUIRED | HIGH |

---

## Final Classification: **C — AUTH NOT IMPLEMENTED**

**Justification, strictly from evidence:** zero authentication endpoints, zero credential handling, zero
JWT issuance capability, zero identity data model, `permitAll()` security configuration confirmed
end-to-end from source. The presence of real JWT *validation* in API Gateway does not change this
classification for Auth Service itself — the task is auditing Auth Service, and Auth Service implements
none of the capabilities its own `pom.xml` description claims (*"JWT issuance and verification
service"*).

**Critical gaps:**
- No authentication endpoint of any kind.
- No JWT issuance capability (no library, no signing, no claims).
- No credential storage or verification mechanism.

**High-priority gaps:**
- Real downstream authorization machinery has no genuine trusted input in a live request path.
- No password hashing exists (nothing to hash yet, but this must exist from day one of real
  implementation).
- No participant-identity source of truth for issuing correct `participantId` claims.
- No test coverage for authentication behavior (appropriately absent given no behavior exists yet, but
  a gap the moment implementation begins).

**Recommended implementation order:** Data model → Password security → Authentication endpoint → JWT
generation (against the already-existing claim contract) → verify (not modify) API Gateway integration →
verify (not modify) downstream authorization → audit wiring → tests → one real end-to-end validation
transaction.

---

**STOP.** No source code, configuration, database schema, or data was modified. No tests were added or
executed. No services were restarted or stopped. No transactions were created. No other service (Routing,
Payment, AI services) was modified. Phase 3.10 was not started. Only this document was created.
