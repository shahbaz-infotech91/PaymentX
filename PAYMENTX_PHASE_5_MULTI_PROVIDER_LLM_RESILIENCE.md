# Phase 5 — Multi-Provider LLM Resilience: OpenAI Last-Resort Paid Fallback

## 1. Objective

Add OpenAI as a fourth `LlmProvider` in `paymentx-llm-service`, completing the desired
failover chain:

```
Gemini (primary)
   |  retryable failure
   v
Groq (fallback 1)
   |  retryable failure
   v
Anthropic (fallback 2)
   |  retryable failure
   v
OpenAI (fallback 3 — last resort, paid)
```

OpenAI is deliberately the **last** provider in the chain. Unlike Gemini/Groq/Anthropic (all
already integrated and, in this environment, either free-tier or already-funded), a real OpenAI
call draws down a **funded, non-free credit balance**. It must only ever be reached once every
higher-priority provider has genuinely failed with a retryable error — never called speculatively,
never called to "prove it works."

This phase adds no new routing mechanism. `provider.LlmProviderRouter` (introduced in Phase 4.8.6,
extended to an ordered N-provider chain in the earlier part of Phase 5 to add Groq) already walks
an arbitrary ordered list of fallback provider names, stopping at the first that succeeds, and
already reuses `LlmException.isRetryable()` uniformly at every hop. Adding OpenAI required **zero
changes to the router itself** — it is registered as a fourth `LlmProvider` Spring bean and named
in the ordered fallback list, exactly like Groq before it.

## 2. Model selection

**Selected: `gpt-5.4-nano`**

Rationale:
- OpenAI is intentionally the paid last-resort hop of a four-provider chain — it should be reached
  rarely, so cost-per-call matters far more than raw capability.
- A "nano" tier model is the cost-efficient choice explicitly requested for this phase, while still
  supporting the existing OpenAI Chat Completions tool-calling wire format this service's
  `LlmProvider` abstraction already targets (the same format Groq's own API mirrors, and the format
  `AgentPlanner`'s prompt/response contract in `paymentx-agent-orchestrator` already expects
  regardless of which concrete provider serves a request).
- No flagship model (e.g. a GPT-5.x full-size model) was used anywhere in this phase — not in
  configuration defaults, not in any test.
- Configurable independently of the default via `OPENAI_MODEL`, matching every other provider's
  own override pattern (`GEMINI_MODEL`, `GROQ_MODEL`, `LLM_MODEL` for Anthropic).

## 3. Configuration

New `llm.openai.*` block, mirroring the existing `llm.anthropic`/`llm.gemini`/`llm.groq` shape
exactly (`LlmProperties.OpenAi`):

| Property | Env var | Default |
|---|---|---|
| `llm.openai.api-key` | `OPENAI_API_KEY` | *(empty placeholder — never hardcoded)* |
| `llm.openai.model` | `OPENAI_MODEL` | `gpt-5.4-nano` |
| `llm.openai.base-url` | `OPENAI_BASE_URL` | `https://api.openai.com/v1` |
| `llm.openai.default-max-tokens` | `OPENAI_MAX_TOKENS` | `4096` |
| `llm.openai.timeout-seconds` | `OPENAI_TIMEOUT_SECONDS` | `60` |

`OPENAI_API_KEY` is read from the **existing Windows User environment variable** already
configured on this host — it is never written into `application.yml`, any other `*.yml` file,
source code, tests, git, documentation, logs, audit records, the frontend, or exception messages.
Every failure path in `OpenAiLlmProvider` surfaces the response body from the failed HTTP call
(diagnostic detail an operator needs), but never the `Authorization` header value.

### Routing — where OpenAI enters the chain

- **`application.yml` (base/prod default): unchanged.** `llm.provider` still defaults to
  `anthropic`; `llm.routing.fallback-providers` still defaults to `anthropic` only (self-fallback,
  effectively disabled — the exact prior behavior). The `llm.openai.*` block is registered
  unconditionally (so the bean, health check, and circuit breaker all exist regardless of
  environment) but is **not** in prod's default failover chain — an operator must explicitly opt
  in via `LLM_FALLBACK_PROVIDERS`, exactly the same convention Groq's addition already
  established. A funded credit balance must never enter a chain by accident.
