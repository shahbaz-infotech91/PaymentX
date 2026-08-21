# PaymentX — Phase 3.2: Prompt Service

**Status:** Implemented as a real, standalone Spring Boot service — `paymentx-prompt-service` (port 8092). It calls no LLM, implements no RAG/embeddings/MCP/agents, and is not yet called by anything in production. Every section below marks **IMPLEMENTED IN PHASE 3.2** vs **FUTURE PHASE 3.3+**.

---

## 1. Purpose

Manages, versions, validates, and safely renders AI prompt templates — the "Prompt Repository → Prompt Version → Prompt Renderer → Prompt Validation → Rendered Prompt" pipeline from the brief's architecture diagram. It is the first real backend service in the AI Platform vertical (Phase 3.1's AI Chat Interface was a contract stub inside `paymentx-control-center/backend`, not its own service).

## 2. Architecture

**Design decision — separate Maven module, not a Control Center component**, per `PAYMENTX_PHASE_3_ARCHITECTURE.md` §5/§16: Prompt Service is real, substantial business logic (CRUD, versioning, database persistence) — a genuine bounded context, unlike Phase 3.1's stub. Control Center's own `docs/ARCHITECTURE.md` states a deliberate zero-business-dependency isolation boundary; embedding Prompt Service's logic there would violate exactly that. `paymentx-prompt-service` follows the flat, sibling-of-every-business-service module structure (not nested), depends on `paymentx-common-library` (never the deprecated `paymentx-common`), and is registered in the root `pom.xml` reactor.

```
(future) AI Chat / LLM Service / RAG Service / Agent Orchestrator
                            │  (not wired up yet — see §19)
                            ▼
                    PromptController  (/api/v1/prompts/**)
                            │
                    PromptService (interface) → PromptServiceImpl
                            │
        ┌───────────────────┼───────────────────┐
        ▼                   ▼                   ▼
PromptTemplateRepository  PromptVersionRepository  PromptRenderer (pure logic)
        │                   │
        └─────────┬─────────┘
                   ▼
         Postgres `paymentx_ai` (prompt_template, prompt_version)
```

No LLM SDK, no RAG code, no embedding code, no MCP code, no agent code exists anywhere in this module.

## 3. Module Location

`C:\PaymentX\paymentx-prompt-service` — registered in root `pom.xml`'s `<modules>`. Package root `com.paymentx.prompt`. Port **8092** (next free slot after the 8080–8089 business/Control-Center range and Kafka UI's 8090; see `PAYMENTX_PHASE_3_ARCHITECTURE.md` §16's 8091–8097 AI Platform allocation).

## 4. Database Model

Two tables, in the new shared `paymentx_ai` database (a deliberate, documented deviation from one-DB-per-service — see `PAYMENTX_PHASE_3_ARCHITECTURE.md` §6; `POSTGRES_MULTIPLE_DATABASES` and `infra/prometheus.yml` updated accordingly).

| Table | Purpose | Key columns | Constraints |
|---|---|---|---|
| `prompt_template` | Stable identity of a versioned prompt | `id` (UUID PK), `prompt_key` (unique), `name`, `description`, `type` | `uq_prompt_template_prompt_key`; `chk_prompt_template_type` (SYSTEM/USER/TOOL/RAG) |
| `prompt_version` | One content-immutable version | `id` (UUID PK), `prompt_template_id` (FK), `version_number`, `content` (TEXT), `status`, `variables` (native **jsonb**) | `fk_prompt_version_template`; `uq_prompt_version_template_version` (template_id, version_number); `chk_prompt_version_status`; **`uq_prompt_version_one_active_per_template`** — a **partial unique index** `ON prompt_version(prompt_template_id) WHERE status='ACTIVE'` |

Both extend `paymentx-common-library`'s `AuditableEntity` (`createdAt`/`updatedAt`/`createdBy`/`updatedBy`/`version` — optimistic locking), the same base every new PaymentX service since Routing Service uses.

