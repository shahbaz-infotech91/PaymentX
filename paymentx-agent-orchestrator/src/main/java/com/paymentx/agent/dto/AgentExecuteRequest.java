package com.paymentx.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * English:
 * The body of POST /api/v1/agent/execute - this service's one real
 * inbound contract, called internally by Control Center's AiChatService
 * (Step 38 - the browser keeps calling the existing POST /api/v1/ai/chat;
 * only what that endpoint calls internally changes). `conversationId`
 * is optional passthrough (matching AiChatRequest's own shape) - Agent
 * Orchestrator does not resolve or persist conversation history from it
 * (Step 21/44 - no memory system exists to look one up in), it is
 * carried only for correlation/logging. `userId` is optional - Control
 * Center's dashboard auth model has no per-user PaymentX identity to
 * supply yet (see PAYMENTX_PHASE_3_ARCHITECTURE.md §1's "no real
 * platform-wide identity system yet" finding); when absent, audit/
 * AgentAuditClient records "unknown".
 * Why it exists: Step 38's internal contract requirement.
 * How it communicates with other components: bound by
 * controller/AgentController.execute; consumed by
 * orchestrator/AgentOrchestratorService.execute.
 *
 * Hinglish:
 * POST /api/v1/agent/execute ka body - is service ka ek real inbound
 * contract, Control Center ke AiChatService dwara internally call kiya
 * jaata hai (Step 38 - browser existing POST /api/v1/ai/chat hi call
 * karta rehta hai; sirf wo endpoint internally kya call karta hai wo
 * badalta hai). `conversationId` optional passthrough hai
 * (AiChatRequest ke apne shape se match karte hue) - Agent Orchestrator
 * isse conversation history resolve ya persist nahi karta (Step 21/44 -
 * koi memory system exist nahi karta jisme ise lookup kiya ja sake), ye
 * sirf correlation/logging ke liye carry hota hai. `userId` optional
 * hai - Control Center ke dashboard auth model ke paas abhi supply
 * karne ke liye koi per-user PaymentX identity nahi hai
 * (PAYMENTX_PHASE_3_ARCHITECTURE.md §1 ka "abhi koi real platform-wide
 * identity system nahi hai" finding dekho); jab absent ho, audit/
 * AgentAuditClient "unknown" record karta hai.
 * Ye kyu hai: Step 38 ka internal contract requirement.
 * Dusre components se kaise communicate karta hai:
 * controller/AgentController.execute ise bind karta hai;
 * orchestrator/AgentOrchestratorService.execute ise consume karta hai.
 */
public record AgentExecuteRequest(
        String conversationId,

        String userId,

        @NotBlank(message = "userQuery must not be blank")
        @Size(max = 2000, message = "userQuery must be at most 2,000 characters")
        String userQuery
) {
}
