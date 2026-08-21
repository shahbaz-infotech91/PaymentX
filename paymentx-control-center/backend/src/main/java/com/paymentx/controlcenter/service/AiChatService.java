package com.paymentx.controlcenter.service;

import com.paymentx.controlcenter.client.AiPlatformClient;
import com.paymentx.controlcenter.config.ControlCenterProperties;
import com.paymentx.controlcenter.config.CorrelationIdFilter;
import com.paymentx.controlcenter.dto.ai.AiChatRequest;
import com.paymentx.controlcenter.dto.ai.AiChatResponse;
import com.paymentx.controlcenter.dto.ai.AiComponentStatus;
import com.paymentx.controlcenter.dto.ai.AiHealthResponse;
import com.paymentx.controlcenter.dto.ai.AiRole;
import com.paymentx.controlcenter.exception.AiServiceNotReadyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ENGLISH: The real backend boundary for the AI Assistant - Phase 3.1
 * built the honest "always reject" contract; Phase 3.3 wired it to a
 * real LLM Service call; Phase 3.6 rewired it to RAG Service for
 * grounded, source-cited answers; Phase 3.8 rewires it AGAIN, this time
 * to Agent Orchestrator, so the browser's existing AI Assistant can now
 * also answer questions that need real, current PaymentX data (a
 * payment's status, routing information, audit history) on top of
 * grounded knowledge - still without ever fabricating content at any
 * step. What it does: sendMessage(...) still throws
 * AiServiceNotReadyException immediately with AI_NOT_CONFIGURED when
 * control-center.ai.enabled is false (unchanged since Phase 3.1) - but
 * when enabled, it now calls AiPlatformClient.executeAgent(...) against
 * the real, configured Agent Orchestrator instead of calling RAG
 * Service directly (Step 38 - "the browser should NOT directly call...
 * Agent Orchestrator... Preferred: POST /api/v1/ai/chat remains the
 * user-facing endpoint. Internally: AI Chat -&gt; Agent Orchestrator" -
 * this class is exactly that indirection point, and only what it calls
 * internally changed, not AiController's contract). Agent
 * Orchestrator's own real `status` (SUCCESS/INSUFFICIENT_CONTEXT/
 * REFUSED/DENIED/MAX_ITERATIONS/TIMEOUT/FAILED - see
 * paymentx-agent-orchestrator's AgentResponseStatus javadoc) passes
 * straight through into AiChatResponse.status verbatim - "COMPLETED" is
 * now only used for a real SUCCESS, matching the honest, specific-
 * outcome pattern every prior phase already established rather than
 * collapsing seven real, distinct outcomes back into one generic "it
 * worked" flag. Any real failure from that call (Agent Orchestrator
 * unreachable, or ANY of the four services it orchestrates failing) is
 * rethrown as AiServiceNotReadyException carrying Agent Orchestrator's
 * own real errorCode/message. WHY THIS PHASE NO LONGER CALLS RAG
 * SERVICE DIRECTLY: RAG Service alone cannot answer a question that
 * needs real, current PaymentX data (Agent Orchestrator's MCP tool
 * access is what supplies that) - calling RAG Service directly now
 * would mean the AI Assistant can never look up a real payment/routing/
 * audit record, only ever answer from documentation, the exact
 * limitation Agent Orchestrator exists to remove (Phase 3.0
 * architecture §12's "Payment Operations Investigation Agent" is
 * precisely this capability). RAG Service remains a real, independent
 * dependency - Agent Orchestrator calls it internally for knowledge
 * retrieval - so its health is still worth reporting here. health() now
 * reports REAL per-component status for promptService/llmService/
 * ragService/mcpGateway/agentOrchestrator (via
 * AiPlatformClient.componentHealth's real /actuator/health probe) - no
 * component in this phase's chat integration is honestly
 * NOT_IMPLEMENTED anymore.
 * Why it exists: Step 38 of the Phase 3.8 brief.
 * How it communicates with the backend: called by AiController; calls
 * AiPlatformClient, which calls the real Agent Orchestrator (and, for
 * health only, Prompt/LLM/RAG Service and MCP Gateway) through the API
 * Gateway-free direct-service-URL path Phase 3.0's architecture
 * documents as this dashboard's existing convention (see
 * ControlCenterProperties.Services' identical pattern for the 9
 * business services).
 *
 * HINGLISH: AI Assistant ka real backend boundary - Phase 3.1 ne honest
 * "hamesha reject karo" contract banaya; Phase 3.3 ne ise ek real LLM
 * Service call se wire kiya; Phase 3.6 ne ise RAG Service se rewire
 * kiya grounded, source-cited answers ke liye; Phase 3.8 ise DOBARA
 * rewire karta hai, is baar Agent Orchestrator se, taaki browser ka
 * existing AI Assistant ab un questions ka bhi jawab de sake jinhe real,
 * current PaymentX data chahiye (ek payment ka status, routing
 * information, audit history) grounded knowledge ke upar - phir bhi
 * kisi bhi step par kabhi content fabricate kiye bina. Ye kya karti hai:
 * sendMessage(...) ab bhi turant AiServiceNotReadyException
 * AI_NOT_CONFIGURED ke saath throw karta hai jab control-center.ai.enabled
 * false ho (Phase 3.1 se unchanged) - lekin jab enabled ho, ye ab real,
 * configured Agent Orchestrator ke against AiPlatformClient.executeAgent(...)
 * call karta hai, seedhe RAG Service call karne ke bajaye (Step 38 -
 * "browser ko seedhe... Agent Orchestrator call NAHI karna chahiye...
 * Preferred: POST /api/v1/ai/chat hi user-facing endpoint rehta hai.
 * Internally: AI Chat -&gt; Agent Orchestrator" - ye class exactly wahi
 * indirection point hai, aur sirf ye internally kya call karti hai wo
 * badla, AiController ka contract nahi). Agent Orchestrator ka apna
 * real `status` (SUCCESS/INSUFFICIENT_CONTEXT/REFUSED/DENIED/
 * MAX_ITERATIONS/TIMEOUT/FAILED) seedhe AiChatResponse.status me
 * verbatim pass hota hai - "COMPLETED" ab sirf ek real SUCCESS ke liye
 * use hota hai. Us call se koi bhi real failure Agent Orchestrator ka
 * apna real errorCode/message carry karte hue AiServiceNotReadyException
 * ke roop me rethrow hoti hai. YE PHASE RAG SERVICE KO SEEDHE KYU NAHI
 * CALL KARTI: RAG Service akele ek aisa question answer nahi kar sakti
 * jise real, current PaymentX data chahiye (Agent Orchestrator ka MCP
 * tool access wo supply karta hai) - RAG Service ko seedhe call karna
 * ab matlab hoga AI Assistant kabhi ek real payment/routing/audit
 * record lookup nahi kar sakta, sirf documentation se hi jawab de
 * sakta hai, exactly wahi limitation jise Agent Orchestrator remove
 * karne ke liye exist karta hai. RAG Service ek real, independent
 * dependency rehti hai - Agent Orchestrator ise internally knowledge
 * retrieval ke liye call karta hai - isliye uski health yahan report
 * karna abhi bhi worth hai. health() ab promptService/llmService/
 * ragService/mcpGateway/agentOrchestrator ke liye REAL per-component
 * status report karta hai - is phase ke chat integration me koi bhi
 * component honestly NOT_IMPLEMENTED nahi raha.
 * Ye kyu hai: Phase 3.8 brief ka Step 38.
 * Backend se kaise connect hogi: AiController ise call karta hai; ye
 * AiPlatformClient ko call karta hai, jo real Agent Orchestrator ko
 * (aur, sirf health ke liye, Prompt/LLM/RAG Service aur MCP Gateway ko)
 * us API Gateway-free direct-service-URL path se call karta hai jise
 * Phase 3.0 ka architecture is dashboard ka existing convention
 * documents karta hai (ControlCenterProperties.Services ka 9 business
 * services ke liye identical pattern dekho).
 */
@Service
public class AiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatService.class);

    /** Mirrors the 7 Phase 3 AI Platform components named in PAYMENTX_PHASE_3_ARCHITECTURE.md §5/§16. */
    private static final String COMPONENT_CHAT_INTERFACE = "chatInterface";
    private static final String COMPONENT_PROMPT_SERVICE = "promptService";
    private static final String COMPONENT_LLM_SERVICE = "llmService";
    private static final String COMPONENT_EMBEDDING_SERVICE = "embeddingService";
    private static final String COMPONENT_RAG_SERVICE = "ragService";
    private static final String COMPONENT_MCP_GATEWAY = "mcpGateway";
    private static final String COMPONENT_AGENT_ORCHESTRATOR = "agentOrchestrator";

    private final ControlCenterProperties.Ai aiProperties;
    private final AiPlatformClient aiPlatformClient;

    public AiChatService(ControlCenterProperties properties, AiPlatformClient aiPlatformClient) {
        this.aiProperties = properties.getAi();
        this.aiPlatformClient = aiPlatformClient;
    }

    /**
     * Always throws AI_NOT_CONFIGURED when the feature is off - unchanged since Phase 3.1. When on, calls
     * the real Agent Orchestrator and either returns a real AiChatResponse (grounded, tool-evidenced, or a
     * plain conversational answer) or rethrows the real upstream failure as AiServiceNotReadyException -
     * see class javadoc for why Agent Orchestrator, not RAG Service directly, is called here as of this
     * phase.
     */
    public AiChatResponse sendMessage(AiChatRequest request) {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        String conversationId = request.conversationId() != null && !request.conversationId().isBlank()
                ? request.conversationId()
                : UUID.randomUUID().toString();

        if (!aiProperties.isEnabled()) {
            log.info("AI chat request rejected: feature not configured. conversationId={} correlationId={} messageLength={}",
                    conversationId, correlationId, request.message().length());
            throw new AiServiceNotReadyException(
                    "AI_NOT_CONFIGURED",
                    "AI Assistant is not configured for this environment. An operator must set "
                            + "control-center.ai.enabled=true before this endpoint becomes reachable.");
        }

        log.info("AI chat request forwarded to Agent Orchestrator. conversationId={} correlationId={} messageLength={}",
                conversationId, correlationId, request.message().length());

        AiPlatformClient.AgentExecuteResult result = aiPlatformClient.executeAgent(
                aiProperties.getAgentOrchestratorUrl(), request.message(), conversationId, correlationId);

        String status = "SUCCESS".equals(result.status()) ? "COMPLETED" : result.status();

        return new AiChatResponse(conversationId, UUID.randomUUID().toString(), AiRole.ASSISTANT, result.answer(), OffsetDateTime.now(), status);
    }

    /** READY is now real for every component - see AiPlatformClient.componentHealth's javadoc. */
    public AiHealthResponse health() {
        Map<String, AiComponentStatus> components = new LinkedHashMap<>();

        AiComponentStatus promptServiceStatus = aiPlatformClient.componentHealth(aiProperties.getPromptServiceUrl());
        AiComponentStatus llmServiceStatus = aiPlatformClient.componentHealth(aiProperties.getLlmServiceUrl());
        AiComponentStatus embeddingServiceStatus = aiPlatformClient.componentHealth(aiProperties.getEmbeddingServiceUrl());
        AiComponentStatus ragServiceStatus = aiPlatformClient.componentHealth(aiProperties.getRagServiceUrl());
        AiComponentStatus mcpGatewayStatus = aiPlatformClient.componentHealth(aiProperties.getMcpGatewayUrl());
        AiComponentStatus agentOrchestratorStatus = aiPlatformClient.componentHealth(aiProperties.getAgentOrchestratorUrl());

        components.put(COMPONENT_CHAT_INTERFACE,
                aiProperties.isEnabled() && agentOrchestratorStatus == AiComponentStatus.READY
                        ? AiComponentStatus.READY
                        : AiComponentStatus.NOT_READY);
        components.put(COMPONENT_PROMPT_SERVICE, promptServiceStatus);
        components.put(COMPONENT_LLM_SERVICE, llmServiceStatus);
        components.put(COMPONENT_EMBEDDING_SERVICE, embeddingServiceStatus);
        components.put(COMPONENT_RAG_SERVICE, ragServiceStatus);
        components.put(COMPONENT_MCP_GATEWAY, mcpGatewayStatus);
        components.put(COMPONENT_AGENT_ORCHESTRATOR, agentOrchestratorStatus);

        String overallStatus = aiProperties.isEnabled() ? "NOT_READY" : "NOT_CONFIGURED";
        if (aiProperties.isEnabled() && agentOrchestratorStatus == AiComponentStatus.READY) {
            overallStatus = "READY";
        }
        return new AiHealthResponse(overallStatus, components, OffsetDateTime.now());
    }
}
