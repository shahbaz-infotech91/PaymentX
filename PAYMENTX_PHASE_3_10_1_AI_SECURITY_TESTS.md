# PaymentX — Phase 3.10.1: Automated AI Security Test Suite

## 1. Objective

Convert the AI security behavior already proven through real, live-session validation (Phase 3.9's
`AgentE2EIntegrationTest`, manual live-Anthropic validation referenced in that test's javadoc, and the ten
items of "Important Existing Evidence" in this task's brief) into deterministic, repeatable automated
tests that act as a production safety net. This task validates the **existing** AI security architecture.
It does not redesign it, and it does not weaken any existing security behavior to make a test pass.

## 2. Existing Security Architecture (as inspected, before writing any test)

### 2.1 Existing security-test convention (Step 1)

Inspected `AuthServiceSecurityTest`, `RoutingSecurityTest`, `ReconciliationSecurityTest`:

- **Framework**: JUnit 5 + AssertJ (`assertThat`, `assertThatThrownBy`), Mockito (`@ExtendWith(MockitoExtension.class)`) for unit-level tests, Testcontainers + `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate` for full-stack tests.
- **Package convention**: `com.paymentx.<module>.security`, class name `<Thing>SecurityTest`.
- **Assertion style**: structured/status-code/error-code assertions, never exact free-text business copy (matches this task's Step 14 requirement for AI tests specifically).
- **Mocking convention**: unit tests mock only the direct collaborator boundary (Mockito `@Mock`), never the class under test; integration tests use real Spring context + Testcontainers Postgres where the module owns a database (the AI Platform modules mostly do not - see below).
- This suite follows the same conventions: JUnit 5 + AssertJ + Mockito, `com.paymentx.<module>.security` packages, `*SecurityTest` class names, structured assertions only.

### 2.2 AI security surface inventory (Step 2)

| AI Component | Security Behavior | Existing Implementation | Existing Test Coverage | New Test Required |
|---|---|---|---|---|
| Agent Orchestrator | Tool allow-list (default-deny) | `policy/AgentToolPolicy` (hardcoded 5-tool allow-list) | `AgentToolPolicyTest`, `AgentPlanValidatorTest` (unit) | Yes - end-to-end orchestrator-level scenarios using the **real** validator+policy (existing `AgentOrchestratorServiceTest` mocks the validator) |
| Agent Orchestrator | Registry existence check | `planning/AgentPlanValidator.validateCallTool` | `AgentPlanValidatorTest` | No (already thorough) |
| Agent Orchestrator | Indirect injection via tool output | Allow-list still applies regardless of LLM's proposed action | `AgentE2EIntegrationTest.execute_maliciousToolOutputProposesRefund_deniedAndNeverExecuted` | No (already proven end-to-end) |
| Agent Orchestrator | Secret leakage on failure paths | `applyPlanningFailure`/`executeTool`/`executeRetrieval` never store raw exception messages in the response DTO | None | **Yes** |
| Agent Orchestrator | Tool-result trust / hallucination containment | `ToolCallRecord`/`ToolEvidence` carry the tool's real result untouched | None | **Yes** |
| Agent Orchestrator | MCP/RAG/LLM failure safety | `runLoop` degrades gracefully (empty tool list) or terminates honestly (FAILED/INSUFFICIENT_CONTEXT) | Partially (`AgentOrchestratorServiceTest`) | Yes - secret-safety variant |
| MCP Gateway | Read/write separation | Every real tool's `McpToolDefinition.readWrite() == READ_ONLY`; `ToolAuthorizationService` always denies `WRITE` | `McpToolCatalogControllerTest` (string-based) | Yes - structural, registry-based |
| MCP Gateway | Two-layer tool authorization (permission + resource ownership) | `ToolAuthorizationService` | `ToolAuthorizationServiceTest`, per-tool tests | Yes - full 5-tool matrix in one place |
| MCP Gateway | Malicious/malformed parameters | Per-tool regex/enum/range validation (`PaymentLookupTool`, `AuditSearchTool`) | Partial (`PaymentLookupToolTest`, `AuditSearchToolTest`) | Yes - oversized/injection-like/path-like/wrong-type cases |
| MCP Gateway | Secret leakage on unanticipated tool failure | `ToolInvoker.executeWithTimeout`/`invoke` | None | **Yes (found a real gap - see §9)** |
| RAG Service | Indirect prompt injection / RAG poisoning | `context/ContextBuilder` treats chunk content as literal text; `PAYMENTX_KNOWLEDGE_ASSISTANT` prompt (prompt-service `V1_0_2` migration) explicitly instructs the LLM to never follow instructions inside CONTEXT | None at the structural/RagServiceImpl level | **Yes** |
| RAG Service | Hallucination refusal (no relevant knowledge) | `RagServiceImpl.query` returns `INSUFFICIENT_CONTEXT` without calling Prompt/LLM Service | `RagServiceImplTest.query_noResultsAtAll_returnsInsufficientContext` / `..._allResultsBelowThreshold...` | No (already covered) |
| RAG Service | Failure safety (embedding/vector/prompt/LLM) | `RagServiceImpl` propagates typed `RagException` | `RagServiceImplTest` (thorough) | No, plus one secret-safety variant added |
| Control Center (AI Chat) | Front-door message forwarding | `AiChatService.sendMessage` forwards the message verbatim, never inspects content | `AiChatServiceTest` (thorough on status pass-through) | Yes - explicit injection-framed test + secret-leakage-on-unexpected-exception |
| Control Center (AI Chat) | Feature-flag short-circuit | `AiChatService.sendMessage` | `AiChatServiceTest` | No (already covered) |

## 3. Security Behaviors Tested (new tests)

1. Direct prompt injection (user message text) cannot force an unauthorized tool call.
2. Indirect prompt injection (malicious tool output) - already proven by Phase 3.9's `AgentE2EIntegrationTest`; not duplicated here.
3. RAG poisoning - malicious retrieved-document content is proven to be treated as inert data, structurally, at both `ContextBuilder` and `RagServiceImpl` level.
4. MCP tool authorization - full 5-tool × 4-permission denial/allow matrix.
5. Read/write separation - structural (registry-level) proof no write tool is ever exposed, plus a synthetic unauthorized write attempt.
6. Malicious/malformed tool parameters - oversized, SQL-injection-like, path-traversal-like, CRLF-injection-like, empty, wrong-type, out-of-range.
7. Secret leakage prevention - sentinel-value tests across Agent Orchestrator (tool/RAG/planning failure paths), MCP Gateway (unanticipated tool exception), Control Center (unanticipated HTTP exception).
8. Hallucination/unknown-data refusal - a nonexistent payment reference's real `found:false` tool evidence is preserved untouched even when a (simulated, worst-case) planner claims otherwise.
9. Tool-result trust - same test as #8, framed at the evidence-integrity layer.
10. Failure safety - MCP Gateway unavailable during tool discovery degrades gracefully without crashing or fabricating tool calls.
11. Authorization boundary cannot be bypassed by a direct, unambiguous request ("Refund PMT-123").

## 4. Test Classes (new, this phase)

| Class | Module | Package | Tests |
|---|---|---|---|
| `AIAgentSecurityTest` | paymentx-agent-orchestrator | `com.paymentx.agent.security` | 16 |
| `McpSecurityTest` | paymentx-mcp-gateway | `com.paymentx.mcp.security` | 17 |
| `RagSecurityTest` | paymentx-rag-service | `com.paymentx.rag.security` | 3 |
| `AIResponseSecurityTest` | paymentx-control-center/backend | `com.paymentx.controlcenter.security` | 3 |

All are pure JUnit 5 + Mockito (+ AssertJ) unit tests. None require Testcontainers, a live Postgres, or a
real LLM provider call - matching Step 14/17's determinism requirement and letting the whole new suite run
in seconds.

## 5. Test Cases → Matrix Mapping (Step 16)

| # | Matrix item | Test(s) | Status |
|---|---|---|---|
| 1 | Direct prompt injection | `AIAgentSecurityTest.directPromptInjection_*` (3 tests) | Covered |
| 2 | Indirect prompt injection | `AgentE2EIntegrationTest.execute_maliciousToolOutputProposesRefund_deniedAndNeverExecuted` (existing, Phase 3.9) | Covered (pre-existing) |
| 3 | RAG poisoning | `RagSecurityTest.contextBuilder_maliciousChunkContent_*`, `..._poisonedRetrievedDocument_*` | Covered |
| 4 | Unauthorized MCP tool | `McpSecurityTest.authorizationMatrix_*`, `syntheticUnauthorizedWriteOperation_*` | Covered |
| 5 | Authorized MCP read-only tool | `McpSecurityTest.authorizationMatrix_everyRealToolRequiresItsOwnPermissionAndRejectsAnyOther` (positive case included) | Covered |
| 6 | Read/write separation | `McpSecurityTest.allRegisteredMcpTools_areReadOnly_noWriteToolIsEverExposed` | Covered |
| 7 | Malicious tool parameters | `McpSecurityTest.paymentLookup_*`, `auditSearch_*` (11 parameterized cases) | Covered |
| 8 | Secret leakage prevention | `AIAgentSecurityTest.*CarryingSecretInExceptionMessage_*` (3), `McpSecurityTest.unexpectedToolFailureCarryingSecretInMessage_*`, `RagSecurityTest.embeddingServiceFailureCarryingSecretInMessage_*`, `AIResponseSecurityTest.unexpectedExceptionCarryingSecretInMessage_*` | Covered |
| 9 | Unknown payment hallucination prevention | `AIAgentSecurityTest.toolResultTrust_notFoundEvidenceIsPreservedEvenWhenPlannerFinalAnswerClaimsOtherwise` | Covered (see §8 Known Limitations for scope) |
| 10 | Tool NOT_FOUND cannot become fabricated success | Same test as #9 | Covered |
| 11 | LLM failure safety | `AIAgentSecurityTest.planningFailureCarryingSecretInExceptionMessage_*` | Covered |
| 12 | MCP failure safety | `AIAgentSecurityTest.toolExecutionFailureCarryingSecretInExceptionMessage_*`, `mcpGatewayUnavailableDuringToolDiscovery_*` | Covered |
| 13 | RAG failure safety | `AIAgentSecurityTest.ragFailureCarryingSecretInExceptionMessage_*`; `RagServiceImplTest` (existing, thorough) | Covered |
| 14 | Unauthorized operation cannot bypass authorization | `AIAgentSecurityTest.authorizationBoundary_agentCannotBypassPolicyEvenWhenPlannerAsksDirectly` | Covered |

No matrix item is marked NOT APPLICABLE - all 14 have real, applicable behavior in the current architecture.

## 6. Determinism Strategy

No test in this suite makes a real LLM call or asserts on LLM-generated free text. Every scenario that
needs to represent "what if the LLM said X" uses a **mocked** `AgentPlanner`/`LlmServiceClient` returning a
fixed, hand-authored plan or answer - always representing the **worst realistic case** (a model tricked by
an injected instruction), never a well-behaved one, because only the worst case exercises the actual
code-level boundary under test. Assertions are exclusively on: HTTP/error codes, enum status values, tool
invocation counts (`verify(..., never())`), and structural presence/absence of a sentinel string - never on
exact natural-language wording.

## 7. Mock/Stub Strategy

- Downstream HTTP clients (`McpToolClient`, `RagServiceClient`, `PaymentServiceClient`,
  `RoutingServiceClient`, `ReconciliationServiceClient`, `AuditServiceClient`, `AiPlatformClient`,
  `EmbeddingServiceClient`, `VectorServiceClient`, `PromptServiceClient`, `LlmServiceClient`) are mocked
  with Mockito - never a real network call.
- The actual security-enforcement classes under test (`AgentPlanValidator`, `AgentToolPolicy`,
  `ToolAuthorizationService`, `ContextBuilder`, `ToolInvoker`, `GlobalExceptionHandler`) are **real,
  non-mocked instances** in every test - mocking the thing being tested would prove nothing.
- No real secret, credential, or API key is ever used. Every "secret" is the synthetic sentinel
  `TEST_SECRET_SHOULD_NEVER_APPEAR_12345`, embedded only in mocked exception messages/tool outputs that
  never leave the test process.
- No real or simulated payment mutation occurs anywhere in this suite.

## 8. Results

Ran the new AI security tests first, in isolation, then the existing directly-related AI tests, module by
module (sequentially, not in parallel, to avoid Postgres/Testcontainers contention - though none of the new
or existing tests exercised here needed a database):

| Module | Test class(es) | Result |
|---|---|---|
| paymentx-agent-orchestrator | `AIAgentSecurityTest` | 16/16 |
| paymentx-mcp-gateway | `McpSecurityTest` (after the one approved production fix, §9) | 17/17 |
| paymentx-rag-service | `RagSecurityTest` | 3/3 |
| paymentx-control-center/backend | `AIResponseSecurityTest` | 3/3 |
| **New security tests total** | | **39/39** |
| paymentx-agent-orchestrator | `AgentPlanValidatorTest`, `AgentToolPolicyTest`, `AgentOrchestratorServiceTest`, `AgentE2EIntegrationTest`, `AgentPlannerTest`, `RagServiceClientTest` | 45/45 |
| paymentx-mcp-gateway | Full module suite minus the new `McpSecurityTest` (all pre-existing classes: `McpToolCatalogControllerTest`, `McpProtocolIntegrationTest`, `ToolInvokerTest`, `ToolAuthorizationServiceTest`, `PaymentLookupToolTest`, `AuditSearchToolTest`, `PaymentServiceClientTest`, `ToolCallRateLimiterTest`) | 46/46 |
| paymentx-rag-service | `RagServiceImplTest`, `ContextBuilderTest` | 19/19 |
| paymentx-control-center/backend | `AiChatServiceTest`, `GlobalExceptionHandlerAiTest`, `AiControllerTest` | 13/13 |
| **Existing AI tests total** | | **123/123** |
| **Grand total** | | **162/162** |

(The `paymentx-mcp-gateway` full-module run, 63/63 including the new `McpSecurityTest`, is reported separately in §10 as the regression check for the one approved production change.)

## 9. Production Changes

**One** production file was modified, after stopping and getting explicit approval, because a new
deterministic test (`McpSecurityTest.unexpectedToolFailureCarryingSecretInMessage_neverLeaksIntoTheMcpErrorResult`)
proved the current implementation did not provide behavior the code's own javadoc already documents as
required.

- **WHERE**: `paymentx-mcp-gateway/src/main/java/com/paymentx/mcp/registry/ToolInvoker.java`, method
  `executeWithTimeout`, the `catch (ExecutionException wrapped)` branch.
- **WHY**: `ToolInvoker`'s class javadoc states the contract explicitly: "Every McpException caught here is
  converted into a real MCP CallToolResult ... **NEVER a raw Java exception/stack trace**." The
  `catch (Exception unexpected)` branch in the sibling `invoke()` method correctly honors this (it returns
  only `"Unexpected failure executing tool: " + toolName`). The `executeWithTimeout` branch did not: it did
  `throw McpException.executionFailed("Tool execution failed: " + wrapped.getCause())`, which
  string-concatenates the raw, unanticipated exception's `toString()` - including its message - into the
  MCP tool result surfaced back to the AI/caller.
- **WHAT BEHAVIOR WAS MISSING**: the same "log the real cause server-side, return only a generic
  tool-name-scoped message to the caller" behavior the sibling branch already has.
- **Reachability**: none of the 5 real production tools trigger this path today - each already converts its
  own failure modes into a curated `McpException` before this branch would ever see them. It is a latent
  gap (reachable by any future/accidental unhandled exception, e.g. a `NullPointerException` bug, inside
  any tool's `execute()`), not an actively exploited one.
- **The fix** (5 lines, no behavior change for any currently-passing case): replaced the raw
  string-concatenation with a `log.error(...)` of the real cause plus a generic
  `"Unexpected failure executing tool: " + tool.definition().name()` message, matching the existing
  sibling pattern exactly.
- Verified: the new test now passes, and the full `paymentx-mcp-gateway` module suite (63/63, including
  every pre-existing test) still passes with no regression.

No other production source was modified. No configuration was modified. No database schema or data was
modified.

- Production source modified: **YES** (1 file, see above, user-approved)
- Configuration modified: **NO**
- Database modified: **NO**
- Test-only files added: 4 (listed in §4)

## 10. Regression Results

- **Phase 1** (Payment/Validation/Routing/API Gateway/Auth/Audit/Notification/Reconciliation/Reporting
  Service): not touched by this phase; not executed (out of scope per this task's explicit instructions -
  "Do not modify Phase 1" / avoid running the full platform suite in parallel). No file under any of those
  modules was read or written this session.
- **Phase 3.9 AI tests**: re-ran directly-related existing suites in all four touched modules (123 tests,
  listed in §8), plus the full `paymentx-mcp-gateway` module suite (63/63, since that module's production
  code changed) - all green, no behavior changed for any existing test.

## 11. Known Limitations

1. **LLM semantic behavior is out of scope for this deterministic suite.** Whether a *real* LLM call
   refuses to reveal a secret, or refuses to follow an injected instruction, is exactly what Phase 3.9's
   manual live-Anthropic validation already covered (see this task's own "Important Existing Evidence"
   list) - it cannot be asserted deterministically without either calling a real provider (nondeterministic,
   explicitly disallowed by Step 14) or asserting on exact wording (also disallowed). Every test in this
   suite that represents "the LLM said X" uses a mocked, worst-case plan/answer instead, and tests the
   **code-level** boundary that holds regardless of what the LLM says.
2. **No code-level "answer contradicts evidence" detector exists.** `AgentOrchestratorService` does not
   (and today cannot, without a real LLM call or embedding-similarity check) verify that the natural
   language `answer` field is consistent with the structured `toolEvidence`/`sources` it also returns. The
   deterministic guarantee this platform provides today is narrower but real: the evidence fields
   themselves always reflect the actual tool/RAG result, untouched, so a consuming UI or a human reviewer
   can always cross-check. `AIAgentSecurityTest.toolResultTrust_*` proves exactly this narrower guarantee,
   not full hallucination detection.
3. **`AiServiceNotReadyException`/`RagException`/`AgentException`/`ControlCenterException` handlers pass
   `ex.getMessage()` through by design** (for legitimate, curated cross-service error reasons the AI/caller
   needs to reason about - e.g. "MCP_GATEWAY_UNAVAILABLE"). This is intentional, existing, documented
   behavior, not a gap - the safety net this suite proves is that *unanticipated* exceptions (the ones that
   could carry arbitrary internal detail) are the ones scrubbed to a generic message, at every layer
   touched (Agent Orchestrator, MCP Gateway, Control Center). This suite does not re-verify every
   individual downstream service's own error-message hygiene (e.g. Payment Service's own error text) - that
   is each service's own responsibility and out of this phase's scope.
4. **`McpToolCatalogControllerTest`'s existing string-based read-only assertion** (`doesNotContain("\"readWrite\":\"WRITE\"")`)
   was left as-is (not modified, per this phase's constraints); `McpSecurityTest` adds the stronger,
   structural registry-based version alongside it rather than replacing it.

## 12. Phase 3.10.1 Status

**COMPLETE**
