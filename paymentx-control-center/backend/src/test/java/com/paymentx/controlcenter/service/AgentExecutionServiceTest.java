package com.paymentx.controlcenter.service;

import com.paymentx.controlcenter.client.AiPlatformClient;
import com.paymentx.controlcenter.config.ControlCenterProperties;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Phase 4.7 - mirrors AiChatServiceTest's exact "mock the collaborators, exercise the real
 * orchestration logic" pattern for the new AI Agent Control Center execution path.
 */
@ExtendWith(MockitoExtension.class)
class AgentExecutionServiceTest {

    @Mock
    private AiPlatformClient aiPlatformClient;
    @Mock
    private AgentExecutionRepository executionRepository;

    private AgentExecutionService newService(boolean enabled) {
        ControlCenterProperties properties = new ControlCenterProperties();
        properties.getAi().setEnabled(enabled);
        return new AgentExecutionService(properties, aiPlatformClient, executionRepository);
    }

    @Test
    void executeThrowsNotConfiguredWhenAiDisabledAndNeverCallsPlatformClient() {
        AgentExecutionService service = newService(false);

        assertThatThrownBy(() -> service.execute(new AgentExecuteRequest("reconciliation-agent", "Is this reconciled?", null)))
                .isInstanceOf(AiServiceNotReadyException.class)
                .extracting(ex -> ((AiServiceNotReadyException) ex).getErrorCode())
                .isEqualTo("AI_NOT_CONFIGURED");

        verifyNoInteractions(aiPlatformClient);
    }

    @Test
    void listAgentsThrowsNotConfiguredWhenAiDisabled() {
        AgentExecutionService service = newService(false);

        assertThatThrownBy(service::listAgents)
                .isInstanceOf(AiServiceNotReadyException.class)
                .extracting(ex -> ((AiServiceNotReadyException) ex).getErrorCode())
                .isEqualTo("AI_NOT_CONFIGURED");

        verifyNoInteractions(aiPlatformClient);
    }

    @Test
    void executeEnabled_returnsRealResultFromAgentOrchestrator() {
        AgentExecutionService service = newService(true);
        when(aiPlatformClient.executeAgentFull(anyString(), anyString(), anyString(), any(), anyString(), any()))
                .thenReturn(new AiPlatformClient.FullAgentExecuteResult(
                        "exec-1", "corr-1", "reconciliation-agent", "SUCCESS", "Reconciliation Finding: RECONCILED.",
                        List.of(), List.of(), new AgentExecutionMetadata(2, 1, false, 500),
                        "gemini", false, null));

        AgentExecuteResponse response = service.execute(
                new AgentExecuteRequest("reconciliation-agent", "Is PMT-1 reconciled?", "PMT-1"));

        assertThat(response.executionId()).isEqualTo("exec-1");
        assertThat(response.correlationId()).isEqualTo("corr-1");
        assertThat(response.agentId()).isEqualTo("reconciliation-agent");
        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(response.answer()).contains("RECONCILED");
        assertThat(response.paymentReference()).isEqualTo("PMT-1");
        assertThat(response.userQuery()).isEqualTo("Is PMT-1 reconciled?");
        assertThat(response.durationMs()).isEqualTo(500);
        assertThat(response.error()).isNull();
        assertThat(response.provider()).isEqualTo("gemini");
        assertThat(response.fallbackUsed()).isFalse();
    }

    @Test
    void executeEnabled_deniedOutcome_passesThroughVerbatimNotAnError() {
        AgentExecutionService service = newService(true);
        when(aiPlatformClient.executeAgentFull(anyString(), anyString(), anyString(), any(), anyString(), any()))
                .thenReturn(new AiPlatformClient.FullAgentExecuteResult(
                        "exec-2", "corr-2", "reconciliation-agent", "DENIED",
                        "This request requires an operation that is not permitted.",
                        List.of(), List.of(), new AgentExecutionMetadata(1, 0, false, 50),
                        null, false, null));

        AgentExecuteResponse response = service.execute(
                new AgentExecuteRequest("reconciliation-agent", "reconcile this batch", null));

        assertThat(response.status()).isEqualTo("DENIED");
        assertThat(response.error()).isNull();
    }

