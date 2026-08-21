# PaymentX Phase 3.7 — MCP Gateway

## 1. MCP Purpose

MCP Gateway is the one, enforced security boundary between an AI/LLM and real PaymentX business APIs. It exposes a fixed, read-only catalog of PaymentX operational lookup tools (`payment.lookup`, `payment.status`, `routing.lookup`, `reconciliation.status`, `audit.search`) through the real Model Context Protocol, so a future AI/Agent (Phase 3.8+) never talks to Postgres, Redis, Kafka, RabbitMQ, the filesystem, a shell, or any PaymentX admin API directly. Every tool call is authorized, input-validated, rate-limited, timeout-bounded, and audited before it ever reaches a real PaymentX service.

## 2. Protocol / Version

Real Model Context Protocol, official Java SDK `io.modelcontextprotocol.sdk:mcp:0.18.3` (Anthropic / MCP steering group, MIT licensed, `https://github.com/modelcontextprotocol/java-sdk`). Coordinates and API surface were **verified live** — `mvn dependency:get` resolved the real jars into the local repository, and every class/method used in this module's code (`McpServer`, `McpServerFeatures`, `McpSchema`, `HttpServletStreamableServerTransportProvider`, `McpTransportContext`, etc.) was inspected with `javap` against the actual `.class` files before being called — nothing was guessed. The server negotiates protocol versions up to `2025-11-25` (`io.modelcontextprotocol.spec.ProtocolVersions.MCP_2025_11_25`), the latest this SDK version supports.

## 3. Transport

**Streamable HTTP**, via `io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider` — a plain `jakarta.servlet.http.HttpServlet`, registered directly into this service's own embedded Tomcat with `ServletRegistrationBean` at `/mcp`. This is the SDK's current, spec-preferred network transport (superseding legacy SSE-only), and requires **no Spring AI, no WebFlux/Netty** — it runs on the exact same servlet stack every other PaymentX service uses. A dev-only stdio server was never considered as the production transport, per the brief's explicit instruction.

## 4. Architecture

```
AI / Agent (future, Phase 3.8+)
        │  Streamable HTTP, /mcp
        ▼
MCP Gateway (paymentx-mcp-gateway, port 8097)
   ├── config/McpServerConfig     — real MCP server + servlet wiring
   ├── registry/ToolRegistry      — fixed, explicit tool catalog
   ├── registry/ToolInvoker       — the one dispatch point (auth → rate-limit → execute → audit)
   ├── security/ToolAuthorizationService — permission + resource-boundary checks
   ├── ratelimit/ToolCallRateLimiter     — per (caller, tool) Resilience4j RateLimiter
   ├── audit/McpAuditClient       — writes to the real Audit Service
   ├── metrics/McpMetrics         — Step 30's metric list
   ├── tool/*                     — 5 real PaymentXTool implementations
   └── client/*                   — 4 real downstream REST clients
        │
        ▼
Real PaymentX APIs: payment-service (via API Gateway), routing-service,
reconciliation-service, audit-service (all direct — see §25)
```

MCP Gateway is **not** an LLM, not an Agent, not a RAG service, not a database, and not a replacement for Payment Service or API Gateway — it is purely the controlled tool-calling boundary in front of them.

## 5. Gateway Boundary

The gateway's tool catalog is a **fixed, hardcoded, compile-time-known list** — five `@Component` classes implementing `PaymentXTool`, collected by `ToolRegistry` via ordinary Spring dependency injection. There is no reflection, no classpath scanning by name, no plugin loader, and no way for an AI-supplied string to cause a new class to be loaded or a new backend to be called. This directly mirrors Control Center's `ApiTesterAllowlist` pattern that Phase 3.0's architecture review identified as the right precedent (`PAYMENTX_PHASE_3_ARCHITECTURE.md` §10).

Every capability is an explicit business-level tool (`payment.lookup`) — never a generic capability (`database.query`, `sql.execute`, `redis.get`, `kafka.publish`, `shell.exec`, `http.proxy`). No such generic tool exists anywhere in this module.

## 6. Tool Registry

