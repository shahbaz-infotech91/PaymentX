package com.paymentx.controlcenter.controller;

import com.paymentx.controlcenter.dto.ApiResponse;
import com.paymentx.controlcenter.dto.ai.AiChatRequest;
import com.paymentx.controlcenter.dto.ai.AiChatResponse;
import com.paymentx.controlcenter.dto.ai.AiComponentStatus;
import com.paymentx.controlcenter.dto.ai.AiHealthResponse;
import com.paymentx.controlcenter.dto.ai.AiRole;
import com.paymentx.controlcenter.exception.AiServiceNotReadyException;
import com.paymentx.controlcenter.service.AiChatService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ENGLISH: Proves AiController is a thin HTTP adapter - it wraps
 * whatever AiChatService returns in ApiResponse.success(...) and never
 * swallows or translates an AiServiceNotReadyException itself (that is
 * GlobalExceptionHandler's job - see its own test coverage there).
 * What it verifies: chat()/health() delegate to the real service with
 * the real request object, wrap a real success value correctly (using
 * a mocked service response - Phase 3.1's real AiChatService never
 * actually returns one, see AiChatServiceTest, but the controller's
 * wrapping behavior must still be correct for the day a real
 * AiChatResponse exists), and let a thrown AiServiceNotReadyException
 * propagate uncaught.
 *
 * HINGLISH: AiController ek thin HTTP adapter hai - ye prove karta hai.
 * Ye kya verify karta hai: chat()/health() real service ko real
 * request object ke saath delegate karte hain, ek real success value
 * ko sahi se wrap karte hain (ek mocked service response use karte
 * hue - Phase 3.1 ka real AiChatService kabhi actually ek return nahi
 * karta, AiChatServiceTest dekho, lekin controller ka wrapping
 * behavior us din ke liye sahi hona chahiye jab ek real AiChatResponse
 * exist karega), aur ek thrown AiServiceNotReadyException ko uncaught
 * propagate hone dete hain.
 */
@ExtendWith(MockitoExtension.class)
class AiControllerTest {

    @Mock
    private AiChatService aiChatService;

    @Test
    void chatWrapsARealServiceResponseInApiResponseSuccess() {
        AiController controller = new AiController(aiChatService);
        AiChatRequest request = new AiChatRequest("conv-1", "Why did PMT-123 fail?");
        AiChatResponse serviceResponse = new AiChatResponse(
                "conv-1", "msg-1", AiRole.ASSISTANT, "answer", OffsetDateTime.now(), "OK");
        when(aiChatService.sendMessage(request)).thenReturn(serviceResponse);

        ApiResponse<AiChatResponse> result = controller.chat(request);

        assertThat(result.success()).isTrue();
        assertThat(result.data()).isEqualTo(serviceResponse);
        verify(aiChatService).sendMessage(request);
    }

    @Test
    void chatLetsAiServiceNotReadyExceptionPropagateUncaught() {
        AiController controller = new AiController(aiChatService);
        AiChatRequest request = new AiChatRequest(null, "hello");
        when(aiChatService.sendMessage(request))
                .thenThrow(new AiServiceNotReadyException("AI_SERVICE_NOT_READY", "not built yet"));

        assertThatThrownBy(() -> controller.chat(request))
                .isInstanceOf(AiServiceNotReadyException.class);
    }

    @Test
    void healthWrapsTheRealServiceResponse() {
        AiController controller = new AiController(aiChatService);
        AiHealthResponse serviceResponse = new AiHealthResponse(
                "NOT_CONFIGURED", Map.of("chatInterface", AiComponentStatus.NOT_READY), OffsetDateTime.now());
        when(aiChatService.health()).thenReturn(serviceResponse);

        ApiResponse<AiHealthResponse> result = controller.health();

        assertThat(result.success()).isTrue();
        assertThat(result.data()).isEqualTo(serviceResponse);
    }
}
