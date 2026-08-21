# PaymentX — Phase 3.3: LLM Service

**Status:** Complete
**Depends on:** Phase 3.0 (Architecture & Readiness Audit), Phase 3.1 (AI Chat Interface), Phase 3.2 (Prompt Service)
**Does NOT implement:** Embeddings, Vector Database, RAG, MCP Gateway, Agent Orchestrator (Phase 3.4+)

---

## 1. Overview

Phase 3.3 delivers `paymentx-llm-service`, a standalone Spring Boot microservice that receives an
already-rendered prompt, calls a real, configured LLM provider (Anthropic, via the official
`anthropic-java` SDK), validates and normalizes the provider's response, and returns it — with real
timeout/retry/circuit-breaking, real usage/latency tracking, and no fabricated content anywhere. It is
the second real AI Platform backend service, after Prompt Service (Phase 3.2). Phase 3.3 also updates
Control Center's `AiChatService` to call this real service instead of always throwing, and reports real
per-component health for both Prompt Service and LLM Service.

## 2. Goals

- Receive a rendered prompt over REST and call a real LLM provider — never a canned/templated response.
- Normalize the provider's response into a stable, provider-agnostic contract.
- Handle timeout, retry, and circuit-breaking using this platform's existing Resilience4j conventions.
- Track real token usage and latency per call.
- Maintain a provider abstraction (`LlmService` → `LlmProvider` → `ProviderAdapter`) so a second
  provider can be added later without touching the application layer.
- Fully externalize configuration — no hardcoded secrets anywhere (source, YAML, tests, logs, README,
  Docker, frontend).
- Integrate with Prompt Service via plain REST only (no database coupling) and wire Control Center's AI
  Chat to this service.

## 3. Explicit Non-Goals

This phase deliberately does **NOT** implement:

- **Embeddings** — no embedding model call, no vector representation of any text.
- **Vector Database** — no vector storage/index of any kind.
- **RAG (Retrieval-Augmented Generation)** — no document retrieval, no context injection from a corpus.
- **MCP Gateway** — no Model Context Protocol server/client.
- **Agents / Multi-Agent Orchestration** — no autonomous tool use, no agent loop, no orchestration.
- **Conversation persistence** — Phase 3.1's constraint still applies; this service accepts a single
  rendered prompt per request, nothing stateful.
- **A second LLM provider** — Anthropic is the one real, configured provider; the `LlmProvider`
  interface exists so a second one can be added later without changing `LlmServiceImpl`.

These are reserved for later phases per `PAYMENTX_PHASE_3_ARCHITECTURE.md`.

## 4. Architecture & Layering

```
LlmController  →  LlmService (interface)  →  LlmServiceImpl
                                                    │
                                                    ▼
                                          LlmProvider (interface)
                                                    │
                                                    ▼
                                        AnthropicLlmProvider (adapter)
                                                    │
                                                    ▼
                                   com.anthropic.* (official Anthropic Java SDK)
```

`com.anthropic.*` types (`AnthropicClient`, `Message`, `MessageCreateParams`, …) appear in **exactly
one** class: `provider/anthropic/AnthropicLlmProvider.java`. Every other class in this service — the
controller, `LlmServiceImpl`, every DTO — only ever sees the provider-agnostic
`LlmProviderRequest`/`LlmProviderResult` (internal) or `GenerateRequest`/`GenerateResponse` (public,
HTTP-shaped) types. Deliberately stateless: no database, no Liquibase, unlike Prompt Service — see
`PAYMENTX_PHASE_3_ARCHITECTURE.md` §6/§8.

## 5. Provider Selection & Rationale

**Chosen provider: Anthropic**, via the official `anthropic-java` SDK (`com.anthropic:anthropic-java:2.34.0`).

- The `claude-api` skill's mandatory-SDK rule (never hand-roll HTTP against a provider that has an
  official SDK) governs how the call is made.
