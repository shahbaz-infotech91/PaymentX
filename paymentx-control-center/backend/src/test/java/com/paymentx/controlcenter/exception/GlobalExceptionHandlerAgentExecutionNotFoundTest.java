package com.paymentx.controlcenter.exception;

import com.paymentx.controlcenter.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Regression test for the TIMEOUT audit/history investigation's confirmed, live-reproduced
 * defect: GET /api/v1/agents/executions/{executionId} for an executionId with no matching
 * audit_event row returned HTTP 502 Bad Gateway instead of a clean 404 Not Found. Root cause:
 * AgentExecutionService.executionDetail() previously threw the generic ControlCenterException,
 * which GlobalExceptionHandler's own handleControlCenterException always maps to 502 - correct
 * for a real upstream-service failure, wrong for a simple lookup miss. Fixed by giving this case
 * its own AgentExecutionNotFoundException + dedicated handler, mirroring
 * AiServiceNotReadyException's identical precedent (see GlobalExceptionHandlerAiTest).
 */
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerAgentExecutionNotFoundTest {

    @Mock
    private HttpServletRequest request;

    @Test
    void mapsAgentExecutionNotFoundExceptionTo404WithRealErrorCode() {
        when(request.getRequestURI()).thenReturn("/api/v1/agents/executions/missing-id");
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        AgentExecutionNotFoundException ex = new AgentExecutionNotFoundException("missing-id");

        ResponseEntity<ApiResponse<Void>> response = handler.handleAgentExecutionNotFound(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().error().errorCode()).isEqualTo("EXECUTION_NOT_FOUND");
        assertThat(response.getBody().error().message()).contains("missing-id");
        assertThat(response.getBody().error().path()).isEqualTo("/api/v1/agents/executions/missing-id");
    }

    @Test
    void genericControlCenterException_stillMapsTo502_notShadowedByTheNewNotFoundHandler() {
        when(request.getRequestURI()).thenReturn("/api/v1/some-upstream-call");
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ControlCenterException ex = new ControlCenterException("UPSTREAM_DOWN", "unreachable");

        ResponseEntity<ApiResponse<Void>> response = handler.handleControlCenterException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody().error().errorCode()).isEqualTo("UPSTREAM_DOWN");
    }
}
