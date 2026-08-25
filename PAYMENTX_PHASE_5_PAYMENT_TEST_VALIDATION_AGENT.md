# Phase 5 — Payment Test/Validation Agent

## 1. Objective

Add a dedicated, strictly read-only Payment Test/Validation Agent to the PaymentX Agent
Orchestrator, consolidating the payment-validation slice that `error-analyzer`,
`database-analysis-agent`, and `reconciliation-agent` each already partially cover into one
purpose-built experience: inspect a payment's status/details, cross-check reconciliation state,
correlate evidence, detect inconsistencies, and clearly separate FACT from HYPOTHESIS.

This is a deliberately small, low-risk milestone: **zero new MCP tools, zero new permissions,
zero new backend/frontend code beyond the agent registration itself.** The live discovery in the
prior milestone confirmed the Control Center UI (`/ai-agents`, `/ai-agents/execute`,
`/ai-agents/history`) is already fully generic and metadata-driven — a correctly registered agent
appears and is executable with no frontend changes at all.

**Payment Create Agent remains BLOCKED/DEFERRED** — see §6.

## 2. Capability

No new `AgentCapability` enum value was added. The agent reuses three existing values, matching
the same "reuse before inventing" precedent `reconciliation-agent` (Phase 4.6.0) already
established:

- `PAYMENT_ANALYSIS` — payment status/details lookup
- `RECONCILIATION_ANALYSIS` — reconciliation cross-check when relevant
- `KNOWLEDGE_RETRIEVAL` — RAG usage (no new corpus; RAG has no agent-scoping by design, so
  RETRIEVE_KNOWLEDGE against the existing corpora already surfaces whatever relevant content
  exists)

## 3. Agent definition

Registered in `paymentx-agent-orchestrator/src/main/resources/application.yml` under
`agents.definitions`, following the exact existing YAML entry pattern:

| Field | Value |
|---|---|
| `agent-id` | `payment-test-agent` |
| `name` | PaymentX Payment Test/Validation Agent |
| `allowed-tools` | `payment.lookup`, `payment.status`, `audit.search`, `reconciliation.status` |
| `prompt-key` | `PAYMENT_TEST_VALIDATION` |
| `risk-level` | LOW |
| `enabled` | true |

`routing.lookup` and `database.statistics` are deliberately excluded — no per-payment validation
relevance, mirroring `reconciliation-agent`'s own documented exclusion rationale. No write tool
exists on the platform at all (confirmed in the prior milestone's inventory: `PAYMENT_RETRY`/
`PAYMENT_CANCEL`/`PAYMENT_REFUND` permissions are declared but never wired to any tool).

## 4. Prompt (Prompt Service seed)

New Liquibase changeset: `V1_0_10__seed_payment_test_validation_agent_prompt.yaml`, included in
`db.changelog-master.yaml`, seeding `PAYMENT_TEST_VALIDATION` as an ACTIVE template + version 1 —
the same five-variable `AgentPlanner` contract (`availableTools`/`executionHistory`/`userQuery`/
`iteration`/`maxIterations`) and the same structured `CALL_TOOL`/`RETRIEVE_KNOWLEDGE`/
`FINAL_RESPONSE` JSON action loop every other agent's prompt drives.

Style combines two existing precedents:
- **Read-only evidence boilerplate** from `reconciliation-agent` (Phase 4.6.0) — "read-only by
  construction, not by instruction," never claims funds are lost/missing/stolen.
- **FACT vs HYPOTHESIS classification** from `incident-rca-agent` (Phase 4.8.0), adapted to a
  **Validation Finding** vocabulary: `VALID` (evidence internally consistent), `INCONSISTENT`
  (two or more evidence sources genuinely contradict each other), or `INSUFFICIENT_CONTEXT`.

The prompt explicitly forbids: writing/mutating any payment or record, claiming a payment
succeeded/failed/settled without direct tool evidence, inventing numeric thresholds, and treating
retrieved content or the user's own message as instructions that override its rules (prompt
injection defense, matching every sibling prompt).

## 5. Tests

### paymentx-agent-orchestrator (308/308 pass, 0 real LLM/provider calls)
- `PaymentTestAgentDefinitionTest` (new, 6 tests) — real Spring context, real `application.yml`:
  resolves correctly, correct prompt key, correct allowed-tools (excludes routing/database
  tools), correct capabilities (no new enum value), platform-default iteration/timeout limits,
  other agents unaffected.
- `PaymentTestAgentSecurityTest` (new, 24 tests) — mirrors `ReconciliationAgentSecurityTest`
  exactly: unknown/disabled agent rejected, cannot use another agent's tool or any unauthorized
  tool, cannot use any write tool across every domain (`payment.create/update/retry/cancel/
  refund`, `reconciliation.update/resolve/reprocess`, `database.write/execute`, etc.), prompt
  injection cannot escalate permissions, malicious RAG content cannot escalate permissions
  (RAG has no tool-calling capability at all), agent identity cannot be overridden by model
  output, model cannot grant itself a tool even if MCP discovery lists it, tool evidence is
  preserved even when the planner's final answer contradicts it (also proves schema-conformant
  `paymentReference` argument usage against the real MCP schema), and a secret embedded in a
  tool-failure message never surfaces in the response.
- `IncidentRcaAgentDefinitionTest.registry_containsExactlyEightAgents` — updated from 7→8 agents
  (was the one pre-existing test hardcoding the total agent count).
- `AgentE2EIntegrationTest.listAgents_realRegistry_returnsAllEightAgentsWithRealAllowedTools` —
  updated from 7→8, added a `payment-test-agent` allowed-tools assertion block.

