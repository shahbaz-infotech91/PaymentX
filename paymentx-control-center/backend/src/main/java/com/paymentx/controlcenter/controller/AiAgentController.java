package com.paymentx.controlcenter.controller;

import com.paymentx.controlcenter.dto.ApiResponse;
import com.paymentx.controlcenter.dto.PageResponse;
import com.paymentx.controlcenter.dto.agent.AgentExecuteRequest;
import com.paymentx.controlcenter.dto.agent.AgentExecuteResponse;
import com.paymentx.controlcenter.dto.agent.AgentExecutionDetail;
import com.paymentx.controlcenter.dto.agent.AgentExecutionFilter;
import com.paymentx.controlcenter.dto.agent.AgentExecutionSummary;
import com.paymentx.controlcenter.dto.agent.AgentSummary;
import com.paymentx.controlcenter.service.AgentExecutionService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Phase 4.7 - the AI Agent Control Center's own API surface, additive and separate from
 * AiController's existing chat-oriented /api/v1/ai/chat (that endpoint and its AiChatService are
 * untouched by this phase - zero regression risk to the existing AI Assistant feature).
 *
 * Real chain, never bypassed: this controller -&gt; AgentExecutionService -&gt; AiPlatformClient -&gt;
 * Agent Orchestrator's real POST /api/v1/agent/execute -&gt; AgentRegistry/AgentToolPolicy/
 * AgentPlanValidator/MCP Gateway/ToolAuthorizationService, exactly the same security chain every
 * other caller of that endpoint goes through. This module has no MCP Gateway URL configured, no
 * MCP client, no direct Postgres/Redis/Kafka write access, and no way to construct or override an
 * agent's identity, tool permissions, or actor type - agentId here only selects which
 * already-registered agent Agent Orchestrator resolves and runs.
 */
@RestController
@RequestMapping("/api/v1/agents")
public class AiAgentController {

    private final AgentExecutionService agentExecutionService;

    public AiAgentController(AgentExecutionService agentExecutionService) {
        this.agentExecutionService = agentExecutionService;
    }

    /** List every registered agent - real AgentRegistry contents, via Agent Orchestrator. */
    @GetMapping
    public ApiResponse<List<AgentSummary>> listAgents() {
        return ApiResponse.success(agentExecutionService.listAgents());
    }

    /** Execute a real agent and return its real, live response. */
    @PostMapping("/execute")
    public ApiResponse<AgentExecuteResponse> execute(@Valid @RequestBody AgentExecuteRequest request) {
        return ApiResponse.success(agentExecutionService.execute(request));
    }

    /** Paginated execution history, sourced from the real audit trail Agent Orchestrator already writes on every execution. */
    @GetMapping("/executions")
    public ApiResponse<PageResponse<AgentExecutionSummary>> executionHistory(
            @RequestParam(required = false) String agentId,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) String paymentReference,
            @RequestParam(required = false) String executionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime toDate,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        AgentExecutionFilter filter = new AgentExecutionFilter(agentId, outcome, paymentReference, executionId, fromDate, toDate);
        return ApiResponse.success(agentExecutionService.executionHistory(filter, page, size));
    }

    /** Full detail for one past execution, retrieved from the real stored audit record. */
    @GetMapping("/executions/{executionId}")
    public ApiResponse<AgentExecutionDetail> executionDetail(@PathVariable String executionId) {
        return ApiResponse.success(agentExecutionService.executionDetail(executionId));
    }
}
