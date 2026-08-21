package com.paymentx.llm.controller;

import com.paymentx.common.dto.ApiResponse;
import com.paymentx.llm.dto.GenerateRequest;
import com.paymentx.llm.dto.GenerateResponse;
import com.paymentx.llm.dto.LlmHealthResponse;
import com.paymentx.llm.service.LlmService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * English:
 * The entire Phase 3.3 LLM Service API surface - exactly two endpoints,
 * matching Step 5/23's minimal, honest contract. Both endpoints are
 * open (no @PreAuthorize) - unlike Prompt Service's admin-gated write
 * endpoints, this service has no mutating/admin operation at all
 * (generate is the one operation every caller genuinely needs, same
 * reasoning as PromptController.render's javadoc); there is nothing
 * here that should ever require a special role. Follows the exact
 * `/api/v1/{resource}` + ApiResponse&lt;T&gt; envelope convention every
 * other PaymentX service uses.
 * Why it exists: Step 5's exact endpoint list.
 * How it communicates with other components: this IS the backend
 * endpoint AI Chat Service's AiChatService.java will call once wired
 * (Step 10) - today, called by nothing in production, same "contract
 * exists before its caller does" pattern Prompt Service's render()
 * endpoint documents.
 *
 * Hinglish:
 * Poora Phase 3.3 LLM Service API surface - exactly do endpoints, Step
 * 5/23 ke minimal, honest contract se match karte hue. Dono endpoints
 * open hain (@PreAuthorize nahi) - Prompt Service ke admin-gated write
 * endpoints ke ulat, is service me koi mutating/admin operation hai hi
 * nahi (generate wo ek operation hai jo har caller ko genuinely chahiye,
 * PromptController.render ke javadoc jaisa hi reasoning); yahan kuch
 * bhi aisa nahi hai jise kabhi ek special role chahiye ho. Har doosri
 * PaymentX service ke exact `/api/v1/{resource}` + ApiResponse&lt;T&gt;
 * envelope convention ko follow karta hai.
 * Ye kyu hai: Step 5 ki exact endpoint list.
 * Dusre components se kaise communicate karta hai: yehi backend
 * endpoint hai jise AI Chat Service ka AiChatService.java ek baar wired
 * hone par (Step 10) call karega - aaj, production me kuch bhi ise call
 * nahi karta, wahi "contract apne caller se pehle exist karta hai"
 * pattern jo Prompt Service ka render() endpoint document karta hai.
 */
@RestController
@RequestMapping("/api/v1/llm")
@RequiredArgsConstructor
@Tag(name = "LLM", description = "Real LLM provider generation and configuration-status health")
public class LlmController {

    private final LlmService llmService;

    @PostMapping("/generate")
    @Operation(summary = "Call the configured LLM provider with an already-rendered prompt and return a real, normalized response")
    public ResponseEntity<ApiResponse<GenerateResponse>> generate(@Valid @RequestBody GenerateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(llmService.generate(request)));
    }

    @GetMapping("/health")
    @Operation(summary = "Report whether an LLM provider API key is configured - does not make a real provider call")
    public ResponseEntity<ApiResponse<LlmHealthResponse>> health() {
        return ResponseEntity.ok(ApiResponse.success(llmService.health()));
    }
}
