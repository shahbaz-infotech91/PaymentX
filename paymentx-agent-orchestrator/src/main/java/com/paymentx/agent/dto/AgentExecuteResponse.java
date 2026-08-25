package com.paymentx.agent.dto;

import java.util.List;

/**
 * English:
 * The body of POST /api/v1/agent/execute's real response - Step 14's
 * exact required shape: `answer`, `status` (dto/AgentResponseStatus,
 * never collapsed to one generic flag), `sources` (RAG evidence, empty
 * when RAG was not consulted), `toolEvidence` (MCP tool evidence, empty
 * when no tool was called), and `executionMetadata` (safe telemetry
 * only). Never carries chain-of-thought, the rendered system prompt, or
 * raw tool credentials (Step 14). This is the contract
 * paymentx-control-center/backend's AiChatService consumes and maps,
 * backward-compatibly, into the existing AiChatResponse the Phase 3.1
 * frontend already renders (Step 39) - `answer`/`status` map directly;
 * `sources`/`toolEvidence` are new, additive fields the frontend may
 * choose to render later without any backend contract break today.
 * Why it exists: Step 14/39.
 * How it communicates with other components: built by
 * orchestrator/AgentOrchestratorService.execute; returned by
 * controller/AgentController.execute; consumed by Control Center's
 * AiChatService/AiPlatformClient.
 *
 * Hinglish:
 * POST /api/v1/agent/execute ke real response ka body - Step 14 ka
 * exact required shape: `answer`, `status` (dto/AgentResponseStatus,
 * kabhi ek generic flag me collapse nahi hota), `sources` (RAG
 * evidence, empty jab RAG consult nahi hui), `toolEvidence` (MCP tool
 * evidence, empty jab koi tool call nahi hua), aur `executionMetadata`
 * (sirf safe telemetry). Kabhi chain-of-thought, rendered system
 * prompt, ya raw tool credentials carry nahi karta (Step 14). Ye wahi
 * contract hai jise paymentx-control-center/backend ka AiChatService
 * consume aur map karta hai, backward-compatibly, existing
 * AiChatResponse me jise Phase 3.1 frontend already render karta hai
 * (Step 39) - `answer`/`status` seedhe map hote hain; `sources`/
 * `toolEvidence` naye, additive fields hain jinhe frontend baad me
 * render karna choose kar sakta hai bina aaj koi backend contract break
 * kiye.
 * Ye kyu hai: Step 14/39.
 * Dusre components se kaise communicate karta hai:
 * orchestrator/AgentOrchestratorService.execute dwara banaya jaata hai;
 * controller/AgentController.execute dwara return hota hai; Control
 * Center ka AiChatService/AiPlatformClient ise consume karta hai.
 */
public record AgentExecuteResponse(
        String answer,
        AgentResponseStatus status,
        List<SourceEvidence> sources,
        List<ToolEvidence> toolEvidence,
        ExecutionMetadata executionMetadata,
        // Phase 4.7 - additive fields (same "frontend may choose to render later without any
        // backend contract break" precedent this record's own javadoc already established for
        // sources/toolEvidence). executionId is the same value AgentAuditClient already records
        // as its audit event's own `reference` field - this is not a new identifier, only a newly
        // surfaced one, so a Control Center execution-history detail lookup can resolve the exact
        // audit record a live execution response corresponds to.
        String executionId,
        String correlationId,
        String agentId,
        // Phase 5 - additive LLM provider/fallback visibility (same "frontend may choose to
        // render later without any backend contract break" precedent this record's own javadoc
        // already established). provider/fallbackReason are the raw provider name / LlmException
        // error code strings LlmProviderRouter already produces - never a credential, never a
        // stack trace.
        String provider,
        boolean fallbackUsed,
        String fallbackReason
) {
}
