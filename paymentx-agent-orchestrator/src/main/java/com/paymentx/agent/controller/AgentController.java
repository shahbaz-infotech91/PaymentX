package com.paymentx.agent.controller;

import com.paymentx.agent.config.CorrelationIdFilter;
import com.paymentx.agent.dto.AgentExecuteRequest;
import com.paymentx.agent.dto.AgentExecuteResponse;
import com.paymentx.agent.dto.AgentHealthResponse;
import com.paymentx.agent.dto.AgentSummaryResponse;
import com.paymentx.agent.orchestrator.AgentOrchestratorService;
import com.paymentx.agent.registry.AgentDefinition;
import com.paymentx.agent.registry.AgentRegistry;
import com.paymentx.common.constant.HeaderConstants;
import com.paymentx.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * English:
 * The entire Phase 3.8 Agent Orchestrator API surface - exactly two
 * endpoints (execute, health), matching every other AI Platform
 * service's minimal contract. Both are open (no @PreAuthorize) - this
 * service has no mutating/admin operation at all; execute is the
 * routine, internal-only operation Control Center's AiChatService needs
 * to call for every agent-backed chat request (Step 38 - "internal-
 * only, called by AI Chat Interface... the browser should NOT directly
 * call... Agent Orchestrator"). Follows the exact `/api/v1/{resource}`
 * + ApiResponse&lt;T&gt; envelope convention every other PaymentX
 * service uses.
 * Why it exists: Step 38's exact endpoint contract.
 * How it communicates with other components: this IS the backend
 * endpoint Control Center's AiChatService now calls (mirroring RAG
 * Service's own Phase 3.6 precedent - see that phase's AiChatService
 * javadoc) instead of calling RAG Service directly.
 *
 * Hinglish:
 * Poora Phase 3.8 Agent Orchestrator API surface - exactly do endpoints
 * (execute, health), har doosri AI Platform service ke minimal contract
 * se match karte hue. Dono open hain (@PreAuthorize nahi) - is service
 * me koi mutating/admin operation hai hi nahi; execute wo routine,
 * internal-only operation hai jise Control Center ke AiChatService ko
 * har agent-backed chat request ke liye call karna hota hai (Step 38 -
 * "internal-only, AI Chat Interface dwara call kiya jaata hai... browser
 * ko seedhe... Agent Orchestrator call NAHI karna chahiye"). Har doosri
 * PaymentX service ke exact `/api/v1/{resource}` + ApiResponse&lt;T&gt;
 * envelope convention ko follow karta hai.
 * Ye kyu hai: Step 38 ka exact endpoint contract.
 * Dusre components se kaise communicate karta hai: yehi backend
 * endpoint hai jise Control Center ka AiChatService ab call karta hai
 * (RAG Service ke apne Phase 3.6 precedent ko mirror karte hue - us
 * phase ka AiChatService javadoc dekho) RAG Service ko seedhe call
 * karne ke bajaye.
 */
@RestController
@RequestMapping("/api/v1/agent")
@RequiredArgsConstructor
@Tag(name = "Agent Orchestrator", description = "The bounded agent loop tying RAG Service, MCP Gateway, and LLM Service together")
public class AgentController {

    private final AgentOrchestratorService agentOrchestratorService;
    private final AgentRegistry agentRegistry;

    @PostMapping("/execute")
    @Operation(summary = "Run one bounded agent execution for a user query",
            description = "Internal-only - called by Control Center's AiChatService, never directly by the browser. "
                    + "An omitted agentId resolves to the platform default agent (Phase 3.8's original single agent), "
                    + "unchanged since before Phase 4.1.")
    public ResponseEntity<ApiResponse<AgentExecuteResponse>> execute(@Valid @RequestBody AgentExecuteRequest request,
                                                                      HttpServletRequest httpRequest) {
        // Phase 4.1 - agent identity is resolved here, from the trusted registry, BEFORE the
        // orchestrator loop starts - never derived from anything the planning LLM later proposes.
        AgentDefinition definition = agentRegistry.resolve(request.agentId());
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        String traceId = httpRequest.getHeader(HeaderConstants.TRACE_ID);
        return ResponseEntity.ok(ApiResponse.success(agentOrchestratorService.execute(request, definition, correlationId, traceId)));
    }

    @GetMapping("/health")
    @Operation(summary = "Report real reachability of the four downstream AI Platform services this agent orchestrates")
    public ResponseEntity<ApiResponse<AgentHealthResponse>> health() {
        return ResponseEntity.ok(ApiResponse.success(agentOrchestratorService.health()));
    }

    @GetMapping("/agents")
    @Operation(summary = "List every registered agent",
            description = "Phase 4.7 - the real AgentRegistry contents, for Control Center's AI Agent Control "
                    + "Center dashboard. Never a hardcoded list; adding/removing an agent from application.yml "
                    + "changes this response with zero code change here.")
    public ResponseEntity<ApiResponse<List<AgentSummaryResponse>>> agents() {
        List<AgentSummaryResponse> summaries = agentRegistry.list().stream()
                .map(definition -> new AgentSummaryResponse(
                        definition.agentId(), definition.name(), definition.description(), definition.version(),
                        definition.capabilities(), definition.allowedTools(), definition.riskLevel(), definition.enabled()))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(summaries));
    }
}
