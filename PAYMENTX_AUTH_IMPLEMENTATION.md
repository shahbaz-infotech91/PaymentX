# PaymentX — Auth Service Implementation

Real, minimal, production-style authentication for Auth Service, issuing JWTs the **existing, unmodified**
API Gateway validator already accepts. No refresh tokens, no logout/revocation, no second JWT contract,
no external identity provider, no Gateway/Routing/Payment/AI service changes.

---

## 1. Before State

Bare skeleton: 2 Java classes (`AuthServiceApplication`, `SecurityConfig`), zero controllers/entities/
repositories, zero JWT dependency, zero password handling, `permitAll()` everywhere, `paymentx_auth` with
zero tables. Real database connectivity existed (prior remediation). Confirmed unchanged immediately
before this task began.

**Real, previously-undiscovered finding made during implementation:** Auth Service's `pom.xml` depended
on a legacy `paymentx-common` module (3 classes only), not the real `paymentx-common-library` every other
one of the platform's 15 other services depends on. This had zero visible effect until this task, because
Auth Service's 2 pre-existing classes never imported anything from either module. Corrected as part of
this implementation (§17) — required for compilation, not optional scope.

---

## 2. Auth Architecture (after)

```
Client → POST /api/v1/auth/login (Auth Service, NEW, public)
       → AuthenticationServiceImpl: find user → check enabled → BCrypt verify
         → load roles → resolve participantId → JwtIssuer (Nimbus, HS256)
       → real JWT, signed with the SAME secret Gateway already validates against
Client → Authorization: Bearer <token> → API Gateway (UNCHANGED)
       → real signature + expiry validation → roles/participantId propagation (UNCHANGED)
       → downstream PaymentX services (UNCHANGED)
```

---

## 3. Database Model

Two tables only, exactly as approved:

- **`auth_user`**: `id` (UUID), `username` (unique), `password_hash`, `participant_id`, `enabled`,
  `failed_login_count`, plus the standard `created_at`/`updated_at`/`created_by`/`updated_by`/`version`
  audit columns (via `AuditableEntity`, the same base class Routing/Audit/Notification/Reconciliation/
  Reporting already use).