`registry/ToolRegistry` collects every `PaymentXTool` Spring bean at startup, indexes by `definition().name()`, and fails fast (`IllegalStateException`) on a duplicate name. It exposes `findByName(String)`, `all()`, and (through each tool's own `enabled()` flag) enabled/disabled state. `controller/McpToolCatalogController` (`GET /api/v1/mcp/tools`) exposes a redacted, read-only view of the catalog for human/operational visibility — it never invokes a tool and is not a management surface.

## 7. Tool Definition

Every tool carries, via the `registry/McpToolDefinition` record: `name`, `description`, `inputSchema` (a real `McpSchema.JsonSchema`, the same object the SDK sends on `tools/list`), `requiredPermission`, `riskLevel` (`LOW`/`MEDIUM`/`HIGH`/`CRITICAL`), `readWrite` (`READ_ONLY`/`WRITE`), `timeout`, `enabled`, and `auditClassification`.

## 8. Tool Discovery

A real MCP client calls the protocol's own `tools/list` method; `config/McpServerConfig` builds one real `McpSchema.Tool` per `McpToolDefinition` at startup and registers it with the SDK's `McpServer` builder. This was proven with a real, non-mocked MCP client in `controller/McpProtocolIntegrationTest.toolDiscovery_realMcpListToolsCall_returnsFixedReadOnlyCatalog`.

## 9. Tool Invocation

A real MCP client calls `tools/call`; the SDK dispatches to the per-tool `callHandler` registered in `McpServerConfig`, which builds a `ToolInvocationContext` from the transport context and delegates to `registry/ToolInvoker.invoke(...)` — the single, centralized dispatch point for every one of Steps 12/15/20/23/26/29/32/33's real enforcement, in order: registry lookup → enabled check → permission authorization → rate limiting → bounded-timeout execution (the tool does its own input validation and resource-level authorization) → audit write (always) → metrics.

**Real finding:** the SDK itself rejects an unregistered tool name at the JSON-RPC protocol layer (`code -32602`) *before* a callHandler is ever invoked — `ToolInvoker`'s own `TOOL_NOT_FOUND` branch is therefore unreachable through a real MCP client and exists as defense-in-depth, proven directly by a pure unit test (`registry/ToolInvokerTest`) rather than through the protocol.

## 10. Authentication

Unchanged trust boundary: JWT is verified once at API Gateway; MCP Gateway is not internet-facing. Caller identity (`X-Roles`, `X-Participant-Id`, `X-Correlation-Id`, `X-Trace-Id`) is read directly off the raw `HttpServletRequest` by `McpServerConfig`'s `contextExtractor`, running synchronously inside the servlet's own request handling — **deliberately not** via Spring Security's `SecurityContextHolder`, because that is a `ThreadLocal` not reliably propagated across the SDK's internal reactor scheduling (a tool's `callHandler` can run on a different thread than the original HTTP request). This is the same header-trust convention `HeaderRoleAuthenticationFilter` uses elsewhere in this platform, applied via the mechanism the SDK itself provides for exactly this problem.

## 11. Authorization

`security/ToolAuthorizationService.checkPermission` is the real enforcement of "AI is NOT trusted": a caller with **no roles at all** gets `TOOL_UNAUTHORIZED`; a caller with roles but not the tool's specific `requiredPermission` gets `TOOL_FORBIDDEN`; a `WRITE`-classified tool is **always** denied (`WRITE_OPERATION_NOT_ALLOWED`) regardless of role — though no write tool is registered as a bean in this phase at all, the strictest possible form of "disabled by default" (Step 22).

## 12. Resource Authorization

`checkResourceOwnership` enforces Step 14's boundary: a caller scoped to one participant (non-null `X-Participant-Id`) may only see resources it actually owns. Enforced per tool, after real data is fetched (since ownership is business data the security layer doesn't own in advance):
- `payment.lookup` — caller must be the payment's debtor or creditor.
- `routing.lookup` — caller may only resolve routing for its own participant (scheme-default lookups, which are not participant-specific, are always allowed).
- `audit.search` — the caller's own participant id **replaces** (never merely validates) any `participantId` filter argument, so a scoped caller cannot see another participant's events even by omitting the filter.
- `payment.status` — **known limitation**: the underlying lightweight status endpoint carries no participant identity at all, so this check cannot be applied without turning every status poll into a full lookup; documented, not silently skipped (see §26).
- `reconciliation.status` — reconciliation batches are not owned by a single participant in this data model; no resource check applies, access is by `RECONCILIATION_READ` role alone.

An unscoped (platform/operator-level) caller — no `X-Participant-Id` — is not resource-scoped and passes unconditionally, an honest limitation of the platform's real, already-existing identity model (`PAYMENTX_PHASE_3_ARCHITECTURE.md` §1: "no real platform-wide identity system yet"), not something this phase invents a stronger guarantee for.

## 13. Tool Risk Model

Every tool declares a `ToolRiskLevel` (`LOW`/`MEDIUM`/`HIGH`/`CRITICAL`) and `ToolReadWrite` (`READ_ONLY`/`WRITE`). All five real tools in this phase are `LOW`/`READ_ONLY`. The enum values above `LOW` and `WRITE` exist purely so a future write tool (Phase 3.8+) has somewhere real to declare itself (`payment.retry` would be `HIGH`, `payment.refund`/`payment.cancel` would be `CRITICAL`) — no such tool is registered today.

## 14. Read-Only Tools

`payment.lookup`, `payment.status`, `routing.lookup`, `reconciliation.status`, `audit.search` — all five, all `READ_ONLY`, all backed by a real, already-existing PaymentX REST GET endpoint. None mutate payment, routing, reconciliation, or audit state.

## 15. Disabled Write Tools

No write tool (`payment.retry`, `payment.cancel`, `payment.refund`, `participant.update`, `routing.update`) is implemented in this phase. `registry/ToolReadWrite.WRITE` and `security/ToolPermissions.PAYMENT_RETRY`/`PAYMENT_CANCEL`/`PAYMENT_REFUND` are declared (per the brief's own instruction to define future write permissions) but are **never referenced by any registered tool, never checked, never granted** — the strictest possible "disabled by default": the capability does not exist as code, not merely as a toggle. `security/ToolAuthorizationService.checkPermission` additionally still refuses any `WRITE`-classified tool outright, so even if one were added carelessly, permission alone could never enable it.

## 16. Input Validation

Every tool validates its own arguments before calling any real API (Step 15/16): `payment.lookup`/`payment.status` require `paymentReference` matching `^[A-Za-z0-9_-]{1,64}$`; `routing.lookup` requires `scheme` to be one of the real `RoutingScheme` enum values (`INSTANT_PAYMENT`, `REAL_TIME_PAYMENT`, `CARD_PAYMENT`) and validates `participantId`'s shape when present; `reconciliation.status` requires `batchId` to parse as a real `UUID`; `audit.search` validates `status`/`eventType` against the real `EventStatus`/`EventType` enums, `fromDate`/`toDate` as ISO-8601, and `page`/`size` as non-negative/bounded integers. LLM-generated arguments are never trusted — every check happens server-side regardless of what the SDK's own generic JSON-Schema validation already rejects.

## 17. Output Filtering

Tool outputs are hand-shaped, never a passthrough of the underlying service's full response (Step 17/18): `payment.lookup` excludes the internal `id` UUID, `traceId`, `correlationId`, and masks `debtorAccount`/`creditorAccount` with `paymentx-common-library`'s real `DataMaskingUtils.mask(value, MaskStrategy.PARTIAL)` (e.g. `"1234567890"` → `"******7890"`) — the platform's own existing masking convention, never an invented rule (Step 19). `audit.search` never returns the raw `payload` field of an audit event (which can legitimately contain an entire recorded API request/response body) — only `id`/`eventType`/`eventStatus`/`sourceService`/`correlationId`/`paymentId`/`participantId`/`reference`/`occurredAt`.

## 18. Rate Limiting

`ratelimit/ToolCallRateLimiter`, backed by a real `io.github.resilience4j.ratelimiter.RateLimiterRegistry` (`config/RateLimiterConfig`, default 30 calls/60s window, configurable via `mcp.rate-limit-*`). One independent `RateLimiter` instance per `(callerId, toolName)` key, created lazily — exhausting one caller/tool's budget never affects another caller or another tool. A denied call returns `TOOL_RATE_LIMITED` immediately (zero-timeout `acquirePermission()`), never blocking. Verified for real, non-mocked, in `ratelimit/ToolCallRateLimiterTest`.

## 19. Timeout

Every tool declares a bounded `Duration` in its `McpToolDefinition` (5s for the four simple lookups, 8s for `audit.search`'s search). `ToolInvoker` runs `tool.execute(...)` on a bounded `CompletableFuture` and enforces this timeout with `Future.get(timeout, ...)`, converting a real timeout into `TOOL_TIMEOUT` — proven with a real slow-tool unit test in `registry/ToolInvokerTest`. Downstream HTTP clients (`client/*`) additionally carry their own connect/read timeouts (`McpGatewayProperties`), so no single outbound call can hang indefinitely either.

## 20. Retry

Each of the four downstream dependencies (`paymentService`/`routingService`/`reconciliationService`/`auditService`) has its own Resilience4j retry instance. `config/ResilienceConfig` supplies the `RetryConfigCustomizer` predicate each instance needs, because every real failure is thrown as the same `McpException` class carrying a per-failure `retryable` flag (a 503/timeout is retryable; a 4xx/auth/validation failure is not) — YAML's class-based `retry-exceptions` cannot express that distinction for a single exception type, the exact pattern established in RAG/LLM/Embedding Service. **This module proactively ships `spring-boot-starter-aop` from day one** (see `pom.xml`'s comment) — Phase 3.6 discovered that `@CircuitBreaker`/`@Retry` are silent no-ops without it, a real gap that had existed undetected in three other AI Platform services. That fix's effectiveness is verified *for real* here, against a real Spring-proxied bean, in `controller/McpProtocolIntegrationTest.callTool_transientPaymentServiceFailure_retriesAndEventuallySucceeds` (a WireMock scenario: first call 503, only a real retry can reach the second, successful stub).

## 21. Idempotency

Not applicable to this phase's tool set — all five tools are pure reads with no side effects. The requirement is carried forward as a hard prerequisite for any future write tool (Phase 3.8+): a write tool must have a real idempotency strategy before it can be safely retried by an LLM, a network layer, or a client, and none of that machinery is built or assumed here.

## 22. Audit

`audit/McpAuditClient` writes one real `POST /api/v1/audit-events` to the already-existing Audit Service (Phase 1) for **every** tool invocation, success or failure, in `ToolInvoker`'s `finally` block. Uses the real, already-existing `EventType.API_REQUEST` value (there is no dedicated "AI tool call" event type in `audit-service`'s schema, and inventing one would violate Step 10's "no fake APIs" — an MCP tool call genuinely *is* an API request MCP Gateway makes on the caller's behalf). `payload` is a small, redacted JSON summary (`toolName`, `riskLevel`, `authorizationResult`, `executionStatus`, `durationMs`, `targetService`, `errorCategory`) — never a secret, never the full raw tool arguments. MCP Gateway sets `X-Roles: AUDIT_WRITER` on this **one** specific outbound call as its own service-level credential for its own operational audit trail — a distinct concern from, and never a substitute for, the read-tool authorization in §11/§12 (see that client class's own javadoc for the full boundary reasoning). A downed/slow Audit Service is deliberately never allowed to fail or delay the real tool response — the write is best-effort and its own failure is only logged.

## 23. Observability

Step 30's exact metric list, hand-registered via `metrics/McpMetrics` (Micrometer/Prometheus, `/actuator/prometheus`, matching every other AI Platform service's explicit-`MeterRegistry`-call convention rather than `@Timed`/`@Counted`):

`mcp_requests_total`, `mcp_tool_calls_total`, `mcp_tool_success_total`, `mcp_tool_failure_total`, `mcp_tool_denied_total`, `mcp_tool_latency`, `mcp_tool_timeout_total`, `mcp_tool_rate_limited_total`, `mcp_authorization_failures`, `mcp_validation_failures` — every varying counter tagged by `tool`/`errorCode`, never carrying tool arguments or business data. `infra/prometheus.yml` gained a `paymentx-mcp-gateway` scrape job (`host.docker.internal:8097`), following every prior AI Platform phase's identical pattern.

## 24. Distributed Tracing

`config/CorrelationIdFilter` (identical pattern to every other AI Platform service) reads/generates `X-Correlation-Id`, puts it in MDC, and echoes it on the response — it runs as a normal Spring Boot global filter, so it covers the manually-registered MCP servlet too, not just `@RestController` requests. The same correlation id is extracted into `ToolInvocationContext` by `McpServerConfig`'s transport-context extractor and forwarded on every downstream client call (`client/*`), verified for real over the wire in `controller/McpProtocolIntegrationTest` (`withHeader("X-Correlation-Id", ...)`). Target trace realized: AI/Agent → MCP Gateway → Payment/Routing/Audit/Reconciliation Service → Database, on the existing Zipkin/`micrometer-tracing-bridge-brave` setup — no new tracing system introduced.

## 25. Security Boundary

No generic SQL/Redis/Kafka/RabbitMQ/shell/filesystem/HTTP-proxy/browser tool exists anywhere in this module — every capability is an explicit business-level tool backed by a real REST endpoint. `payment.lookup`/`payment.status` call **through the real API Gateway** (`http://localhost:8080`) since payment-service is one of only two services the Gateway currently routes to (confirmed live against `paymentx-api-gateway`'s own `GatewayConfig.java`); `routing.lookup`, `reconciliation.status`, and `audit.search` call their respective services **directly on their real ports** (8084/8087/8085) since no Gateway route exists for them yet — this is the exact same real, already-verified routing split Control Center's own `ApiTesterAllowlist` established for these five endpoints, reused rather than re-derived. MCP Gateway never reads a database, never mints or influences the `X-Roles`/`X-Participant-Id` identity it forwards to read tools (see §10/§11), and cannot be given a shortcut to assert elevated identity on the caller's behalf.

## 26. Known Limitations

- `participant.lookup` and `payment.error.lookup` (from the brief's suggested initial tool set) are **NOT AVAILABLE** — no real business API backs either today. `participant.lookup` has no owning service (only Control Center's incidental, non-authorized DB-read endpoint exists, deliberately not used per `PAYMENTX_PHASE_3_ARCHITECTURE.md` §10's own explicit decision); `payment.error.lookup` would duplicate `payment.lookup`'s real `failureReason` field without a distinct backing API. Both are documented gaps, not faked.
- `payment.status`'s resource-boundary check cannot be enforced (see §12) — the underlying lightweight endpoint carries no participant identity.
- No tool chaining/multi-step planning exists — each MCP `tools/call` executes exactly one tool; an AI/Agent composing multiple calls (e.g. `payment.lookup` then `audit.search`) does so itself, across separate requests (Step 28's boundary — MCP executes explicitly requested calls, autonomous planning is Phase 3.8's Agent Orchestrator).
- No human-approval workflow is implemented beyond the write-tool-always-denied policy boundary (Step 21) — there is nothing to approve since no write tool exists.
- MCP Gateway is reached directly on its own port (8097), not routed through API Gateway, matching the exact same precedent every other AI Platform service (Prompt/LLM/Embedding/Vector/RAG) already set — see §27 for the reasoning.

## 27. Tool List

| Tool | Backing API | Status | Risk | Permission |
|---|---|---|---|---|
| `payment.lookup` | `GET /api/v1/payments/{reference}` (via API Gateway) | **AVAILABLE** | LOW / READ_ONLY | `PAYMENT_READ` |
| `payment.status` | `GET /api/v1/payments/{reference}/status` (via API Gateway) | **AVAILABLE** | LOW / READ_ONLY | `PAYMENT_READ` |
| `routing.lookup` | `GET /api/v1/routes/participant/{id}` or `/default` (routing-service direct) | **AVAILABLE** | LOW / READ_ONLY | `ROUTING_READ` |
| `reconciliation.status` | `GET /api/v1/reconciliation/batches/{id}` (+`/summary`) (reconciliation-service direct) | **AVAILABLE** | LOW / READ_ONLY | `RECONCILIATION_READ` |
| `audit.search` | `GET /api/v1/audit-events` (audit-service direct) | **AVAILABLE** | LOW / READ_ONLY | `AUDIT_READ` |
| `participant.lookup` | — | **NOT AVAILABLE** | — | — |
| `payment.error.lookup` | — | **NOT AVAILABLE** | — | — |

## 28. API Gateway Integration

MCP Gateway itself is **not** exposed through the API Gateway in this phase — it is reached directly on its own port (8097), exactly matching the established precedent of every other AI Platform service (Prompt Service 8092, LLM Service 8093, Embedding Service 8094, Vector Service 8095, RAG Service 8096), none of which are Gateway-routed either, since none are internet-facing (they are internal service-to-service callers, trusting the same `X-Roles`/`X-Participant-Id` header model). Adding a new API Gateway route for MCP Gateway's own inbound traffic was evaluated and deliberately deferred to stay consistent with this precedent rather than introduce a one-off exception; it remains a real, scoped option for whichever phase first builds a genuine external/browser-facing AI client. MCP Gateway's own **outbound** calls, by contrast, do partially use the API Gateway already — `payment.lookup`/`payment.status` route through it (§25) — because payment-service itself is Gateway-routed today.

## 29. Testing

46 real tests, `mvn -pl paymentx-mcp-gateway test`, all passing:
- `registry/ToolInvokerTest` (9) — unknown tool, disabled tool, no-role/wrong-role, write-tool-always-denied, rate-limited, timeout, invalid-arguments propagation, successful structured result — all against a real `ToolAuthorizationService` + real `RateLimiterRegistry` + real `MeterRegistry`.
- `security/ToolAuthorizationServiceTest` (7) — pure unit, permission and resource-ownership matrix.
- `ratelimit/ToolCallRateLimiterTest` (4) — real Resilience4j registry, independent per-(caller,tool) budgets.
- `tool/PaymentLookupToolTest` (6), `tool/AuditSearchToolTest` (5) — masking, data minimization, forced participant scoping, bounded pagination, invalid input.
- `client/PaymentServiceClientTest` (4) — real WireMock wire-format tests (success parse, real 404→empty, real 5xx→retryable, unreachable→retryable).
- `controller/McpProtocolIntegrationTest` (9) — the real end-to-end suite, see §30.
- `controller/McpToolCatalogControllerTest` (2) — the REST catalog endpoint + actuator health (doubling as this module's startup/readiness check).

## 30. Real Integration Test

`controller/McpProtocolIntegrationTest` runs a full `@SpringBootTest` (real application context, real embedded Tomcat, real `McpSyncServer`/servlet wiring) and drives it with the **official SDK's own client** (`McpClient.sync(HttpClientStreamableHttpTransport...)`) over the real Streamable HTTP wire protocol — not a hand-rolled HTTP call. Only the one real external dependency this test set touches, payment-service, is stood in with WireMock (the same, already-justified precedent RAG Service's own Phase 3.6 E2E test set). Proven for real, end to end: MCP request → Tool Registry → Authorization → real `PaymentServiceClient` → real HTTP call → real business response → real, normalized `CallToolResult`; unauthorized/resource-forbidden denial; a genuine business "not found" returned as a successful result, never an error; a downstream outage mapped to `TARGET_SERVICE_UNAVAILABLE`; a transient 503 genuinely retried through a real Spring-AOP-proxied bean and eventually succeeding; account-number masking; and correlation-id propagation onto the real downstream HTTP call. Only safe, read-only GET operations were exercised — no real or simulated payment mutation ever occurred (Step 42).

---

## IMPLEMENTED IN PHASE 3.7

- Real MCP server (Streamable HTTP, official SDK) — `paymentx-mcp-gateway`, port 8097.
- Fixed, explicit, five-tool read-only catalog backed by real PaymentX REST APIs.
- Tool registry, authorization (permission + resource-boundary), input validation, output data-minimization/masking, rate limiting, bounded timeout, retry (with correct retryable/non-retryable classification).
- Real audit trail via the existing Audit Service; observability metrics; correlation-id propagation.
- One plain read-only REST catalog endpoint (`GET /api/v1/mcp/tools`) for operational visibility.

## FUTURE PHASES (NOT IMPLEMENTED)

- **Agent Orchestrator** — NOT IMPLEMENTED.
- **Multi-Agent** — NOT IMPLEMENTED.
- **Autonomous tool planning / tool chaining across multiple calls** — NOT IMPLEMENTED.
- **Autonomous payment actions / any write tool** (`payment.retry`, `payment.cancel`, `payment.refund`, `participant.update`, `routing.update`) — NOT IMPLEMENTED.
- **AI Security platform** — NOT IMPLEMENTED.
- **Human approval workflow** beyond the write-tool-always-denied boundary — NOT IMPLEMENTED.
- **`participant.lookup` / `payment.error.lookup`** — NOT AVAILABLE (no backing business API exists; a prerequisite backlog item, not solved by a workaround).
