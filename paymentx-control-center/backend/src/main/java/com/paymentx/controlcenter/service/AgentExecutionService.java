package com.paymentx.controlcenter.service;

import com.paymentx.controlcenter.client.AiPlatformClient;
import com.paymentx.controlcenter.config.ControlCenterProperties;
import com.paymentx.controlcenter.config.CorrelationIdFilter;
import com.paymentx.controlcenter.dto.PageResponse;
import com.paymentx.controlcenter.dto.agent.AgentExecuteRequest;
import com.paymentx.controlcenter.dto.agent.AgentExecuteResponse;
import com.paymentx.controlcenter.dto.agent.AgentExecutionDetail;
import com.paymentx.controlcenter.dto.agent.AgentExecutionFilter;
import com.paymentx.controlcenter.dto.agent.AgentExecutionMetadata;
import com.paymentx.controlcenter.dto.agent.AgentExecutionSummary;
import com.paymentx.controlcenter.dto.agent.AgentSummary;
import com.paymentx.controlcenter.exception.AgentExecutionNotFoundException;
import com.paymentx.controlcenter.exception.AiServiceNotReadyException;
import com.paymentx.controlcenter.repository.AgentExecutionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Phase 4.7 - the real backend boundary for the AI Agent Control Center: a generic execute path
 * (any of the real registered agents, selected by agentId, never a per-agent hardcoded
 * implementation) plus a real, read-only execution-history view backed entirely by the audit
 * trail Agent Orchestrator's own AgentAuditClient already writes unconditionally on every run
 * (see AgentExecutionRepository's own javadoc for why no new persistence layer was created).
 *
 * Security note: this service never lets the caller's request override agent identity, actor
 * type, or tool permissions - agentId here only SELECTS which already-registered agent to run
 * (resolved server-side, on Agent Orchestrator's own trusted AgentRegistry, exactly the same as
 * every other caller of POST /api/v1/agent/execute); it is not itself a grant of any kind. The
 * full AgentToolPolicy -&gt; MCP Gateway -&gt; ToolAuthorizationService chain runs unchanged and is
 * never bypassed or reachable directly from this class.
 */
@Service
public class AgentExecutionService {

    private static final Logger log = LoggerFactory.getLogger(AgentExecutionService.class);
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 200;

    private final ControlCenterProperties.Ai aiProperties;
    private final AiPlatformClient aiPlatformClient;
    private final AgentExecutionRepository executionRepository;

    public AgentExecutionService(ControlCenterProperties properties, AiPlatformClient aiPlatformClient,
                                  AgentExecutionRepository executionRepository) {
        this.aiProperties = properties.getAi();
        this.aiPlatformClient = aiPlatformClient;
        this.executionRepository = executionRepository;
    }

    public List<AgentSummary> listAgents() {
        requireEnabled();
        return aiPlatformClient.listAgents(aiProperties.getAgentOrchestratorUrl());
    }

    public AgentExecuteResponse execute(AgentExecuteRequest request) {
        requireEnabled();
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        String conversationId = UUID.randomUUID().toString();
        OffsetDateTime startedAt = OffsetDateTime.now();

        log.info("Agent execute request forwarded to Agent Orchestrator. agentId={} correlationId={} queryLength={}",
                request.agentId(), correlationId, request.userQuery().length());

        AiPlatformClient.FullAgentExecuteResult result = aiPlatformClient.executeAgentFull(
                aiProperties.getAgentOrchestratorUrl(), request.agentId(), request.userQuery(),
                request.paymentReference(), conversationId, correlationId);

        OffsetDateTime completedAt = OffsetDateTime.now();
        long durationMs = result.executionMetadata() != null
                ? result.executionMetadata().totalLatencyMs()
                : java.time.Duration.between(startedAt, completedAt).toMillis();

        return new AgentExecuteResponse(
                result.executionId(), result.correlationId(), result.agentId(),
                request.userQuery(), request.paymentReference(), result.status(), result.answer(),
                result.sources(), result.toolsCalled(),
                result.executionMetadata() != null ? result.executionMetadata() : new AgentExecutionMetadata(0, 0, false, durationMs),
                startedAt, completedAt, durationMs, null,
                result.provider(), result.fallbackUsed(), result.fallbackReason());
    }

    public PageResponse<AgentExecutionSummary> executionHistory(AgentExecutionFilter filter, Integer page, Integer size) {
        int p = clampPage(page), s = clampSize(size);
        return new PageResponse<>(
                executionRepository.findPage(filter, p, s), p, s, executionRepository.count(filter));
    }

    public AgentExecutionDetail executionDetail(String executionId) {
        return executionRepository.findByExecutionId(executionId)
                .orElseThrow(() -> new AgentExecutionNotFoundException(executionId));
    }

    private void requireEnabled() {
        if (!aiProperties.isEnabled()) {
            throw new AiServiceNotReadyException(
                    "AI_NOT_CONFIGURED",
                    "AI Agent Control Center is not configured for this environment. An operator must set "
                            + "control-center.ai.enabled=true before this endpoint becomes reachable.");
        }
    }

    private int clampPage(Integer page) {
        return page == null || page < 0 ? 0 : page;
    }

    private int clampSize(Integer size) {
        if (size == null) return DEFAULT_SIZE;
        return Math.max(1, Math.min(size, MAX_SIZE));
    }
}
