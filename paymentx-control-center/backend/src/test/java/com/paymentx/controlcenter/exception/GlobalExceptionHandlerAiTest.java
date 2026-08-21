package com.paymentx.controlcenter.exception;

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
 * ENGLISH: Proves GlobalExceptionHandler maps AiServiceNotReadyException
 * to a real 503 Service Unavailable - deliberately distinct from the
 * generic ControlCenterException's 502 Bad Gateway, since "AI not built
 * yet" is not "an upstream call failed." What it verifies: the real
 * errorCode/message the exception carries reaches the response body
 * unchanged, and the HTTP status is exactly 503.
 *
 * HINGLISH: GlobalExceptionHandler AiServiceNotReadyException ko ek
 * real 503 Service Unavailable par map karta hai - jaan-boojh kar
 * generic ControlCenterException ke 502 Bad Gateway se alag, kyunki
 * "AI abhi nahi bani" ka matlab "ek upstream call fail hui" nahi hai.
 * Ye kya verify karta hai: exception jo real errorCode/message carry
 * karta hai wo response body tak bina badle pahunchta hai, aur HTTP
 * status exactly 503 hota hai.
 */
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerAiTest {

    @Mock
    private HttpServletRequest request;

    @Test
    void mapsAiServiceNotReadyExceptionTo503WithRealErrorCode() {
        when(request.getRequestURI()).thenReturn("/api/v1/ai/chat");
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        AiServiceNotReadyException ex = new AiServiceNotReadyException("AI_SERVICE_NOT_READY", "not built yet");

        ResponseEntity<com.paymentx.controlcenter.dto.ApiResponse<Void>> response =
                handler.handleAiServiceNotReady(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().error().errorCode()).isEqualTo("AI_SERVICE_NOT_READY");
        assertThat(response.getBody().error().message()).isEqualTo("not built yet");
        assertThat(response.getBody().error().path()).isEqualTo("/api/v1/ai/chat");
    }
}