    @Test
    void executeEnabled_platformClientFailure_propagatesRealErrorCode() {
        AgentExecutionService service = newService(true);
        when(aiPlatformClient.executeAgentFull(anyString(), anyString(), anyString(), any(), anyString(), any()))
                .thenThrow(new AiServiceNotReadyException("MCP_GATEWAY_UNAVAILABLE", "Agent Orchestrator could not reach MCP Gateway"));

        assertThatThrownBy(() -> service.execute(new AgentExecuteRequest("reconciliation-agent", "hello", null)))
                .isInstanceOf(AiServiceNotReadyException.class)
                .extracting(ex -> ((AiServiceNotReadyException) ex).getErrorCode())
                .isEqualTo("MCP_GATEWAY_UNAVAILABLE");
    }

    @Test
    void listAgentsEnabled_returnsRealRegistryContents() {
        AgentExecutionService service = newService(true);
        when(aiPlatformClient.listAgents(anyString())).thenReturn(List.of(
                new AgentSummary("reconciliation-agent", "PaymentX Reconciliation Agent", "desc", "1.0",
                        List.of("RECONCILIATION_ANALYSIS"), List.of("payment.lookup", "reconciliation.status"), "LOW", true)));

        List<AgentSummary> agents = service.listAgents();

        assertThat(agents).hasSize(1);
        assertThat(agents.get(0).agentId()).isEqualTo("reconciliation-agent");
        assertThat(agents.get(0).enabled()).isTrue();
    }

    @Test
    void executionHistory_clampsPageAndSize_andDelegatesToRepository() {
        AgentExecutionService service = newService(true);
        AgentExecutionFilter filter = new AgentExecutionFilter("reconciliation-agent", null, null, null, null, null);
        when(executionRepository.findPage(filter, 0, 20)).thenReturn(List.of(
                new AgentExecutionSummary("exec-1", "corr-1", "reconciliation-agent", "q", null, "SUCCESS",
                        OffsetDateTime.now(), 500L, 1, false, "gemini", false)));
        when(executionRepository.count(filter)).thenReturn(1L);

        PageResponse<AgentExecutionSummary> page = service.executionHistory(filter, null, null);

        assertThat(page.content()).hasSize(1);
        assertThat(page.page()).isEqualTo(0);
        assertThat(page.size()).isEqualTo(20);
        assertThat(page.totalElements()).isEqualTo(1);
    }

    @Test
    void executionHistory_oversizedRequest_clampedToMax200() {
        AgentExecutionService service = newService(true);
        when(executionRepository.findPage(any(), org.mockito.ArgumentMatchers.eq(0), org.mockito.ArgumentMatchers.eq(200)))
                .thenReturn(List.of());
        when(executionRepository.count(any())).thenReturn(0L);

        PageResponse<AgentExecutionSummary> page = service.executionHistory(null, 0, 5000);

        assertThat(page.size()).isEqualTo(200);
    }

    @Test
    void executionDetail_found_returnsRealStoredRecord() {
        AgentExecutionService service = newService(true);
        AgentExecutionDetail detail = new AgentExecutionDetail(
                "exec-1", "corr-1", "reconciliation-agent", "Is PMT-1 reconciled?", "PMT-1", "SUCCESS",
                "Reconciliation Finding: RECONCILED.", List.of("reconciliation-01"),
                List.of(new AgentExecutionDetail.ToolCallNameStatus("reconciliation.status", "SUCCESS")),
                OffsetDateTime.now().minusSeconds(1), OffsetDateTime.now(), 500L, 2, false, null,
                "gemini", false, null);
        when(executionRepository.findByExecutionId("exec-1")).thenReturn(Optional.of(detail));

        AgentExecutionDetail result = service.executionDetail("exec-1");

        assertThat(result.executionId()).isEqualTo("exec-1");
        assertThat(result.answer()).contains("RECONCILED");
    }

    @Test
    void executionDetail_notFound_throwsExecutionNotFound() {
        AgentExecutionService service = newService(true);
        when(executionRepository.findByExecutionId("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.executionDetail("missing"))
                .isInstanceOf(AgentExecutionNotFoundException.class)
                .extracting(ex -> ((AgentExecutionNotFoundException) ex).getErrorCode())
                .isEqualTo("EXECUTION_NOT_FOUND");
    }
}
