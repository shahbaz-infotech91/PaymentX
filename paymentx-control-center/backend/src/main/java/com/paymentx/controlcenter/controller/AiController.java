package com.paymentx.controlcenter.controller;

import com.paymentx.controlcenter.dto.ApiResponse;
import com.paymentx.controlcenter.dto.ai.AiChatRequest;
import com.paymentx.controlcenter.dto.ai.AiChatResponse;
import com.paymentx.controlcenter.dto.ai.AiHealthResponse;
import com.paymentx.controlcenter.service.AiChatService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ENGLISH: The Phase 3.1 AI Assistant API surface - POST /api/v1/ai/chat
 * and GET /api/v1/ai/health, following the exact
 * `/api/v1/{resource}` + ApiResponse&lt;T&gt; envelope convention every
 * other controller in this module already uses (see e.g.
 * E2EController, HealthController). What it does: both endpoints
 * delegate straight to AiChatService, which never fabricates a
 * response - a chat request that cannot be honestly answered throws
 * AiServiceNotReadyException (mapped to a real 503 by
 * GlobalExceptionHandler) rather than this controller inventing an
 * "assistant" reply. Why it exists: this is the "minimum backend
 * contract required for the future AI Chat Interface" this phase
 * requires - the React AI Assistant page (see frontend
 * services/aiService.ts) has a real, stable HTTP contract to call
 * today, even before any LLM exists behind it. How it will communicate
 * with the backend: this IS the backend endpoint, called by the
 * frontend's useAiChat/useAiHealth hooks via services/aiService.ts.
 *
 * HINGLISH: Phase 3.1 ka AI Assistant API surface - POST
 * /api/v1/ai/chat aur GET /api/v1/ai/health, exactly wahi
 * `/api/v1/{resource}` + ApiResponse&lt;T&gt; envelope convention
 * follow karte hue jo is module ka har doosra controller already use
 * karta hai (jaise E2EController, HealthController dekho). Ye kya
 * karti hai: dono endpoints seedhe AiChatService ko delegate karte
 * hain, jo kabhi ek response fabricate nahi karta - ek chat request
 * jiska honestly jawab nahi diya ja sakta AiServiceNotReadyException
 * throw karta hai (GlobalExceptionHandler ke through ek real 503 par
 * map hoti hai) is controller ke khud ek "assistant" reply invent
 * karne ke bajaye. Ye dashboard me kyu hai: yehi is phase ka required
 * "future AI Chat Interface ke liye minimum backend contract" hai -
 * React AI Assistant page (frontend services/aiService.ts dekho) ke
 * paas aaj hi ek real, stable HTTP contract hai call karne ke liye,
 * chahe abhi iske peeche koi LLM na ho. Backend se kaise connect hogi:
 * yehi backend endpoint hai, frontend ke useAiChat/useAiHealth hooks
 * ise services/aiService.ts ke through call karte hain.
 */
@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private final AiChatService aiChatService;

    public AiController(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    @PostMapping("/chat")
    public ApiResponse<AiChatResponse> chat(@Valid @RequestBody AiChatRequest request) {
        return ApiResponse.success(aiChatService.sendMessage(request));
    }

    @GetMapping("/health")
    public ApiResponse<AiHealthResponse> health() {
        return ApiResponse.success(aiChatService.health());
    }
}
