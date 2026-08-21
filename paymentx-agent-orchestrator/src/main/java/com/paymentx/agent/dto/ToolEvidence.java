package com.paymentx.agent.dto;

import java.util.Map;

/**
 * English:
 * One real MCP tool call's outcome, carried into the final agent
 * response - a direct projection of state/ToolCallRecord. `result` is
 * MCP Gateway's own already-sanitized structured tool output, never
 * re-derived here (see ToolCallRecord's javadoc for why Agent
 * Orchestrator trusts MCP Gateway's own data-minimization boundary
 * rather than re-implementing it). This is what Step 15's "Tool
 * Evidence" example (Payment Status: FAILED / Error Code:
 * DUPLICATE_REQUEST) becomes in the real response.
 * Why it exists: Step 14/15/39.
 * How it communicates with other components: built by
 * orchestrator/AgentOrchestratorService from state/ToolCallRecord;
 * returned inside dto/AgentExecuteResponse.toolEvidence().
 *
 * Hinglish:
 * Ek real MCP tool call ka outcome, final agent response me carry hota
 * hai - state/ToolCallRecord ka ek direct projection. `result` MCP
 * Gateway ka apna already-sanitized structured tool output hai, yahan
 * kabhi re-derive nahi hota (ToolCallRecord ka javadoc dekho ki Agent
 * Orchestrator MCP Gateway ke apne data-minimization boundary par trust
 * kyu karta hai, ise re-implement karne ke bajaye). Ye wahi hai jo Step
 * 15 ka "Tool Evidence" example (Payment Status: FAILED / Error Code:
 * DUPLICATE_REQUEST) real response me ban jaata hai.
 * Ye kyu hai: Step 14/15/39.
 * Dusre components se kaise communicate karta hai:
 * orchestrator/AgentOrchestratorService dwara state/ToolCallRecord se
 * banaya jaata hai; dto/AgentExecuteResponse.toolEvidence() ke andar
 * return hota hai.
 */
public record ToolEvidence(
        String toolName,
        String status,
        Map<String, Object> result
) {
}
