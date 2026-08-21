package com.paymentx.embedding.controller;

import com.paymentx.common.dto.ApiResponse;
import com.paymentx.embedding.dto.BatchEmbeddingRequest;
import com.paymentx.embedding.dto.BatchEmbeddingResponse;
import com.paymentx.embedding.dto.EmbeddingHealthResponse;
import com.paymentx.embedding.dto.EmbeddingRequest;
import com.paymentx.embedding.dto.EmbeddingResponse;
import com.paymentx.embedding.service.EmbeddingService;
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
 * The entire Phase 3.4 Embedding Service API surface - exactly three
 * endpoints (single embed, batch embed, health), matching Step 8's
 * minimal, honest contract and LLM Service's LlmController shape
 * exactly. All three endpoints are open (no @PreAuthorize) - this
 * service has no mutating/admin operation at all, same reasoning as
 * LlmController's javadoc. Follows the exact `/api/v1/{resource}` +
 * ApiResponse&lt;T&gt; envelope convention every other PaymentX service
 * uses.
 * Why it exists: Step 8's exact endpoint list.
 * How it communicates with other components: this IS the backend
 * endpoint a future Phase 3.5 Vector Database ingestion path will call
 * to turn a document chunk (or user query) into a vector - not called
 * by anything in production yet, same "contract exists before its
 * caller does" pattern Prompt Service's/LLM Service's own controllers
 * document.
 *
 * Hinglish:
 * Poora Phase 3.4 Embedding Service API surface - exactly teen
 * endpoints (single embed, batch embed, health), Step 8 ke minimal,
 * honest contract aur LLM Service ke LlmController shape se exactly
 * match karte hue. Teenon endpoints open hain (@PreAuthorize nahi) - is
 * service me koi mutating/admin operation hai hi nahi, LlmController ke
 * javadoc jaisa hi reasoning. Har doosri PaymentX service ke exact
 * `/api/v1/{resource}` + ApiResponse&lt;T&gt; envelope convention ko
 * follow karta hai.
 * Ye kyu hai: Step 8 ki exact endpoint list.
 * Dusre components se kaise communicate karta hai: yehi backend
 * endpoint hai jise ek future Phase 3.5 Vector Database ingestion path
 * call karega ek document chunk (ya user query) ko ek vector me badalne
 * ke liye - abhi production me kuch bhi ise call nahi karta, wahi
 * "contract apne caller se pehle exist karta hai" pattern jo Prompt
 * Service/LLM Service ke apne controllers document karte hain.
 */
@RestController
@RequestMapping("/api/v1/embeddings")
@RequiredArgsConstructor
@Tag(name = "Embeddings", description = "Real text-to-vector embedding generation and configuration-status health")
public class EmbeddingController {

    private final EmbeddingService embeddingService;

    @PostMapping
    @Operation(summary = "Convert one piece of text into a real vector using the configured embedding provider")
    public ResponseEntity<ApiResponse<EmbeddingResponse>> embed(@Valid @RequestBody EmbeddingRequest request) {
        return ResponseEntity.ok(ApiResponse.success(embeddingService.embed(request)));
    }

    @PostMapping("/batch")
    @Operation(summary = "Convert multiple texts into real vectors in one call, preserving order")
    public ResponseEntity<ApiResponse<BatchEmbeddingResponse>> embedBatch(@Valid @RequestBody BatchEmbeddingRequest request) {
        return ResponseEntity.ok(ApiResponse.success(embeddingService.embedBatch(request)));
    }

    @GetMapping("/health")
    @Operation(summary = "Report whether embedding generation is enabled and an API key is configured - does not make a real provider call")
    public ResponseEntity<ApiResponse<EmbeddingHealthResponse>> health() {
        return ResponseEntity.ok(ApiResponse.success(embeddingService.health()));
    }
}
