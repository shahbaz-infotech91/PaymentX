package com.paymentx.agent.state;

import java.util.Map;

/**
 * English:
 * One real MCP tool invocation's outcome, recorded into
 * state/AgentExecution.toolCalls() for the duration of a single agent
 * run. `result` is the tool's own already-sanitized structured output
 * as returned by MCP Gateway (account numbers already masked, no raw
 * internal IDs, no raw audit payloads - see PAYMENTX_PHASE_3_7_MCP_GATEWAY.md
 * §17) - Agent Orchestrator trusts MCP Gateway's own data-minimization
 * boundary rather than re-implementing it (that responsibility belongs
 * to MCP Gateway, per its own architecture). `status` is one of
 * SUCCESS/DENIED/FAILED (never a fabricated SUCCESS for a call that
 * actually failed or was denied - Step 16/17).
 * Why it exists: Step 2's `toolCalls`/`toolResults`, Step 15's Evidence
 * requirement, Step 36's safe audit trail.
 * How it communicates with other components: appended to
 * state/AgentExecution by orchestrator/AgentOrchestratorService after
 * every real tool call; surfaced in dto/ToolEvidence in the final
 * response; recorded by audit/AgentAuditClient.
 *
 * Hinglish:
 * Ek real MCP tool invocation ka outcome, state/AgentExecution.toolCalls()
 * me record hota hai ek single agent run ki duration ke liye. `result`
 * tool ka apna already-sanitized structured output hai jaisa MCP
 * Gateway return karta hai (account numbers already masked, koi raw
 * internal IDs nahi, koi raw audit payloads nahi -
 * PAYMENTX_PHASE_3_7_MCP_GATEWAY.md §17 dekho) - Agent Orchestrator MCP
 * Gateway ke apne data-minimization boundary par trust karta hai, ise
 * dobara implement karne ke bajaye (wo responsibility MCP Gateway ki
 * hai, uski apni architecture ke hisaab se). `status` SUCCESS/DENIED/
 * FAILED me se ek hai (kabhi ek fabricated SUCCESS us call ke liye
 * nahi jo actually fail ho gayi ya deny ho gayi - Step 16/17).
 * Ye kyu hai: Step 2 ka `toolCalls`/`toolResults`, Step 15 ka Evidence
 * requirement, Step 36 ka safe audit trail.
 * Dusre components se kaise communicate karta hai:
 * orchestrator/AgentOrchestratorService dwara har real tool call ke
 * baad state/AgentExecution me append hota hai; final response me
 * dto/ToolEvidence me surface hota hai; audit/AgentAuditClient dwara
 * record hota hai.
 */
public record ToolCallRecord(
        String toolName,
        Map<String, Object> arguments,
        String status,
        Map<String, Object> result,
        String errorCode,
        long latencyMs
) {
}