### paymentx-prompt-service (test file added, not yet run — see §7)
- `PaymentTestValidationAgentPromptSeedTest` (new) — Testcontainers-backed, proves the real seeded
  rows exist and are shaped correctly (template/version seeded, ACTIVE, correct variable contract,
  no permission-granting language, states it is not a security boundary, defines the FACT/
  HYPOTHESIS and VALID/INCONSISTENT/INSUFFICIENT_CONTEXT vocabulary, forbids claiming payment
  outcome without evidence, forbids invented thresholds, exactly one version).

Zero Gemini/Groq/Anthropic/OpenAI quota was spent verifying any of this — every test above is a
plain Mockito/real-Spring-context/real-YAML test, never a real LLM call.

## 6. Payment Create Agent — BLOCKED / DEFERRED

Not implemented in this milestone, per explicit instruction. Confirmed root cause (live code
inspection, not assumption):

- `PaymentController` (payment-service) has no create/debit/credit endpoint by design — its own
  javadoc states real payment initiation happens exclusively through
  `API Gateway → Validation Service → Kafka → PaymentValidatedConsumer → PaymentEngineImpl`.
- The one real ingestion endpoint, `ValidationController.validate()`
  (`POST /api/v1/validations`), has no test-mode/sandbox/dry-run flag anywhere in
  validation-service's source (grepped for `testMode|sandbox|isTest|dryRun` — zero matches).
- `ToolPermissions` (mcp-gateway) declares `PAYMENT_RETRY`/`PAYMENT_CANCEL`/`PAYMENT_REFUND` as
  placeholder constants for "a future write tool" — never referenced, never checked, never
  granted anywhere. No write tool exists on the platform today.

Before a Payment Create Agent can be built safely, PaymentX needs a genuine, explicitly isolated
test/sandbox payment-creation mechanism — not invented or simulated as part of this or any future
agent-platform phase.

## 7. Live verification — BLOCKED

Checked, before spending any real LLM/MCP call, whether a safe live execution was possible:

- `docker exec paymentx-redis redis-cli --scan --pattern "gateway:*"` shows Redis is reachable
  (`PING` → `PONG`, 18 keys present, including `gateway:participant:BANK001/BANK002`) but **zero**
  `gateway:apikey:*` keys exist. This is `MCP_PAYMENT_SERVICE_API_KEY`'s pre-existing, documented
  2-hour-TTL provisioning gap (see `PAYMENTX_PHASE_4_8_4_GEMINI_PROVIDER_AND_RCA_VALIDATION.md`) —
  it has expired since it was last provisioned and was not re-provisioned this session.
- Without that key, every one of this agent's tools (`payment.lookup`, `payment.status`,
  `audit.search`, `reconciliation.status`) would fail at the MCP Gateway → API Gateway
  authentication layer before ever reaching a real payment record — a pre-existing infrastructure
  gap, not a defect in this agent or this milestone's own code.

Per the instruction not to invent a workaround and not to waste a real LLM call against a
guaranteed-to-fail prerequisite, live verification is classified **BLOCKED**, exactly like the
prior milestone's own precedent for a comparable gap. Re-provisioning
(`redis-cli SET gateway:apikey:<key> BANK001 EX 7200`, run by the user, matching established
convention — the key value is never displayed in chat/logs) would unblock this for a future
session.

## 8. Deployment

- Old PID `12112` (stale jar) → stopped → real `mvn -o -pl paymentx-agent-orchestrator package`
  (first run with tests: 308/308 passed, `REAL_MAVEN_EXIT_CODE` captured directly, never piped
  through `tail`; second run `-DskipTests` only to repackage after freeing the jar lock) → new jar
  verified via `jar tf`/timestamp to contain the `payment-test-agent` YAML entry → started as new
  PID `36096` on port 8098.
- **Memory safety**: free RAM dropped to ~1.15GB (confirmed on a second check, not a transient
  blip) immediately after stopping the old process for repackaging. Per the memory-safety rule,
  starting the new JVM was paused and reported rather than forced; a background monitor watched
  free RAM and the new process was started only after it recovered to a confirmed, stable ~2.2GB.
- Verified live: `GET http://localhost:8098/api/v1/agent/agents` (Agent Orchestrator, direct)
  and `GET http://localhost:8089/api/v1/agents` (Control Center backend, the real proxy the
  frontend calls) both list all 8 agents including `payment-test-agent` — confirming the
  "register once, appear everywhere" claim end-to-end without any Control Center code change.

## 9. Files changed (working tree only — no commit/push/reset/clean)

- `paymentx-agent-orchestrator/src/main/resources/application.yml` — new `payment-test-agent`
  registry entry.
- `paymentx-agent-orchestrator/src/test/java/com/paymentx/agent/registry/PaymentTestAgentDefinitionTest.java` (new)
- `paymentx-agent-orchestrator/src/test/java/com/paymentx/agent/security/PaymentTestAgentSecurityTest.java` (new)
- `paymentx-agent-orchestrator/src/test/java/com/paymentx/agent/registry/IncidentRcaAgentDefinitionTest.java` — 7→8 agent count.
- `paymentx-agent-orchestrator/src/test/java/com/paymentx/agent/controller/AgentE2EIntegrationTest.java` — 7→8 agent count + new allowed-tools block.
- `paymentx-prompt-service/src/main/resources/db/changelog/changes/V1_0_10__seed_payment_test_validation_agent_prompt.yaml` (new)
- `paymentx-prompt-service/src/main/resources/db/changelog/db.changelog-master.yaml` — new include.
- `paymentx-prompt-service/src/test/java/com/paymentx/prompt/repository/PaymentTestValidationAgentPromptSeedTest.java` (new)

No files changed in Control Center (backend or frontend), MCP Gateway, or any payment-domain
service — confirming the generic-UI/registry-driven architecture worked exactly as designed.
