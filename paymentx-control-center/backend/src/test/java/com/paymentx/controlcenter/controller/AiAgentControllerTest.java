package com.paymentx.controlcenter.controller;

import com.paymentx.controlcenter.dto.ApiResponse;
import com.paymentx.controlcenter.dto.PageResponse;
import com.paymentx.controlcenter.dto.agent.AgentExecuteRequest;
import com.paymentx.controlcenter.dto.agent.AgentExecuteResponse;
import com.paymentx.controlcenter.dto.agent.AgentExecutionDetail;
import com.paymentx.controlcenter.dto.agent.AgentExecutionFilter;
import com.paymentx.controlcenter.dto.agent.AgentExecutionMetadata;
import com.paymentx.controlcenter.dto.agent.AgentExecutionSummary;
import com.paymentx.controlcenter.dto.agent.AgentSummary;
import com.paymentx.controlcenter.exception.AiServiceNotReadyException;
import com.paymentx.controlcenter.exception.ControlCenterException;
import com.paymentx.controlcenter.service.AgentExecutionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 4.7 - mirrors AiControllerTest's exact "thin HTTP adapter" verification pattern: this
 * controller wraps whatever AgentExecutionService returns in ApiResponse.success(...) and never
 * swallows a thrown AiServiceNotReadyException/ControlCenterException itself.
 */
@ExtendWith(MockitoExtension.class)
class AiAgentControllerTest {

    @Mock
    private AgentExecutionService agentExecutionService;

    @Test
    void listAgentsWrapsRealServiceResponse() {
        AiAgentController controller = new AiAgentController(agentExecutionService);
        List<AgentSummary> agents = List.of(new AgentSummary(
                "reconciliation-agent", "PaymentX Reconciliation Agent", "desc", "1.0",
                List.of("RECONCILIATION_ANALYSIS"), List.of("payment.lookup"), "LOW", true));
        when(agentExecutionService.listAgents()).thenReturn(agents);

        ApiResponse<List<AgentSummary>> result = controller.listAgents();

        assertThat(result.success()).isTrue();
        assertThat(result.data()).isEqualTo(agents);
    }

    @Test
    void executeWrapsRealServiceResponseAndDelegatesTheRealRequest() {
        AiAgentController controller = new AiAgentController(agentExecutionService);
        AgentExecuteRequest request = new AgentExecuteRequest("reconciliation-agent", "Is PMT-1 reconciled?", "PMT-1");
        AgentExecuteResponse serviceResponse = new AgentExecuteResponse(
                "exec-1", "corr-1", "reconciliation-agent", request.userQuery(), "PMT-1", "SUCCESS",
                "Reconciliation Finding: RECONCILED.", List.of(), List.of(),
                new AgentExecutionMetadata(1, 1, false, 500),
                OffsetDateTime.now(), OffsetDateTime.now(), 500, null,
                "gemini", false, null);
        when(agentExecutionService.execute(request)).thenReturn(serviceResponse);

        ApiResponse<AgentExecuteResponse> result = controller.execute(request);

        assertThat(result.success()).isTrue();
        assertThat(result.data()).isEqualTo(serviceResponse);
        verify(agentExecutionService).execute(request);
    }

    @Test
    void executeLetsAiServiceNotReadyExceptionPropagateUncaught() {
        AiAgentController controller = new AiAgentController(agentExecutionService);
        AgentExecuteRequest request = new AgentExecuteRequest("reconciliation-agent", "hello", null);
        when(agentExecutionService.execute(request))
                .thenThrow(new AiServiceNotReadyException("AI_NOT_CONFIGURED", "not configured"));

        assertThatThrownBy(() -> controller.execute(request))
                .isInstanceOf(AiServiceNotReadyException.class);
    }

    @Test
    void executionHistoryBuildsFilterFromQueryParamsAndWrapsRealPage() {
        AiAgentController controller = new AiAgentController(agentExecutionService);
        PageResponse<AgentExecutionSummary> servicePage = new PageResponse<>(List.of(), 0, 20, 0);
        when(agentExecutionService.executionHistory(any(AgentExecutionFilter.class), org.mockito.ArgumentMatchers.eq(0), org.mockito.ArgumentMatchers.eq(20)))
                .thenReturn(servicePage);

        ApiResponse<PageResponse<AgentExecutionSummary>> result = controller.executionHistory(
                "reconciliation-agent", "SUCCESS", "PMT-1", null, null, null, 0, 20);

        assertThat(result.success()).isTrue();
        assertThat(result.data()).isEqualTo(servicePage);
        verify(agentExecutionService).executionHistory(
                new AgentExecutionFilter("reconciliation-agent", "SUCCESS", "PMT-1", null, null, null), 0, 20);
    }

    @Test
    void executionDetailWrapsRealServiceResponse() {
        AiAgentController controller = new AiAgentController(agentExecutionService);
        AgentExecutionDetail detail = new AgentExecutionDetail(
                "exec-1", "corr-1", "reconciliation-agent", "q", "PMT-1", "SUCCESS", "answer",
                List.of(), List.of(), OffsetDateTime.now(), OffsetDateTime.now(), 500L, 1, false, null,
                "gemini", false, null);
        when(agentExecutionService.executionDetail("exec-1")).thenReturn(detail);

        ApiResponse<AgentExecutionDetail> result = controller.executionDetail("exec-1");

        assertThat(result.success()).isTrue();
        assertThat(result.data()).isEqualTo(detail);
    }

    @Test
    void executionDetailLetsNotFoundExceptionPropagateUncaught() {
        AiAgentController controller = new AiAgentController(agentExecutionService);
        when(agentExecutionService.executionDetail("missing"))
                .thenThrow(new ControlCenterException("EXECUTION_NOT_FOUND", "not found"));

        assertThatThrownBy(() -> controller.executionDetail("missing"))
                .isInstanceOf(ControlCenterException.class);
    }
}
