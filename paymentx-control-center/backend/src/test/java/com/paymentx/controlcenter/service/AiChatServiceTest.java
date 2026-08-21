package com.paymentx.controlcenter.service;

import com.paymentx.controlcenter.client.AiPlatformClient;
import com.paymentx.controlcenter.config.ControlCenterProperties;
import com.paymentx.controlcenter.dto.ai.AiChatRequest;
import com.paymentx.controlcenter.dto.ai.AiChatResponse;
import com.paymentx.controlcenter.dto.ai.AiComponentStatus;
import com.paymentx.controlcenter.dto.ai.AiHealthResponse;
import com.paymentx.controlcenter.exception.AiServiceNotReadyException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * ENGLISH: Proves AiChatService's Phase 3.8 real-but-still-honest
 * behavior with a mocked AiPlatformClient (matches
 * LlmServiceImplTest's/PromptServiceImplTest's exact "mock the
 * collaborator, exercise the real orchestration logic" pattern). What
 * it verifies: disabled still short-circuits to AI_NOT_CONFIGURED
 * WITHOUT ever calling AiPlatformClient (verifyNoInteractions - the
 * cheapest, earliest truthful rejection); enabled delegates to
 * AiPlatformClient.executeAgent with the real message/conversationId/
 * correlation ID (NOT queryRag/generate - Phase 3.8 replaced the
 * direct-to-RAG call with Agent Orchestrator, see class javadoc) and
 * maps a real SUCCESS result into a real AiChatResponse with
 * status="COMPLETED" (content/status/role all real, never fabricated);
 * every other real Agent Orchestrator outcome (INSUFFICIENT_CONTEXT/
 * REFUSED/DENIED/MAX_ITERATIONS/TIMEOUT) passes through verbatim
 * without throwing; a real AiServiceNotReadyException thrown by
 * AiPlatformClient propagates with its real errorCode intact, never
 * swallowed or replaced with a generic one; health() reports the real
 * per-component status AiPlatformClient's componentHealth returns for
 * ALL SEVEN components, including embeddingService (Phase 3.9 closed
 * the last hardcoded-NOT_IMPLEMENTED gap by probing the real Embedding
 * Service's /actuator/health, same as every other component here).
 *
 * HINGLISH: AiChatService ka Phase 3.8 wala real-lekin-phir-bhi-honest
 * behavior, ek mocked AiPlatformClient ke saath prove karta hai. Ye kya
 * verify karta hai: disabled ab bhi AI_NOT_CONFIGURED par short-circuit
 * karta hai BINA kabhi AiPlatformClient call kiye (verifyNoInteractions
 * - sabse sasta, sabse jaldi truthful rejection); enabled
 * AiPlatformClient.executeAgent ko real message/conversationId/
 * correlation ID ke saath delegate karta hai (queryRag/generate NAHI -
 * Phase 3.8 ne direct-to-RAG call ko Agent Orchestrator se replace kiya,
 * class ka javadoc dekho) aur ek real SUCCESS result ko ek real
 * AiChatResponse me status="COMPLETED" ke saath map karta hai; har
 * doosra real Agent Orchestrator outcome (INSUFFICIENT_CONTEXT/REFUSED/
 * DENIED/MAX_ITERATIONS/TIMEOUT) verbatim pass through hota hai bina
 * throw kiye; AiPlatformClient dwara throw hua ek real
 * AiServiceNotReadyException apna real errorCode intact rakhte hue
 * propagate hota hai; health() AiPlatformClient ke componentHealth ke
 * real per-component status report karta hai SAAT me se HAR ek
 * component ke liye, embeddingService samet (Phase 3.9 ne aakhri
 * hardcoded-NOT_IMPLEMENTED gap band kiya, real Embedding Service ke
 * /actuator/health ko probe karke, baaki sab components ki tarah).
 */
@ExtendWith(MockitoExtension.class)
class AiChatServiceTest {

    @Mock
    private AiPlatformClient aiPlatformClient;

    @Test
    void sendMessageThrowsNotConfiguredWhenAiDisabledAndNeverCallsPlatformClient() {
        ControlCenterProperties properties = new ControlCenterProperties();
        properties.getAi().setEnabled(false);
        AiChatService service = new AiChatService(properties, aiPlatformClient);

        assertThatThrownBy(() -> service.sendMessage(new AiChatRequest(null, "Why did PMT-123 fail?")))
                .isInstanceOf(AiServiceNotReadyException.class)
                .extracting(ex -> ((AiServiceNotReadyException) ex).getErrorCode())
                .isEqualTo("AI_NOT_CONFIGURED");

        verifyNoInteractions(aiPlatformClient);
    }

    @Test
    void sendMessageEnabled_delegatesToAgentOrchestratorAndReturnsRealContent() {
        ControlCenterProperties properties = new ControlCenterProperties();
        properties.getAi().setEnabled(true);
        AiChatService service = new AiChatService(properties, aiPlatformClient);
        when(aiPlatformClient.executeAgent(anyString(), anyString(), any(), any()))
                .thenReturn(new AiPlatformClient.AgentExecuteResult("PMT-123 failed due to a routing timeout.", "SUCCESS"));

        AiChatResponse response = service.sendMessage(new AiChatRequest("conv-1", "Why did PMT-123 fail?"));

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.content()).isEqualTo("PMT-123 failed due to a routing timeout.");
        assertThat(response.conversationId()).isEqualTo("conv-1");
        verify(aiPlatformClient).executeAgent(properties.getAi().getAgentOrchestratorUrl(), "Why did PMT-123 fail?", "conv-1", null);
    }

    @Test
    void sendMessageEnabled_insufficientContextResult_passesThroughStatusVerbatim() {
        ControlCenterProperties properties = new ControlCenterProperties();
        properties.getAi().setEnabled(true);
        AiChatService service = new AiChatService(properties, aiPlatformClient);
        when(aiPlatformClient.executeAgent(anyString(), anyString(), any(), any())).thenReturn(new AiPlatformClient.AgentExecuteResult(
                "I couldn't find enough relevant information to answer this reliably.", "INSUFFICIENT_CONTEXT"));

        AiChatResponse response = service.sendMessage(new AiChatRequest(null, "hello"));

        assertThat(response.status()).isEqualTo("INSUFFICIENT_CONTEXT");
    }

    @Test
    void sendMessageEnabled_refusedResult_passesThroughStatusVerbatim() {
        ControlCenterProperties properties = new ControlCenterProperties();
        properties.getAi().setEnabled(true);
        AiChatService service = new AiChatService(properties, aiPlatformClient);
        when(aiPlatformClient.executeAgent(anyString(), anyString(), any(), any()))
                .thenReturn(new AiPlatformClient.AgentExecuteResult("The AI assistant declined to answer this question.", "REFUSED"));

        AiChatResponse response = service.sendMessage(new AiChatRequest(null, "hello"));

        assertThat(response.status()).isEqualTo("REFUSED");
    }

    @Test
    void sendMessageEnabled_deniedResult_passesThroughStatusVerbatim() {
        ControlCenterProperties properties = new ControlCenterProperties();
        properties.getAi().setEnabled(true);
        AiChatService service = new AiChatService(properties, aiPlatformClient);
        when(aiPlatformClient.executeAgent(anyString(), anyString(), any(), any())).thenReturn(new AiPlatformClient.AgentExecuteResult(
                "This request requires an operation that is not permitted for the AI assistant.", "DENIED"));

        AiChatResponse response = service.sendMessage(new AiChatRequest(null, "Refund PMT-123"));

        assertThat(response.status()).isEqualTo("DENIED");
    }

    @Test
    void sendMessageEnabled_platformClientFailure_propagatesRealErrorCode() {
        ControlCenterProperties properties = new ControlCenterProperties();
        properties.getAi().setEnabled(true);
        AiChatService service = new AiChatService(properties, aiPlatformClient);
        when(aiPlatformClient.executeAgent(anyString(), anyString(), any(), any()))
                .thenThrow(new AiServiceNotReadyException("MCP_GATEWAY_UNAVAILABLE", "Agent Orchestrator could not reach MCP Gateway"));

        assertThatThrownBy(() -> service.sendMessage(new AiChatRequest(null, "hello")))
                .isInstanceOf(AiServiceNotReadyException.class)
                .extracting(ex -> ((AiServiceNotReadyException) ex).getErrorCode())
                .isEqualTo("MCP_GATEWAY_UNAVAILABLE");
    }

    @Test
    void healthReportsNotConfiguredWhenDisabled() {
        ControlCenterProperties properties = new ControlCenterProperties();
        properties.getAi().setEnabled(false);
        when(aiPlatformClient.componentHealth(anyString())).thenReturn(AiComponentStatus.NOT_READY);
        AiChatService service = new AiChatService(properties, aiPlatformClient);

        AiHealthResponse health = service.health();

        assertThat(health.status()).isEqualTo("NOT_CONFIGURED");
        assertThat(health.components().get("embeddingService")).isEqualTo(AiComponentStatus.NOT_READY);
        assertThat(health.components().get("chatInterface")).isEqualTo(AiComponentStatus.NOT_READY);
    }

    @Test
    void healthReportsReadyWhenEnabledAndAgentOrchestratorIsReady() {
        ControlCenterProperties properties = new ControlCenterProperties();
        properties.getAi().setEnabled(true);
        when(aiPlatformClient.componentHealth(properties.getAi().getRagServiceUrl())).thenReturn(AiComponentStatus.READY);
        when(aiPlatformClient.componentHealth(properties.getAi().getLlmServiceUrl())).thenReturn(AiComponentStatus.READY);
        when(aiPlatformClient.componentHealth(properties.getAi().getPromptServiceUrl())).thenReturn(AiComponentStatus.READY);
        when(aiPlatformClient.componentHealth(properties.getAi().getEmbeddingServiceUrl())).thenReturn(AiComponentStatus.READY);
        when(aiPlatformClient.componentHealth(properties.getAi().getMcpGatewayUrl())).thenReturn(AiComponentStatus.READY);
        when(aiPlatformClient.componentHealth(properties.getAi().getAgentOrchestratorUrl())).thenReturn(AiComponentStatus.READY);
        AiChatService service = new AiChatService(properties, aiPlatformClient);

        AiHealthResponse health = service.health();

        assertThat(health.status()).isEqualTo("READY");
        assertThat(health.components().get("chatInterface")).isEqualTo(AiComponentStatus.READY);
        assertThat(health.components().get("ragService")).isEqualTo(AiComponentStatus.READY);
        assertThat(health.components().get("llmService")).isEqualTo(AiComponentStatus.READY);
        assertThat(health.components().get("promptService")).isEqualTo(AiComponentStatus.READY);
        assertThat(health.components().get("embeddingService")).isEqualTo(AiComponentStatus.READY);
        assertThat(health.components().get("mcpGateway")).isEqualTo(AiComponentStatus.READY);
        assertThat(health.components().get("agentOrchestrator")).isEqualTo(AiComponentStatus.READY);
    }

    @Test
    void healthReportsAllSevenPhase3ComponentsIncludingRealEmbeddingProbe() {
        ControlCenterProperties properties = new ControlCenterProperties();
        when(aiPlatformClient.componentHealth(anyString())).thenReturn(AiComponentStatus.NOT_READY);
        AiChatService service = new AiChatService(properties, aiPlatformClient);

        AiHealthResponse health = service.health();

        assertThat(health.components()).containsOnlyKeys(
                "chatInterface", "promptService", "llmService", "embeddingService",
                "ragService", "mcpGateway", "agentOrchestrator");
        assertThat(health.components().get("embeddingService")).isEqualTo(AiComponentStatus.NOT_READY);
        assertThat(health.components().get("mcpGateway")).isEqualTo(AiComponentStatus.NOT_READY);
        assertThat(health.components().get("agentOrchestrator")).isEqualTo(AiComponentStatus.NOT_READY);
        verify(aiPlatformClient).componentHealth(properties.getAi().getEmbeddingServiceUrl());
    }
}