- **`auth_user_role`**: `id`, `user_id` (real FK to `auth_user`), `role` (raw name, no `ROLE_` prefix —
  Gateway's converter adds that), unique on `(user_id, role)`.

No participant table (references Validation Service's existing, authoritative one by string ID only), no
permission table, no session table, no refresh_token table — none required, matching the approved design.

---

## 4. Liquibase Migration

`db/changelog/db.changelog-master.yaml` includes `V1_0_0__create_auth_user_tables.yaml` (both tables,
real primary keys, real foreign key, real unique constraints, real index on `participant_id`) and
`V1_0_1__seed_dev_test_user.yaml` (one synthetic dev/test identity — see §17). No plaintext password in
any migration file — the seed changeset stores only a real BCrypt digest (a one-way hash is safe to
commit, the same principle that makes storing any password hash anywhere safe).

Verified real: Liquibase ran successfully against both a Testcontainers instance (test suite) and the
real local Postgres instance (manual restart) — `"Liquibase: Update has been successful. Rows affected:
5"` in both cases.

---

## 5. Password Security

`BCryptPasswordEncoder`, Spring Security's own standard implementation — already transitively available
via `spring-boot-starter-security` (already a dependency); **no new dependency was needed for hashing**.
Salt handled internally by BCrypt. Verification via `passwordEncoder.matches(raw, hash)`. Failed attempts
increment `auth_user.failed_login_count` (recorded, no active lockout policy — no existing precedent in
this codebase to derive one from). Password never logged, never returned in any response DTO.

---

## 6. Authentication API

`POST /api/v1/auth/login` — the only new endpoint. Request: `LoginRequest(username, password)`, both
`@NotBlank`. Response (success, 200): `ApiResponse.success(LoginResponse(accessToken, tokenType,
expiresInSeconds))`. Response (failure, 401): `ApiResponse.error(...)` with the existing
`ErrorCodes.UNAUTHORIZED` code and the identical generic message regardless of failure reason (unknown
user / wrong password / disabled account all produce byte-for-byte the same response).

---

## 7. JWT Issuance

`com.nimbusds:nimbus-jose-jwt:9.37.4` — the exact same version already resolved transitively by API
Gateway (confirmed via `dependency:tree` before adding). Same API shape API Gateway's own `TestJwtUtil`
test helper already uses. Algorithm: HMAC-SHA256, matching Gateway's hardcoded `"HmacSHA256"` decoder
exactly.

---

## 8. JWT Claims

All five contract claims issued, exactly as `SecurityConstants` defines them:

| Claim | Real issued value (example) |
|---|---|
| `sub` | `"test-user"` |
| `iss` | `"paymentx-auth-service"` |
| `roles` | `["USER"]` |
| `participantId` | `"TEST-PARTICIPANT-001"` |
| `tokenType` | `"ACCESS"` |
| `iat`/`exp` | real timestamps, 1800s apart (configurable, `auth.jwt.access-token-ttl-seconds`) |

Verified twice: once via unit test (raw Nimbus decode + `MACVerifier` against the real secret), once via
a real, live-issued token decoded by hand during manual validation (§13) — both showed byte-for-byte the
expected shape.

---

## 9. Gateway Compatibility

**Zero Gateway files were modified.** Real proof, not inference: a real JWT issued by the new
`/api/v1/auth/login` endpoint was presented to the running, unmodified API Gateway and accepted —
real 200 response, real downstream payment data returned (§14). This is the strongest possible evidence
of compatibility: the actual production code path, not a simulation.

---

## 10. Participant Identity

`auth_user.participant_id` → JWT `participantId` claim → Gateway's existing
`JwtParticipantPropagationGlobalFilter` → `X-Participant-Id` header — unchanged mechanism, now fed a real
value. If a user has no participant association, authentication fails safely (generic 401) rather than
issuing a token with a fabricated participant ID — implemented exactly as designed, verified by test
(`missingParticipantAssociation_failsSafely...`).

---

## 11. Authorization

Not redesigned. Downstream `@PreAuthorize`/`HeaderRoleAuthenticationFilter` machinery is entirely
unchanged. This implementation only supplies it with a genuine `roles` claim for the first time.

---

## 12. Security Configuration

`SecurityConfig` updated: added an explicit (redundant with `anyRequest().permitAll()`, but intentionally
explicit for clarity) `permitAll()` on `/api/v1/auth/login` and a `PasswordEncoder` bean. Everything else
unchanged — there is still nothing else in this module to protect; the login endpoint is public by
necessity (a client cannot present a JWT to obtain one).

---

## 13. Audit

**Real, structured local logging exists** in `AuthenticationServiceImpl` (INFO on success with username/
participantId/role count; WARN on each specific failure reason — unknown username, wrong password,
disabled account, missing participant). **Centralized Audit Service integration was investigated and
explicitly not implemented**: Audit Service's real Kafka consumer (`AuditEventConsumer`) only listens to
a fixed, closed list of specific business-event topics (payment/routing lifecycle) — there is no generic
"publish any audit event" topic. Wiring a real `LOGIN_SUCCESS`/`LOGIN_FAILURE` event into the centralized
system would require adding a new topic to Audit Service's own consumed-topics list, a genuine Audit
Service modification explicitly out of this task's authorized scope. Documented here rather than
expanding scope, per this task's own explicit instruction for exactly this situation.

---

## 14. Real Auth Validation (Step 19 — executed, real evidence)

1. **Login**: `POST /api/v1/auth/login` with the real seeded `test-user` credential → real 200, real JWT.
2. **JWT issued**: real token, decoded and verified (claims exactly as §8).
3. **Gateway validation**: the real token, presented as `Authorization: Bearer <token>` to
   `GET /api/v1/payments` through the **real, running, unmodified API Gateway**, returned a real 200 with
   real payment data (37 real payment records, including transactions from every prior task this
   session).
4. **Unauthorized (no token)**: real 401.
5. **Invalid token** (tampered signature): real 401.
6. **Expired token** (real signature, deliberately-past `exp` claim, generated via the same Nimbus/HS256
   mechanism): real 401 — Gateway's existing `JwtTimestampValidator` correctly rejected it.

**Role propagation**: the `roles` claim was correctly issued (verified by decode) and Gateway's
pre-existing, unmodified `JwtGrantedAuthoritiesConverter` is the mechanism that would convert it — this
conversion mechanism itself was not independently re-observed in this specific request, because neither
Gateway-routed endpoint (`/api/v1/validations`, `/api/v1/payments`) enforces role-based access (see §19,
Known Limitations).

---

## 15. Real Protected API Validation

Covered above (§14, item 3) — `GET /api/v1/payments`, a real, existing, Gateway-routed, protected
(requires *some* valid identity) read-only endpoint. No fabricated endpoint was used.

---

## 16. Payment Validation (Step 20 — executed)

One real `TEST-E2E-AUTH-JWT-001` payment submitted via `POST /api/v1/validations`, authenticated with the
real JWT (not an API key — the first real transaction this session authenticated this way). Result:
**SETTLED**. Full lifecycle confirmed real:

```
payment_audit: PAYMENT_RECEIVED → PAYMENT_ROUTED (BANK001, instant-payment-processor-bank001)
             → PAYMENT_ROUTED (BANK002, instant-payment-processor) → PAYMENT_COMPLETED
notification: 4 real records
```

This is the first payment in this platform's history to be authenticated via a real, Auth-Service-issued
JWT rather than an API key, and the first to exercise the full stack (Authentication + Routing
integration + the already-proven core lifecycle) together in one transaction.

---

## 17. Security Results

- **Secrets hardcoded: NO** — `auth.jwt.secret` is bound from the same `${GATEWAY_JWT_SECRET:...}`
  environment variable and identical self-labeled local-dev-only fallback literal Gateway's own config
  already uses (not a new secret, the existing one, reused). Database credentials use the existing
  env-var-driven pattern established in the prior connectivity remediation.
- **Passwords plaintext: NO** — BCrypt only, everywhere, including the seed changeset (a hash, not a
  plaintext value).
- **JWT secret exposed: NO** — never printed in any tool output, test, log, or this document.
- **Test credential**: TEST CREDENTIAL PRESENT (synthetic, `test-user` / `TEST-PARTICIPANT-001` /
  role `USER` — not a real identity, not printed in this section).

---

## 18. Change Summary

**Files added (18):** `entity/AuthUser.java`, `entity/AuthUserRole.java`, `repository/
AuthUserRepository.java`, `repository/AuthUserRoleRepository.java`, `dto/LoginRequest.java`, `dto/
LoginResponse.java`, `config/AuthJwtProperties.java`, `config/JpaAuditingConfig.java`, `security/
JwtIssuer.java`, `service/AuthenticationService.java`, `service/impl/AuthenticationServiceImpl.java`,
`exception/GlobalExceptionHandler.java`, `controller/AuthController.java`, `db/changelog/
db.changelog-master.yaml`, `db/changelog/changes/V1_0_0__create_auth_user_tables.yaml`, `db/changelog/
changes/V1_0_1__seed_dev_test_user.yaml`, `test/.../AuthenticationServiceImplTest.java`, `test/.../
AuthControllerIntegrationTest.java`.

**Files modified (4):** `pom.xml` (added `nimbus-jose-jwt`, `liquibase-core`; corrected the
`paymentx-common` → `paymentx-common-library` dependency), `config/SecurityConfig.java` (added
`PasswordEncoder` bean, explicit login-path `permitAll()`), `application.yml` (added `auth.jwt.*`),
`application-dev.yml` (`ddl-auto: none` → `validate`, Liquibase enabled).

**Files deleted:** none.

**Gateway modified: NO. Payment Service modified: NO. Routing Service modified: NO. AI services
modified: NO.**

---

## 19. Known Limitations

- **Role-based (403) authorization could not be proven end-to-end through API Gateway** — Gateway
  currently routes only to Validation Service and Payment Service, and neither has a role-gated
  endpoint. The `roles` claim is correctly issued and Gateway's conversion mechanism is unchanged, but
  full enforcement was not independently observable in this environment.
- **Participant isolation (wrong-participant → 403) is not enforced anywhere in the codebase** — this
  is a pre-existing platform gap (confirmed in the prior audit), not something this task was authorized
  to design or build.
- **Authentication events are not wired into the centralized Audit Service** — real local logging exists;
  centralized integration would require modifying Audit Service's own Kafka topic list (out of scope).
- **No account lockout policy** — failed-login count is recorded but not acted upon.
- **`liquibase-core` and `nimbus-jose-jwt` are new dependencies** — both were necessary (proven by
  compilation failure without them), matching the "only add if compilation proves it required" instruction.
- **The `paymentx-common` → `paymentx-common-library` dependency fix was necessary, not optional** — no
  DTO/exception/base-entity class this implementation needed existed in the legacy module.

---

## 20. Rollback Strategy

Revert the 4 modified files and delete the 18 added files (all confined to `paymentx-auth-service`) — no
other service's files were touched, so no coordinated rollback is required elsewhere. If only the runtime
behavior needs disabling without a code revert, restarting Auth Service with its previous build/JAR
achieves the same effect, since the schema changes are additive-only (new tables, no altered/dropped
existing structure) and no other service depends on Auth Service's new tables.