- **`application-dev.yml` (this environment's active profile): the full desired chain.**
  ```yaml
  llm:
    provider: ${LLM_PROVIDER:gemini}
    routing:
      fallback-providers: ${LLM_FALLBACK_PROVIDERS:groq,anthropic,openai}
  ```
  Groq is now tried *before* Anthropic in this profile (previously the reverse) because this
  environment's Anthropic account remains billing-blocked (see
  `PAYMENTX_PHASE_4_8_4_GEMINI_PROVIDER_AND_RCA_VALIDATION.md`) while Groq's free tier is
  genuinely reachable here — trying Anthropic first would waste a hop on a provider already known
  to fail in this environment. OpenAI is last.

### Independent circuit breakers / retry

Each provider retains its own Resilience4j circuit-breaker and retry instance
(`llmProvider-gemini`, `llmProvider-anthropic`, `llmProvider-groq`, `llmProvider-openai`) — same
sliding-window/threshold tuning duplicated per instance, same
`RetryConfigCustomizer`-supplied `ex instanceof LlmException le && le.isRetryable()` predicate
(`ResilienceConfig.llmProviderOpenAiRetryConfigCustomizer`). A broken OpenAI provider can never
trip the breaker guarding Gemini/Groq/Anthropic, and vice versa. If Gemini recovers, it is
automatically primary again on the very next request — `LlmProviderRouter` always tries the
configured primary first, on every single call; there is no persisted "currently degraded" flag
to reset and no manual provider switch anywhere in this design.

## 4. Failover behavior (reusing existing mechanisms only)

- **`LlmException.isRetryable()`** is the sole retry/fallback decision function, unchanged from
  Phase 4.8.6/the earlier Groq addition. A retryable failure (429/quota, timeout, 5xx,
  circuit-open) at any hop — including OpenAI's own — moves to the next configured entry (or, for
  OpenAI as the last entry, exhausts the chain). A non-retryable failure (invalid request,
  credentials rejected, not configured) at **any** hop stops the whole chain immediately and
  propagates that specific failure unmodified — never masked by silently trying yet another
  provider, including OpenAI.