**Simplification decision (Step 5/7):** no separate `prompt_variable` table. A version's variables have no independent lifecycle — never queried/updated except with their owning version — so they're a native Postgres `jsonb` column (`@JdbcTypeCode(SqlTypes.JSON)`, Hibernate ORM 6's built-in mapping, **zero new persistence-framework dependency**), not a third table.

**No `status` column on `prompt_template`:** a template's "is it usable" state is entirely derived from whether it has an ACTIVE version — a second, independently-settable status field would risk drifting out of sync with the real version-level truth.

## 5. Liquibase Migration

`db/changelog/db.changelog-master.yaml` → `V1_0_0__create_prompt_tables.yaml` (both tables, all constraints/indexes) → `V1_0_1__seed_payment_error_analysis_prompt.yaml` (one DRAFT seed prompt, §17). Same YAML changelog format, `gen_random_uuid()` defaults, and raw-`sql:` mechanism for constraints Liquibase's declarative changes don't cover (partial unique index, CHECK constraints) that Routing Service's `V1_0_0__create_routing_rule_table.yaml` already establishes. **Validated live**: `PromptVersionRepositoryTest`/`PromptControllerIntegrationTest` boot a real Spring context with Liquibase against a Testcontainers Postgres 16 — both changesets apply cleanly (confirmed in this session).

## 6. Prompt Versioning

`PromptStatus`: `DRAFT → ACTIVE → INACTIVE`, plus terminal `ARCHIVED` (never re-activatable). A version number is **always server-computed** (`MAX(version_number) + 1`), never client-supplied — a caller cannot overwrite or skip a version. **A freshly created version never starts ACTIVE** — Step 4 requires an explicit activation call; nothing auto-activates, including the seeded `PAYMENT_ERROR_ANALYSIS` prompt (seeded as DRAFT).

## 7. Prompt Lifecycle

Create prompt (template + v1 DRAFT) → create additional versions (DRAFT) → activate one version (atomically deactivates whichever was previously ACTIVE, in the same transaction, `saveAndFlush` used so a real constraint violation surfaces inside the method's own try/catch rather than at commit time) → deactivate → render. Activating an ARCHIVED version is rejected (`INVALID_PROMPT_STATUS`); deactivating a non-ACTIVE version is rejected (`INVALID_PROMPT_STATUS`); activating an already-ACTIVE version is an idempotent no-op.

## 8. Rendering

`PromptRenderer` — **pure regex substitution only** (`\{\{\s*(name)\s*\}\}`), no templating engine, no SpEL, no scripting of any kind — prompt content is DATA, never executable code (Step 9, satisfied by construction: there is no expression evaluator anywhere in the class). `POST /{promptKey}/render` resolves either an explicit `version` or the current ACTIVE version.

## 9. Variable Validation

Order: (1) every `{{placeholder}}` in content must be a declared variable → `INVALID_PROMPT_CONTENT` (checked at create/version-create time, and defensively again at render); (2) every supplied render-time key must be declared → `UNKNOWN_VARIABLE`; (3) every `required=true` variable must have a non-blank value (null and blank string both count as missing) → `MISSING_VARIABLE`; (4) a `required=false` variable with no supplied value substitutes as an empty string — the explicit, documented exception Step 10 allows for optional variables.

## 10. APIs

All under `/api/v1/prompts`, `ApiResponse<T>` envelope (from `paymentx-common-library`, matching every business service — not Control Center's independent copy):

| Method | Path | Auth |
|---|---|---|
| POST | `/api/v1/prompts` | `PROMPT_ADMIN` |
| GET | `/api/v1/prompts/{promptKey}` | open |
| GET | `/api/v1/prompts/{promptKey}/versions` | open |
| GET | `/api/v1/prompts/{promptKey}/versions/active` | open |
| GET | `/api/v1/prompts/{promptKey}/versions/{version}` | open |
| POST | `/api/v1/prompts/{promptKey}/versions` | `PROMPT_ADMIN` |
| POST | `/api/v1/prompts/{promptKey}/versions/{version}/activate` | `PROMPT_ADMIN` |
| POST | `/api/v1/prompts/{promptKey}/versions/{version}/deactivate` | `PROMPT_ADMIN` |
| POST | `/api/v1/prompts/{promptKey}/render` | open |

`versions/active` is its own endpoint (mirrors Routing Service's `/routes/default` sub-resource pattern) rather than folding into `/versions/{version}` — "active" isn't a version number.

## 11. Security

No secrets logged (no API keys/JWTs/DB credentials exist in this module at all). Rendered prompt content is **not** logged by default — only metadata (`promptKey`, `version`, `variablesUsedCount`, status) per Step 14. `application-prod.yml` uses `${DB_URL}`/`${DB_USERNAME}`/`${DB_PASSWORD}` only, no defaults, matching platform convention.

## 12. Authorization

Reuses the existing platform pattern exactly (Routing Service's `HeaderRoleAuthenticationFilter` + `SecurityConfig`, copied verbatim) — no new authentication system invented. JWT verification happens at API Gateway (not yet routed to this service — see §19); this service trusts the `X-Roles` header Gateway would propagate. Mutating endpoints require `PROMPT_ADMIN`; reads and `render` stay open, since AI Chat/future LLM Service/RAG Service/Agent Orchestrator need `render` as an ordinary service-to-service call, not an admin action.

## 13. Audit

**No integration with Audit Service in this phase** — Audit Service currently only ingests from Kafka topics (14 of them) plus a role-gated direct-write endpoint; wiring Prompt Service into that would mean either producing Kafka events (out of this phase's scope — Prompt Service has no Kafka dependency at all, deliberately minimal) or calling Audit Service's write API directly (not yet gateway-routed either). **Documented future integration point, not invented infrastructure**: prompt lifecycle changes (`PROMPT_CREATED`, `PROMPT_VERSION_CREATED`, `PROMPT_ACTIVATED`, `PROMPT_DEACTIVATED`, `PROMPT_RENDERED`) are logged today via structured `log.info(...)` calls carrying `promptKey`/version/status (see `PromptServiceImpl`), which is the same evidence trail every log-based operational review in this platform already relies on, until a real event-based audit integration is deliberately built.

## 14. Observability

Standard Actuator/Micrometer/Zipkin, matching Routing Service exactly. `PromptMetrics` exposes exactly the six meters Step 17 lists: `prompt_requests_total` (by operation), `prompt_render_success_total`/`prompt_render_failure_total` (by promptKey/errorCode), `prompt_render_latency`/`active_prompt_lookup_latency` (Timers), `validation_failure_count` (by errorCode). Correlation ID (`CorrelationIdFilter`, own copy per platform convention — not shared via the library) and trace ID (Zipkin, 100% sampling in dev) preserved identically to every other service. `infra/prometheus.yml` gained a new scrape target.

## 15. Caching Decision

**Not implemented in this phase — deliberate.** Step 18 explicitly forbids adding Redis automatically; nothing calls this service in production yet (Phase 3.3+ is the first real caller), so there is no real traffic pattern to optimize for, and adding a cache now would be premature optimization with an invalidation-correctness cost (activate/deactivate would need to evict) for zero measured benefit. Revisit once Phase 3.3+ traffic makes `getActiveVersion`/`render` lookup latency a real, measured concern — `active_prompt_lookup_latency` (§14) is already instrumented specifically so that future decision has real data behind it.

## 16. Concurrency Handling

Two layers, per Step 20's explicit requirement not to rely on Java in-memory synchronization alone: (1) a **database partial unique index** (`uq_prompt_version_one_active_per_template`) is the real backstop — verified live in this session via `PromptVersionRepositoryTest`, which persists two ACTIVE rows for the same template and confirms Postgres rejects the second with a real `DataIntegrityViolationException`; (2) `PromptServiceImpl.activateVersion` calls `saveAndFlush` (not `save`) so that violation surfaces inside its own try/catch, translated to a real `409 ACTIVE_VERSION_CONFLICT` rather than an unhandled transaction-rollback failure. The same pattern protects duplicate version-number races (`uq_prompt_version_template_version`) and duplicate prompt-key races (`uq_prompt_template_prompt_key`).

## 17. Testing

**38/38 tests pass** (verified live in this session, real Postgres via Testcontainers — Docker Desktop was started specifically to run these):
- `PromptServiceImplTest` (18) — plain Mockito, matching `RoutingServiceImplTest`'s convention: create/duplicate/version/activate/deactivate/render/not-found, including simulated `DataIntegrityViolationException` race translation.
- `PromptRendererTest` (8) — pure logic, no Spring: multi-variable substitution, missing/null/empty/unknown variable, undeclared placeholder, optional-variable empty-string substitution.
- `PromptVersionRepositoryTest` (6) — `@DataJpaTest` + Testcontainers Postgres 16: proves the partial unique index and duplicate-version constraint at the real database level, and doubles as a Liquibase migration validity check (a malformed changeset would fail context startup).
- `PromptControllerIntegrationTest` (6) — full `@SpringBootTest` + Testcontainers: real HTTP create→activate→render happy path, 403 without `PROMPT_ADMIN`, 400 on blank key, 409 on duplicate key, 404 on unknown prompt, 422 on missing render variable.

Phase 3.1 (Control Center backend) re-verified in this session: still builds, all 18 AI-related tests still pass, `AI_SERVICE_NOT_READY`/`AI_NOT_CONFIGURED` behavior unchanged.

## 18. Known Limitations

- Not reachable through API Gateway yet (no route added — out of this phase's scope, tracked for Phase 3.3+ alongside the other gateway-routing prerequisites `PAYMENTX_PHASE_3_ARCHITECTURE.md` §10 already flags).
- No Audit Service integration (§13) — structured logs only, for now.
- No caching (§15) — deliberate, revisit with real traffic data.
- `render`'s max-100-variables and 20000-char content bound are reasonable defaults, not derived from any measured LLM context-window limit — LLM Service (Phase 3.3) owns that concern for real.
- The real `infra/docker-compose.yml` Postgres instance was **not** restarted in this session (only Testcontainers' ephemeral instances were used for tests) — an operator must restart/recreate that container (or run the init script again) before `paymentx_ai` exists there for a real `mvn spring-boot:run`.

## 19. Future Phase 3.3+ Integration Points

- **LLM Service (Phase 3.3)** calls `POST /{promptKey}/render` (or `GET /{promptKey}/versions/active` + its own rendering, though reusing this endpoint is preferred) to get grounded text before calling an LLM provider — `RenderPromptResponse.renderedContent` is exactly that contract, already stable.
- **AI Chat Service** (`paymentx-control-center/backend`) is **unchanged by this phase** — `AiChatService.sendMessage` still always throws `AiServiceNotReadyException`, exactly as Phase 3.1 left it (Step 30's explicit requirement). Wiring it to call Prompt Service is Phase 3.3+ work, once LLM Service also exists to complete the chain.
- **API Gateway** needs a new `/api/v1/prompts/**` route before any out-of-process caller other than a direct `localhost:8092` call can reach this service in a Gateway-routed deployment.
- **RAG Service / Agent Orchestrator** (Phase 3.6/3.8) are expected to use `PromptType.RAG`/`PromptType.TOOL` templates respectively — no code changes needed here, the type enum already anticipates both.

---

**LLM Service is NOT implemented. RAG is NOT implemented. Embedding Service is NOT implemented. Vector Database integration is NOT implemented. MCP is NOT implemented. Agent Orchestrator is NOT implemented.**
