package com.paymentx.controlcenter.dto.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Phase 4.7 - the AI Agent Control Center's own execute request. Deliberately narrower than
 * Agent Orchestrator's real AgentExecuteRequest: this module never accepts or forwards an
 * actor/role/agentId-override style field that could change WHO is executing or WHAT
 * permissions apply - agentId here only selects WHICH already-registered, already-authorized
 * agent runs; the backend is still the sole authority on tool permissions, MCP roles, and actor
 * type (see AgentExecutionService - actorId/actorType are never taken from this request).
 */
public record AgentExecuteRequest(
        @NotBlank(message = "agentId must not be blank")
        String agentId,

        @NotBlank(message = "userQuery must not be blank")
        @Size(max = 2000, message = "userQuery must be at most 2,000 characters")
        String userQuery,

        // Optional - only meaningful for agents whose allowed tools can resolve evidence from a
        // payment reference (error-analyzer, database-analysis-agent, fraud-detection-agent,
        // reconciliation-agent). Same validation shape MCP's own PaymentLookupTool already
        // enforces server-side; this is not itself a security boundary.
        @Size(max = 64, message = "paymentReference must be at most 64 characters")
        String paymentReference
) {
}