- **`fallbackUsed`/`fallbackReason`** on the response always reflect whether *any* fallback was
  used and the *original* primary failure's error code, regardless of how many hops it took to
  reach OpenAI (e.g. `provider=openai`, `fallbackUsed=true`,
  `fallbackReason=LLM_RATE_LIMITED` when Gemini's quota was the actual root cause).
- **No new global rate limiter.** The existing `resilience4j.ratelimiter.instances.llmGenerate`
  (8 permits / 10s, `LLM_LOCAL_RATE_LIMITED`) is untouched and still guards the single
  `LlmServiceImpl.generate()` choke point every provider call — including OpenAI's — funnels
  through, regardless of which provider ultimately serves the request.
- **No new error-classification mechanism.** `OpenAiLlmProvider` maps HTTP status codes to the
  exact same `LlmException` factory methods `GroqLlmProvider`/`GeminiLlmProvider` already use
  (401/403 → `credentialsRejected`, 429 → `rateLimited`, other 4xx → `invalidRequest`, 5xx →
  `providerUnavailable`, connection/read timeout → `timeout`, empty `choices` →
  `responseInvalid`).

## 5. Provider visibility chain — unaffected

`LlmServiceClient` (agent-orchestrator) → `AgentExecution` → `AgentPlanner` →
`AgentExecuteResponse` → `AgentAuditClient` → Control Center → Frontend already carry
`provider`/`fallbackUsed`/`fallbackReason` end-to-end as opaque strings (established in Phase
4.8.6). None of these layers reference "gemini"/"anthropic"/"groq" by name anywhere in their own
logic — `provider="openai"` flows through identically to any other provider name with zero code
change required in any of these classes. No files in `paymentx-agent-orchestrator` were touched by
this phase.

`GET /api/v1/llm/health`'s `allProviders` array (Phase 5/Groq addition) now reports a fourth entry
for `openai` (`apiKeyPresent`/`configuredModel` only — never the key itself), alongside
gemini/anthropic/groq, via `LlmServiceImpl.allProviderConfigStatuses()`.

## 6. MCP / agent tool-calling compatibility — not regressed

No changes were made to `McpToolClient`, `ToolSummary`, `AgentPlanner`, or any other class in the
`inputSchema` → schema-conformant-tool-arguments chain fixed earlier in Phase 4.8.5. This phase's
changes are entirely confined to `paymentx-llm-service`'s provider/routing layer; the
provider-agnostic `LlmProviderRequest`/`LlmProviderResult` contract `OpenAiLlmProvider` implements
is identical in shape to every other provider's, so `LlmServiceClient`'s existing request/response
handling requires no changes.

## 7. Cost protection — zero real OpenAI calls made

Every `OpenAiLlmProvider` test points at a local WireMock server, never `https://api.openai.com`.
`LlmProviderRouterTest`'s OpenAI-chain scenarios use plain Mockito mocks of the `LlmProvider`
interface — no real HTTP call of any kind. The full higher-priority-provider chain
(Gemini/Groq/Anthropic) was never intentionally exhausted against a real endpoint to "prove
OpenAI works" — every fallback-to-OpenAI scenario is simulated via mocked `LlmException` throws,
consistent with how the existing Gemini→Anthropic and Gemini→Groq scenarios were already tested
before this phase.

**Real API verification is intentionally deferred.** OpenAI provider is implemented and
deterministically verified but real API verification is intentionally deferred to protect the
funded OpenAI credit balance. This is not classified as an implementation defect — see the
"Classification" section below.

## 8. Files changed

### Source
- `paymentx-llm-service/src/main/java/com/paymentx/llm/config/LlmProperties.java` — added
  `openai` field + `OpenAi` nested config class.
- `paymentx-llm-service/src/main/java/com/paymentx/llm/config/ResilienceConfig.java` — added
  `llmProviderOpenAiRetryConfigCustomizer` bean.
- `paymentx-llm-service/src/main/java/com/paymentx/llm/provider/openai/OpenAiLlmProvider.java`
  *(new)* — the OpenAI `LlmProvider` implementation.
- `paymentx-llm-service/src/main/java/com/paymentx/llm/provider/openai/OpenAiRequest.java` *(new)*
- `paymentx-llm-service/src/main/java/com/paymentx/llm/provider/openai/OpenAiResponse.java` *(new)*
- `paymentx-llm-service/src/main/java/com/paymentx/llm/service/impl/LlmServiceImpl.java` — added
  an `"openai"` branch to `health()` and a fourth entry in `allProviderConfigStatuses()`.

### Configuration
- `paymentx-llm-service/src/main/resources/application.yml` — added `llm.openai.*` block,
  `resilience4j.circuitbreaker.instances.llmProvider-openai`,
  `resilience4j.retry.instances.llmProvider-openai`. Prod-facing defaults (`llm.provider`,
  `llm.routing.fallback-providers`) unchanged.
- `paymentx-llm-service/src/main/resources/application-dev.yml` — `fallback-providers` changed
  from `anthropic,groq` to `groq,anthropic,openai`.

### Tests
- `paymentx-llm-service/src/test/java/com/paymentx/llm/provider/openai/OpenAiLlmProviderTest.java`
  *(new)* — 12 deterministic WireMock-based tests (success, refusal, missing key, 401, 429, 400,
  503, timeout, malformed body, empty choices).
- `paymentx-llm-service/src/test/java/com/paymentx/llm/provider/LlmProviderRouterTest.java` —
  added an `openai` mock and 7 new full-chain scenarios (4-hop success, OpenAI-not-called at each
  earlier successful hop, OpenAI-only-reached-when-all-else-fails, OpenAI circuit-open, OpenAI
  non-retryable-as-last-hop).
- `paymentx-llm-service/src/test/java/com/paymentx/llm/provider/LlmProviderCircuitBreakerIndependenceTest.java`
  — extended from 3 to 4 independently-verified circuit breakers.

No files were changed in `paymentx-agent-orchestrator`, `paymentx-control-center`,
`paymentx-mcp-gateway`, or any other module.

## 9. Test results

*(filled in after `mvn test` — see build/deploy report below)*

## 10. Build / deploy

*(filled in after `mvn package` — see build/deploy report below)*

## 11. Classification

**B — implementation complete with external/live verification deferred.**

Rationale: every requirement in this phase's scope (provider bean, configuration loading, model
configuration, circuit breaker registration, routing order, fallback metadata, deterministic
provider behavior) is implemented and verified deterministically. Real OpenAI API verification is
deliberately out of scope for this phase, per explicit instruction, to protect the funded OpenAI
credit balance — this is the same "B" classification the earlier Gemini/RCA validation work used
for a comparable external-verification gap
(`PAYMENTX_PHASE_4_8_5_REMEDIATION_AND_FINAL_CLOSURE.md`), not a defect.
