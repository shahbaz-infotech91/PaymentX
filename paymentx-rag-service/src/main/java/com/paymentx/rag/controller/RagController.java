package com.paymentx.rag.controller;

import com.paymentx.common.dto.ApiResponse;
import com.paymentx.rag.dto.RagHealthResponse;
import com.paymentx.rag.dto.RagQueryRequest;
import com.paymentx.rag.dto.RagQueryResponse;
import com.paymentx.rag.service.RagService;
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
 * The entire Phase 3.6 RAG Service API surface - exactly two endpoints
 * (query, health), matching every other AI Platform service's minimal
 * contract. Both are open (no @PreAuthorize) - this service has no
 * mutating/admin operation at all (same reasoning as LlmController's/
 * EmbeddingController's javadoc); query is the routine, non-admin
 * operation AI Chat Service needs to call for every grounded question
 * (Step 35). Follows the exact `/api/v1/{resource}` +
 * ApiResponse&lt;T&gt; envelope convention every other PaymentX service
 * uses.
 * Why it exists: Step 5/6/34's exact endpoint list.
 * How it communicates with other components: this IS the backend
 * endpoint Control Center's AiChatService now calls (Step 35) instead
 * of calling LLM Service directly - the browser continues calling the
 * existing, unchanged AI Chat endpoint; only what AiChatService calls
 * internally changed.
 *
 * Hinglish:
 * Poora Phase 3.6 RAG Service API surface - exactly do endpoints
 * (query, health), har doosri AI Platform service ke minimal contract
 * se match karte hue. Dono open hain (@PreAuthorize nahi) - is service
 * me koi mutating/admin operation hai hi nahi (LlmController/
 * EmbeddingController ke javadoc jaisa hi reasoning); query wo routine,
 * non-admin operation hai jise AI Chat Service ko har grounded question
 * ke liye call karna hota hai (Step 35). Har doosri PaymentX service ke
 * exact `/api/v1/{resource}` + ApiResponse&lt;T&gt; envelope convention
 * ko follow karta hai.
 * Ye kyu hai: Step 5/6/34 ki exact endpoint list.
 * Dusre components se kaise communicate karta hai: yehi backend
 * endpoint hai jise Control Center ki AiChatService ab (Step 35) call
 * karti hai seedhe LLM Service call karne ke bajaye - browser wahi
 * existing, unchanged AI Chat endpoint call karta rehta hai; sirf
 * AiChatService internally kya call karti hai wo badla.
 */
@RestController
@RequestMapping("/api/v1/rag")
@RequiredArgsConstructor
@Tag(name = "RAG", description = "Retrieval-augmented generation over the PaymentX knowledge base")
public class RagController {

    private final RagService ragService;

    @PostMapping("/query")
    @Operation(summary = "Answer a question grounded in retrieved PaymentX knowledge, or honestly report insufficient context")
    public ResponseEntity<ApiResponse<RagQueryResponse>> query(@Valid @RequestBody RagQueryRequest request) {
        return ResponseEntity.ok(ApiResponse.success(ragService.query(request)));
    }

    @GetMapping("/health")
    @Operation(summary = "Report real reachability of the four downstream AI Platform services this service orchestrates")
    public ResponseEntity<ApiResponse<RagHealthResponse>> health() {
        return ResponseEntity.ok(ApiResponse.success(ragService.health()));
    }
}