- The SDK is isolated inside `AnthropicLlmProvider` — the one deliberate deviation from this platform's
  usual "everything outbound goes through `RestTemplate`" convention (Control Center's
  `HttpClientConfig`, notification-service's `WebClientConfig`), justified because it is fully contained
  and does not introduce a second general-purpose HTTP client pattern anywhere else in the codebase.
- Default model: `claude-opus-5` (the skill's mandatory default), overridable per-request
  (`GenerateRequest.model`) or via `LLM_MODEL`.
- The SDK's own `maxRetries` is set to `0` — Resilience4j (below) owns all retry/circuit-breaking
  behavior centrally, so retry counts are observable in one place and never silently doubled.

## 6. Module Structure

```
paymentx-llm-service/
  pom.xml
  src/main/java/com/paymentx/llm/
    LlmServiceApplication.java
    config/       LlmProperties, SecurityConfig, CorrelationIdFilter, ResilienceConfig
    controller/   LlmController
    dto/          GenerateRequest, GenerateResponse, LlmUsage, LlmHealthResponse
    exception/    LlmErrorCodes, LlmException, GlobalExceptionHandler
    metrics/      LlmMetrics
    provider/     LlmProvider, LlmProviderRequest, LlmProviderResult
      anthropic/  AnthropicLlmProvider
    service/      LlmService
      impl/       LlmServiceImpl
  src/main/resources/
    application.yml, application-dev.yml, application-prod.yml
  src/test/java/com/paymentx/llm/
    provider/anthropic/AnthropicLlmProviderTest   (WireMock, real wire-level)
    service/LlmServiceImplTest                    (Mockito, mocked LlmProvider)
    controller/LlmControllerIntegrationTest        (real Spring context + WireMock)
```

Port: **8093** (matches the Phase 3.0 architecture's AI Platform port allocation).

## 7. API Contract — `POST /api/v1/llm/generate`

Request body (`GenerateRequest`):

```json
{
  "prompt": "Summarize this payment dispute in one sentence.",
  "systemPrompt": null,
  "model": null,
  "maxTokens": null,
  "temperature": null
}
```

`prompt` is the **already-rendered** text a caller obtained from Prompt Service's
`POST /api/v1/prompts/{key}/render` (`RenderPromptResponse.renderedContent`). This service does not call
Prompt Service and does not accept a `promptKey`/`variables` pair — see §17.

Response body (`ApiResponse<GenerateResponse>`, HTTP 200):

```json
{
  "success": true,
  "data": {
    "provider": "anthropic",
    "model": "claude-opus-5",
    "content": "The dispute concerns a duplicate charge on PMT-123.",
    "stopReason": "end_turn",
    "refused": false,
    "usage": {"inputTokens": 42, "outputTokens": 12, "cacheCreationInputTokens": null, "cacheReadInputTokens": null},
    "latencyMs": 812
  },
  "timestamp": "2026-08-18T01:20:00Z"
}
```

## 8. API Contract — `GET /api/v1/llm/health`

Deliberately does **not** make a real (billed) call to the provider — see §19.

```json
{
  "success": true,
  "data": {
    "status": "CONFIGURED",
    "provider": "anthropic",
    "configuredModel": "claude-opus-5",
    "apiKeyPresent": true,
    "note": "This check confirms whether an API key is configured, not whether it is valid or whether the provider is currently reachable...",
    "checkedAt": "2026-08-18T01:20:00Z"
  }
}
```

## 9. DTOs & Data Model

| Type | Layer | Purpose |
|---|---|---|
| `GenerateRequest` | HTTP (public) | Validated inbound request |
| `GenerateResponse` | HTTP (public) | Normalized outbound response |
| `LlmUsage` | HTTP (public) | Real token counts, nested in `GenerateResponse` |
| `LlmHealthResponse` | HTTP (public) | Honest config-presence health signal |
| `LlmProviderRequest` | Internal | Fully-resolved request passed to `LlmProvider` |
| `LlmProviderResult` | Internal | Provider-agnostic result returned by `LlmProvider` |

No entity/repository/Liquibase — this service is stateless (§4).

## 10. Error Codes & HTTP Status Mapping

Defined in `LlmErrorCodes`, thrown as `LlmException` (carries `errorCode`, `message`, `retryable`,
`httpStatus`):

| errorCode | HTTP status | retryable | Meaning |
|---|---|---|---|
| `LLM_NOT_CONFIGURED` | 503 | false | No API key configured |
| `LLM_CREDENTIALS_REJECTED` | 503 | false | Provider rejected the credentials (401/403) |
| `LLM_PROVIDER_TIMEOUT` | 504 | true | Timeout or connection failure |
| `LLM_RATE_LIMITED` | 429 | true | Provider rate limit (429) |
| `LLM_PROVIDER_UNAVAILABLE` | 502 | true | Provider 5xx / unexpected status |
| `LLM_INVALID_REQUEST` | 422 | false | Provider rejected the request as malformed (400/404/422) |
| `LLM_RESPONSE_INVALID` | 502 | false | Response could not be parsed/validated |
| `LLM_INTERNAL_ERROR` | 500 | false | Unexpected SDK error |

A **refusal** (`stop_reason == "refusal"`) is explicitly **not** one of these — it is a normal HTTP 200
response with `refused: true` (see §19).

## 11. Resilience — Circuit Breaker & Retry

Reuses this platform's existing Resilience4j convention (`resilience4j-spring-boot3`, the same
instance-naming/tuning shape as payment-service's previously-unwired `routingService` instance),
actually wired via `@CircuitBreaker(name = "llmProvider")` / `@Retry(name = "llmProvider")` on
`AnthropicLlmProvider.generate`.

- Circuit breaker: count-based sliding window (20), min 10 calls, 50% failure threshold, 30s open wait,
  5 half-open trial calls.
- Retry: 3 attempts, 500ms base wait, exponential backoff ×2, randomized wait factor.
- **Which failures retry** is decided by `ResilienceConfig`'s `RetryConfigCustomizer` — a
  `retryOnException` predicate reading `LlmException.isRetryable()` — because every failure this
  service throws is the *same* class (`LlmException`) with a per-instance `retryable` flag, which YAML's
  class-based `retry-exceptions`/`ignore-exceptions` lists cannot express.

## 12. Security & Trust Boundary

Matches every other PaymentX service's established trust boundary: JWT verification happens at API
Gateway; this service is not internet-facing. Unlike Prompt Service, `LlmController` has no admin/write
endpoint, so this module deliberately has **no** `@EnableMethodSecurity`, `HeaderRoleAuthenticationFilter`,
or `@PreAuthorize` — adding one would be dead code (see `SecurityConfig`'s javadoc).

## 13. Configuration & Secrets Management

All `llm.*` config is bound via `LlmProperties` (`@ConfigurationProperties(prefix = "llm")`,
`@ConfigurationPropertiesScan` on `LlmServiceApplication`):

| Property | Env var | Default |
|---|---|---|
| `llm.anthropic.api-key` | `LLM_API_KEY` | *(empty — no default, no fake key)* |
| `llm.anthropic.model` | `LLM_MODEL` | `claude-opus-5` |
| `llm.anthropic.base-url` | `LLM_BASE_URL` | *(SDK default)* |
| `llm.anthropic.default-max-tokens` | `LLM_MAX_TOKENS` | `4096` |
| `llm.anthropic.timeout-seconds` | `LLM_TIMEOUT_SECONDS` | `60` |

`apiKey` is `@ToString.Exclude`d — never appears in a log line, actuator endpoint, or exception message.
No test, YAML file, Dockerfile, or frontend file in this repository contains a real or fake Anthropic API
key at any point in this phase.

## 14. Observability — Metrics, Logging, Tracing

`LlmMetrics` (Micrometer, hand-registered — matches `PromptMetrics`/`RoutingMetrics`'s explicit-call-site
pattern, not `@Timed`/`@Counted`): `llm_requests_total`, `llm_generate_success_total`,
`llm_generate_failure_total`, `llm_generate_refused_total`, `llm_generate_latency`,
`llm_provider_error_total`, `llm_input_tokens_total`, `llm_output_tokens_total`,
`llm_not_configured_total`, `llm_health_check_total` — 10 meters. Resilience4j's own circuit-breaker/retry
state is auto-exported separately (not duplicated here). `CorrelationIdFilter` propagates
`X-Correlation-Id` into every log line (matches Prompt Service exactly). Exposed at
`/actuator/prometheus`; scraped by `infra/prometheus.yml`'s new `paymentx-llm-service` target (port 8093).

## 15. Testing Strategy & Coverage

| Test class | Kind | Focus |
|---|---|---|
| `AnthropicLlmProviderTest` | WireMock, real wire-level | Real HTTP request shape, real SDK typed-exception mapping (success, refusal, 429, 401, 400, 500, malformed JSON) |
| `LlmServiceImplTest` | Mockito, mocked `LlmProvider` | Not-configured short-circuit, success/refusal mapping, error propagation, health |
| `LlmControllerIntegrationTest` | Real Spring context + WireMock | Validation → 400, real success → 200, real 429 → `LLM_RATE_LIMITED`, health |

**16 tests, 16 passing.** WireMock (`org.wiremock:wiremock-standalone:3.13.2`) is an already-established
test dependency in this platform (notification-service, api-gateway) — reused here, not newly introduced.

Control Center backend gained 2 new/updated test classes for the Step 10 wiring:
`AiChatServiceTest` (rewritten for the new `AiPlatformClient` collaborator — 7 tests) and
`AiPlatformClientTest` (new, WireMock-based — 6 tests). All pass (17 AI-related Control Center tests
total, plus the 3 pre-existing `AiControllerTest`/`GlobalExceptionHandlerAiTest` tests unaffected).

## 16. Deployment — Ports, Docker, Prometheus

- Port **8093**, host-process convention (matches every other PaymentX service — `infra/prometheus.yml`
  scrapes `host.docker.internal:8093`).
- No database — `infra/docker-compose.yml`'s `paymentx_ai` database is unchanged (only Prompt Service
  owns tables there); no Liquibase changelog in this module.
- `infra/prometheus.yml` gained a `paymentx-llm-service` scrape target.

## 17. Integration — Prompt Service → LLM Service

**By design, LLM Service does not call Prompt Service.** The target flow
(`PAYMENTX_PHASE_3_ARCHITECTURE.md` §4/§9) is:

```
AI Chat → Prompt Service (render) → Rendered Prompt → LLM Service (generate) → Normalized Response → AI Chat
```

Prompt Service's real `POST /api/v1/prompts/{key}/render` contract (proven in Phase 3.2, 38/38 tests) is
the integration point — it is the *caller's* (AI Chat Service's) responsibility to render a prompt first
and pass the rendered text to `POST /api/v1/llm/generate`. This keeps the two services decoupled via
plain REST, with no database coupling, exactly as required.

## 18. Integration — Control Center AI Chat → LLM Service

`AiChatService.sendMessage()` (Control Center backend) now:

1. Still throws `AI_NOT_CONFIGURED` immediately when `control-center.ai.enabled=false` — unchanged from
   Phase 3.1, and `AiPlatformClient` is never called in that path (verified by test).
2. When enabled, calls the new `AiPlatformClient.generate(llmServiceUrl, message, correlationId)` against
   the real, configured LLM Service and returns a real `AiChatResponse` (`status`: `"COMPLETED"` or
   `"REFUSED"`, real `content`).
3. Any real upstream failure (no credentials, rate limited, provider unavailable, LLM Service
   unreachable) is rethrown as `AiServiceNotReadyException` carrying LLM Service's **real** `errorCode`
   and message — never collapsed into one generic reason.

**Why this phase does not also call Prompt Service for chat:** wiring a real `Prompt Service → LLM
Service` chain for free-typed chat messages would require a named prompt *template* (a `promptKey`) to
exist in Prompt Service's data — no such template exists yet (creating one is content/operator work
through Prompt Service's own admin API, not a code-wiring change). Today, the user's raw message is sent
directly as the LLM prompt. Prompt Service's render contract is unchanged and ready for a future phase to
point this call at once such a template is defined. This is a documented, deliberate scope decision, not
an oversight.

`AiChatService.health()` now reports **real** per-component status for `promptService`/`llmService` (via
`AiPlatformClient.componentHealth`'s real, free `/actuator/health` probe — never a billed generation
call) instead of Phase 3.1's hardcoded `NOT_IMPLEMENTED`, now that both real services exist.
`embeddingService`/`ragService`/`mcpGateway`/`agentOrchestrator` remain honestly `NOT_IMPLEMENTED`.

Frontend (`services/aiService.ts`, `types/ai.ts`): `classifyAiError` now recognizes LLM Service's real
error codes (`LLM_NOT_CONFIGURED`/`LLM_CREDENTIALS_REJECTED` → new `PROVIDER_NOT_CONFIGURED` category;
`LLM_RATE_LIMITED` → `RATE_LIMITED`; `LLM_PROVIDER_TIMEOUT` → `TIMEOUT`;
`LLM_PROVIDER_UNAVAILABLE`/`LLM_INVALID_REQUEST`/`LLM_RESPONSE_INVALID`/`LLM_INTERNAL_ERROR` → new
`PROVIDER_UNAVAILABLE` category) instead of falling through to a generic `UNKNOWN`. No redesign of the AI
Assistant UI — `useAiChat.ts` already handled a real `response.content` and a real classified error
generically; a refusal renders as a normal assistant message with honest declined-to-answer text.

## 19. No-Fake-AI Compliance Statement

- If `LLM_API_KEY` is unset, the service starts normally (does not crash) and every
  `POST /api/v1/llm/generate` call fails honestly with `LLM_NOT_CONFIGURED` (503) — never a canned
  answer.
- `GET /api/v1/llm/health` never makes a real, billed provider call; it reports whether an API key is
  *configured*, explicitly stating in the response body that this is not proof of validity or
  reachability.
- A provider **refusal** (`stop_reason == "refusal"`) is surfaced as `refused: true` with empty/partial
  `content` — never papered over with invented text, and never treated as an error.
- Test doubles (Mockito mocks, WireMock stubs) exist only inside `src/test/java` — no test double,
  fixture, or fallback value exists anywhere in `src/main/java`.
- Every error path returns a real, specific `errorCode` traceable to an actual failure (typed SDK
  exception, HTTP status, or connectivity failure) — never a generic "AI failed."

## 20. Known Limitations & Deferred Work

- Single provider (Anthropic) — the `LlmProvider` interface exists for a future second provider, but no
  selection strategy is implemented (explicitly future-phase scope).
- No streaming — a single blocking `messages().create()` call; no SSE/streaming response to the caller.
- No conversation/context persistence — matches Phase 3.1's constraint, single rendered prompt/request
  only.
- `AnthropicIoException` (timeouts and connection failures) cannot be distinguished from each other via
  the SDK's typed exception surface — both map to `LLM_PROVIDER_TIMEOUT`, documented as a deliberate,
  honest simplification in `AnthropicLlmProvider`'s javadoc.
- Prompt Service is not called by the chat flow yet (§17/§18) — deferred until a real chat prompt
  template exists.
- Embeddings, Vector DB, RAG, MCP Gateway, Agent Orchestrator: **not implemented** (§3).

## 21. Verification Checklist & Final Status

- [x] `paymentx-llm-service` module created, registered in root `pom.xml`, builds standalone.
- [x] Real Anthropic SDK integration, isolated to one adapter class.
- [x] Provider abstraction (`LlmProvider`) in place for future providers.
- [x] Resilience4j circuit breaker + retry actually wired (not just configured).
- [x] No hardcoded secrets anywhere (source, YAML, tests, logs, Docker, frontend) — verified by review.
- [x] 16/16 `paymentx-llm-service` tests passing.
- [x] Control Center `AiChatService` wired to real LLM Service; 17/17 related Control Center tests
      passing (including the 3 pre-existing AI tests, unaffected).
- [x] Frontend error classification extended for real LLM Service error codes; 22/22 related frontend
      tests passing.
- [x] Phase 3.1 (`AiChatServiceTest`, rewritten) and Phase 3.2 (`paymentx-prompt-service`, 38/38) both
      still pass.
- [x] `infra/prometheus.yml` updated with the new scrape target.
- [x] This document written.

**Embedding Service, Vector Database, RAG Service, MCP Gateway, and Agent Orchestrator are NOT
IMPLEMENTED as of the end of Phase 3.3.**
